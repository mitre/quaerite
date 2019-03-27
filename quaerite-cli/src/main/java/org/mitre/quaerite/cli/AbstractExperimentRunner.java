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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.stat.inference.TTest;
import org.apache.log4j.Logger;
import org.mitre.quaerite.connectors.QueryRequest;
import org.mitre.quaerite.connectors.SearchClient;
import org.mitre.quaerite.connectors.SearchClientException;
import org.mitre.quaerite.connectors.SearchClientFactory;
import org.mitre.quaerite.core.Experiment;
import org.mitre.quaerite.core.ExperimentConfig;
import org.mitre.quaerite.core.ExperimentSet;
import org.mitre.quaerite.core.JudgmentList;
import org.mitre.quaerite.core.Judgments;
import org.mitre.quaerite.core.QueryInfo;
import org.mitre.quaerite.core.ResultSet;
import org.mitre.quaerite.core.features.Feature;
import org.mitre.quaerite.core.features.StringListFeature;
import org.mitre.quaerite.core.features.WeightableListFeature;
import org.mitre.quaerite.core.scoreaggregators.DistributionalScoreAggregator;
import org.mitre.quaerite.core.scoreaggregators.ScoreAggregator;
import org.mitre.quaerite.core.scoreaggregators.SummingScoreAggregator;
import org.mitre.quaerite.core.util.MapUtil;
import org.mitre.quaerite.db.ExperimentDB;

public abstract class AbstractExperimentRunner extends AbstractCLI {
    static final Judgments POISON = new Judgments(new QueryInfo("", "", -1));

    static Logger LOG = Logger.getLogger(AbstractExperimentRunner.class);

    static final int DEFAULT_NUM_THREADS = 8;
    private static final int MAX_MATRIX_COLS = 100;
    //this caches a judgment list of valid judgments
    //per search server url
    Map<String, JudgmentList> searchServerValidatedMap = new HashMap<>();

    private final ExperimentConfig experimentConfig;
    NumberFormat threePlaces = new DecimalFormat(".000",
            DecimalFormatSymbols.getInstance(Locale.US));
    public AbstractExperimentRunner(ExperimentConfig experimentConfig) {
        this.experimentConfig = experimentConfig;
    }


    void runExperiment(String experimentName, ExperimentDB experimentDB, JudgmentList judgmentList,
                       String judgmentListId, boolean logResults) throws SQLException {
        if (experimentDB.hasScores(experimentName)) {
            LOG.info("Already has scores for " + experimentName + "; skipping.  " +
                    "Use the -freshStart commandline option to clear all scores");
            return;
        }
        ExperimentSet experimentSet = experimentDB.getExperiments();
        Experiment ex = experimentSet.getExperiment(experimentName);
        List<ScoreAggregator> scoreAggregators = experimentSet.getScoreAggregators();
        experimentDB.initScoreTable(scoreAggregators);
        SearchClient searchClient = SearchClientFactory.getClient(ex.getSearchServerUrl());
        JudgmentList validated = searchServerValidatedMap.get(ex.getSearchServerUrl()+"_"+judgmentListId);
        if (validated == null) {
            validated = validate(searchClient, judgmentList);
            searchServerValidatedMap.put(ex.getSearchServerUrl()+"_"+judgmentListId, validated);
        }
        ExecutorService executorService = Executors.newFixedThreadPool(experimentConfig.getNumThreads());
        ExecutorCompletionService<Integer> executorCompletionService = new ExecutorCompletionService<>(executorService);
        ArrayBlockingQueue<Judgments> queue = new ArrayBlockingQueue<>(
                validated.getJudgmentsList().size() + experimentConfig.getNumThreads());

        queue.addAll(validated.getJudgmentsList());
        for (int i = 0; i < experimentConfig.getNumThreads(); i++) {
            queue.add(POISON);
        }

        for (int i = 0; i < experimentConfig.getNumThreads(); i++) {
            executorCompletionService.submit(
                    new QueryRunner(experimentConfig.getIdField(), experimentDB.getExperiments().getMaxRows(),
                            queue, ex, experimentDB, scoreAggregators));
        }

        int completed = 0;
        while (completed < experimentConfig.getNumThreads()) {
            try {
                Future<Integer> future = executorCompletionService.take();
                future.get();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                completed++;
            }
        }
        executorService.shutdown();
        executorService.shutdownNow();
        insertScores(experimentDB, experimentName, scoreAggregators);
        experimentDB.insertScoresAggregated(experimentName, scoreAggregators);
        if (logResults) {
            logResults(scoreAggregators);
        }
    }

