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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.org.apache.regexp.internal.RE;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.log4j.Logger;
import org.mitre.quaerite.core.Experiment;
import org.mitre.quaerite.core.ExperimentSet;
import org.mitre.quaerite.core.Judgments;
import org.mitre.quaerite.core.QueryInfo;
import org.mitre.quaerite.core.QueryStrings;
import org.mitre.quaerite.core.scoreaggregators.ScoreAggregator;
import org.mitre.quaerite.db.ExperimentDB;

public abstract class AbstractCLI {

    private static final String QUERY_SET = "querySet";
    private static final String QUERY_ID = "queryId";
    private static final String DOCUMENT_ID = "id";
    private static final String RELEVANCE = "relevance";
    private static final String COUNT = "count";

    static Logger LOG = Logger.getLogger(AbstractCLI.class);

    private static Set<String> DEFINED_JUDGMENT_COLUMNS =
            Collections.unmodifiableSet(
                    new HashSet(
                            Arrays.asList(new String[]{
                                    QUERY_SET, QUERY_ID, DOCUMENT_ID, RELEVANCE, COUNT })
                    )
            );

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
                boolean hasQuerySet = (((CSVParser) records).getHeaderMap().containsKey(QUERY_SET)) ? true : false;
                boolean hasCount = (((CSVParser) records).getHeaderMap().containsKey(COUNT)) ? true : false;
                boolean hasQueryId = (((CSVParser) records).getHeaderMap().containsKey(QUERY_ID)) ? true : false;
                Set<String> queryStringNames = getQueryStringNames(((CSVParser)records).getHeaderMap().keySet());

                for (CSVRecord record : records) {
                    String querySet = (hasQuerySet) ? record.get(QUERY_SET) : QueryInfo.DEFAULT_QUERY_SET;
                    QueryStrings queryStrings = getQueryStrings(hasQueryId, queryStringNames, record);
                    String documentId = record.get("id");
                    int count = (hasCount) ? Integer.parseInt(record.get(COUNT)) : 1;
                    double relevanceScore =
                            Double.parseDouble(record.get(RELEVANCE));
                    Map<String, Judgments> querySetMap = queries.get(querySet);
                    if (querySetMap == null) {
                        querySetMap = new HashMap<>();
                    }
                    Judgments judgments = querySetMap.get(queryStrings.getId());
                    if (judgments == null) {
                        judgments = new Judgments(new QueryInfo(querySet, queryStrings, count));
                    }
                    judgments.addJudgment(documentId, relevanceScore);
                    querySetMap.put(queryStrings.getId(), judgments);
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

    private static QueryStrings getQueryStrings(
            boolean hasQueryId, Set<String> queryStringNames, CSVRecord record) {
        QueryStrings queryStrings;
        if (hasQueryId) {
            queryStrings = new QueryStrings(record.get(QUERY_ID));
        } else {
            queryStrings = new QueryStrings();
        }
        for (String name : queryStringNames) {
            queryStrings.addQueryString(name, record.get(name));
        }
        return queryStrings;
    }

    private static Set<String> getQueryStringNames(Set<String> keySet) {
        Set<String> undefined = new HashSet<>();
        for (String s : keySet) {
            if (! DEFINED_JUDGMENT_COLUMNS.contains(s)) {
                undefined.add(s);
                LOG.debug("adding column for queryString:'"+s+"'");
            }
        }
        return undefined;
    }


}
