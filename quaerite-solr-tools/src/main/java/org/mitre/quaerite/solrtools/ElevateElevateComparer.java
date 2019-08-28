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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;
import org.mitre.quaerite.connectors.SearchClient;
import org.mitre.quaerite.connectors.SearchClientException;
import org.mitre.quaerite.connectors.SearchClientFactory;
import org.xml.sax.SAXException;

public class ElevateElevateComparer {
    static Logger LOG = Logger.getLogger(ElevateAnalysisEvaluator.class);

    static Options OPTIONS = new Options();


    static {
        OPTIONS.addOption(
                Option.builder("s")
                        .hasArg().required().desc("solr url").build()
        );

        OPTIONS.addOption(
                Option.builder("f")
                        .longOpt("field")
                        .hasArg(true)
                        .required(true)
                        .desc("field to use to normalize the queries").build()
        );

        OPTIONS.addOption(
                Option.builder("e1")
                        .longOpt("elevate1")
                        .hasArg(true)
                        .required(true)
                        .desc("baseline elevate file (xml)").build()
        );
        OPTIONS.addOption(
                Option.builder("e2")
                        .longOpt("elevate2")
                        .hasArg(true)
                        .required(true)
                        .desc("candidate elevate file (xml)").build()
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
                        .desc("regex to subset ids (in case of multiple logical " +
                                "indices stored in a single Solr index)").build()
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
        Matcher idMatcher = null;
        if (commandLine.hasOption("r")) {
            idMatcher = Pattern.compile(commandLine.getOptionValue("r")).matcher("");
        }

        SearchClient client = SearchClientFactory.getClient(commandLine.getOptionValue("s"));
        String analysisField = commandLine.getOptionValue("f");
        ElevateElevateComparer comparer = new ElevateElevateComparer();
        comparer.compare(Paths.get(commandLine.getOptionValue("e1")),
                Paths.get(commandLine.getOptionValue("e2")),
                idMatcher,
                client,
                analysisField);
    }

    private void compare(Path baselinePath, Path candidatePath, Matcher idMatcher,
                         SearchClient searchClient, String analysisField) throws Exception {
        Map<String, List<Elevate>> baseline =
                loadElevate(baselinePath, idMatcher, searchClient, analysisField);
        Map<String, List<Elevate>> candidate =
                loadElevate(candidatePath, idMatcher, searchClient, analysisField);

        for (String q : candidate.keySet()) {
            if (! baseline.containsKey(q)) {
                System.out.println("baseline missing:\t" + q);
            } else {
                System.out.println("baseline contains:\t" + q);
            }
        }
        for (String q : candidate.keySet()) {
            if (baseline.containsKey(q)) {
                List<String> couldAdd = new ArrayList<>();
                Set<String> baselineIds = getIds(baseline.get(q));
                Set<String> candidateIds = getIds(candidate.get(q));
                for (String cand : candidateIds) {
                    if (! baselineIds.contains(cand)) {
                        couldAdd.add(cand);
                    }
                }

                if (couldAdd.size() > 0) {
                    System.out.println(q);
                    for (String id : baselineIds) {
                        System.out.println("\texisting\t" + id);
                    }
                    for (String id : couldAdd) {
                        if (!baselineIds.contains(id)) {
                            System.out.println("\tcandidate\t" + id);
                        }
                    }
                }
            }
        }

    }

    private Set<String> getIds(List<Elevate> elevates) {
        Set<String> ids = new LinkedHashSet<>();
        for (Elevate e : elevates) {
            for (String id : e.getIds()) {
                ids.add(id);
            }
        }
        return ids;
    }

    private Map<String, List<Elevate>> loadElevate(Path path, Matcher idMatcher,
                                                   SearchClient searchClient,
                                                   String analysisField)
            throws ParserConfigurationException,
            SAXException,
            IOException, SearchClientException {
        return WinnowAnalyzedElevate.analyze(ElevateScraper.scrape(path, idMatcher),
                searchClient, analysisField);
    }


}