    private void logResults(List<ScoreAggregator> scoreAggregators) {
        StringBuilder result = new StringBuilder();
        for (ScoreAggregator scoreAggregator : scoreAggregators) {
            for (String querySetName : scoreAggregator.getQuerySets()) {
                Map<String, Double> summaryStats = scoreAggregator.getSummaryStatistics(querySetName);
                if (!StringUtils.isBlank(querySetName)) {
                    result.append("Query Set: ").append(querySetName);
                } else {
                    result.append("All Queries: ");
                }
                result.append(scoreAggregator.getName());
                result.append(" - ");
                if (scoreAggregator instanceof SummingScoreAggregator) {
                    result.append("sum: ");
                    result.append(getValueString(summaryStats.get(SummingScoreAggregator.SUM)));
                } else if (scoreAggregator instanceof DistributionalScoreAggregator) {
                    result.append("mean: ");
                    result.append(
                            getValueString(summaryStats.get(DistributionalScoreAggregator.MEAN)));
                    result.append(", median: ");
                    result.append(
                            getValueString(summaryStats.get(DistributionalScoreAggregator.MEDIAN)));
                }
                LOG.info(result);
                result.setLength(0);
            }
        }
    }

    protected String getValueString(Double value) {

        if (value != null) {
            if((long) value.doubleValue() == value) {
                return Long.toString( (long)value.doubleValue());
            } else {
                return threePlaces.format(value);
            }
        } else {
            return "couldn't find value?!";
        }
    }

    private void insertScores(ExperimentDB experimentDB, String experimentName, List<ScoreAggregator> scoreAggregators)
            throws SQLException {
        Set<QueryInfo> queries = scoreAggregators.get(0).getScores().keySet();
        //TODO -- need to add better handling for missing queries
        Map<String, Double> tmpScores = new HashMap<>();
        for (QueryInfo queryInfo : queries) {
            tmpScores.clear();
            for (ScoreAggregator scoreAggregator : scoreAggregators) {
                double val = scoreAggregator.getScores().get(queryInfo);
                tmpScores.put(scoreAggregator.getName(), val);
            }
            experimentDB.insertScores(queryInfo, experimentName, scoreAggregators, tmpScores);
        }

    }

    //TODO -- make this multi threaded

    /**
     * This reads through the judgment list and makes sure that the
     * a document with a given judgment's id is actually available in the
     * index.  This removes those ids that are not in the index and returns
     * a winnowed/validated {@link JudgmentList}.
     *
     * @param searchClient
     * @param judgmentList
     * @return
     */
    private JudgmentList validate(SearchClient searchClient, JudgmentList judgmentList) {

        Set<String> judgmentIds = new HashSet<>();
        for (Judgments j : judgmentList.getJudgmentsList()) {
            judgmentIds.addAll(j.getSortedJudgments().keySet());
        }

        Set<String> valid = new HashSet<>();
        StringBuilder queryString = new StringBuilder();

        int expected = 0;
        for (String id : judgmentIds) {
            if (expected++ > 0) {
                queryString.append(" OR ");
            }
            queryString.append(experimentConfig.getIdField() + ":\"" + id + "\"");
            if (queryString.length() > 1000) {
                addValid(queryString.toString(), experimentConfig.getIdField(), searchClient, expected, valid);
                queryString.setLength(0);
                expected = 0;
            }
        }
        addValid(queryString.toString(), experimentConfig.getIdField(), searchClient, expected, valid);

        int validIds = 0;
        int invalidIds = 0;
        if (judgmentIds.size() != valid.size()) {
            for (String id : judgmentIds) {
                if (!valid.contains(id)) {
                    invalidIds++;
                    LOG.warn("I regret that I could not find: " + id + " in the index. " +
                            "I'll remove this from the judgments before scoring.");
                } else {
                    validIds++;
                }
            }
        }
        if (invalidIds > 0) {
            LOG.warn("There were "+validIds +" unique valid ids and " +
                    invalidIds + " unique invalid ids");
        }
        int validQueries = 0;
        int invalidQueries = 0;
        JudgmentList retList = new JudgmentList();
        for (Judgments j : judgmentList.getJudgmentsList()) {
            Judgments winnowedJugments = new Judgments(new QueryInfo(j.getQuerySet(), j.getQuery(), j.getQueryCount()));
            for (Map.Entry<String, Double> e : j.getSortedJudgments().entrySet()) {
                if (valid.contains(e.getKey())) {
                    winnowedJugments.addJudgment(e.getKey(), e.getValue());
                } else {
                    LOG.warn("Could not find " + e.getKey() + " in the index!");
                }
            }
            if (winnowedJugments.getSortedJudgments().size() > 0) {
                retList.addJudgments(winnowedJugments);
                validQueries++;
            } else {
                LOG.warn(
                        "After removing invalid jugments, there were 0 judgments for query: " +
                                j.getQuery());
                invalidQueries++;
            }
        }
        if (validQueries > 0) {
            LOG.warn("I had to remove "+invalidQueries+" queries because there were no judgments for them. "+
                    " There were " + validQueries + " valid queries.");
        }
        return retList;

    }

