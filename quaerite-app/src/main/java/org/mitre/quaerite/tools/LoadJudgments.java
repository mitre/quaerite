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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

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
import org.mitre.quaerite.Judgments;
import org.mitre.quaerite.QueryInfo;
import org.mitre.quaerite.db.ExperimentDB;


public class LoadJudgments {
    static Options OPTIONS = new Options();
    static {

        OPTIONS.addOption(
                Option.builder("db")
                .hasArg()
                .required()
                .desc("database folder").build()
        );

        OPTIONS.addOption(
                Option.builder("id")
                        .hasArg()
                        .required(false)
                        .desc("field name for id field for ground truth (default: 'id')").build()
        );

        OPTIONS.addOption(
                Option.builder("f")
                        .longOpt("file")
                        .hasArg()
                        .required()
                        .desc("csv file with truth data").build()
        );

        OPTIONS.addOption(
                Option.builder("freshStart")
                        .required(false)
                        .hasArg(false)
                        .desc("delete all existing judgments").build()
        );
    }
    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("java -jar org.mitre.eval.LoadJudgments", OPTIONS);
            return;
        }

        String idField = commandLine.hasOption("id") ? commandLine.getOptionValue("id") : "id";
        Path file = Paths.get(commandLine.getOptionValue("f"));
        Path dbDir = Paths.get(commandLine.getOptionValue("db"));
        boolean freshStart = (commandLine.hasOption("freshStart")) ? true : false;
        load(file, idField, dbDir, freshStart);

    }

    public static void load(Path file, String idField, Path dbDir, boolean freshStart) throws IOException, SQLException {
        try (ExperimentDB experimentDB = ExperimentDB.open(dbDir)) {
            if (freshStart) {
                experimentDB.clearJudgments();
            }
            experimentDB.setIdField(idField);
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
                        judgments.addJugment(id, relevanceScore);
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

}
