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
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.mitre.quaerite.Experiment;
import org.mitre.quaerite.ExperimentSet;
import org.mitre.quaerite.JudgmentList;
import org.mitre.quaerite.Judgments;
import org.mitre.quaerite.QueryInfo;
import org.mitre.quaerite.scorecollectors.ScoreCollector;
import org.mitre.quaerite.scorecollectors.ScoreCollectorListSerializer;

public class ExperimentDB implements Closeable {

    static Logger LOG = Logger.getLogger(ExperimentDB.class);
    static Object[] INSERT_SCORE_LOCK = new Object[0];

    private static final String ID_FIELD = "ID_FIELD";

    private final Connection connection;
    private final PreparedStatement selectExperiments;
    private final PreparedStatement insertExperiments;
    private final PreparedStatement mergeExperiments;

    private final PreparedStatement selectAllJudgments;
    private final PreparedStatement selectJudgments;
    private final PreparedStatement insertJudgments;

    private final PreparedStatement setJudgmentIdField;
    private final PreparedStatement getJudgmentIdField;

    private final PreparedStatement getQuerySets;

    private final PreparedStatement selectScoreCollectors;
    private final PreparedStatement insertScoreCollectors;

    private PreparedStatement selectQueryComparisons;

    private PreparedStatement insertScores;
    private PreparedStatement insertScoresAggregated;

    private PreparedStatement selectScores;
    //cache of upserting scores keyed by scorer name
    private Map<String, PreparedStatement> upsertScoreStatements = new HashMap<>();
    private Map<String, PreparedStatement> selectScoreStatements = new HashMap<>();

    public static ExperimentDB open(Path dbDir) throws SQLException, IOException {
        try {
            Class.forName ("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return new ExperimentDB(DriverManager.getConnection(
                    "jdbc:h2:"+dbDir.resolve("h2_database").toAbsolutePath()));
    }

    private ExperimentDB(Connection connection) throws SQLException {
        this.connection = connection;
        initTables();
        selectExperiments = connection.prepareStatement("select name, last_edited, json from experiments");
        insertExperiments = connection.prepareStatement(
                "insert into experiments (name, last_edited, json) values (?,?,?)"
        );

        mergeExperiments = connection.prepareStatement(
                "merge into experiments (name, last_edited, json) KEY(name) values (?,?,?)"
        );

        selectAllJudgments = connection.prepareStatement(
                "select json from judgments"
        );

        selectJudgments = connection.prepareStatement(
                "select json from judgments where query=?"
        );

        insertJudgments = connection.prepareStatement(
                "insert into judgments (query_set, query, query_count, json) values (?,?,?,?)"
        );

        //this is the id field to use in Solr/elastic for the id
        setJudgmentIdField = connection.prepareStatement(
                "merge into judgments_metadata KEY(KEY) values (?,?)"
        );

        //this is the id field to use in Solr/elastic for the id
        getJudgmentIdField = connection.prepareStatement(
                "select value from judgments_metadata where key=?"
        );

        selectScoreCollectors = connection.prepareStatement(
            "select name, json from SCORERS"
        );
        insertScoreCollectors = connection.prepareStatement(
                "merge into scorers KEY(name) values ((select max(id) from " +
                        "scorers s where s.name=?), ?, ?)"
        );

        getQuerySets = connection.prepareStatement(
                "select query_set from judgments group by query_set"
        );
    }

    private void initTables() throws SQLException {
        initExperiments();
        initJudgments();
        initScorers();
    }

