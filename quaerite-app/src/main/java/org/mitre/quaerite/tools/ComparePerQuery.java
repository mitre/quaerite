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

package org.mitre.quaerite.tools;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.mitre.quaerite.db.ExperimentDB;

public class ComparePerQuery {
    static Options OPTIONS = new Options();
    private static final String DEFAULT_SCORER = "ndcg_10";

    static {
        OPTIONS.addOption("db", "db", true, "database folder");
        OPTIONS.addOption("d", "resultDir", true, "result directory");
        OPTIONS.addOption("s", "scorer", true, "scorer for comparison");
        OPTIONS.addOption("e", "experiments", true, "experiments to compare");
    }

    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new GnuParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(
                    "java -jar org.mitre.quaerite.tools.AddExperiments",
                    OPTIONS);
            return;
        }
        Path resultsDir = Paths.get(commandLine.getOptionValue("d"));
        Path dbDir = Paths.get(commandLine.getOptionValue("db"));
        String scorer = "ndcg_10";
        if (commandLine.hasOption("s")) {
            scorer = commandLine.getOptionValue("s");
        }
        List<String> experiments = Arrays.asList(commandLine.getOptionValue("e").split(","));
        if (experiments.size() < 2) {
            System.err.println("must have > 1 experiment to compare");
        }
        dump(resultsDir, dbDir, scorer, experiments);

    }

    private static void dump(Path resultsDir, Path dbDir, String scorer, List<String> experiments) throws IOException, SQLException {
        if (! Files.isDirectory(resultsDir)) {
            Files.createDirectories(resultsDir);
        }
        try (ExperimentDB experimentDB = ExperimentDB.open(dbDir)) {
            experimentDB.createQueryComparisons(scorer, experiments);
            try (BufferedWriter writer = Files.newBufferedWriter(resultsDir.resolve("per_query_comparisons.csv"),
                    StandardCharsets.UTF_8)) {
                try (ResultSet rs = experimentDB.getQueryComparisons()) {
                    writeHeaders(rs.getMetaData(), writer);
                    while (rs.next()) {
                        writeRow(rs, writer);
                    }
                    writer.flush();
                }
            }
        }

    }

    private static void writeHeaders(ResultSetMetaData metaData, BufferedWriter writer) throws IOException, SQLException {
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            writer.write(clean(metaData.getColumnName(i)));
            writer.write(",");
        }
        writer.write("\n");
    }

    private static void writeRow(java.sql.ResultSet resultSet, BufferedWriter writer) throws IOException, SQLException {
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
