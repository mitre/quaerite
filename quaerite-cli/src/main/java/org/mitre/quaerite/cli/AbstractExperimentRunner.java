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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.mitre.quaerite.connectors.QueryRequest;
import org.mitre.quaerite.connectors.SearchClient;
import org.mitre.quaerite.connectors.SearchClientException;
import org.mitre.quaerite.connectors.SearchClientFactory;
import org.mitre.quaerite.core.Experiment;
import org.mitre.quaerite.core.ExperimentSet;
import org.mitre.quaerite.core.JudgmentList;
import org.mitre.quaerite.core.Judgments;
import org.mitre.quaerite.core.QueryInfo;
import org.mitre.quaerite.core.ResultSet;
import org.mitre.quaerite.core.features.Feature;
import org.mitre.quaerite.core.features.WeightableListFeature;
import org.mitre.quaerite.core.scorecollectors.ScoreCollector;
import org.mitre.quaerite.db.ExperimentDB;

public abstract class AbstractExperimentRunner extends AbstractCLI {
    static final Judgments POISON = new Judgments(new QueryInfo("", "", -1));

    static Logger LOG = Logger.getLogger(AbstractExperimentRunner.class);

    static final int DEFAULT_NUM_THREADS = 8;

    //this caches a judgment list of valid judgments
    //per search server url
    Map<String, JudgmentList> searchServerValidatedMap = new HashMap<>();

    private final int numThreads;

    public AbstractExperimentRunner(int numThreads) {
        this.numThreads = numThreads;
    }


