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
package org.mitre.quaerite.cli;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.apache.commons.math3.stat.inference.TTest;
import org.mitre.quaerite.db.ExperimentDB;
import org.mitre.quaerite.util.MapUtil;

public class DumpResults {

    static Options OPTIONS = new Options();
    private static final String DEFAULT_SCORER = "ndcg_10";

    static {
        OPTIONS.addOption(
                Option.builder("db")
                        .hasArg().required().desc("database folder").build()
        );

        OPTIONS.addOption(
                Option.builder("d")
                        .longOpt("resultsDir")
                        .hasArg()
                        .required()
                        .desc("result file directory").build()
        );
        OPTIONS.addOption(
                Option.builder("q")
                        .longOpt("querySets")
                        .hasArg()
                        .required()
                        .desc("querySets to dump in rollups (comma-delimited)").build()
        );
        OPTIONS.addOption(
                Option.builder("s")
                        .longOpt("scorers")
                        .hasArg()
                        .required(false)
                        .desc("scorers to dump in statistical " +
                                "significance matrices (comma-delimited)").build()
        );
    }

    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(
                    "java -jar org.mitre.quaerite.cli.AddExperiments",
                    OPTIONS);
            return;
        }
        Path outputDir = Paths.get(commandLine.getOptionValue("d"));
        Path dbDir = Paths.get(commandLine.getOptionValue("db"));
        List<String> scorers = new ArrayList<>();
        if (commandLine.hasOption("s")) {
            scorers.addAll(
                    Arrays.asList(commandLine.getOptionValue("s").split(",")));
        } else {
            scorers.add(DEFAULT_SCORER);
        }
        List<String> querySets = new ArrayList<>();
        if (commandLine.hasOption("q")) {
            querySets.addAll(
                    Arrays.asList(commandLine.getOptionValue("q").split(","))
            );
        } else {
            querySets.add("");
        }
        Files.createDirectories(outputDir);
        dump(querySets, scorers, outputDir, dbDir);
    }

    private static void dump(List<String> querySets, List<String> targetScorers, Path outputDir, Path dbDir) throws Exception {
        try (ExperimentDB experimentDB = ExperimentDB.open(dbDir)) {
            try (BufferedWriter writer = Files.newBufferedWriter(outputDir.resolve("per_query_scores.csv"), StandardCharsets.UTF_8)) {
                try (Statement st = experimentDB.getConnection().createStatement()) {
                    String select = experimentDB.hasNamedQuerySets() ?
                            "select * from SCORES where QUERY_SET <> ''" : "select * from SCORES";
                    try (ResultSet resultSet = st.executeQuery(select)) {
                        writeHeaders(resultSet.getMetaData(), writer);
                        while (resultSet.next()) {
                            writeRow(resultSet, writer);
                        }
                    }
                    writer.flush();
                }
            }
            try (BufferedWriter writer = Files.newBufferedWriter(outputDir.resolve("scores_aggregated.csv"), StandardCharsets.UTF_8)) {
                try (Statement st = experimentDB.getConnection().createStatement()) {
                    try (ResultSet resultSet = st.executeQuery("select * from SCORES_AGGREGATED")) {
                        writeHeaders(resultSet.getMetaData(), writer);
                        while (resultSet.next()) {
                            writeRow(resultSet, writer);
                        }
                    }
                    writer.flush();
                }
            }
            if (querySets.size() > 0) {
                for (String querySet : querySets) {
                    dumpSignificanceMatrices(querySet, targetScorers, experimentDB, outputDir);
                }
            }
            //now dump across all query sets
            dumpSignificanceMatrices("", targetScorers, experimentDB, outputDir);

        }

    }

    private static void dumpSignificanceMatrices(String querySet, List<String> targetScorers, ExperimentDB experimentDB, Path outputDir) throws Exception {
        List<String> scorers = experimentDB.getScoreCollectorNames();
        Set<String> scorerSet = new HashSet<>();
        scorerSet.addAll(scorers);
        TTest tTest = new TTest();
        for (String scorer : targetScorers) {
            if (!scorerSet.contains(scorer)) {
                System.err.println("I regret I couldn't find this scorer ('" + scorer +
                        "') among the scorers: " +
                        scorerSet);
            }
            Map<String, Double> aggregatedScores = experimentDB.getMeanExperimentScores(scorer);
            Map<String, Double> sorted = MapUtil.sortByDescendingValue(aggregatedScores);
            List<String> experiments = new ArrayList();
            experiments.addAll(sorted.keySet());
            writeMatrix(tTest, scorer, querySet, experiments, experimentDB, outputDir);
        }
    }

    private static void writeMatrix(TTest tTest, String scorer, String querySet,
                                    List<String> experiments, ExperimentDB experimentDB, Path outputDir) throws Exception {

        String fileName = "sig_diffs_" + scorer + (
                (StringUtils.isBlank(querySet)) ? ".csv" : "_" + querySet + ".csv");

        try (BufferedWriter writer = Files.newBufferedWriter(outputDir.resolve(fileName))) {

            for (String experiment : experiments) {
                writer.write(",");
                writer.write(experiment);
            }
            writer.write("\n");

            for (int i = 0; i < experiments.size(); i++) {
                String experimentA = experiments.get(i);
                writer.write(experimentA);
                for (int k = 0; k <= i; k++) {
                    writer.write(",");
                }
                writer.write(String.format(Locale.US, "%.3G", 1.0d) + ",");//p-value of itself
                //map of query -> score for experiment A given this particular scorer
                Map<String, Double> scoresA = experimentDB.getScores(querySet, experimentA, scorer);
                for (int j = i + 1; j < experiments.size(); j++) {
                    String experimentB = experiments.get(j);
                    double significance = calcSignificance(tTest, querySet, scoresA, experimentA, experimentB, scorer, experimentDB);
                    writer.write(String.format(Locale.US, "%.3G", significance));
                    writer.write(",");
                }
                writer.write("\n");
            }
        }
    }

    private static double calcSignificance(TTest tTest, String querySet, Map<String, Double> scoresA, String experimentA,
                                           String experimentB, String scorer,
                                           ExperimentDB experimentDB) throws SQLException {

        Map<String, Double> scoresB = experimentDB.getScores(querySet, experimentB, scorer);
        if (scoresA.size() != scoresB.size()) {
            //log
            System.err.println("Different number of scores for " + experimentA + "(" + scoresA.size() +
                    ") vs. " + experimentB + "(" + scoresB.size() + ")");
        }
        double[] arrA = new double[scoresA.size()];
        double[] arrB = new double[scoresB.size()];

        int i = 0;
        for (String query : scoresA.keySet()) {
            Double scoreA = scoresA.get(query);
            Double scoreB = scoresB.get(query);
            if (scoreA == null || scoreA < 0) {
                scoreA = 0.0d;
            }
            if (scoreB == null || scoreB < 0) {
                scoreB = 0.0d;
            }
            arrA[i] = scoreA;
            arrB[i] = scoreB;
            i++;
        }
//        WilcoxonSignedRankTest w = new WilcoxonSignedRankTest();
        //      w.wilcoxonSignedRankTest()
        return tTest.tTest(arrA, arrB);

    }

    private static void writeHeaders(ResultSetMetaData metaData, BufferedWriter writer) throws Exception {
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            writer.write(clean(metaData.getColumnName(i)));
            writer.write(",");
        }
        writer.write("\n");
    }

    private static void writeRow(ResultSet resultSet, BufferedWriter writer) throws Exception {
        for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
            writer.write(clean(resultSet.getString(i)));
            writer.write(",");
        }
        writer.write("\n");
    }

    private static String clean(String string) {
        if (string == null) {
            return "";
        }
        string = string.replaceAll("[\r\n]", " ");
        if (string.contains(",")) {
            string.replaceAll("\"", "\"\"");
            string = "\"" + string + "\"";
        }
        return string;
    }
}
