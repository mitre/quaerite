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
package org.mitre.quaerite.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.mitre.quaerite.FacetResult;
import org.mitre.quaerite.JudgmentList;
import org.mitre.quaerite.Judgments;
import org.mitre.quaerite.connectors.QueryRequest;
import org.mitre.quaerite.connectors.SearchClient;
import org.mitre.quaerite.connectors.SearchClientFactory;
import org.mitre.quaerite.db.ExperimentDB;
import org.mitre.quaerite.stats.ContrastResult;
import org.mitre.quaerite.stats.YatesChi;

public class FindFeatures {

    static Options OPTIONS = new Options();
    private static NumberFormat NUMBER_FORMAT = new DecimalFormat("#.##");

    static {
        OPTIONS.addOption(
                Option.builder("db")
                        .hasArg()
                        .required().desc("database folder").build()
        );
        OPTIONS.addOption(
                Option.builder("s")
                        .longOpt("searchServerUrl")
                        .hasArg()
                        .required()
                        .desc("search server's url").build()
        );
        OPTIONS.addOption(
                Option.builder("f").longOpt("fields")
                        .hasArg()
                        .desc("comma-delimited list of fields").build()
        );
        OPTIONS.addOption(
                Option.builder("fq")
                        .longOpt("filterQuery")
                        .hasArg()
                        .required(false)
                        .desc("filter query to run to subset data").build()
        );
    }

    private YatesChi yatesChi = new YatesChi();

    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(
                    "java -jar org.mitre.quaerite.cli.FindFeatures",
                    OPTIONS);
            return;
        }
        String searchServerUrl = commandLine.getOptionValue("s");
        Path dbDir = Paths.get(commandLine.getOptionValue("db"));
        ExperimentDB db = ExperimentDB.open(dbDir);
        SearchClient searchClient = SearchClientFactory.getClient(searchServerUrl);
        String[] fields = commandLine.getOptionValue("f").split(",");
        String filterQuery = null;
        if (commandLine.hasOption("fq")) {
            filterQuery = commandLine.getOptionValue("fq");
        }
        FindFeatures findFeatures = new FindFeatures();
        findFeatures.execute(db, searchClient, fields, filterQuery);
    }

    private void execute(ExperimentDB db, SearchClient searchClient, String[] fields,
                         String filterQuery) throws Exception {
        JudgmentList judgmentList = db.getJudgments();
        String idField = judgmentList.getIdField();
        List<Judgments> judgments = judgmentList.getJudgmentsList();
        Set<String> ids = new HashSet<>();
        for (Judgments j : judgments) {
            Set<String> localIds = j.getSortedJudgments().keySet();
            ids.addAll(localIds);
        }
        for (String f : fields) {
            FacetResult truthCounts = getFacets(f, idField, ids, searchClient);
            QueryRequest sq = new QueryRequest("*:*");
            sq.addParameter("fq", filterQuery);
            FacetResult backgroundCounts = getFacets(f, sq, searchClient);
            List<ContrastResult> chis = getChis(truthCounts, backgroundCounts);
            reportResult(f, chis);
        }
    }

    private FacetResult getFacets(String f, String idField, Set<String> ids,
                                  SearchClient searchClient) throws Exception {
        Map<String, Long> ret = new HashMap<>();
        List<String> cache = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        int cnt = 0;
        for (String id : ids) {
            if (cnt++ > 0) {
                sb.append(" OR ");
            }
            sb.append(idField).append(":").append(id);
            if (sb.length() > 1000) {
                addAll(getFacets(f, new QueryRequest(sb.toString()), searchClient).getFacetCounts(), ret);
                sb.setLength(0);
                cnt = 0;
            }
        }
        if (sb.length() > 0) {
            addAll(getFacets(f, new QueryRequest(sb.toString()), searchClient).getFacetCounts(), ret);
        }
        return new FacetResult(ids.size(), ret);
    }

    private void addAll(Map<String, Long> facetCounts, Map<String, Long> ret) {
        for (Map.Entry<String, Long> e : facetCounts.entrySet()) {
            Long val = ret.get(e.getKey());
            val = (val == null) ? 0l : val;
            val += e.getValue();
            ret.put(e.getKey(), val);
        }
    }

    private void reportResult(String field, List<ContrastResult> chis) {
        System.out.println(field + ":");
        int reported = 0;
        for (ContrastResult chi : chis) {
            if (++reported >= 100) {
                break;
            }
            System.out.println("\t" + chi);
        }
    }

    private List<ContrastResult> getChis(FacetResult foreground, FacetResult background) {
        Map<String, Long> tmpB = new HashMap<>();
        tmpB.putAll(background.getFacetCounts());
        //discount those in the foreground set
        for (Map.Entry<String, Long> e : foreground.getFacetCounts().entrySet()) {
            Long backgroundCnt = tmpB.get(e.getKey());
            if (backgroundCnt != null) {
                backgroundCnt -= e.getValue();
                tmpB.put(e.getKey(), backgroundCnt);
            }
        }

        long totalDocs = background.getTotalDocs() - foreground.getTotalDocs();
        List<ContrastResult> ret = new ArrayList<>();
        for (Map.Entry<String, Long> e : foreground.getFacetCounts().entrySet()) {
            long a = e.getValue();
            Long b = tmpB.get(e.getKey());
            b = (b == null) ? 0 : b;
            double c = foreground.getTotalDocs() - a;
            double d = totalDocs - b;
            double chi = (a == 0) ? 0.0 : yatesChi.calculateValue(a, (double) b, c, d);
            if (a == 0L && b == 0L) {
                //skip
            } else {
                ret.add(new ContrastResult(e.getKey(), a, foreground.getTotalDocs(), b, totalDocs, chi));
            }
        }
        Collections.sort(ret);
        return ret;
    }

    private FacetResult getFacets(String facetFieldName, QueryRequest queryRequest,
                                  SearchClient searchClient) throws Exception {
        queryRequest.addFacetField(facetFieldName);
        queryRequest.setFacetLimit(10000);
        queryRequest.setNumResults(0);
        return searchClient.facet(queryRequest);
    }

}
