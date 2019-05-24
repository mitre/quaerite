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

package org.mitre.quaerite.db;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.log4j.Logger;
import org.mitre.quaerite.core.QueryInfo;
import org.mitre.quaerite.core.scoreaggregators.ScoreAggregator;

/**
 * To be used by a single scorer thread.  This class is not thread safe,
 * but it is designed for each thread to have its own set of connections
 * to the db.
 */
public class QueryRunnerDBClient implements Closeable {

    private static Gson GSON = new GsonBuilder().create();
    static Logger LOG = Logger.getLogger(QueryRunnerDBClient.class);

    private PreparedStatement insertScores;
    private PreparedStatement insertResults;

    protected QueryRunnerDBClient(Connection connection, List<ScoreAggregator> scoreAggregators) throws SQLException {
        insertResults = connection.prepareStatement(
                "insert into search_results (query_set, query, experiment_name, json) values (?,?,?,?)"
        );

        StringBuilder insertSql = new StringBuilder();
        insertSql.append("insert into SCORES (QUERY_SET, QUERY, QUERY_COUNT, EXPERIMENT");
        for (ScoreAggregator scoreAggregator : scoreAggregators) {
            insertSql.append(", ");
            insertSql.append(scoreAggregator.getName());
        }
        insertSql.append(") VALUES (?,?,?,?");
        for (ScoreAggregator scoreAggregator : scoreAggregators) {
            insertSql.append(", ");
            insertSql.append("?");
        }
        insertSql.append(")");
        insertScores = connection.prepareStatement(insertSql.toString());
    }

    public void insertScores(QueryInfo queryInfo,
                             String experimentName, List<ScoreAggregator> scoreAggregators) throws SQLException {

        insertScores.setString(1, queryInfo.getQuerySet());
        insertScores.setString(2, queryInfo.getQueryId());
        insertScores.setInt(3, queryInfo.getQueryCount());
        insertScores.setString(4, experimentName);

        int i = 5;
        //TODO: check that score is not null
        for (ScoreAggregator scoreAggregator : scoreAggregators) {
            insertScores.setDouble(i++,
                    scoreAggregator.getScores().get(queryInfo));
        }
        insertScores.addBatch();
    }

    public void insertSearchResults(String querySet, String query, String experimentName,
                                    org.mitre.quaerite.core.ResultSet results) throws SQLException {
        String json = GSON.toJson(results);
        insertResults.setString(1, querySet);
        insertResults.setString(2, query);
        insertResults.setString(3, experimentName);
        insertResults.setString(4, json);
        insertResults.addBatch();
    }

    public void executeBatch() throws SQLException {
        insertScores.executeBatch();
        insertResults.executeBatch();
    }

    @Override
    public void close() throws IOException {
        try {
            insertScores.close();
            insertResults.close();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }
}