    private void initExperiments() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " +
                "EXPERIMENTS(" +
                "NAME VARCHAR(255) PRIMARY KEY, " +
                "LAST_EDITED TIMESTAMP, "+
                "JSON VARCHAR(100000));";
        executeSQL(connection, sql);
    }

    private void initScorers() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " +
                "SCORERS( " +
                "id bigint auto_increment,"+
                "NAME VARCHAR(255) UNIQUE, " +
                "JSON VARCHAR(10000));";
        executeSQL(connection, sql);

    }

    private void initJudgments() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS JUDGMENTS ("+
                "QUERY_SET VARCHAR(256),"+
                "QUERY VARCHAR(256),"+
                "QUERY_COUNT INTEGER,"+
                "JSON VARCHAR(10000));";
        executeSQL(connection, sql);
        sql = "ALTER TABLE JUDGMENTS " +
                " ADD CONSTRAINT IF NOT EXISTS " +
                " UQ_JUDGMENTS UNIQUE(QUERY_SET, QUERY);";
        executeSQL(connection, sql);

        sql = "CREATE TABLE IF NOT EXISTS " +
                "JUDGMENTS_METADATA ("+
                "KEY VARCHAR(256) PRIMARY KEY, VALUE VARCHAR(256));";

        executeSQL(connection, sql);
    }


    private static boolean executeSQL(Connection connection, String sql) throws SQLException{
        try (Statement st = connection.createStatement()) {
            return st.execute(sql);
        }
    }

    public void addExperiment(Experiment experiment) throws SQLException {
        addExperiment(experiment, false);
    }

    public void addExperiment(Experiment experiment, boolean merge) throws SQLException {
        if (merge) {
            mergeExperiments.clearParameters();
            mergeExperiments.setString(1, experiment.getName());
            mergeExperiments.setTimestamp(2, new Timestamp(Instant.now().getEpochSecond()));
            mergeExperiments.setString(3, experiment.toJson());
            mergeExperiments.execute();
        } else {
            insertExperiments.clearParameters();
            insertExperiments.setString(1, experiment.getName());
            insertExperiments.setTimestamp(2, new Timestamp(Instant.now().getEpochSecond()));
            insertExperiments.setString(3, experiment.toJson());
            insertExperiments.execute();
        }
    }

    public ExperimentSet getExperiments() throws SQLException {
        ExperimentSet experimentSet = new ExperimentSet();
        try (ResultSet resultSet = selectExperiments.executeQuery()) {
            while (resultSet.next()) {
                String name = resultSet.getString(1);
                Timestamp timestamp = resultSet.getTimestamp(2);
                String json = resultSet.getString(3);
                Experiment ex = Experiment.fromJson(json);
                experimentSet.addExperiment(name, ex);
            }
        }
        try (ResultSet resultSet = selectScoreCollectors.executeQuery()) {
            while (resultSet.next()) {
                String name = resultSet.getString(1);
                String json = resultSet.getString(2);
                ScoreCollector rankScorer = ScoreCollectorListSerializer.fromJson(json);
                experimentSet.addScoreCollector(rankScorer);
            }
        }
        return experimentSet;
    }

    public void setIdField(String idField) throws SQLException {
        setJudgmentIdField.clearParameters();
        setJudgmentIdField.setString(1, ID_FIELD);
        setJudgmentIdField.setString(2, idField);
        setJudgmentIdField.execute();
    }

    /**
     *
     * @return id field or null if it doesn't exist
     * @throws SQLException
     */
    public String getIdField() throws SQLException {
        getJudgmentIdField.clearParameters();
        getJudgmentIdField.setString(1, ID_FIELD);
        try (ResultSet rs = getJudgmentIdField.executeQuery()) {
            if (rs.next()) {
                return rs.getString(1);
            }
        }
        return null;
    }

    public void addJudgment(Judgments judgments)
            throws SQLException {
        insertJudgments.clearParameters();
        insertJudgments.setString(1, judgments.getQuerySet());
        insertJudgments.setString(2, judgments.getQuery());
        insertJudgments.setInt(3, judgments.getQueryCount());
        insertJudgments.setString(4, judgments.toJson());
        insertJudgments.execute();
    }

    public Set<String> extractQuerySets(List<ScoreCollector> scoreCollectors) {
        Set<String> querySets = new HashSet<>();
        for (ScoreCollector collector : scoreCollectors) {
            querySets.addAll(collector.getQuerySets());
        }
        return querySets;
    }

    public List<String> getQuerySets() throws SQLException {
        List<String> querySets = new ArrayList<>();
        try (ResultSet rs = getQuerySets.executeQuery()) {
            querySets.add(rs.getString(1));
        }
        return querySets;
    }

    public JudgmentList getJudgments() throws SQLException {
        ResultSet rs = selectAllJudgments.executeQuery();
        JudgmentList list = new JudgmentList(getIdField());
        while (rs.next()) {
            String json = rs.getString(1);
            list.addJudgments(Judgments.fromJson(json));
        }
        return list;
    }

    public Judgments getJudgments(String query) throws SQLException {
        selectJudgments.clearParameters();
        selectJudgments.setString(1, query);
        try (ResultSet rs = selectAllJudgments.executeQuery()) {
            if (rs.next()) {
                String json = rs.getString(1);
                return Judgments.fromJson(json);
            }
        }
        throw new IllegalArgumentException("I couldn't find a judgment for query="+query);
    }


    public void close() throws IOException {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    public void clearJudgments() throws SQLException {
        String sql = "DROP TABLE JUDGMENTS";
        executeSQL(connection, sql);
        sql = "DROP TABLE JUDGMENTS_METADATA";
        executeSQL(connection, sql);
        initJudgments();
    }

    public void clearExperiments() throws SQLException {
        String sql = "DROP TABLE EXPERIMENTS";
        executeSQL(connection, sql);
        initExperiments();
    }


    public void clearScorers() throws SQLException {
        String sql = "DROP TABLE SCORERS";
        executeSQL(connection, sql);
        initScorers();
    }

    public void addScoreCollector(ScoreCollector scoreCollector) throws SQLException {
        insertScoreCollectors.clearParameters();
        insertScoreCollectors.setString(1, scoreCollector.getName());
        insertScoreCollectors.setString(2, scoreCollector.getName());
        insertScoreCollectors.setString(3, ScoreCollectorListSerializer.toJson(scoreCollector));
        insertScoreCollectors.execute();
    }

    public void initScoreTable(List<ScoreCollector> scoreCollectors) throws SQLException {
        boolean mismatch = false;
        try(Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("select * from scores limit 1")) {
                ResultSetMetaData metaData = rs.getMetaData();
                //4 = queryset query querycount experiment
                if (metaData.getColumnCount() != scoreCollectors.size()+4) {
                    mismatch = true;
                }
                if (! mismatch) {
                    for (int i = 0; i < scoreCollectors.size(); i++) {
                        System.out.println(metaData.getColumnName(i+5) + " : "+
                                scoreCollectors.get(i).getName());
                        if (!metaData.getColumnName(i + 5).equalsIgnoreCase(scoreCollectors.get(i).getName())) {
                            mismatch = true;
                            break;
                        }
                    }
                }
            } catch (SQLException e) {
                mismatch = true;
            }
        }
        if (mismatch) {
            LOG.warn("dropping score table to reload with new columns");
        } else {
            if (insertScores == null) {
                initInsertScores(scoreCollectors);
            }
            return;
        }
        executeSQL(connection, "drop table if exists scores");
        executeSQL(connection, "drop index if exists scores_query_idx");
        executeSQL(connection, "drop index if exists scores_experiment_idx");

        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE SCORES (" +
                "QUERY_SET VARCHAR(1024) NOT NULL, " +
                "QUERY VARCHAR(1024) NOT NULL, " +
                "QUERY_COUNT INT, "+
                "EXPERIMENT VARCHAR(1024) NOT NULL, ");
        int i = 0;
        for (ScoreCollector scoreCollector : scoreCollectors) {
            if (i++ > 0) {
                sql.append(",");
            }
            sql.append(scoreCollector.getName()).append(" DOUBLE");
        }
        sql.append(")");
        executeSQL(connection, sql.toString());

        executeSQL(connection,
                "ALTER TABLE SCORES ADD PRIMARY KEY (QUERY_SET, QUERY, EXPERIMENT)");

        executeSQL(connection,
                "CREATE INDEX SCORES_QUERY_IDX on SCORES(QUERY_SET, QUERY)");
        executeSQL(connection,
                "CREATE INDEX SCORES_EXPERIMENT_IDX on SCORES(EXPERIMENT)");

        executeSQL(connection, "drop table if exists scores_aggregated");
        sql.setLength(0);
        sql.append("create table scores_aggregated (query_set varchar(256) not null," +
                "experiment varchar(256) not null, ");
        i = 0;
        for (ScoreCollector scoreCollector : scoreCollectors) {
            if (i++ > 0) {
                sql.append(",");
            }
            int j = 0;
            for (String statistic : scoreCollector.getStatistics()) {
                if (j++ > 0) {
                    sql.append(",");
                }
                sql.append(scoreCollector.getName()+"_"+statistic).append(" DOUBLE");
            }
        }
        sql.append(")");
        executeSQL(connection, sql.toString());
        executeSQL(connection,
                "ALTER TABLE SCORES_AGGREGATED ADD PRIMARY KEY (QUERY_SET, EXPERIMENT)");

        initInsertScores(scoreCollectors);
    }

    private void initInsertScores(List<ScoreCollector> scoreCollectors) throws SQLException {
        StringBuilder insertSql = new StringBuilder();
        insertSql.append("insert into SCORES (QUERY_SET, QUERY, QUERY_COUNT, EXPERIMENT");
        for (ScoreCollector scoreCollector : scoreCollectors) {
            insertSql.append(", ");
            insertSql.append(scoreCollector.getName());
        }
        insertSql.append(") VALUES (?,?,?,?");
        for (ScoreCollector scoreCollector : scoreCollectors) {
            insertSql.append(", ");
            insertSql.append("?");
        }
        insertSql.append(")");
        insertScores = connection.prepareStatement(insertSql.toString());
    }

    public void insertScores(QueryInfo queryInfo,
                             String experiment, List<ScoreCollector> scoreCollectors, Map<String, Double> scores) throws SQLException {
        synchronized (INSERT_SCORE_LOCK) {
            insertScores.clearParameters();
            insertScores.setString(1, queryInfo.getQuerySet());
            insertScores.setString(2, queryInfo.getQuery());
            insertScores.setInt(3, queryInfo.getQueryCount());
            insertScores.setString(4, experiment);
            int i = 5;
            //TODO: check that score is not null
            for (ScoreCollector scoreCollector : scoreCollectors) {
                insertScores.setDouble(i++, scores.get(scoreCollector.getName()));
            }
            insertScores.execute();
        }
    }

    public void insertScoresAggregated(String experimentName,
                                       List<ScoreCollector> scoreCollectors) throws SQLException {

        if (insertScoresAggregated == null) {
            initInsertScoresAggregated(scoreCollectors);
        }
        Set<String> querySets = extractQuerySets(scoreCollectors);
        for (String querySet : querySets) {
            insertScoresAggregated.clearParameters();
            insertScoresAggregated.setString(1, querySet);
            insertScoresAggregated.setString(2, experimentName);
            int i = 3;
            for (ScoreCollector scoreCollector : scoreCollectors) {
                Map<String, Double> statValues =
                        scoreCollector.getSummaryStatistics(querySet);

                for (String stat : scoreCollector.getStatistics()) {
                    insertScoresAggregated.setDouble(i++, statValues.get(stat));
                }
            }
            insertScoresAggregated.execute();
        }

    }

    private void initInsertScoresAggregated(List<ScoreCollector> scoreCollectors) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("insert into scores_aggregated (QUERY_SET, EXPERIMENT,");
        int i = 0;
        for (ScoreCollector scoreCollector : scoreCollectors) {
            for (String statName : scoreCollector.getStatistics()) {
                if (i++ > 0) {
                    sb.append(", ");
                }
                sb.append(scoreCollector.getName()).append("_").append(statName);
            }
        }
        sb.append(" ) values ( ?,?");
        for (ScoreCollector scoreCollector : scoreCollectors) {
            for (String statName : scoreCollector.getStatistics()) {
                sb.append(",?");
            }
        }
        sb.append(")");
        insertScoresAggregated = connection.prepareStatement(sb.toString());
    }


    public void clearScores() throws SQLException {
        executeSQL(connection, "DROP TABLE IF EXISTS SCORES");
        executeSQL(connection, "DROP TABLE IF EXISTS SCORES_AGGREGATED");
    }

    public void clearScores(String experimentName) throws SQLException {
        if (tableExists("SCORES")) {
            executeSQL(connection, "delete from SCORES where experiment='" + experimentName + "'");

        }
        if (tableExists("SCORES_AGGREGATED")) {
            executeSQL(connection, "delete from SCORES_AGGREGATED where experiment='"+experimentName+"'");
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public Map<String, Double> getScores(String querySet, String experimentName, String scorerName) throws SQLException {
        PreparedStatement selectScores = selectScoreStatements.get(scorerName);
        if (selectScores == null) {
            selectScores = connection.prepareStatement("select query, "+scorerName+" from scores where query_set=? and experiment=?");
            selectScoreStatements.put(scorerName, selectScores);
        }
        selectScores.clearParameters();
        selectScores.setString(1, querySet);
        selectScores.setString(2, experimentName);
        Map<String, Double> values = new HashMap<>();
        try(ResultSet rs = selectScores.executeQuery()) {
            while (rs.next()) {
                String query = rs.getString(1);
                double d = rs.getDouble(2);
                values.put(query, d);
            }
        }
        return values;
    }

    public boolean hasScores(String experimentName) throws SQLException {
        String sql = "select experiment from SCORES where experiment='"+experimentName+"'";
        int cnt = 0;
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    cnt++;
                }
            } catch (SQLException e) {
                return false;
            }
        }
        if (cnt > 0) {
            return true;
        }
        return false;
    }

    public String getLatestExperiment() throws SQLException {
        String sql = "select name from experiments order by last_edited desc limit 1";
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    }

    public boolean tableExists(String tableName) throws SQLException {
        try (ResultSet rset = connection.getMetaData().getTables(null, null, tableName, null)) {
            if (rset.next()) {
                return true;
            }
        }
        return false;
    }

    public Map<String, Double> getMeanExperimentScores(String scorer) throws SQLException {
        Map<String, Double> map = new HashMap<>();
        String sql = "select experiment, "+scorer+"_mean from scores_aggregated";
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    map.put(rs.getString(1), rs.getDouble(2));
                }
            }
        }
        return map;
    }

    public List<String> getScoreCollectorNames() throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "select name from scorers order by id";
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    list.add(rs.getString(1));
                }
            }
        }
        return list;
    }

    public boolean hasNamedQuerySets() throws SQLException {
        try (ResultSet rs = getQuerySets.executeQuery()) {
            while (rs.next()) {
                if (! rs.getString(1).equals(QueryInfo.DEFAULT_QUERY_SET)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void createQueryComparisons(String scorer, List<String> experiments) throws SQLException {
        executeSQL(connection, "drop table if exists query_comparisons");
        StringBuilder sql = new StringBuilder()
                .append("create table query_comparisons (")
                .append("query_set varchar(1024) not null, query varchar(1024) not null");
        int i = 0;
        for (String experiment : experiments) {
            sql.append(",").append(experiment).append(" double ");
            if (i++ > 0) {
                sql.append(",").append(experiment+"_diff double");
            }
        }
        sql.append(")");
        executeSQL(connection, sql.toString());
        sql.setLength(0);

        sql.append("ALTER TABLE query_comparisons ")
            .append(" ADD CONSTRAINT IF NOT EXISTS ")
            .append(" UQ_query_comparisons PRIMARY KEY(QUERY_SET, QUERY);");
        executeSQL(connection, sql.toString());

        sql.setLength(0);

        sql.append("insert into query_comparisons (query_set, query)(");
        sql.append("select query_set, query from scores group by query_set, query)");
        executeSQL(connection, sql.toString());
        sql.setLength(0);
        for (String experiment : experiments) {
            String s = "update query_comparisons c set c."+experiment+" =" +
                    " select s."+scorer+" from scores s where (" +
                    " s.query_set=c.query_set and" +
                    " s.query=c.query and" +
                    " s.experiment='"+experiment+"');";
            executeSQL(connection, s);
        }

        String baselineExperiment = experiments.get(0);
        for (int j = 1; j < experiments.size(); j++) {
            String s = "update query_comparisons c set c."+experiments.get(j)+"_diff="+
                    "(c."+experiments.get(j)+"-c."+baselineExperiment+")";
            executeSQL(connection, s);
        }
        selectQueryComparisons = connection.prepareStatement(
                "select * from query_comparisons order by "+
                        experiments.get(1)+"_diff asc");
    }

    public ResultSet getQueryComparisons() throws SQLException {
        if (selectQueryComparisons == null) {
            selectQueryComparisons = connection.prepareStatement(
                    "select * from query_comparisons"
            );
        }
        return selectQueryComparisons.executeQuery();
    }
}