    private static void addValid(String queryString, String idField, SearchClient searchClient, int expected, Set<String> valid) {
        if (expected == 0) {
            return;
        }
        QueryRequest q = new QueryRequest(queryString,
                null, idField);
        q.setNumResults(expected * 2);
        ResultSet resultSet;
        try {
            resultSet = searchClient.search(q);
        } catch (SearchClientException | IOException e) {
            throw new RuntimeException(e);
        }
        Set<String> localValid = new HashSet<>();
        for (int i = 0; i < resultSet.size(); i++) {
            String id = resultSet.get(i);
            if (localValid.contains(id)) {
                LOG.warn("Found non-unique key: " + id);
            }
            valid.add(id);
        }

    }


    static class QueryRunner implements Callable<Integer> {
        private static AtomicInteger IDs = new AtomicInteger();
        private final int threadNum = IDs.getAndIncrement();
        private final String idField;
        private final int maxRows;
        private final ArrayBlockingQueue<Judgments> queue;
        private final Experiment experiment;
        private final List<ScoreAggregator> scoreAggregators;
        private final SearchClient searchClient;

        public QueryRunner(String idField, int maxRows, ArrayBlockingQueue<Judgments> judgments,
                           Experiment experiment, ExperimentDB experimentDB,
                           List<ScoreAggregator> scoreAggregators) {
            this.idField = idField;
            this.maxRows = maxRows;
            this.queue = judgments;
            this.experiment = experiment;
            this.searchClient = SearchClientFactory.getClient(experiment.getSearchServerUrl());
            this.scoreAggregators = scoreAggregators;
        }

        @Override
        public Integer call() throws Exception {

            while (true) {
                Judgments judgments = queue.poll();
                if (judgments.equals(POISON)) {
//                    LOG.trace(threadNum + ": scorer thread hit poison. stopping now");
                    return 1;
                }
                executeTest(judgments, scoreAggregators);
            }
        }

        private void executeTest(Judgments judgments, List<ScoreAggregator> scoreAggregators) throws SQLException {
            scoreEach(judgments, scoreAggregators);
        }

        private void scoreEach(Judgments judgments, List<ScoreAggregator> scoreAggregators) {
            QueryRequest queryRequest = new QueryRequest(judgments.getQuery(), experiment.getCustomHandler(), idField);

            for (Map.Entry<String, Feature> e : experiment.getParams().entrySet()) {
                if (e.getValue() instanceof WeightableListFeature) {
                    WeightableListFeature list = (WeightableListFeature) e.getValue();
                    for (int i = 0; i < list.size(); i++) {
                        queryRequest.addParameter(e.getKey(), list.get(i).toString());
                    }
                } else if (e.getValue() instanceof StringListFeature){
                    StringListFeature list = (StringListFeature) e.getValue();
                    for (int i = 0; i < list.size(); i++) {
                        queryRequest.addParameter(e.getKey(), list.get(i));
                    }

                } else {
                    queryRequest.addParameter(e.getKey(), e.getValue().toString());
                }
            }
            List<String> results = new ArrayList<>();
            queryRequest.setNumResults(maxRows);
            for (String filterQuery : experiment.getFilterQueries()) {
                //solr specific -- clean up
                queryRequest.addParameter("fq", filterQuery);
            }
            ResultSet resultSet = null;
            try {
                resultSet = searchClient.search(queryRequest);
            } catch (SearchClientException | IOException e) {
                //TODO add exception to resultSet and log
                e.printStackTrace();
            }
            for (ScoreAggregator scoreAggregator : scoreAggregators) {
                scoreAggregator.add(judgments, resultSet);
            }
        }
    }


