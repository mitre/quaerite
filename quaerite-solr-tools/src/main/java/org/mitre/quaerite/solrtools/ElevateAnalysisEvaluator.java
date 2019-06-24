/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.mitre.quaerite.solrtools;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableLong;
import org.apache.log4j.Logger;
import org.mitre.quaerite.analysis.EquivalenceSet;
import org.mitre.quaerite.connectors.SearchClient;
import org.mitre.quaerite.connectors.SearchClientException;
import org.mitre.quaerite.connectors.SearchClientFactory;
import org.mitre.quaerite.core.util.MapUtil;
import org.mitre.quaerite.core.util.StringUtil;

public class ElevateAnalysisEvaluator {
    static Logger LOG = Logger.getLogger(ElevateAnalysisEvaluator.class);

    static Options OPTIONS = new Options();


    static {
        OPTIONS.addOption(
                Option.builder("s")
                        .hasArg().required().desc("solr url").build()
        );

        OPTIONS.addOption(
                Option.builder("e")
                        .longOpt("elevate")
                        .hasArg(true)
                        .required(true)
                        .desc("elevate file (xml)").build()
        );
        OPTIONS.addOption(
                Option.builder("f")
                        .longOpt("field")
                        .hasArg(true)
                        .required(true)
                        .desc("analysis field to use").build()
        );
        OPTIONS.addOption(
                Option.builder("q")
                        .longOpt("queries")
                        .hasArg(true)
                        .required(false)
                        .desc("query file").build()
        );
        //let's say you have a single solr index that hosts
        //several logical indices: "general", "catlovers", "doglovers",
        //and there ids include the logical index, e.g. general-1; catlovers-1
        //you may only want to focus on ids that match this regex:
        //~/(?i)general-\\d+/
        OPTIONS.addOption(
                Option.builder("r")
                        .longOpt("regex")
                        .hasArg(true)
                        .required(false)
                        .desc("regex to subset ids (in case of multiple logical indices stored in a single Solr index)").build()
        );
    }

    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(
                    "java -jar org.mitre.quaerite.solrtools.ElevateQueryComparer",
                    OPTIONS);
            return;
        }

        Matcher elevateIDMatcher = null;
        if (commandLine.hasOption("r")) {
            elevateIDMatcher = Pattern.compile(commandLine.getOptionValue("r")).matcher("");
        }
        SearchClient client = SearchClientFactory.getClient(commandLine.getOptionValue("s"));


        Map<String, Elevate> elevate = ElevateScraper.scrape(
                Paths.get(commandLine.getOptionValue("e")), elevateIDMatcher);

        String field = commandLine.getOptionValue("f");
        ElevateAnalysisEvaluator eval = new ElevateAnalysisEvaluator();

        Path queryFile = null;
        if (commandLine.hasOption("q")) {
            queryFile = Paths.get(commandLine.getOptionValue("q"));
        }
        if (queryFile == null) {
            eval.execute(field, elevate, client);
        } else {
            eval.execute(queryFile, field, elevate, client);
        }
    }

    private void execute(Path queryFile, String field,
                         Map<String, Elevate> elevate,
                         SearchClient client) throws IOException, SearchClientException {
        Map<String, Integer> queries = loadQueries(queryFile);
        Map<String, Integer> queryTokenCounts = new HashMap<>();
        Map<String, EquivalenceSet> elevateEquivalences = equivalate(field, elevate, client, queryTokenCounts);

        int noAnalysisCovered = 0;
        int noAnalysisNotCovered = 0;
        for (Map.Entry<String, Integer> e : queries.entrySet()) {
            if (elevate.containsKey(e.getKey())) {
                noAnalysisCovered += e.getValue();
            } else {
                noAnalysisNotCovered += e.getValue();
            }
        }
        double percentNoAnalysisCovered = (noAnalysisCovered+noAnalysisNotCovered == 0) ? 0.0 :
                100.0* ((double) noAnalysisCovered) / (double) (noAnalysisCovered + noAnalysisNotCovered);
        System.out.println(String.format(Locale.US,
                "Without analysis %,d queries are covered, " +
                        "and %,d queries are not covered.%n",
//                        "That's %.2f%% of queries are covered by the elevate file.",
                noAnalysisCovered, noAnalysisNotCovered));//,percentNoAnalysisCovered));

        Map<String, EquivalenceSet> equivalatedQueries = new HashMap<>();

        for (Map.Entry<String, Integer> q : queries.entrySet()) {
            List<String> tokens = client.analyze(field, q.getKey());
            add(tokens, queryTokenCounts);
            String analyzed = StringUtil.joinWith(" ", tokens);
            EquivalenceSet equivalenceSet = equivalatedQueries.get(analyzed);
            if (equivalenceSet == null) {
                equivalenceSet = new EquivalenceSet();
                equivalatedQueries.put(analyzed, equivalenceSet);
            }
            equivalenceSet.addTerm(q.getKey(), q.getValue());
        }

        long analysisCovered = 0;
        long analysisNotCovered = 0;
        Map<String, EquivalenceSet> unelevatedQueries = new HashMap<>();
        for (Map.Entry<String, EquivalenceSet> e : equivalatedQueries.entrySet()) {
            if (elevateEquivalences.containsKey(e.getKey())) {
                analysisCovered += e.getValue().getTotalCount();
            } else {
                analysisNotCovered += e.getValue().getTotalCount();
                unelevatedQueries.put(e.getKey(), e.getValue());
            }
        }
        double percentAnalysisCovered = (analysisCovered+analysisNotCovered == 0) ? 0.0 :
                100.0* ((double) analysisCovered) / (double) (analysisCovered + analysisNotCovered);

        System.out.println(String.format(Locale.US,
                "Without analysis %,d queries are covered, " +
                        "and %,d queries are not covered.%n",
//                        "That's %.2f%% of queries are covered by the elevate file.",
                analysisCovered, analysisNotCovered));//,         percentAnalysisCovered));

        int topN = 100;
        System.out.println("Top "+topN+" queries not currently covered");
        int i = 0;
        for (Map.Entry<String, EquivalenceSet> e :
                MapUtil.sortByDescendingValue(unelevatedQueries,
                        new EquivalenceSet.DescendingTotalComparator()).entrySet()) {
            if (++i >= topN) {
                break;
            }
            Map<String, MutableLong> sorted = e.getValue().getSortedMap();

                System.out.println(
                        String.format(Locale.US, "%s (%,d): ",
                                e.getKey(), e.getValue().getTotalCount()));
                for (Map.Entry<String, MutableLong> setEntry : sorted.entrySet()) {
                    System.out.println(
                            String.format(Locale.US,
                                    "\t%s (%,d)",
                                    setEntry.getKey(), setEntry.getValue().longValue()));
                }
                i++;

        }
    }


    private void execute(String field, Map<String, Elevate> elevate, SearchClient client) throws IOException, SearchClientException {

        Map<String, Integer> tokenCounts = new HashMap<>();
        Map<String, EquivalenceSet> equivalenceMap = equivalate(field, elevate, client, tokenCounts);

        int totalElevateEntries = elevate.size();
        int gt1 = 0;
        //number in equivSet, count -- key is the equivset size, and the value
        //is how often that happens
        Map<Integer, Integer> counts = new HashMap<>();
        for (Map.Entry<String, EquivalenceSet> e :
                MapUtil.sortByDescendingValue(equivalenceMap).entrySet()) {
            String tokenized = e.getKey();
            EquivalenceSet set = e.getValue();
            Integer cnt = counts.get(set.size());
            if (cnt == null) {
                cnt = 1;
            } else {
                cnt++;
            }
            counts.put(set.size(), cnt);

            Map<String, MutableLong> sorted = set.getSortedMap();
            if (sorted.size() > 1) {
                System.out.println(tokenized + ": ");
                for (Map.Entry<String, MutableLong> setEntry : sorted.entrySet()) {
                    System.out.println("\t" + setEntry.getKey());
                }
                gt1++;
            }
        }
        System.out.println("");
        System.out.println(String.format(Locale.US,
                "%s analyzed elevate entries have more than 1 equivalent entry\n" +
                        "when using the analyzer for field %s",
                gt1, field));

        System.out.println("");
        System.out.println("Equivalent Set Size\tNumber of Entries");
        for (Map.Entry<Integer, Integer> e :
                MapUtil.sortByDescendingValue(counts).entrySet()) {
            System.out.println(String.format(Locale.US,
                    "%s\t%s", e.getKey(), e.getValue()));
        }

        System.out.println("\n\nTop 100 Elevate Terms");
        int i = 0;
        for (Map.Entry<String, Integer> e :
                MapUtil.sortByDescendingValue(tokenCounts).entrySet()) {
            if (++i >= 100) {
                break;
            }
            System.out.println(e.getKey() + "\t" + e.getValue());

        }
    }

    private Map<String, EquivalenceSet> equivalate(String field,
                                                   Map<String, Elevate> elevate,
                                                   SearchClient client, Map<String, Integer> tokenCounts) throws IOException, SearchClientException {
        Map<String, EquivalenceSet> equivalenceMap = new HashMap<>();
        for (String q : elevate.keySet()) {
            List<String> tokens = client.analyze(field, q);
            add(tokens, tokenCounts);
            String analyzed = StringUtil.joinWith("", tokens);
            EquivalenceSet equivalenceSet = equivalenceMap.get(analyzed);
            if (equivalenceSet == null) {
                equivalenceSet = new EquivalenceSet();
                equivalenceMap.put(analyzed, equivalenceSet);
            }
            equivalenceSet.addTerm(q, 1);
        }
        return equivalenceMap;
    }

    private void add(List<String> tokens, Map<String, Integer> tokenCounts) {
        for (String t : tokens) {
            Integer cnt = tokenCounts.get(t);
            if (cnt == null) {
                cnt = 1;
            } else {
                cnt++;
            }
            tokenCounts.put(t, cnt);
        }
    }

    private Map<String, Integer> loadQueries(Path queryFile) throws IOException {
        Map<String, Integer> queries = new HashMap<>();
        try (Reader reader = new InputStreamReader(
                new BOMInputStream(Files.newInputStream(queryFile)), "UTF-8")) {
            Iterable<CSVRecord> records = CSVFormat.EXCEL
                    .withFirstRecordAsHeader().parse(reader);
            boolean hasCount = false;
            if ((((CSVParser) records)).getHeaderMap().containsKey("count")) {
                hasCount = true;
            }

            for (CSVRecord r : records) {
                String query = r.get("query");

                query = query.toLowerCase(Locale.US);
                int cnt = 1;
                if (hasCount) {
                    String count = r.get("count");
                    cnt = Integer.parseInt(count);
                }
                Integer existing = queries.get(query);
                if (existing != null) {
                    cnt += existing;
                }
                queries.put(query, cnt);
            }
        }
        return queries;

    }
}
