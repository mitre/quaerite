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
package org.mitre.quaerite.tools;

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
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.mitre.quaerite.JudgmentList;
import org.mitre.quaerite.Judgments;
import org.mitre.quaerite.db.ExperimentDB;
import org.mitre.quaerite.stats.ContrastResult;
import org.mitre.quaerite.stats.YatesChi;

public class FindFeatures {

    static Options OPTIONS = new Options();
    private static NumberFormat NUMBER_FORMAT = new DecimalFormat("#.##");

    static {
        OPTIONS.addOption("db", "db", true, "database folder");
        OPTIONS.addOption("s", "solr", true, "solr url");
        OPTIONS.addOption("f", "fields", true, "comma-delimited list of fields");
        OPTIONS.addOption("fq", "filterQuery", true, "filter query to run to subset data");
    }
    private YatesChi yatesChi = new YatesChi();
    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new GnuParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(
                    "java -jar org.mitre.quaerite.tools.FindFeatures",
                    OPTIONS);
            return;
        }
        String solrUrl = commandLine.getOptionValue("s");
        Path dbDir = Paths.get(commandLine.getOptionValue("db"));
        ExperimentDB db = ExperimentDB.open(dbDir);
        SolrServer solrServer = new HttpSolrServer(solrUrl);
        String[] fields = commandLine.getOptionValue("f").split(",");
        String filterQuery = null;
        if (commandLine.hasOption("fq")) {
            filterQuery = commandLine.getOptionValue("fq");
        }
        FindFeatures findFeatures = new FindFeatures();
        findFeatures.execute(db, solrServer, fields, filterQuery);
    }

    private void execute(ExperimentDB db, SolrServer solrServer, String[] fields,
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
            FacetResult truthCounts = getFacets(f, idField, ids, solrServer);
            SolrQuery sq = new SolrQuery("*:*");
            sq.addFilterQuery(filterQuery);
            FacetResult backgroundCounts = getFacets(f, sq, solrServer);
            List<ContrastResult> chis = getChis(truthCounts, backgroundCounts);
            reportResult(f, chis);
        }
    }

    private FacetResult getFacets(String f, String idField, Set<String> ids,
                                  SolrServer solrServer) throws Exception {
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
                addAll(getFacets(f, new SolrQuery(sb.toString()), solrServer).getFacetCounts(), ret);
                sb.setLength(0);
                cnt = 0;
            }
        }
        if (sb.length() > 0) {
            addAll(getFacets(f, new SolrQuery(sb.toString()), solrServer).getFacetCounts(), ret);
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
        System.out.println(field +":");
        int reported = 0;
        for (ContrastResult chi : chis) {
            if (++reported >= 100) {
                break;
            }
            System.out.println("\t"+chi);
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
            double c = foreground.getTotalDocs()-a;
            double d = totalDocs-b;
            double chi = (a == 0) ? 0.0 : yatesChi.calculateValue(a, (double)b, c, d);
            if ( a == 0L && b == 0L) {
                //skip
            } else {
                ret.add(new ContrastResult(e.getKey(), a, foreground.getTotalDocs(), b, totalDocs, chi));
            }
        }
        Collections.sort(ret);
        return ret;
    }

    private FacetResult getFacets(String facetFieldName, SolrQuery solrQuery,
                                           SolrServer solrServer) throws Exception {
        solrQuery.addFacetField(facetFieldName);
        solrQuery.setFacetMissing(true);
        solrQuery.setFacet(true);
        solrQuery.setFacetLimit(10000);
        solrQuery.setRows(0);
        //System.out.println(solrQuery);

        QueryResponse queryResponse = solrServer.query(solrQuery);
        long totalDocs = queryResponse.getResults().getNumFound();
        FacetField facetField = queryResponse.getFacetField(facetFieldName);
        Map<String, Long> cnts = new HashMap<>();
        for (FacetField.Count cnt : facetField.getValues()) {
            cnts.put(cnt.getName(), cnt.getCount());
        }
        return new FacetResult(totalDocs, cnts);
    }


    private static class FacetResult {
        private final long totalDocs;
        private final Map<String, Long> facetCounts;

        public FacetResult(long totalDocs, Map<String, Long> facetCounts) {
            this.totalDocs = totalDocs;
            this.facetCounts = facetCounts;
        }

        public long getTotalDocs() {
            return totalDocs;
        }

        public Map<String, Long> getFacetCounts() {
            return facetCounts;
        }
    }
}