    void runExperiment(String experimentName, ExperimentDB experimentDB) throws SQLException {
        if (experimentDB.hasScores(experimentName)) {
            LOG.info("Already has scores for " + experimentName + "; skipping");
            return;
        }
        LOG.info("running experiment " + experimentName);
        ExperimentSet experimentSet = experimentDB.getExperiments();
        Experiment ex = experimentSet.getExperiment(experimentName);
        List<ScoreCollector> scoreCollectors = experimentSet.getScoreCollectors();
        experimentDB.initScoreTable(scoreCollectors);
        JudgmentList judgmentList = experimentDB.getJudgments();
        SearchClient searchClient = SearchClientFactory.getClient(ex.getSearchServerUrl());
        JudgmentList validated = searchServerValidatedMap.get(ex.getSearchServerUrl());
        if (validated == null) {
            validated = validate(searchClient, judgmentList);
            searchServerValidatedMap.put(ex.getSearchServerUrl(), validated);
        }
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        ExecutorCompletionService<Integer> executorCompletionService = new ExecutorCompletionService<>(executorService);
        ArrayBlockingQueue<Judgments> queue = new ArrayBlockingQueue<>(
                validated.getJudgmentsList().size() + numThreads);

        queue.addAll(validated.getJudgmentsList());
        for (int i = 0; i < numThreads; i++) {
            queue.add(POISON);
        }

        for (int i = 0; i < numThreads; i++) {
            executorCompletionService.submit(
                    new QueryRunner(validated.getIdField(), experimentDB.getExperiments().getMaxRows(),
                            queue, ex, experimentDB, scoreCollectors));
        }

        int completed = 0;
        while (completed < numThreads) {
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
        long start = System.currentTimeMillis();
        insertScores(experimentDB, experimentName, scoreCollectors);
        experimentDB.insertScoresAggregated(experimentName, scoreCollectors);
    }

    private void insertScores(ExperimentDB experimentDB, String experimentName, List<ScoreCollector> scoreCollectors)
            throws SQLException {
        Set<QueryInfo> queries = scoreCollectors.get(0).getScores().keySet();
        //TODO -- need to add better handling for missing queries
        Map<String, Double> tmpScores = new HashMap<>();
        for (QueryInfo queryInfo : queries) {
            tmpScores.clear();
            for (ScoreCollector scoreCollector : scoreCollectors) {
                double val = scoreCollector.getScores().get(queryInfo);
                tmpScores.put(scoreCollector.getName(), val);
            }
            experimentDB.insertScores(queryInfo, experimentName, scoreCollectors, tmpScores);
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
    private static JudgmentList validate(SearchClient searchClient, JudgmentList judgmentList) {

        Set<String> ids = new HashSet<>();
        for (Judgments j : judgmentList.getJudgmentsList()) {
            ids.addAll(j.getSortedJudgments().keySet());
        }

        Set<String> valid = new HashSet<>();
        for (String id : ids) {
            QueryRequest q = new QueryRequest(
                    judgmentList.getIdField() + ":\"" + id + "\"",
                    null, judgmentList.getIdField());
            ResultSet resultSet;
            try {
                resultSet = searchClient.search(q);
            } catch (SearchClientException | IOException e) {
                throw new RuntimeException(e);
            }
            long numFound = resultSet.getTotalHits();
            if (numFound == 0L) {
                LOG.warn("Couldn't find expected document: " + id);
            } else if (numFound > 1L) {
                LOG.warn("Found non-unique key: " + id);
            } else {
                valid.add(id);
            }
        }

        JudgmentList retList = new JudgmentList(judgmentList.getIdField());
        for (Judgments j : judgmentList.getJudgmentsList()) {
            Judgments winnowedJugments = new Judgments(new QueryInfo(j.getQuerySet(), j.getQuery(), j.getQueryCount()));
            for (Map.Entry<String, Double> e : j.getSortedJudgments().entrySet()) {
                if (valid.contains(e.getKey())) {
                    winnowedJugments.addJugment(e.getKey(), e.getValue());
                }
            }
            if (winnowedJugments.getSortedJudgments().size() > 0) {
                retList.addJudgments(winnowedJugments);
            } else {
                System.err.println("After removing invalid jugments, there were 0 judgments for query: " +
                        j.getQuery());
                LOG.warn(
                        "After removing invalid jugments, there were 0 judgments for query: " +
                                j.getQuery());
            }
        }
        return retList;

    }


    static class QueryRunner implements Callable<Integer> {
        private static AtomicInteger IDs = new AtomicInteger();
        private final int threadNum = IDs.getAndIncrement();
        private final String idField;
        private final int maxRows;
        private final ArrayBlockingQueue<Judgments> queue;
        private final Experiment experiment;
        private final List<ScoreCollector> scoreCollectors;
        private final SearchClient searchClient;

        public QueryRunner(String idField, int maxRows, ArrayBlockingQueue<Judgments> judgments,
                           Experiment experiment, ExperimentDB experimentDB,
                           List<ScoreCollector> scoreCollectors) {
            this.idField = idField;
            this.maxRows = maxRows;
            this.queue = judgments;
            this.experiment = experiment;
            this.searchClient = SearchClientFactory.getClient(experiment.getSearchServerUrl());
            this.scoreCollectors = scoreCollectors;
        }

        @Override
        public Integer call() throws Exception {

            while (true) {
                Judgments judgments = queue.poll();
                if (judgments.equals(POISON)) {
                    LOG.info(threadNum + ": Hit poison stopping");
                    return 1;
                }
                executeTest(judgments, scoreCollectors);
            }
        }

        private void executeTest(Judgments judgments, List<ScoreCollector> scoreCollectors) throws SQLException {
            scoreEach(judgments, scoreCollectors);
        }

        private void scoreEach(Judgments judgments, List<ScoreCollector> scoreCollectors) {
            QueryRequest queryRequest = new QueryRequest(judgments.getQuery(), experiment.getCustomHandler(), idField);

            for (Map.Entry<String, Feature> e : experiment.getParams().entrySet()) {
                if (e.getValue() instanceof WeightableListFeature) {
                    WeightableListFeature list = (WeightableListFeature)e.getValue();
                    for (int i = 0; i < list.size(); i++) {
                        queryRequest.addParameter(e.getKey(), list.get(i).toString());
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
            for (ScoreCollector scoreCollector : scoreCollectors) {
                scoreCollector.add(judgments, resultSet);
            }
        }
    }
}
