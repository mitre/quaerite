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
 */
package org.mitre.quaerite.analysis;

import static org.mitre.quaerite.core.util.CommandLineUtil.getInt;
import static org.mitre.quaerite.core.util.CommandLineUtil.getLong;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableLong;
import org.apache.log4j.Logger;
import org.mitre.quaerite.connectors.SearchClient;
import org.mitre.quaerite.connectors.SearchClientException;
import org.mitre.quaerite.connectors.SearchClientFactory;
import org.mitre.quaerite.core.stats.TokenDF;
import org.mitre.quaerite.core.util.CommandLineUtil;
import org.mitre.quaerite.core.util.MapUtil;

public class CompareAnalyzers {

    private static int DEFAULT_NUM_THREADS = 10;
    private static int DEFAULT_MIN_SET_SIZE = 1;
    private static long DEFAULT_MIN_DF = 0;

    static Options OPTIONS = new Options();
    static Logger LOG = Logger.getLogger(SearchClient.class);

    static {
        OPTIONS.addOption(
                Option.builder("s")
                        .hasArg()
                        .required()
                        .desc("search server").build()
        );
        OPTIONS.addOption(Option.builder("bf")
                .longOpt("baseField")
                .hasArg(true)
                .required()
                .desc("baseField").build()
        );
        OPTIONS.addOption(
                Option.builder("ff").longOpt("filteredField")
                        .hasArg()
                        .required()
                        .desc("filtered field").build()
        );
        OPTIONS.addOption(
                Option.builder("q")
                        .longOpt("queries")
                        .hasArg()
                        .required(false)
                        .desc("query csv file to filter results -- UTF-8 csv with at least 'query' column header").build()
        );
        OPTIONS.addOption(
                Option.builder("n")
                        .longOpt("numThreads")
                        .hasArg()
                        .required(false)
                        .desc("number of threads").build()
        );
        OPTIONS.addOption(
                Option.builder("minSetSize")
                        .longOpt("minEquivalenceSetSize")
                        .hasArg()
                        .required(false)
                        .desc("minimum size for an equivalence set (default ="+
                                DEFAULT_MIN_SET_SIZE+")").build()
        );
        OPTIONS.addOption(
                Option.builder("minDF")
                        .longOpt("minDocumentFrequency")
                        .hasArg()
                        .required(false)
                        .desc("minimum document frequency (default = 0)").build()
        );
    }
    private int numThreads = DEFAULT_NUM_THREADS;
    private Set<String> targetTokens = Collections.EMPTY_SET;
    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(
                    "java -jar org.mitre.quaerite.analysis.CompareAnalyzers",
                    OPTIONS);
            return;
        }
        SearchClient client = SearchClientFactory.getClient(commandLine.getOptionValue("s"));

        CompareAnalyzers compareAnalyzers = new CompareAnalyzers();
        int minSetSize = getInt(commandLine, "minSetSize", DEFAULT_MIN_SET_SIZE);
        long minDF = getLong(commandLine, "minDF", DEFAULT_MIN_DF);
        compareAnalyzers.setNumThreads(
                getInt(commandLine, "n", DEFAULT_NUM_THREADS));

        String filteredField = commandLine.getOptionValue("ff");
        String baseField = commandLine.getOptionValue("bf");
        Set<String> targetTokens;
        List<QueryTokenPair> queryTokenPairs;
        if (commandLine.hasOption("q")) {
            targetTokens = ConcurrentHashMap.newKeySet();
            queryTokenPairs = loadQueries(
                    CommandLineUtil.getPath(commandLine, "q", true),
                    client, baseField, filteredField);
            for (QueryTokenPair p : queryTokenPairs) {
                targetTokens.addAll(p.getTokens());
            }
        } else {
            targetTokens = Collections.EMPTY_SET;
            queryTokenPairs = Collections.EMPTY_LIST;
        }
        compareAnalyzers.setTargetTokens(targetTokens);

        Map<String, EquivalenceSet> map = compareAnalyzers.compare(
                client,
                baseField,
                filteredField);

        for (Map.Entry<String, EquivalenceSet> e : map.entrySet()) {
            if (e.getValue().getMap().size() > minSetSize) {
                boolean printed = false;
                for (Map.Entry<String, MutableLong> orig : e.getValue().getSortedMap().entrySet()) {
                    if (orig.getValue().longValue() < minDF) {
                        continue;
                    }
                    if (! printed) {
                        System.out.println(e.getKey());
                        printed = true;
                    }
                    System.out.println("\t" + orig.getKey() + ": " + orig.getValue());
                }
            }
        }
        System.out.println("\n\nQUERIES...\n\n\n");

        int maxEquivalences = 10;
        for (QueryTokenPair q : queryTokenPairs) {
            System.out.println(q.query);
            for (String token : q.getTokens()) {
                EquivalenceSet e = map.get(token);
                if (e == null) {
                    System.out.println("\t"+token);
                } else {
                    boolean printed = false;
                    int equivs = 0;
                    for (Map.Entry<String, MutableLong> orig : e.getSortedMap().entrySet()) {
                        if (! printed) {
                            System.out.println("\t" + token);
                            printed = true;
                        }
                        System.out.println("\t\t" + orig.getKey() + ": " + orig.getValue());
                        if (equivs++ >= maxEquivalences) {
                            System.out.println("\t\t...");
                            break;
                        }
                    }
                }
            }
            System.out.println("\n");
        }
    }

    private void setTargetTokens(Set<String> targetTokens) {
        this.targetTokens = targetTokens;
    }

    private static List<QueryTokenPair> loadQueries(Path path,
                                                    SearchClient searchClient,
                                                    String baseField, String filterField) throws IOException, SearchClientException {

        Set<String> queries = new HashSet<>();
        try (InputStream is = Files.newInputStream(path)) {
            try (Reader reader = new InputStreamReader(new BOMInputStream(is), "UTF-8")) {
                Iterable<CSVRecord> records = CSVFormat.EXCEL
                        .withFirstRecordAsHeader().parse(reader);
                for (CSVRecord record : records) {
                    String query = record.get("query");
                    queries.add(query);
                }
            }
        }
        List<QueryTokenPair> queryTokenPairs = new ArrayList<>();
        int max = 0;
        for (String query : queries) {
            List<String> baseAnalyzed = searchClient.analyze(baseField, query);
            List<String> allFiltered = new ArrayList<>();
            for (String baseToken : baseAnalyzed) {
                List<String> filtered = searchClient.analyze(filterField, baseToken);
                if (filtered.size() == 0) {
                    filtered.add("");
                }
                allFiltered.add(StringUtils.join(filtered, ", "));
            }
            queryTokenPairs.add(
                    new QueryTokenPair(query, allFiltered)
            );
        }
        return queryTokenPairs;
    }

    private void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }

    public Map<String, EquivalenceSet> compare(SearchClient client,
                                               String baseField,
                                               String filteredField) {

        ArrayBlockingQueue<Set<TokenDF>> queue = new ArrayBlockingQueue<>(100);
        List<ReAnalyzer> reAnalyzers = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            reAnalyzers.add(new ReAnalyzer(queue, client, filteredField));
        }

        ExecutorService executorService = Executors.newFixedThreadPool(numThreads+1);
        ExecutorCompletionService<Integer> completionService = new ExecutorCompletionService<>(executorService);
        completionService.submit(new TermGetter(queue, numThreads, client, baseField));
        for (int i = 0; i < numThreads; i++) {
            completionService.submit(reAnalyzers.get(i));
        }
        //map
        int completed = 0;
        int totalAnalyzed = 0;
        while (completed < numThreads+1) {
            try {
                Future<Integer> future = completionService.poll(1, TimeUnit.SECONDS);
                if (future != null) {
                    int analyzed = future.get();
                    if (analyzed > 0) {
                        totalAnalyzed += analyzed;
                    }
                    completed++;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        LOG.info("Analyzed "+totalAnalyzed);
        executorService.shutdownNow();
        //reduce
        Map<String, EquivalenceSet> overall = new HashMap<>();
        for (ReAnalyzer reAnalyzer : reAnalyzers) {
            reduce(reAnalyzer.getMap(), overall);
        }

        return MapUtil.sortByDescendingValue(overall);
    }

    private void reduce(Map<String, EquivalenceSet> src,
                        Map<String, EquivalenceSet> overall) {
        for (Map.Entry<String, EquivalenceSet> e : src.entrySet()) {
            String filtered = e.getKey();
            EquivalenceSet overallEs = overall.get(filtered);
            if (overallEs == null) {
                overall.put(filtered, e.getValue());
            } else {
                mergeInto(e.getValue(), overallEs);
            }
        }
    }

    private void mergeInto(EquivalenceSet es, EquivalenceSet overallEs) {
        for (Map.Entry<String, MutableLong> e : es.getMap().entrySet()) {
            overallEs.addTerm(e.getKey(), e.getValue().longValue());
        }
    }

    private static class TermGetter implements Callable<Integer> {

        private final int termSetSize = 100;
        private final int minDF = 0;
        private final ArrayBlockingQueue<Set<TokenDF>> queue;
        private final int numThreads;
        private final SearchClient client;
        private final String field;

        public TermGetter(ArrayBlockingQueue<Set<TokenDF>> queue, int numThreads,
                          SearchClient client, String field) {
            this.queue = queue;
            this.numThreads = numThreads;
            this.client = client;
            this.field = field;
        }

        @Override
        public Integer call() throws Exception {
            String lower = "";

            while (true) {
                List<TokenDF> terms = client.getTerms(field, lower, termSetSize, minDF);
                if (terms.size() == 0) {
                    break;
                }
                Set<TokenDF> tdf = new HashSet<>(terms);
                boolean added = queue.offer(tdf, 1, TimeUnit.SECONDS);
                while (added == false) {
                    added = queue.offer(tdf, 1, TimeUnit.SECONDS);
                    LOG.debug("waiting to offer");
                }

                lower = terms.get(terms.size() - 1).getToken();
            }
            for (int i = 0; i < numThreads; i++) {
                boolean added = queue.offer(Collections.EMPTY_SET, 1, TimeUnit.SECONDS);
                while (added == false) {
                    added = queue.offer(Collections.EMPTY_SET, 1, TimeUnit.SECONDS);
                    LOG.debug("waiting to offer poison");
                }
            }
            return -1;
        }
    }

    private class ReAnalyzer implements Callable<Integer> {

        private final ArrayBlockingQueue<Set<TokenDF>> queue;
        private final SearchClient client;
        private final String field;
        private final Map<String, EquivalenceSet> equivalenceMap = new HashMap<>();

        public ReAnalyzer(ArrayBlockingQueue<Set<TokenDF>> queue, SearchClient client, String field) {
            this.queue = queue;
            this.client = client;
            this.field = field;
        }

        @Override
        public Integer call() throws Exception {
            int analyzed = 0;
            while (true) {
                Set<TokenDF> set = queue.take();
                if (set != null) {
                    if (set.size() == 0) {
                        break;
                    }
                    for (TokenDF tdf : set) {
                        String filtered = analyze(client, field, tdf.getToken());
                        analyzed++;
                        if (filtered == null) {
                            continue;
                        }
                        if (targetTokens.size() == 0 || targetTokens.contains(filtered)) {
                            EquivalenceSet es = equivalenceMap.get(filtered);
                            if (es == null) {
                                es = new EquivalenceSet();
                                es.addTerm(tdf.getToken(), tdf.getDf());
                                equivalenceMap.put(filtered, es);
                            } else {
                                es.addTerm(tdf.getToken(), tdf.getDf());
                            }
                        }
                    }
                }
            }
            return analyzed;
        }

        private String analyze(SearchClient client, String field, String s) {
            List<String> tokens = null;
            try {
                tokens = client.analyze(field, s);
            } catch (IOException|SearchClientException e) {
                LOG.warn(e);
                return null;
            }
            return StringUtils.join(tokens, "|");
        }

        public Map<String, EquivalenceSet> getMap() {
            return equivalenceMap;
        }
    }


    private static class QueryTokenPair {
        private final String query;
        private final List<String> filteredTokens;

        public QueryTokenPair(String query, List<String> filteredTokens) {
            this.query = query;
            this.filteredTokens = filteredTokens;
        }

        public List<String> getTokens() {
            return filteredTokens;
        }
    }
}
