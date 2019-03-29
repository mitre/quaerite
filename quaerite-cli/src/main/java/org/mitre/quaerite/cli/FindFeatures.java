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

import static org.mitre.quaerite.core.util.CommandLineUtil.getPath;
import static org.mitre.quaerite.core.util.CommandLineUtil.getString;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.stat.inference.ChiSquareTest;
import org.mitre.quaerite.core.ExperimentConfig;
import org.mitre.quaerite.core.FacetResult;
import org.mitre.quaerite.core.JudgmentList;
import org.mitre.quaerite.core.Judgments;
import org.mitre.quaerite.connectors.QueryRequest;
import org.mitre.quaerite.connectors.SearchClient;
import org.mitre.quaerite.connectors.SearchClientFactory;
import org.mitre.quaerite.db.ExperimentDB;
import org.mitre.quaerite.core.stats.ContrastResult;

public class FindFeatures extends AbstractCLI {

    static Options OPTIONS = new Options();

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
        OPTIONS.addOption(
                Option.builder("j")
                        .longOpt("judgments")
                        .hasArg(true)
                        .required(false)
                        .desc("judgment .csv file (optional as long as judgements have been loaded earlier!)").build()
        );
        OPTIONS.addOption(
                Option.builder("id")
                        .hasArg()
                        .required(false)
                        .desc("field name for id field for judgments file (optional; default: 'id')").build()
        );

    }
    ChiSquareTest chi = new ChiSquareTest();
    private NumberFormat decimalFormat = new DecimalFormat("0.000",
            DecimalFormatSymbols.getInstance(Locale.US));

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
        Path judgmentsFile = getPath(commandLine, "j", false);
        ExperimentDB db = ExperimentDB.open(dbDir);
        if (judgmentsFile != null) {
            loadJudgments(db, judgmentsFile, true);
        }
        SearchClient searchClient = SearchClientFactory.getClient(searchServerUrl);
        String[] fields = commandLine.getOptionValue("f").split(",");
        String idField = getString(commandLine, "id",
                ExperimentConfig.DEFAULT_SEARCH_SERVER_ID_FIELD);
        String filterQuery = null;
        if (commandLine.hasOption("fq")) {
            filterQuery = commandLine.getOptionValue("fq");
        }
        FindFeatures findFeatures = new FindFeatures();
        findFeatures.execute(db, searchClient, fields, filterQuery);
    }

    private void execute(ExperimentDB db, SearchClient searchClient,
                         String[] fields,
                         String filterQuery) throws Exception {
        JudgmentList judgmentList = db.getJudgments();

        List<Judgments> judgments = judgmentList.getJudgmentsList();
        Set<String> ids = new HashSet<>();
        for (Judgments j : judgments) {
            Set<String> localIds = j.getSortedJudgments().keySet();
            ids.addAll(localIds);
        }
        String idField = searchClient.getIdField();
        for (String f : fields) {
            FacetResult targetCounts = getFacets(f, idField, ids, filterQuery, searchClient);
            QueryRequest sq = new QueryRequest("*:*");
            sq.addField(idField);
            if (filterQuery != null) {
                sq.addParameter("fq", filterQuery);
            }
            FacetResult backgroundCounts = getFacets(f, sq, searchClient);
            List<ContrastResult> chis = getChis(targetCounts, backgroundCounts);
            reportResult(f, chis);
        }
    }

    private FacetResult getFacets(String f, String idField, Set<String> ids,
                                  String filterQuery, SearchClient searchClient) throws Exception {
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
                QueryRequest qr = new QueryRequest(sb.toString());
                qr.addField(id);
                if (filterQuery != null) {
                    qr.addParameter("fq", filterQuery);
                }
                addAll(getFacets(f, qr, searchClient).getFacetCounts(), ret);
                sb.setLength(0);
                cnt = 0;
            }
        }
        if (sb.length() > 0) {
            QueryRequest qr = new QueryRequest(sb.toString());
            qr.addField(idField);
            if (filterQuery != null) {
                qr.addParameter("fq", filterQuery);
            }
            addAll(getFacets(f, qr, searchClient).getFacetCounts(), ret);
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
            if (++reported >= 10) {
                break;
            }
            String targPercent = (chi.getTargTotal() == 0L) ? "" :
                    String.format(Locale.US, "%.2f%%", ((double)chi.getTargCount()/(double)chi.getTargTotal())*100.0f);
            String otherPercent = (chi.getOtherTotal() == 0L) ? "" :
                    String.format(Locale.US, "%.2f%%", ((double)chi.getOtherCount()/(double)chi.getOtherTotal())*100.0f);

            System.out.println(StringUtils.join(new String[]{
                    "\tfacet_value="+chi.getTerm(),
                    "\t\ttargCount="+chi.getTargCount(),
                    "\t\ttargTotal="+chi.getTargTotal(),
                    "\t\ttargPercent="+targPercent,
                    "\t\tbackgroundCount="+chi.getOtherCount(),
                    "\t\tbackgroundTotal="+chi.getOtherTotal(),
                    "\t\tbackgroundPercent="+otherPercent,
                    "\t\tcontrastValue="+decimalFormat.format(chi.getContrastValue())

            }, '\n'));
        }
    }

    private List<ContrastResult> getChis(FacetResult foreground, FacetResult background) {
        Map<String, Long> tmpB = new HashMap<>();
        tmpB.putAll(background.getFacetCounts());
/*        //discount those in the foreground set
        for (Map.Entry<String, Long> e : foreground.getFacetCounts().entrySet()) {
            Long backgroundCnt = tmpB.get(e.getKey());
            if (backgroundCnt != null) {
                backgroundCnt -= e.getValue();
                tmpB.put(e.getKey(), backgroundCnt);
            }
        }
*/
        long totalDocs = background.getTotalDocs();
        List<ContrastResult> ret = new ArrayList<>();
        for (Map.Entry<String, Long> e : foreground.getFacetCounts().entrySet()) {
            long a = e.getValue();
            Long b = background.getFacetCounts().get(e.getKey());
            b = (b == null) ? 0 : b;
            long c = foreground.getTotalDocs() - a;
            long d = totalDocs - b;
            double chi = (a == 0) ? 0.0 : chi(a, b, c, d);
            if (a == 0L && b == 0L) {
                //skip
            } else {
                ret.add(new ContrastResult(e.getKey(), a, foreground.getTotalDocs(), b, totalDocs, chi));
            }
        }
        Collections.sort(ret);
        return ret;
    }

    private double chi(long a, Long b, long c, long d) {
        long[][] twoByTwo = new long[2][2];
        twoByTwo[0][0] = a;
        twoByTwo[0][1] = b;
        twoByTwo[1][0] = c;
        twoByTwo[1][1] = d;
        return chi.chiSquare(twoByTwo);
    }

    private FacetResult getFacets(String facetFieldName, QueryRequest queryRequest,
                                  SearchClient searchClient) throws Exception {
        queryRequest.setFacetField(facetFieldName);
        queryRequest.setFacetLimit(10000);
        queryRequest.setNumResults(0);
        return searchClient.facet(queryRequest);
    }

}
