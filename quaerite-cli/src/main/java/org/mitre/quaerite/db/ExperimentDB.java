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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mitre.quaerite.core.Experiment;
import org.mitre.quaerite.core.ExperimentConfig;
import org.mitre.quaerite.core.ExperimentSet;
import org.mitre.quaerite.core.JudgmentList;
import org.mitre.quaerite.core.Judgments;
import org.mitre.quaerite.core.QueryInfo;
import org.mitre.quaerite.core.SearchResultSet;
import org.mitre.quaerite.core.scorers.AbstractJudgmentScorer;
import org.mitre.quaerite.core.scorers.DistributionalScoreAggregator;
import org.mitre.quaerite.core.scorers.Scorer;
import org.mitre.quaerite.core.scorers.SummingScoreAggregator;
import org.mitre.quaerite.core.serializers.ScorerListSerializer;
import org.mitre.quaerite.core.stats.ExperimentNameScorePair;
import org.mitre.quaerite.core.stats.ExperimentScorePair;
import org.mitre.quaerite.core.util.StringUtil;

public class ExperimentDB implements Closeable {

    private static Gson GSON = new GsonBuilder().create();

    static Logger LOG = Logger.getLogger(ExperimentDB.class);
    final Connection connection;
    private final PreparedStatement selectExperiments;
    private final PreparedStatement selectOneExperiment;
    private final PreparedStatement selectExperimentNames;

    private final PreparedStatement insertExperiments;
    private final PreparedStatement mergeExperiments;

    private final PreparedStatement selectAllJudgments;
    private final PreparedStatement selectJudgments;
    private final PreparedStatement insertJudgments;

    private final PreparedStatement getQuerySets;

    private final PreparedStatement selectScorers;
    private final PreparedStatement insertScorers;

    private PreparedStatement selectQueryComparisons;

    private PreparedStatement insertScoresAggregated;

    private PreparedStatement selectResults;

    //cache of selecting scores keyed by scorer name
    private Map<String, PreparedStatement> selectScoreStatements = new HashMap<>();

    //cache of selecting scores keyed by scorer name and specifying queryset
    private Map<String, PreparedStatement> selectQuerySetScoreStatements = new HashMap<>();

    public static ExperimentDB openAndDrop(Path dbDir) throws SQLException, IOException {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return new ExperimentDB(DriverManager.getConnection(
                "jdbc:h2:" + dbDir.resolve("h2_database").toAbsolutePath()), true);
    }

    public static ExperimentDB open(Path dbDir) throws SQLException, IOException {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return new ExperimentDB(DriverManager.getConnection(
                "jdbc:h2:" + dbDir.resolve("h2_database").toAbsolutePath()), false);
    }

    ExperimentDB(Connection connection, boolean dropAll) throws SQLException {
        this.connection = connection;
        if (dropAll) {
            dropTables();
        }
        initTables();
        selectExperiments = connection.prepareStatement(
                "select name, last_edited, json from experiments");
        selectOneExperiment = connection.prepareStatement(
                "select name, last_edited, json from experiments where name=?");
        selectExperimentNames = connection.prepareStatement(
                "select name from experiments group by name");

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
                "select json from judgments where query_id=?"
        );

        insertJudgments = connection.prepareStatement(
                "insert into judgments (query_id, query_set, query_count, json) values (?,?,?,?)"
        );

        selectScorers = connection.prepareStatement(
                "select name, json from SCORERS"
        );
        insertScorers = connection.prepareStatement(
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
        initSearchResults();
    }

    private void dropTables() throws SQLException {
        executeSQL(connection, "drop table if exists experiments");
        executeSQL(connection, "drop table if exists judgments");
        executeSQL(connection, "drop table if exists scorers");
        executeSQL(connection, "drop table if exists scores");
        executeSQL(connection, "drop table if exists scores_aggregated");
        executeSQL(connection, "drop table if exists search_results");
    }


