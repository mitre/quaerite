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
import java.nio.file.Path;
import java.nio.file.Paths;
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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mitre.quaerite.Experiment;
import org.mitre.quaerite.ExperimentSet;
import org.mitre.quaerite.JudgmentList;
import org.mitre.quaerite.Judgments;
import org.mitre.quaerite.QueryInfo;
import org.mitre.quaerite.ResultSet;
import org.mitre.quaerite.connectors.QueryRequest;
import org.mitre.quaerite.connectors.SearchServer;
import org.mitre.quaerite.connectors.SearchServerException;
import org.mitre.quaerite.connectors.solr.SolrServer_4x;
import org.mitre.quaerite.db.ExperimentDB;
import org.mitre.quaerite.scorecollectors.ScoreCollector;

public class RunExperiments {

    static final int NUM_THREADS = 8;
    static final Judgments POISON = new Judgments(new QueryInfo("", "", -1));

    static Logger LOG = Logger.getLogger(RunExperiments.class);

    static Options OPTIONS = new Options();

    static {
        OPTIONS.addOption("db", "db", true, "database folder");
        OPTIONS.addOption("freshStart", "freshStart", false, "delete all results");
        OPTIONS.addOption("e", "experiment", true, "which experiment to run");
        OPTIONS.addOption("l", "latest", false, "rerun just the most recently added experiment");
    }

    Map<String, JudgmentList> solrUrlValidatedMap = new HashMap<>();
    long batchStart = -1l;

    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new GnuParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("java -jar org.mitre.eval.RunExperiments", OPTIONS);
            return;
        }

        Path dbDir = Paths.get(commandLine.getOptionValue("db"));
        String experimentName = (commandLine.hasOption("experiment")) ? commandLine.getOptionValue("experiment") : "";
        boolean freshStart = (commandLine.hasOption("freshStart")) ? true : false;
        boolean latest = (commandLine.hasOption("latest")) ? true : false;

        RunExperiments runExperiments = new RunExperiments();
        runExperiments.run(dbDir, experimentName, freshStart, latest);
    }

    private void run(Path dbDir, String experimentName, boolean freshStart, boolean latest) throws SQLException, IOException {
        try (ExperimentDB experimentDB = ExperimentDB.open(dbDir)) {

            ExperimentSet experimentSet = experimentDB.getExperiments();
            if (freshStart) {
                experimentDB.clearScores();
            } else if (latest) {
                experimentName = experimentDB.getLatestExperiment();
            }
            if (StringUtils.isBlank(experimentName)) {
                batchStart = System.currentTimeMillis();
                int finished = 0;
                for (String eName : experimentSet.getExperiments().keySet()) {
                    runExperiment(eName, experimentDB);
                    long elapsed = System.currentTimeMillis() - batchStart;
                    finished++;
                    System.out.println("Finished " + finished + " in " +
                            (double) elapsed / (double) 1000 + " seconds");
                    double perExperiment = (double) elapsed / (double) finished;
                    int togo = experimentSet.getExperiments().entrySet().size() - finished;
                    System.out.println("Still have " + togo + " to go; estimate: " +
                            ((double) togo * perExperiment) / (double) 1000 + " seconds");
                }
            } else {
                Experiment experiment = experimentDB.getExperiments().getExperiment(experimentName);
                if (experiment == null) {
                    System.err.println("I'm sorry, but I couldn't find this experiment:" + experimentName);
                    return;
                }
                experimentDB.clearScores(experimentName);
                runExperiment(experimentName, experimentDB);
            }
        }
    }

    private void runExperiment(String experimentName, ExperimentDB experimentDB) throws SQLException {
        if (experimentDB.hasScores(experimentName)) {
            LOG.info("Already has scores for " + experimentName + "; skipping");
            return;
        }
        LOG.info("running experiment " + experimentName);
        System.out.println("running " + experimentName);
        ExperimentSet experimentSet = experimentDB.getExperiments();
        Experiment ex = experimentSet.getExperiment(experimentName);
        List<ScoreCollector> scoreCollectors = experimentSet.getScoreCollectors();
        experimentDB.initScoreTable(scoreCollectors);
        JudgmentList judgmentList = experimentDB.getJudgments();
        JudgmentList validated = solrUrlValidatedMap.get(ex.getSolrUrl());
        if (validated == null) {
            validated = validate(ex.getSolrUrl(), judgmentList);
            solrUrlValidatedMap.put(ex.getSolrUrl(), validated);
        }
        ExecutorService executorService = Executors.newFixedThreadPool(NUM_THREADS);
        ExecutorCompletionService<Integer> executorCompletionService = new ExecutorCompletionService<>(executorService);
        ArrayBlockingQueue<Judgments> queue = new ArrayBlockingQueue<>(
                validated.getJudgmentsList().size() + NUM_THREADS);

        queue.addAll(validated.getJudgmentsList());
        for (int i = 0; i < NUM_THREADS; i++) {
            queue.add(POISON);
        }

        for (int i = 0; i < NUM_THREADS; i++) {
            executorCompletionService.submit(
                    new QueryRunner(validated.getIdField(), experimentDB.getExperiments().getMaxRows(),
                            queue, ex, experimentDB, scoreCollectors));
        }

        int completed = 0;
        while (completed < NUM_THREADS) {
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
        System.out.println("took " + (System.currentTimeMillis() - start) + " milliseconds to summarize scores");
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

    private static JudgmentList validate(String solrUrl, JudgmentList judgmentList) {
        SearchServer solrServer = new SolrServer_4x(solrUrl);

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
                resultSet = solrServer.search(q);
            } catch (SearchServerException | IOException e) {
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

    private static class QueryRunner implements Callable<Integer> {
        private static AtomicInteger IDs = new AtomicInteger();
        private final int threadNum = IDs.getAndIncrement();
        private final String idField;
        private final int maxRows;
        private final ArrayBlockingQueue<Judgments> queue;
        private final Experiment experiment;
        private final ExperimentDB experimentDB;
        private final List<ScoreCollector> scoreCollectors;
        private final SearchServer solrServer;

        public QueryRunner(String idField, int maxRows, ArrayBlockingQueue<Judgments> judgments,
                           Experiment experiment, ExperimentDB experimentDB,
                           List<ScoreCollector> scoreCollectors) {
            this.idField = idField;
            this.maxRows = maxRows;
            this.queue = judgments;
            this.experiment = experiment;
            this.experimentDB = experimentDB;
            this.solrServer = new SolrServer_4x(experiment.getSolrUrl());
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

            for (Map.Entry<String, String[]> e : experiment.getParams().entrySet()) {
                for (String val : e.getValue()) {
                    queryRequest.addParameter(e.getKey(), val);
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
                resultSet = solrServer.search(queryRequest);
            } catch (SearchServerException | IOException e) {
                e.printStackTrace();
            }
            for (ScoreCollector scoreCollector : scoreCollectors) {
                scoreCollector.add(judgments, resultSet);
            }
        }
    }

}
