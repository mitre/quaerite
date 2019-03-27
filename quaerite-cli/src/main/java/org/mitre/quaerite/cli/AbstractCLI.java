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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;
import org.mitre.quaerite.core.Experiment;
import org.mitre.quaerite.core.ExperimentSet;
import org.mitre.quaerite.core.Judgments;
import org.mitre.quaerite.core.QueryInfo;
import org.mitre.quaerite.core.scoreaggregators.ScoreAggregator;
import org.mitre.quaerite.db.ExperimentDB;

public abstract class AbstractCLI {

    static int getInt(CommandLine commandLine, String opt, int dfault) {
        if (commandLine.hasOption(opt)) {
            return Integer.parseInt(commandLine.getOptionValue(opt));
        }
        return dfault;
    }

    static float getFloat(CommandLine commandLine, String opt, float def) {
        if (commandLine.hasOption(opt)) {
            return Float.parseFloat(commandLine.getOptionValue(opt));
        }
        return def;
    }

    static Path getPath(CommandLine commandLine, String opt, boolean mustExist) {
        if (! commandLine.hasOption(opt)) {
            return null;
        }
        Path p = Paths.get(commandLine.getOptionValue(opt));
        if (mustExist && !Files.exists(p)) {
            throw new IllegalArgumentException("File "+p+" must exist");
        }
        return p;
    }

    static boolean getBoolean(CommandLine commandLine, String opt) {
        if (commandLine.hasOption(opt)) {
            return true;
        }
        return false;
    }

    static String getString(CommandLine commandLine, String opt, String dfault) {
        if (commandLine.hasOption(opt)) {
            return commandLine.getOptionValue(opt);
        }
        return dfault;
    }


    static ExperimentSet addExperiments(ExperimentDB experimentDB, Path experimentsJson, boolean merge, boolean freshStart) throws SQLException, IOException {
        if (freshStart) {
            experimentDB.clearExperiments();
            experimentDB.clearScorers();
            experimentDB.clearScores();
        }
        ExperimentSet experiments = null;
        try (Reader reader = Files.newBufferedReader(experimentsJson, StandardCharsets.UTF_8)) {
            experiments = ExperimentSet.fromJson(reader);
        }

        for (Experiment experiment : experiments.getExperiments().values()) {
            experimentDB.addExperiment(experiment, merge);
        }

        List<ScoreAggregator> scoreAggregators = experiments.getScoreAggregators();
        if (scoreAggregators != null && scoreAggregators.size() > 0) {
            experimentDB.clearScorers();
            for (ScoreAggregator scoreAggregator : scoreAggregators) {
                experimentDB.addScoreAggregator(scoreAggregator);
            }
        }

        return experiments;
    }

    public static void loadJudgments(ExperimentDB experimentDB, Path file, boolean freshStart) throws IOException, SQLException {
        if (freshStart) {
            experimentDB.clearJudgments();
        }
        Map<String, Map<String, Judgments>> queries = new HashMap<>();
        try (InputStream is = Files.newInputStream(file)) {
            try (Reader reader = new InputStreamReader(new BOMInputStream(is), "UTF-8")) {
                Iterable<CSVRecord> records = CSVFormat.EXCEL
                        .withFirstRecordAsHeader().parse(reader);
                boolean hasQuerySet = (((CSVParser) records).getHeaderMap().containsKey("querySet")) ? true : false;
                boolean hasCount = (((CSVParser) records).getHeaderMap().containsKey("count")) ? true : false;
                for (CSVRecord record : records) {
                    String querySet = (hasQuerySet) ? record.get("querySet") : QueryInfo.DEFAULT_QUERY_SET;
                    String query = record.get("query");
                    String id = record.get("id");
                    int count = (hasCount) ? Integer.parseInt(record.get("count")) : 1;
                    double relevanceScore =
                            Double.parseDouble(record.get("relevance"));
                    Map<String, Judgments> querySetMap = queries.get(querySet);
                    if (querySetMap == null) {
                        querySetMap = new HashMap<>();
                    }
                    Judgments judgments = querySetMap.get(query);
                    if (judgments == null) {
                        judgments = new Judgments(new QueryInfo(querySet, query, count));
                    }
                    judgments.addJudgment(id, relevanceScore);
                    querySetMap.put(query, judgments);
                    queries.put(querySet, querySetMap);
                }
            }
        }
        for (String querySet : queries.keySet()) {
            for (Judgments judgments : queries.get(querySet).values()) {
                experimentDB.addJudgment(judgments);
            }
        }
    }


}