    ////////////DUMP RESULTS
    static void dumpResults(ExperimentDB experimentDB,
                            List<String> querySets,
                            List<ScoreAggregator> targetScorers, Path outputDir, boolean isTest) throws Exception {
        if (! Files.isDirectory(outputDir)) {
            Files.createDirectories(outputDir);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputDir.resolve("per_query_scores.csv"), StandardCharsets.UTF_8)) {
            try (Statement st = experimentDB.getConnection().createStatement()) {
                String select = experimentDB.hasNamedQuerySets() ?
                        "select * from SCORES where QUERY_SET <> ''" : "select * from SCORES";
                try (java.sql.ResultSet resultSet = st.executeQuery(select)) {
                    writeHeaders(resultSet.getMetaData(), writer);
                    while (resultSet.next()) {
                        writeRow(resultSet, writer);
                    }
                }
                writer.flush();
            }
        }
        String orderByPriority1 = null;
        String orderByPriority2 = null;
        for (ScoreAggregator scoreAggregator : experimentDB.getExperiments().getScoreAggregators()) {
            if (isTest && scoreAggregator.getUseForTest()) {
                orderByPriority1 = scoreAggregator.getPrimaryStatisticName();
                break;
            }
            if (scoreAggregator.getUseForTrain()) {
                orderByPriority2 = scoreAggregator.getPrimaryStatisticName();
            }
        }
        String orderBy = "";
        if (orderByPriority1 != null) {
            orderBy = " order by "+orderByPriority1+" desc";
        } else if (orderByPriority1 == null && orderByPriority2 != null) {
            orderBy = " order by "+orderByPriority2+" desc";
        }
        try (BufferedWriter writer = Files.newBufferedWriter(outputDir.resolve("scores_aggregated.csv"), StandardCharsets.UTF_8)) {
            try (Statement st = experimentDB.getConnection().createStatement()) {
                try (java.sql.ResultSet resultSet = st.executeQuery("select * from SCORES_AGGREGATED "+orderBy)) {
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

    private static void dumpSignificanceMatrices(String querySet, List<ScoreAggregator> targetScorers, ExperimentDB experimentDB, Path outputDir) throws Exception {
        TTest tTest = new TTest();
        for (ScoreAggregator scorer : targetScorers) {
            if (scorer.getExportPMatrix()) {
                Map<String, Double> aggregatedScores = experimentDB.getKeyExperimentScore(scorer);

                Map<String, Double> sorted = MapUtil.sortByDescendingValue(aggregatedScores);
                List<String> experiments = new ArrayList();
                experiments.addAll(sorted.keySet());
                writeMatrix(tTest, scorer, querySet, experiments, experimentDB, outputDir);
            }
        }
    }

    private static void writeMatrix(TTest tTest, ScoreAggregator scorer, String querySet,
                                    List<String> experiments, ExperimentDB experimentDB, Path outputDir) throws Exception {

        String fileName = "sig_diffs_" + scorer.getName() + (
                (StringUtils.isBlank(querySet)) ? ".csv" : "_" + querySet + ".csv");

        List<String> matrixExperiments = new ArrayList<>();
        for (int i = 0; i < experiments.size() && i < MAX_MATRIX_COLS; i++) {
            matrixExperiments.add(experiments.get(i));
        }
        try (BufferedWriter writer = Files.newBufferedWriter(outputDir.resolve(fileName))) {

            for (String experiment : matrixExperiments) {
                writer.write(",");
                writer.write(experiment);
            }
            writer.write("\n");

            for (int i = 0; i < matrixExperiments.size(); i++) {
                String experimentA = matrixExperiments.get(i);
                writer.write(experimentA);
                for (int k = 0; k <= i; k++) {
                    writer.write(",");
                }
                writer.write(String.format(Locale.US, "%.3G", 1.0d) + ",");//p-value of itself
                //map of query -> score for experiment A given this particular scorer
                Map<String, Double> scoresA = experimentDB.getScores(querySet, experimentA, scorer.getName());
                for (int j = i + 1; j < matrixExperiments.size(); j++) {
                    String experimentB = matrixExperiments.get(j);
                    double significance = calcSignificance(tTest, querySet, scoresA, experimentA, experimentB,
                            scorer.getName(), experimentDB);
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

    private static void writeRow(java.sql.ResultSet resultSet, BufferedWriter writer) throws Exception {
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
