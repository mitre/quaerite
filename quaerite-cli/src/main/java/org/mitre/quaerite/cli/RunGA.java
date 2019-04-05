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

import static org.mitre.quaerite.core.util.CommandLineUtil.getPath;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.util.FastMath;
import org.apache.log4j.Logger;
import org.mitre.quaerite.connectors.SearchClientException;
import org.mitre.quaerite.core.Experiment;
import org.mitre.quaerite.core.ExperimentFactory;
import org.mitre.quaerite.core.ExperimentSet;
import org.mitre.quaerite.core.GAConfig;
import org.mitre.quaerite.core.JudgmentList;
import org.mitre.quaerite.core.Judgments;
import org.mitre.quaerite.core.features.Feature;
import org.mitre.quaerite.core.features.ParamsMap;
import org.mitre.quaerite.core.features.SimpleStringFeature;
import org.mitre.quaerite.core.features.SimpleStringListFeature;
import org.mitre.quaerite.core.features.factories.FeatureFactory;
import org.mitre.quaerite.core.scoreaggregators.ScoreAggregator;
import org.mitre.quaerite.core.stats.ExperimentNameScorePair;
import org.mitre.quaerite.core.stats.ExperimentScorePair;
import org.mitre.quaerite.core.util.GAOperation;
import org.mitre.quaerite.core.util.MathUtil;
import org.mitre.quaerite.db.ExperimentDB;
import org.mitre.quaerite.db.GADB;
import org.mitre.quaerite.db.TrainTestJudmentListPair;

public class RunGA extends AbstractExperimentRunner {

    static Logger LOG = Logger.getLogger(RunGA.class);

    static Options OPTIONS = new Options();

    private static String GEN_PREFIX = "gen_";
    private static String TRAIN_PREFIX = "train_";
    private static String TEST_PREFIX = "test_";
    private static String FOLD_PREFIX = "fold_";
    private static String SEED_PREFIX = "seed_";


