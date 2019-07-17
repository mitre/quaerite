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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mitre.quaerite.core.Judgments;
import org.mitre.quaerite.core.QueryInfo;
import org.mitre.quaerite.core.QueryStrings;
import org.mitre.quaerite.db.ExperimentDB;

class QueryLoader {

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
                                    QUERY_SET, QUERY_ID, DOCUMENT_ID, RELEVANCE, COUNT})
                    )
            );


    public static void loadJudgments(ExperimentDB experimentDB, Path file,
                                     boolean freshStart) throws IOException, SQLException {
        if (freshStart) {
            experimentDB.clearJudgments();
        }

        Map<String, Judgments> judgmentsMap = null;
        try (InputStream is = Files.newInputStream(file)) {
            try (Reader reader = new InputStreamReader(new BOMInputStream(is), "UTF-8")) {
                Iterable<CSVRecord> records = CSVFormat.EXCEL
                        .withFirstRecordAsHeader().parse(reader);
                boolean hasJudgments = (((CSVParser) records)).getHeaderMap().containsKey(
                        DOCUMENT_ID) ? true : false;
                boolean hasQuerySet = (((CSVParser) records).getHeaderMap().containsKey(
                        QUERY_SET)) ? true : false;
                boolean hasCount = (((CSVParser) records).getHeaderMap().containsKey(
                        COUNT)) ? true : false;
                boolean hasQueryId = (((CSVParser) records).getHeaderMap().containsKey(
                        QUERY_ID)) ? true : false;
                Set<String> queryStringNames = getQueryStringNames(((CSVParser) records)
                        .getHeaderMap().keySet());
                if (hasQueryId) {
                    judgmentsMap = loadJudgmentsWithId(hasJudgments, hasQuerySet, hasCount,
                            queryStringNames, records);
                } else {
                    judgmentsMap = loadJudmentsWithoutId(hasJudgments, hasQuerySet,
                            hasCount, queryStringNames, records);
                }
            }
        }
        for (Judgments judgments : judgmentsMap.values()) {
            experimentDB.addJudgment(judgments);
        }
    }

    private static Map<String, Judgments> loadJudmentsWithoutId(boolean hasJudgments,
                                                                boolean hasQuerySet,
                                                                boolean hasCount,
                                                                Set<String> queryStringNames,
                                                                Iterable<CSVRecord> records) {
        //queryset, Map<queryInfo.getId, Judgments>
        Map<String, Map<QueryStrings, Judgments>> queries = new HashMap<>();

        int uniqueJudgments = 0;
        for (CSVRecord record : records) {
            String querySet = (hasQuerySet) ? record.get(QUERY_SET) : QueryInfo.DEFAULT_QUERY_SET;
            QueryStrings queryStrings = getQueryStrings(queryStringNames, record);
            int queryCount = (hasCount) ? Integer.parseInt(record.get(COUNT)) : 1;

            Judgments judgments = null;
            if (queries.containsKey(querySet) && queries.get(querySet).containsKey(queryStrings)) {
                QueryInfo cachedQueryInfo = queries.get(querySet).get(queryStrings).getQueryInfo();
                QueryInfo newQueryInfo = new QueryInfo(cachedQueryInfo.getQueryId(), querySet, queryStrings,
                        queryCount);
                if (!cachedQueryInfo.equals(newQueryInfo)) {
                    throw new IllegalArgumentException("There's a mismatch between the previously loaded:" +
                            cachedQueryInfo + "\nand the QueryInfo loaded for this row: " + newQueryInfo);

                }
                judgments = queries.get(querySet).get(queryStrings);
            } else {
                String queryId = Integer.toString(uniqueJudgments++);
                QueryInfo newQueryInfo = new QueryInfo(queryId, querySet, queryStrings, queryCount);
                judgments = new Judgments(newQueryInfo);
                if (queries.containsKey(querySet)) {
                    queries.get(querySet).put(queryStrings, judgments);
                } else {
                    Map<QueryStrings, Judgments> map = new HashMap<>();
                    map.put(queryStrings, judgments);
                    queries.put(querySet, map);
                }
            }
            if (hasJudgments) {
                String documentId = record.get(DOCUMENT_ID);
                double relevanceScore =
                        Double.parseDouble(record.get(RELEVANCE));

                judgments.addJudgment(documentId, relevanceScore);
            }
        }
        Map<String, Judgments> ret = new HashMap<>();
        for (Map.Entry<String, Map<QueryStrings, Judgments>> e : queries.entrySet()) {
            for (Judgments j : e.getValue().values()) {
                ret.put(j.getQueryInfo().getQueryId(), j);
            }
        }
        return ret;
    }


    private static Map<String, Judgments> loadJudgmentsWithId(
            boolean hasJudgments, boolean hasQuerySet, boolean hasCount,
            Set<String> queryStringNames, Iterable<CSVRecord> records) throws SQLException {

        //queryId, judgments
        Map<String, Judgments> judgmentsMap = new HashMap<>();
        for (CSVRecord record : records) {
            String querySet = (hasQuerySet) ? record.get(QUERY_SET) : QueryInfo.DEFAULT_QUERY_SET;
            QueryStrings queryStrings = getQueryStrings(queryStringNames, record);
            int queryCount = (hasCount) ? Integer.parseInt(record.get(COUNT)) : 1;

            String queryId = record.get(QUERY_ID);
            if (StringUtils.isBlank(queryId)) {
                throw new IllegalArgumentException("If the csv has a '" + QUERY_ID + "' column, " +
                        "there must be a non-empty value for every row");
            }
            QueryInfo newQueryInfo = new QueryInfo(queryId, querySet, queryStrings, queryCount);
            if (judgmentsMap.containsKey(queryId)) {
                QueryInfo cachedQueryInfo = judgmentsMap.get(queryId).getQueryInfo();
                if (!newQueryInfo.equals(cachedQueryInfo)) {
                    throw new IllegalArgumentException("There's a mismatch between the previously loaded:" +
                            cachedQueryInfo + "\nand the QueryInfo loaded for this row: " + newQueryInfo);
                }
            } else {
                judgmentsMap.put(queryId, new Judgments(newQueryInfo));
            }

            if (hasJudgments) {
                String documentId = record.get(DOCUMENT_ID);
                double relevanceScore =
                        Double.parseDouble(record.get(RELEVANCE));
                Judgments judgments = judgmentsMap.get(newQueryInfo.getQueryId());
                judgments.addJudgment(documentId, relevanceScore);
            }
        }
        return judgmentsMap;
    }


    private static QueryStrings getQueryStrings(Set<String> queryStringNames, CSVRecord record) {
        QueryStrings queryStrings = new QueryStrings();

        for (String name : queryStringNames) {
            queryStrings.addQueryString(name, record.get(name));
        }
        return queryStrings;
    }

    private static Set<String> getQueryStringNames(Set<String> keySet) {
        Set<String> undefined = new HashSet<>();
        for (String s : keySet) {
            if (!DEFINED_JUDGMENT_COLUMNS.contains(s)) {
                undefined.add(s);
                LOG.debug("adding column for queryString:'" + s + "'");
            }
        }
        return undefined;
    }
}