    private void initExperiments() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " +
                "EXPERIMENTS(" +
                "NAME VARCHAR(255) PRIMARY KEY, " +
                "LAST_EDITED TIMESTAMP, " +
                "JSON VARCHAR(100000));";
        executeSQL(connection, sql);
    }

    private void initScorers() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " +
                "SCORERS( " +
                "id bigint auto_increment," +
                "NAME VARCHAR(255) UNIQUE, " +
                "JSON VARCHAR(10000));";
        executeSQL(connection, sql);

    }

    private void initJudgments() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS JUDGMENTS (" +
                "QUERY_ID VARCHAR(256) PRIMARY KEY," +
                "QUERY_SET VARCHAR(256)," +
                "QUERY_COUNT INTEGER," +
                "JSON VARCHAR(10000));";
        executeSQL(connection, sql);
    }

    private void initSearchResults() throws SQLException {
        //this table stores the literal search results
        //returned from the search clients
        String sql = "CREATE TABLE IF NOT EXISTS " +
                "SEARCH_RESULTS( " +
                "QUERY_ID VARCHAR(256), " +
                "EXPERIMENT_NAME VARCHAR(256)," +
                "JSON VARCHAR(100000));";
        executeSQL(connection, sql);

        sql = "ALTER TABLE SEARCH_RESULTS " +
                " ADD CONSTRAINT IF NOT EXISTS " +
                " UQ_SEARCH_RESULTS UNIQUE(QUERY_ID, EXPERIMENT_NAME);";
        executeSQL(connection, sql);

        //TODO: add indices to this table
        selectResults = connection.prepareStatement(
                "select json from search_results where (query_id=? and experiment_name=?)"
        );

    }

    static boolean executeSQL(Connection connection, String sql) throws SQLException {
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
            mergeExperiments.setTimestamp(2,
                    new Timestamp(Instant.now().getEpochSecond()));
            mergeExperiments.setString(3, experiment.toJson());
            mergeExperiments.execute();
        } else {
            insertExperiments.clearParameters();
            insertExperiments.setString(1, experiment.getName());
            insertExperiments.setTimestamp(2,
                    new Timestamp(Instant.now().getEpochSecond()));
            insertExperiments.setString(3, experiment.toJson());
            insertExperiments.execute();
        }
    }

    public Collection<String> getExperimentNames() throws SQLException {
        selectExperimentNames.clearParameters();
        Set<String> names = new HashSet<>();
        Experiment ex = null;
        try (ResultSet resultSet = selectExperimentNames.executeQuery()) {
            while (resultSet.next()) {
                String exName = resultSet.getString(1);
                names.add(exName);
            }
        }
        return names;
    }

    public Experiment getExperiment(String name) throws SQLException {
        selectOneExperiment.clearParameters();
        selectOneExperiment.setString(1, name);
        Experiment ex = null;
        try (ResultSet resultSet = selectOneExperiment.executeQuery()) {
            while (resultSet.next()) {
                String exName = resultSet.getString(1);
                Timestamp timestamp = resultSet.getTimestamp(2);
                String json = resultSet.getString(3);
                ex = Experiment.fromJson(json);
                ex.setName(name);//do we need this?
            }
        }
        return ex;
    }

    public ExperimentSet getExperiments() throws SQLException {
        return getExperiments(new ExperimentConfig());
    }

    public ExperimentSet getExperiments(ExperimentConfig experimentConfig) throws SQLException {
        ExperimentSet experimentSet = new ExperimentSet(experimentConfig);
        try (ResultSet resultSet = selectExperiments.executeQuery()) {
            while (resultSet.next()) {
                String name = resultSet.getString(1);
                Timestamp timestamp = resultSet.getTimestamp(2);
                String json = resultSet.getString(3);
                Experiment ex = Experiment.fromJson(json);
                ex.setName(name);//do we need this?
                experimentSet.addExperiment(ex);
            }
        }
        try (ResultSet resultSet = selectScorers.executeQuery()) {
            while (resultSet.next()) {
                String name = resultSet.getString(1);
                String json = resultSet.getString(2);
                Scorer scorer = ScorerListSerializer.fromJson(json);
                experimentSet.addScorer(scorer);
            }
        }
        return experimentSet;
    }

    public void addJudgment(Judgments judgments)
            throws SQLException {
        insertJudgments.clearParameters();
        insertJudgments.setString(1, judgments.getQueryInfo().getQueryId());
        insertJudgments.setString(2, judgments.getQuerySet());
        //this is to use later, potentially, in weighting scores for more frequent queries
        insertJudgments.setInt(3, judgments.getQueryCount());
        insertJudgments.setString(4, judgments.toJson());
        insertJudgments.execute();
    }

    public Set<String> extractQuerySets(List<Scorer> scorers) {
        Set<String> querySets = new HashSet<>();
        for (Scorer scorer : scorers) {
            if (scorer instanceof AbstractJudgmentScorer) {
                querySets.addAll(((AbstractJudgmentScorer) scorer).getQuerySets());
            }
        }
        return querySets;
    }

    public List<String> getQuerySets() throws SQLException {
        if (hasNamedQuerySets()) {
            List<String> querySets = new ArrayList<>();
            try (ResultSet rs = getQuerySets.executeQuery()) {
                while (rs.next()) {
                    querySets.add(rs.getString(1));
                }
            }
            return querySets;
        }
        return Collections.singletonList(QueryInfo.DEFAULT_QUERY_SET);
    }

    public JudgmentList getJudgments() throws SQLException {
        ResultSet rs = selectAllJudgments.executeQuery();
        JudgmentList list = new JudgmentList();
        while (rs.next()) {
            String json = rs.getString(1);
            list.addJudgments(Judgments.fromJson(json));
        }
        return list;
    }

    public Judgments getJudgments(String queryId) throws SQLException {
        selectJudgments.clearParameters();
        selectJudgments.setString(1, queryId);
        try (ResultSet rs = selectAllJudgments.executeQuery()) {
            if (rs.next()) {
                String json = rs.getString(1);
                return Judgments.fromJson(json);
            }
        }
        throw new IllegalArgumentException("I couldn't find a judgment for query_id=" + queryId);
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

    public void clearSearchResults() throws SQLException {
        String sql = "DROP TABLE SEARCH_RESULTS";
        executeSQL(connection, sql);
        initSearchResults();
    }

    public void addScorer(Scorer scorer) throws SQLException {
        String json = ScorerListSerializer.toJson(scorer);
        insertScorers.clearParameters();
        insertScorers.setString(1, scorer.getName());
        insertScorers.setString(2, scorer.getName());
        insertScorers.setString(3, json);
        insertScorers.execute();
    }

    public void initScoreTable(List<Scorer> scorers) throws SQLException {
        boolean mismatch = false;
        boolean tableProbDoesntExist = false;
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("select * from scores limit 1")) {
                ResultSetMetaData metaData = rs.getMetaData();
                //4 = queryset query querycount experiment
                if (metaData.getColumnCount() != scorers.size() + 4) {
                    mismatch = true;
                }
                if (!mismatch) {
                    for (int i = 0; i < scorers.size(); i++) {
                        if (!metaData.getColumnName(i + 5).
                                equalsIgnoreCase(scorers.get(i).getName())) {
                            mismatch = true;
                            break;
                        }
                    }
                }
            } catch (SQLException e) {
                tableProbDoesntExist = true;
            }
        }
        if (mismatch || tableProbDoesntExist) {
            if (mismatch) {
                LOG.warn("dropping score table to reload with new columns");
            }
            dropCreateScoreTables(scorers);
        }

    }

    private void dropCreateScoreTables(List<Scorer> scorers) throws SQLException {
        executeSQL(connection, "drop table if exists scores");
        executeSQL(connection, "drop index if exists scores_query_idx");
        executeSQL(connection, "drop index if exists scores_experiment_idx");

        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE SCORES (" +
                "QUERY_ID VARCHAR(1024) NOT NULL, " +
                "QUERY_SET VARCHAR(1024) NOT NULL, " +
                "QUERY_COUNT INT, " +
                "EXPERIMENT VARCHAR(1024) NOT NULL, ");
        int i = 0;
        for (Scorer scorer : scorers) {
            if (i++ > 0) {
                sql.append(",");
            }
            sql.append(scorer.getName()).append(" DOUBLE");
        }
        sql.append(")");
        executeSQL(connection, sql.toString());

        executeSQL(connection,
                "ALTER TABLE SCORES ADD PRIMARY KEY (QUERY_ID, EXPERIMENT)");

        executeSQL(connection,
                "CREATE INDEX SCORES_QUERY_IDX on SCORES(QUERY_SET, QUERY_ID)");
        executeSQL(connection,
                "CREATE INDEX SCORES_EXPERIMENT_IDX on SCORES(EXPERIMENT)");

        executeSQL(connection, "drop table if exists scores_aggregated");
        sql.setLength(0);
        sql.append("create table scores_aggregated (query_set varchar(256) not null," +
                "experiment varchar(256) not null, ");
        i = 0;
        for (Scorer scorer : scorers) {
            if (i++ > 0) {
                sql.append(",");
            }
            int j = 0;
            for (String statistic : ((Scorer) scorer).getStatistics()) {
                if (j++ > 0) {
                    sql.append(",");
                }
                sql.append(scorer.getName() + "_" + statistic).append(" DOUBLE");
            }
        }
        sql.append(")");
        executeSQL(connection, sql.toString());
        executeSQL(connection,
                "ALTER TABLE SCORES_AGGREGATED ADD PRIMARY KEY (QUERY_SET, EXPERIMENT)");

    }

    /**
     * NOT THREAD SAFE
     *
     * @param queryId
     * @param experimentName
     * @return {@link SearchResultSet} or null if not found
     */
    public SearchResultSet getSearchResults(String queryId,
                                            String experimentName) throws SQLException {
        selectResults.clearParameters();
        selectResults.setString(1, queryId);
        selectResults.setString(2, experimentName);
        //TODO: maybe add checks for more than one result?
        try (ResultSet rs = selectResults.executeQuery()) {
            while (rs.next()) {
                String json = rs.getString(1);
                return GSON.fromJson(json, SearchResultSet.class);
            }
        }
        return null;
    }

    public void insertScoresAggregated(String experimentName,
                                       List<Scorer> scorers) throws SQLException {

        if (insertScoresAggregated == null) {
            initInsertScoresAggregated(scorers);
        }
        Set<String> querySets = extractQuerySets(scorers);
        for (String querySet : querySets) {
            insertScoresAggregated.clearParameters();
            insertScoresAggregated.setString(1, querySet);
            insertScoresAggregated.setString(2, experimentName);
            int i = 3;
            for (Scorer scorer : scorers) {
                Map<String, Double> statValues =
                        scorer.getSummaryStatistics(querySet);

                for (String stat : scorer.getStatistics()) {
                    insertScoresAggregated.setDouble(i++, statValues.get(stat));
                }
            }
            insertScoresAggregated.execute();
        }

    }

    private void initInsertScoresAggregated(List<Scorer> scorers) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("insert into scores_aggregated (QUERY_SET, EXPERIMENT,");
        int i = 0;
        for (Scorer scorer : scorers) {
            for (String statName : scorer.getStatistics()) {
                if (i++ > 0) {
                    sb.append(", ");
                }
                sb.append(scorer.getName()).append("_").append(statName);
            }
        }
        sb.append(" ) values ( ?,?");
        for (Scorer scorer : scorers) {
            for (String statName : scorer.getStatistics()) {
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
            executeSQL(connection, "delete from SCORES where experiment='" + experimentName
                    + "'");

        }
        if (tableExists("SCORES_AGGREGATED")) {
            executeSQL(connection, "delete from SCORES_AGGREGATED where experiment='"
                    + experimentName + "'");
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public Map<String, Double> getScores(String querySet, String experimentName,
                                         String scorerName) throws SQLException {
        PreparedStatement selectScores = null;
        if (StringUtils.isBlank(querySet)) {
            selectScores = getSelectScores(selectScoreStatements, scorerName, false);
            selectScores.setString(1, experimentName);
        } else  {
            selectScores = getSelectScores(selectQuerySetScoreStatements, scorerName, true);
            selectScores.setString(1, querySet);
            selectScores.setString(2, experimentName);
        }
        Map<String, Double> values = new HashMap<>();
        try (ResultSet rs = selectScores.executeQuery()) {
            while (rs.next()) {
                String queryId = rs.getString(1);
                double d = rs.getDouble(2);
                values.put(queryId, d);
            }
        }
        return values;
    }

    private PreparedStatement getSelectScores(
            Map<String, PreparedStatement> map, String scorerName, boolean hasQuerySet) throws SQLException {
        PreparedStatement selectScores = map.get(scorerName);
        if (selectScores == null) {
            if (hasQuerySet) {
                selectScores = connection.prepareStatement("select query_id, "
                        + scorerName + " from scores where query_set=? and experiment=?");
            } else {
                selectScores = connection.prepareStatement("select query_id, "
                        + scorerName + " from scores where experiment=?");

            }
            map.put(scorerName, selectScores);
        }
        selectScores.clearParameters();
        return selectScores;
    }

    public boolean hasScores(String experimentName) throws SQLException {
        String sql = "select experiment from SCORES where experiment='" + experimentName + "'";
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
        try (ResultSet rset = connection.getMetaData().getTables(null,
                null, tableName, null)) {
            if (rset.next()) {
                return true;
            }
        }
        return false;
    }

    public Map<String, Double> getKeyExperimentScore(Scorer scorer, String querySet) throws SQLException {
        Map<String, Double> map = new HashMap<>();
        String columnName;
        if (scorer instanceof SummingScoreAggregator) {
            columnName = ((SummingScoreAggregator) scorer).getName() + "_" +
                    SummingScoreAggregator.SUM;
        } else if (scorer instanceof DistributionalScoreAggregator) {
            columnName = ((DistributionalScoreAggregator) scorer).getName() + "_"
                    + DistributionalScoreAggregator.MEAN;
        } else {
            throw new IllegalArgumentException("I don't yet support: " + scorer.getClass());
        }

        String sql = "select experiment, " + columnName + " from scores_aggregated";
        if (!StringUtils.isBlank(querySet)) {
            sql += " where query_set='"+querySet+"'";
        }
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    map.put(rs.getString(1), rs.getDouble(2));
                }
            }
        }
        return map;
    }

    public List<String> getScoreAggregatorNames() throws SQLException {
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
                if (!rs.getString(1).equals(QueryInfo.DEFAULT_QUERY_SET)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void createQueryComparisons(String scorer, List<String> experiments)
            throws SQLException {
        executeSQL(connection, "drop table if exists query_comparisons");
        StringBuilder sql = new StringBuilder()
                .append("create table query_comparisons (")
                .append("query_id varchar(1024) primary key, " +
                        "query_set varchar(1024) not null");
        int i = 0;
        for (String experiment : experiments) {
            sql.append(",").append(experiment).append(" double ");
            if (i++ > 0) {
                sql.append(",").append(experiment + "_diff double");
            }
        }
        sql.append(")");
        executeSQL(connection, sql.toString());
        sql.setLength(0);


        sql.append("insert into query_comparisons (query_id, query_set)(");
        sql.append("select query_id, query_set from scores group by query_set, query)");
        executeSQL(connection, sql.toString());
        sql.setLength(0);
        for (String experiment : experiments) {
            String s = "update query_comparisons c set c." + experiment + " =" +
                    " select s." + scorer + " from scores s where (" +
                    " s.query_id=c.query_id and" +
                    " s.experiment='" + experiment + "');";
            executeSQL(connection, s);
        }

        String baselineExperiment = experiments.get(0);
        for (int j = 1; j < experiments.size(); j++) {
            String s = "update query_comparisons c set c." + experiments.get(j) + "_diff=" +
                    "(c." + experiments.get(j) + "-c." + baselineExperiment + ")";
            executeSQL(connection, s);
        }
        selectQueryComparisons = connection.prepareStatement(
                "select * from query_comparisons order by " +
                        experiments.get(1) + "_diff asc");
    }

    public ResultSet getQueryComparisons() throws SQLException {
        if (selectQueryComparisons == null) {
            selectQueryComparisons = connection.prepareStatement(
                    "select * from query_comparisons"
            );
        }
        return selectQueryComparisons.executeQuery();
    }

    public void addScoreAggregators(Collection<Scorer> scorers) throws SQLException {
        for (Scorer scorer : scorers) {
            addScorer(scorer);
        }
    }

    public List<ExperimentScorePair> getExperimentScores(String experimentNamePrefix,
                                                         String scorerName) throws SQLException {

        return getNBestExperiments(experimentNamePrefix, -1, scorerName);
    }

    public List<ExperimentScorePair> getNBestExperiments(int num,
                                                         String scorerName) throws SQLException {
        return getNBestExperiments(StringUtils.EMPTY, num, scorerName);
    }

    public List<ExperimentScorePair> getNBestExperiments(String experimentNamePrefix,
                                                         int num, String scorerName)
            throws SQLException {
        String prefix = experimentNamePrefix;
        if (prefix.endsWith("*")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        prefix = prefix + "%";
        String prefixIlike = StringUtils.isBlank(prefix) ? StringUtils.EMPTY :
                "where sa.experiment ilike '" + prefix + "' ";

        String limit = (num > -1) ? "limit " + num : StringUtils.EMPTY;
        String sql = "select sa.experiment, e.json, sa." + scorerName + " " +
                "from scores_aggregated sa " +
                "join experiments e on sa.experiment=e.name " +
                prefixIlike +
                "order by " + scorerName + " desc " +
                limit;

        List<ExperimentScorePair> experiments = new ArrayList<>();
        try (Statement st = connection.createStatement()) {
            try (ResultSet resultSet = st.executeQuery(sql)) {
                while (resultSet.next()) {
                    String name = resultSet.getString(1);
                    String json = resultSet.getString(2);
                    double score = resultSet.getDouble(3);
                    Experiment ex = Experiment.fromJson(json);
                    experiments.add(new ExperimentScorePair(ex, score));
                }
            }
        }
        return experiments;
    }

    public List<ExperimentNameScorePair> getNBestExperimentNames(
            String experimentNamePrefix, int num, String scorerName)
            throws SQLException {
        String prefix = experimentNamePrefix;
        if (prefix.endsWith("*")) {
            prefix = prefix.substring(0, prefix.length() - 1);

        }
        prefix = prefix + "%";
        String prefixIlike = StringUtils.isBlank(prefix) ? StringUtils.EMPTY :
                "where sa.experiment ilike '" + prefix + "' ";

        String limit = (num > -1) ? "limit " + num : StringUtils.EMPTY;
        String sql = "select sa.experiment, sa." + scorerName + " " +
                "from scores_aggregated sa " +
                "join experiments e on sa.experiment=e.name " +
                prefixIlike +
                "order by " + scorerName + " desc " +
                limit;

        List<ExperimentNameScorePair> experiments = new ArrayList<>();
        try (Statement st = connection.createStatement()) {
            try (ResultSet resultSet = st.executeQuery(sql)) {
                while (resultSet.next()) {
                    String name = resultSet.getString(1);
                    double score = resultSet.getDouble(2);
                    experiments.add(new ExperimentNameScorePair(name, score));
                }
            }
        }
        return experiments;
    }

    public QueryRunnerDBClient getQueryRunnerDBClient(
            List<Scorer> scorers) throws SQLException {
        return new QueryRunnerDBClient(connection, scorers);
    }
}