    static {
        OPTIONS.addOption(
                Option.builder("db")
                        .hasArg()
                        .required()
                        .desc("database folder").build()
        );

        OPTIONS.addOption(
                Option.builder("f")
                        .longOpt("factory")
                        .hasArg()
                        .desc("experiment factory json file")
                        .required().build()
        );
        OPTIONS.addOption(
                Option.builder("e")
                        .longOpt("experiment_seed")
                        .required(false)
                        .hasArg()
                        .desc("specify initial seed experiment(s) (json file) as the first generation; " +
                                "if not specified, first generation is randomly generated from the featuresets file").build()
        );

        OPTIONS.addOption(
                Option.builder("o")
                        .longOpt("outputDirectory")
                        .hasArg(true)
                        .required(false)
                        .desc("output directory for experiment files and results (optional; default ga_experiments").build()
        );
        OPTIONS.addOption(
                Option.builder("j")
                        .longOpt("judgments")
                        .hasArg(true)
                        .required(false)
                        .desc("judgments ('truth') file").build()
        );
        OPTIONS.addOption(
                Option.builder("test")
                        .longOpt("test_judgments")
                        .hasArg(true)
                        .required(false)
                        .desc("testing judgments ('truth') file").build()
        );
        OPTIONS.addOption(
                Option.builder("train")
                        .longOpt("train_judgments")
                        .hasArg(true)
                        .required(false)
                        .desc("training judgments ('truth') file").build()
        );
    }
    private final GAConfig gaConfig;
    private final ExperimentFactory experimentFactory;
    public RunGA(ExperimentFactory experimentFactory) {
        super(experimentFactory.getGAConfig());
        this.gaConfig = experimentFactory.getGAConfig();
        this.experimentFactory = experimentFactory;
    }
    
    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("java -jar org.mitre.quaerite.cli.RunGA", OPTIONS);
            return;
        }
        GAPaths gaPaths = new GAPaths();
        gaPaths.dbPath = getPath(commandLine, "db", false);
        gaPaths.experimentFactory = getPath(commandLine, "f", true);
        gaPaths.seedExperiments = getPath(commandLine, "e", false);
        gaPaths.outputDir = getPath(commandLine, "o", false);
        gaPaths.judgmentsFile = getPath(commandLine, "j", true);
        gaPaths.testJudgmentsFile = getPath(commandLine, "test", true);
        gaPaths.trainJudgmentsFile = getPath(commandLine, "train", true);
        if (gaPaths.outputDir == null) {
            gaPaths.outputDir = Paths.get("ga_experiments");
        }
        ExperimentFactory experimentFactory = loadExperimentFactory(gaPaths.experimentFactory);
        LOG.debug(experimentFactory.getGAConfig());
        validateCommandLine(gaPaths);
        validateSettings(experimentFactory);
        RunGA runGA = new RunGA(experimentFactory);
        if (gaPaths.judgmentsFile != null) {
            runGA.executeNFold(gaPaths);
        } else {
            runGA.executeTrainTest(gaPaths);
        }
    }

    private void executeTrainTest(GAPaths gaPaths) throws IOException, SQLException, SearchClientException {

        if (! Files.isDirectory(gaPaths.outputDir)) {
            Files.createDirectories(gaPaths.outputDir);
        }

        GADB gaDb = GADB.openAndDrop(gaPaths.dbPath);
        //gaDb = GADB.open(gaPaths.dbPath);

        loadJudgments(gaDb, gaPaths.testJudgmentsFile, true);
        JudgmentList testJudgments = gaDb.getJudgments();
        loadJudgments(gaDb, gaPaths.trainJudgmentsFile, false);
        JudgmentList allJudgments = gaDb.getJudgments();
        gaDb.initTrainTest(testJudgments, allJudgments);

        if (gaPaths.seedExperiments != null) {
            loadSeed(gaDb, gaPaths.seedExperiments, gaConfig.getNFolds());
        }

        if (gaPaths.seedExperiments == null) {
            //write out the seed generation
            generateRandomSeeds(experimentFactory, gaDb);
        }
        gaDb.addScoreAggregators(experimentFactory.getScoreAggregators());

        runFold(0, gaDb, experimentFactory, gaPaths);
        reportFinal(gaDb, experimentFactory, 1);
    }

    private void executeNFold(GAPaths gaPaths) throws IOException, SQLException, SearchClientException {

        if (! Files.isDirectory(gaPaths.outputDir)) {
            Files.createDirectories(gaPaths.outputDir);
        }

        GADB gaDb = GADB.openAndDrop(gaPaths.dbPath);

        loadJudgments(gaDb, gaPaths.judgmentsFile, true);

        if (gaPaths.seedExperiments != null) {
            loadSeed(gaDb, gaPaths.seedExperiments, gaConfig.getNFolds());
        }

        //gaDb = GADB.open(gaPaths.dbPath);

        if (gaPaths.seedExperiments == null) {
            //write out the seed generation
            generateRandomSeeds(experimentFactory, gaDb);
        }
        gaDb.addScoreAggregators(experimentFactory.getScoreAggregators());

        gaDb.initTrainTest(gaConfig.getNFolds());

        for (int i = 0; i < gaConfig.getNFolds(); i++) {
            runFold(i, gaDb, experimentFactory, gaPaths);
        }
        reportFinal(gaDb, experimentFactory, gaConfig.getNFolds());
    }

    private void reportFinal(GADB gaDb, ExperimentFactory experimentFactory, int num) throws SQLException{

        System.out.println("--------------------------------");
        System.out.println("FINAL RESULTS ON TESTING:");
        List<ExperimentNameScorePair> scores = gaDb.getNBestExperimentNames(
                TEST_PREFIX, num,
                experimentFactory.getTestScoreAggregator().getPrimaryStatisticName());

        SummaryStatistics summaryStatistics = new SummaryStatistics();
        double[] vals = new double[gaConfig.getNFolds()];
        int i = 0;
        for (ExperimentNameScorePair esp : scores) {
            System.out.println("experiment '"+esp.getExperimentName()+"': "
                    +threePlaces.format(esp.getScore()));
            vals[i++] = esp.getScore();
            summaryStatistics.addValue(esp.getScore());
        }
        if (scores.size() > 1) {
            Median median = new Median();
            median.setData(vals);
            System.out.println("");

            System.out.println("mean: " +
                    threePlaces.format(summaryStatistics.getMean()));
            System.out.println("median: " +
                    threePlaces.format(median.evaluate()));
            System.out.println("stdev:" +
                    threePlaces.format(summaryStatistics.getStandardDeviation()));
        }
    }

    private static ExperimentFactory loadExperimentFactory(Path experimentFactories) throws IOException {
        try (Reader reader = Files.newBufferedReader(experimentFactories, StandardCharsets.UTF_8)) {
            return ExperimentFactory.fromJson(reader);
        }
    }

    private void loadSeed(GADB gaDb, Path seedExperiments, int folds) throws SQLException, IOException {
        try (Reader reader = Files.newBufferedReader(seedExperiments, StandardCharsets.UTF_8)) {
            ExperimentSet set = ExperimentSet.fromJson(reader);
            int i = 0;
            for (Experiment experiment : set.getExperiments().values()) {
                for (int fold = 0; fold < folds; fold++) {
                    String seedName = getSeedName(fold, i);
                    experiment.setName(seedName);
                    gaDb.addExperiment(experiment);
                }
                i++;
            }
        }
    }


    private void runFold(int fold, GADB gaDb, ExperimentFactory experimentFactory, GAPaths gaPaths) throws IOException, SQLException, SearchClientException {
        TrainTestJudmentListPair trainTestJudmentListPair = gaDb.getTrainTestJudgmentsByFold(fold);
        JudgmentList trainJudgmentList = trainTestJudmentListPair.getTrain();
        LOG.info("scoring training seed for fold: "+fold);
        scoreSeed(fold, gaDb, trainJudgmentList,
                experimentFactory.getTrainScoreAggregator().getPrimaryStatisticName(), gaPaths);
        LOG.info("starting training for fold "+fold +"; train set size ("+trainJudgmentList.getJudgmentsList().size()+
                "), test set size ("+trainTestJudmentListPair.getTest().getJudgmentsList().size()+
                ")");
        if (LOG.isDebugEnabled()) {
            for (Judgments j : trainJudgmentList.getJudgmentsList()) {
                LOG.debug("fold " + fold + ", training query: " + j.getQuery());
            }
            for (Judgments j : trainTestJudmentListPair.getTest().getJudgmentsList()) {
                LOG.debug("fold " + fold + ", testing query: " + j.getQuery());
            }
        }

        for (int i = 0; i < gaConfig.getGenerations(); i++) {
            runGeneration(fold, i, gaDb, experimentFactory, trainJudgmentList, gaPaths);
        }
        List<ExperimentNameScorePair> scores = gaDb.getNBestExperimentNames(
                TRAIN_PREFIX+FOLD_PREFIX+fold+"_*", 10,
                experimentFactory.getTrainScoreAggregator().getPrimaryStatisticName());
        System.out.println("FOLD "+fold+" TRAINING");
        for (ExperimentNameScorePair esp : scores) {
            System.out.println("experiment '"+esp.getExperimentName()+"': "+threePlaces.format(esp.getScore()));
        }
        System.out.println("");

        JudgmentList testingJudgments = trainTestJudmentListPair.getTest();
        List<ExperimentScorePair> experiments = gaDb.getNBestExperiments("train_fold_"+fold+"_", 1, "ndcg_10_mean");
        Experiment bestTrainingExperiment = experiments.get(0).getExperiment();
        String testName = getTestExperimentName(bestTrainingExperiment.getName());

        bestTrainingExperiment.setName(testName);
        gaDb.addExperiment(bestTrainingExperiment);
        runExperiment(testName,
                gaDb, testingJudgments, "test_"+fold, false);
        scores = gaDb.getNBestExperimentNames(
                TEST_PREFIX+FOLD_PREFIX+fold+"_*", 10,
                experimentFactory.getTrainScoreAggregator().getPrimaryStatisticName());
        System.out.println("FOLD "+fold+" TESTING");
        for (ExperimentNameScorePair esp : scores) {
            System.out.println("experiment '"+esp.getExperimentName()+"': "+threePlaces.format(esp.getScore()));
        }
        System.out.println("");

    }

    private void scoreSeed(int fold, GADB gaDb, JudgmentList trainJudgmentList,
                           String trainScoreAggregatorName, GAPaths gaPaths) throws SQLException, IOException, SearchClientException {
        ExperimentSet experimentSet = gaDb.getExperiments(gaConfig);

        String trainFoldSeedPrefix = TRAIN_PREFIX+FOLD_PREFIX+fold+"_"+SEED_PREFIX;
        for (String experimentName : experimentSet.getExperiments().keySet()) {
            if (experimentName.startsWith(trainFoldSeedPrefix)) {
                runExperiment(experimentName, gaDb, trainJudgmentList,
                        "seed_test_fold_" + fold, false);
            }
        }

        System.out.println("FOLD "+fold + " TRAINING (SEED)");
        List<ExperimentNameScorePair> scores = gaDb.getNBestExperimentNames(
                trainFoldSeedPrefix, 10,
                trainScoreAggregatorName);

        for (ExperimentNameScorePair esp : scores) {
            System.out.println("experiment '"+esp.getExperimentName()+"': "
                    +threePlaces.format(esp.getScore()));
        }
        System.out.println("");
        System.out.println("");

        String json = experimentSet.toJson();

        Files.write(gaPaths.outputDir.resolve("seed_experiments.json"),
                json.getBytes(StandardCharsets.UTF_8));

    }

    private void runGeneration(int fold, int generation, ExperimentDB experimentDB,
                               ExperimentFactory experimentFactory,
                               JudgmentList judgmentList, GAPaths gaPaths) throws SQLException, IOException, SearchClientException {
        List<String> experimentNames = generateNewExperiments(fold, generation,
                experimentDB, experimentFactory);
        LOG.info("starting generation "+generation + " for fold "+fold);
        for (String experimentName : experimentNames) {
            runExperiment(experimentName, experimentDB, judgmentList, "foldId_"+fold,
                    false);
        }
        if (LOG.isDebugEnabled()) {
            String experimentPrefix = TRAIN_PREFIX+FOLD_PREFIX+fold+"_"+GEN_PREFIX+generation;
            List<ExperimentNameScorePair> results = experimentDB.getNBestExperimentNames(experimentPrefix, 10,
                    experimentFactory.getTrainScoreAggregator().getPrimaryStatisticName());

            for (ExperimentNameScorePair experimentScorePair : results) {
                LOG.debug(experimentScorePair);
            }

        }
        ExperimentSet experimentSet = experimentDB.getExperiments(experimentFactory.getGAConfig());
        String json = experimentSet.toJson(experimentNames);

        Files.write(gaPaths.outputDir.resolve("fold_"+fold+"_gen_"+generation+"_experiments.json"),
                json.getBytes(StandardCharsets.UTF_8));
    }

    private List<String> generateNewExperiments(int fold, int generation,
                                                ExperimentDB experimentDB,
                                                ExperimentFactory experimentFactory) throws SQLException{

        //this currently only pulls from the previous generation
        String genString = (generation == 0) ? "seed" : GEN_PREFIX+(generation-1);

        List<ExperimentScorePair> scorePairs = experimentDB.getNBestExperiments(
                TRAIN_PREFIX+FOLD_PREFIX+fold+"_"+genString,
                gaConfig.getPopulation(),
                experimentFactory.getTrainScoreAggregator().getPrimaryStatisticName());

        if (scorePairs.size() == 0) {
            throw new IllegalArgumentException("Need to have some experiments from seed/last generation!");
        }        int expCount = 0;

        List<ExperimentScorePair> fitnessProportions = MathUtil.calcFitnessProportions(scorePairs);
        List<String> nextGenExpNames = new ArrayList<>();

        while (nextGenExpNames.size() < gaConfig.getPopulation()) {
            GAOperation gaOperation = MathUtil.nextGAOperation(gaConfig);
            switch (gaOperation) {
                case CROSSOVER:
                    crossover(fold, generation, fitnessProportions, nextGenExpNames, experimentDB);
                    break;
                case REPRODUCE:
                    reproduce(fold, generation, fitnessProportions, nextGenExpNames, experimentDB);
                    break;
                case MUTATE:
                    mutate(fold, generation, fitnessProportions, nextGenExpNames, experimentDB);
                    break;
            }
        }
        return nextGenExpNames;
    }

    private void mutate(int fold, int generation,
                        List<ExperimentScorePair> fitnessProportions,
                        List<String> nextGenExpNames, ExperimentDB experimentDB) throws SQLException {
        Experiment parent = MathUtil.select(fitnessProportions);
        Experiment mutated = experimentFactory.mutate(parent, gaConfig.getMutationProbability(), gaConfig.getMutationAmplitude());
        String name = getTrainExperimentName(fold, generation, nextGenExpNames.size());
        nextGenExpNames.add(name);
        experimentDB.addExperiment(mutated);
    }

    private void reproduce(int fold, int generation, List<ExperimentScorePair> fitnessProportions,
                           List<String> nextGenExpNames, ExperimentDB experimentDB) throws SQLException {
        Experiment parent = MathUtil.select(fitnessProportions);
        LOG.trace("reproducing: " + parent);
        String name = getTrainExperimentName(fold, generation, nextGenExpNames.size());
        Experiment child = parent.deepCopy();
        child.setName(name);
        experimentDB.addExperiment(child);
        nextGenExpNames.add(name);
    }

    private void crossover(int fold, int generation, List<ExperimentScorePair> fitnessProportions,
                           List<String> nextGenExpNames, ExperimentDB experimentDB) throws SQLException {
        Experiment parentA = MathUtil.select(fitnessProportions);
        Experiment parentB = MathUtil.select(fitnessProportions);
        int tries = 0;
        while (parentA.getName().equals(parentB.getName()) && tries++ < 5) {
            parentA = MathUtil.select(fitnessProportions);
            parentB = MathUtil.select(fitnessProportions);
        }
        if (tries == 5 && parentA.getName().equals(parentB.getName())) {
            LOG.warn("crossover with self: "+parentA.getName());
        }
        LOG.trace("crossing over: " + parentA + " : " + parentB);
        Pair<Experiment, Experiment> pair = experimentFactory.crossover(parentA, parentB);

        String nameA = getTrainExperimentName(fold, generation, nextGenExpNames.size());
        pair.getLeft().setName(nameA);

        LOG.trace(parentA +
                "\n+\n" + parentB + "\n->\n" + pair.getLeft());
        experimentDB.addExperiment(pair.getLeft());
        nextGenExpNames.add(nameA);

        if (nextGenExpNames.size() >= gaConfig.getPopulation()) {
            return;
        }

        String nameB = getTrainExperimentName(fold, generation, nextGenExpNames.size());
        pair.getRight().setName(nameB);
        LOG.trace("childB: " + pair.getRight());

        nextGenExpNames.add(nameB);
        experimentDB.addExperiment(pair.getRight());
    }

    private String getSeedName(int fold, int i) {
        return TRAIN_PREFIX + FOLD_PREFIX + fold + "_seed_exp_" + i;
    }

    private String getTestExperimentName(String trainExperimentName) {
        return TEST_PREFIX +
                trainExperimentName.substring(TRAIN_PREFIX.length(), trainExperimentName.length());
    }

    private String getTrainExperimentName(int fold, int generation, int i) {
        return TRAIN_PREFIX+FOLD_PREFIX+fold+"_"+GEN_PREFIX+generation+"_exp_"+i;
    }

    private void generateRandomSeeds(ExperimentFactory experimentFactory, GADB gadb) throws SQLException {
        for (int fold = 0; fold < gaConfig.getNFolds(); fold++) {
            for (int i = 0; i < gaConfig.getPopulation(); i++) {
                Experiment ex = experimentFactory.generateRandomExperiment(getSeedName(fold, i));
                gadb.addExperiment(ex);
            }
        }
    }


    private static class GAPaths {
        Path testJudgmentsFile;
        Path trainJudgmentsFile;
        Path judgmentsFile;
        Path dbPath;
        Path experimentFactory;
        Path seedExperiments;
        Path outputDir;
    }

    private static void validateSettings(ExperimentFactory experimentFactory) {
        GAConfig gaConfig = experimentFactory.getGAConfig();
        double gaOpProbs = gaConfig.getCrossoverProbability()+
                gaConfig.getMutationProbability()+gaConfig.getReproductionProbability();
        if (Math.abs(1.0d-gaOpProbs) > 0.001) {
            throw new IllegalArgumentException("crossoverProbability+mutationProbability+reproductionProbability should = 1.0");
        }
        ScoreAggregator trainScoreAggregator = null;
        ScoreAggregator testScoreAggregator = null;
        for (ScoreAggregator scoreAggregator : experimentFactory.getScoreAggregators()) {
            if (scoreAggregator.getUseForTrain()) {
                if (trainScoreAggregator != null) {
                    throw new IllegalArgumentException("Can't have more than one trainScoreAggregator:"+
                            trainScoreAggregator + " and "+scoreAggregator);
                }
                trainScoreAggregator = scoreAggregator;
            }
            if (scoreAggregator.getUseForTest()) {
                if (testScoreAggregator != null) {
                    throw new IllegalArgumentException("Can't have more than one trainScoreAggregator:"+
                            testScoreAggregator + " and "+scoreAggregator);
                }
                testScoreAggregator = scoreAggregator;
            }
        }
    }

    private static void validateCommandLine(GAPaths gaPaths) {
        if (gaPaths.judgmentsFile != null &&
                (gaPaths.trainJudgmentsFile != null
                        || gaPaths.testJudgmentsFile != null)) {
            throw new IllegalArgumentException("Must either select -j or (-train AND -test). Can't mix two modes.");
        }
        if (gaPaths.trainJudgmentsFile != null && gaPaths.testJudgmentsFile == null
                || gaPaths.trainJudgmentsFile == null && gaPaths.testJudgmentsFile != null) {
            throw new IllegalArgumentException("Must specify both a -train AND -test if specifying one");
        }

        if (gaPaths.judgmentsFile == null && gaPaths.trainJudgmentsFile == null &&
                gaPaths.testJudgmentsFile == null) {
            throw new IllegalArgumentException("Must either select -j (for nfold cross validation) or (-train AND -test)");
        }
    }


}
