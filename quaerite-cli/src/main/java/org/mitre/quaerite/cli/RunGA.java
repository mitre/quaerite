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
import org.mitre.quaerite.core.Experiment;
import org.mitre.quaerite.core.ExperimentFactory;
import org.mitre.quaerite.core.ExperimentSet;
import org.mitre.quaerite.core.GAConfig;
import org.mitre.quaerite.core.JudgmentList;
import org.mitre.quaerite.core.Judgments;
import org.mitre.quaerite.core.features.factories.FeatureFactories;
import org.mitre.quaerite.core.features.factories.FeatureFactory;
import org.mitre.quaerite.db.ExperimentDB;
import org.mitre.quaerite.db.ExperimentScorePair;
import org.mitre.quaerite.core.features.Feature;
import org.mitre.quaerite.core.features.ParamsMap;
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

    private static final int DEFAULT_NFOLDS = 5;

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
                        .required(true)
                        .desc("judgments ('truth') file").build()
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
        gaPaths.experimentFactories = getPath(commandLine, "f", true);
        gaPaths.seedExperiments = getPath(commandLine, "e", false);
        gaPaths.outputDir = getPath(commandLine, "o", false);
        gaPaths.judgmentsFile = getPath(commandLine, "j", true);
        if (gaPaths.outputDir == null) {
            gaPaths.outputDir = Paths.get("ga_experiments");
        }
        int threads = getInt(commandLine, "n", 8);
        ExperimentFactory experimentFactory = loadExperimentFactory(gaPaths.experimentFactories);
        LOG.debug(experimentFactory.getGAConfig());
        RunGA runGA = new RunGA(experimentFactory);
        runGA.execute(gaPaths);
    }

    private static ExperimentFactory loadExperimentFactory(Path experimentFactories) throws IOException {
        ExperimentFactory experimentFactory = null;

        try (Reader reader = Files.newBufferedReader(experimentFactories, StandardCharsets.UTF_8)) {
            return ExperimentFactory.fromJson(reader);
        }
    }


    private void execute(GAPaths gaPaths) throws IOException, SQLException {

        if (! Files.isDirectory(gaPaths.outputDir)) {
            Files.createDirectories(gaPaths.outputDir);
        }

        GADB gaDb = GADB.openAndDrop(gaPaths.dbPath);

        loadJudgments(gaDb, gaPaths.judgmentsFile, true);

        if (gaPaths.seedExperiments != null) {
            loadSeed(gaDb, gaPaths.seedExperiments, gaConfig.getNFolds());
        }

        gaDb = GADB.open(gaPaths.dbPath);

        if (gaPaths.seedExperiments == null) {
            //write out the seed generation
            generateRandomSeeds(experimentFactory.getFeatureFactories(), gaDb);
        }
        gaDb.addScoreCollectors(experimentFactory.getScoreCollectors());

        gaDb.initTrainTest(gaConfig.getNFolds());

        for (int i = 0; i < gaConfig.getNFolds(); i++) {
            runFold(i, gaDb, experimentFactory, gaPaths);
        }
        System.out.println("--------------------------------");
        System.out.println("FINAL RESULTS ON TESTING:");
        List<ExperimentScorePair> scores = gaDb.getNBestResults(
                TEST_PREFIX, gaConfig.getNFolds(),
                experimentFactory.getTestScoreCollector().getPrimaryStatisticName());

        SummaryStatistics summaryStatistics = new SummaryStatistics();
        double[] vals = new double[gaConfig.getNFolds()];
        int i = 0;
        for (ExperimentScorePair esp : scores) {
            System.out.println("experiment '"+esp.getExperimentName()+"': "
                    +threePlaces.format(esp.getScore()));
            vals[i++] = esp.getScore();
            summaryStatistics.addValue(esp.getScore());
        }
        Median median = new Median();
        median.setData(vals);
        System.out.println("");

        System.out.println("mean: "+
                threePlaces.format(summaryStatistics.getMean()));
        System.out.println("median: "+
                threePlaces.format(median.evaluate()));
        System.out.println("stdev:"+
                threePlaces.format(summaryStatistics.getStandardDeviation()));
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


    private void runFold(int fold, GADB gaDb, ExperimentFactory experimentFactory, GAPaths gaPaths) throws IOException, SQLException {
        TrainTestJudmentListPair trainTestJudmentListPair = gaDb.getTrainTestJudgmentsByFold(fold);
        JudgmentList trainJudgmentList = trainTestJudmentListPair.getTrain();
        LOG.info("scoring training seed for fold: "+fold);
        scoreSeed(fold, gaDb, trainJudgmentList,
                experimentFactory.getTrainScoreCollector().getPrimaryStatisticName(), gaPaths);
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
        List<ExperimentScorePair> scores = gaDb.getNBestResults(
                TRAIN_PREFIX+FOLD_PREFIX+fold+"_*", 10,
                experimentFactory.getTrainScoreCollector().getPrimaryStatisticName());
        System.out.println("FOLD "+fold+" TRAINING");
        for (ExperimentScorePair esp : scores) {
            System.out.println("experiment '"+esp.getExperimentName()+"': "+threePlaces.format(esp.getScore()));
        }
        System.out.println("");

        JudgmentList testingJudgments = trainTestJudmentListPair.getTest();
        List<Experiment> experiments = gaDb.getNBestExperiments("train_fold_"+fold+"_", 1, "ndcg_10_mean");
        Experiment bestTrainingExperiment = experiments.get(0);
        String testName = getTestExperimentName(bestTrainingExperiment.getName());

        bestTrainingExperiment.setName(testName);
        gaDb.addExperiment(bestTrainingExperiment);
        runExperiment(testName,
                gaDb, testingJudgments, "test_"+fold, false);
        scores = gaDb.getNBestResults(
                TEST_PREFIX+FOLD_PREFIX+fold+"_*", 10,
                experimentFactory.getTrainScoreCollector().getPrimaryStatisticName());
        System.out.println("FOLD "+fold+" TESTING");
        for (ExperimentScorePair esp : scores) {
            System.out.println("experiment '"+esp.getExperimentName()+"': "+threePlaces.format(esp.getScore()));
        }
        System.out.println("");

    }

    private void scoreSeed(int fold, GADB gaDb, JudgmentList trainJudgmentList,
                           String trainScoreCollectorName, GAPaths gaPaths) throws SQLException, IOException {
        ExperimentSet experimentSet = gaDb.getExperiments(gaConfig);

        String trainFoldSeedPrefix = TRAIN_PREFIX+FOLD_PREFIX+fold+"_"+SEED_PREFIX;
        for (String experimentName : experimentSet.getExperiments().keySet()) {
            if (experimentName.startsWith(trainFoldSeedPrefix)) {
                runExperiment(experimentName, gaDb, trainJudgmentList,
                        "seed_test_fold_" + fold, false);
            }
        }

        System.out.println("FOLD "+fold + " TRAINING (SEED)");
        List<ExperimentScorePair> scores = gaDb.getNBestResults(
                trainFoldSeedPrefix, 10,
                trainScoreCollectorName);

        for (ExperimentScorePair esp : scores) {
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
                               JudgmentList judgmentList, GAPaths gaPaths) throws SQLException, IOException {
        List<String> experimentNames = generateNewExperiments(fold, generation,
                experimentDB, experimentFactory, gaPaths);
        LOG.info("starting generation "+generation);
        for (String experimentName : experimentNames) {
            runExperiment(experimentName, experimentDB, judgmentList, "foldId_"+fold,
                    false);
        }
        if (LOG.isDebugEnabled()) {
            String experimentPrefix = TRAIN_PREFIX+FOLD_PREFIX+fold+"_"+GEN_PREFIX+generation;
            List<ExperimentScorePair> results = experimentDB.getNBestResults(experimentPrefix, 10,
                    experimentFactory.getTrainScoreCollector().getPrimaryStatisticName());

            for (ExperimentScorePair experimentScorePair : results) {
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
                                                ExperimentFactory experimentFactory,
                                                GAPaths gaPaths) throws SQLException{

        int listLength = calcListLength(gaConfig.getPopulation());
        //this prioritizes the more fit in affecting crossover
        listLength = (int)((double)listLength/1.25f);
        listLength = (listLength < 2) ? 2 : listLength;
        //create new experiments
        //this call includes all previous generations -- intergenerational swapping
        //        String previousGenerationsNamePattern = (generation == 0) ? "*" : GEN_PREFIX+(generation-1)+"_*";
        //To limit to the previous generation add the above. to the call to getNBest
        //TODO: parameterize this?
        List<Experiment> experiments = experimentDB.getNBestExperiments(
                TRAIN_PREFIX+FOLD_PREFIX+fold+"_", listLength,
                experimentFactory.getTrainScoreCollector().getPrimaryStatisticName());
        if (experiments.size() == 0) {
            throw new IllegalArgumentException("Need to have some experiments from seed/last generation!");
        }        int expCount = 0;
        List<String> nextGenExpNames = new ArrayList<>();
        while (nextGenExpNames.size() < gaConfig.getPopulation()) {
            for (int i = 0; i < experiments.size() - 1; i++) {
                for (int j = i + 1; j < experiments.size(); j++) {
                    Pair<ParamsMap, ParamsMap> pair = experiments.get(i).getAllFeatures()
                            .crossover(experiments.get(j).getAllFeatures());

                    String nameA = getTrainExperimentName(fold, generation, expCount++);
                    ParamsMap paramsMapA = pair.getLeft();
                    LOG.trace(experiments.get(i) +
                            "\n+\n" + experiments.get(j) + "\n->\n"+paramsMapA);
                    paramsMapA = paramsMapA.mutate(experimentFactory.getFeatureFactories(),
                            gaConfig.getMutationProbability(),
                            gaConfig.getMutationAmplitude());
                    LOG.trace("mutated: "+paramsMapA);
                    Experiment childA = new Experiment(nameA, paramsMapA);

                    experimentDB.addExperiment(childA);
                    nextGenExpNames.add(nameA);

                    if (expCount >= gaConfig.getPopulation()) {
                        return nextGenExpNames;
                    }

                    String nameB = getTrainExperimentName(fold, generation, expCount++);
                    ParamsMap paramsMapB = pair.getRight();
                    LOG.trace(experiments.get(i) +
                            "\n+\n" + experiments.get(j) + "\n->\n"+paramsMapB);
                    paramsMapB = paramsMapB.mutate(experimentFactory.getFeatureFactories(),
                            gaConfig.getMutationProbability(),
                            gaConfig.getMutationAmplitude());
                    Experiment childB = new Experiment(nameB, paramsMapB);

                    LOG.trace("mutated: "+paramsMapB);

                    nextGenExpNames.add(nameB);
                    experimentDB.addExperiment(
                            childB);
                    if (expCount >= gaConfig.getPopulation()) {
                        return nextGenExpNames;
                    }
                }
            }
        }
        return nextGenExpNames;
    }


    static int calcListLength(int population) {
        return (int)FastMath.ceil((1.0 + Math.sqrt(1.0+
                (8.0*(double)population)))/2.0);
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

    private void generateRandomSeeds(FeatureFactories featureFactories, GADB gadb) throws SQLException {
        List<Experiment> experiments = new ArrayList<>();
        for (int i = 0; i < gaConfig.getPopulation(); i++) {
            Map<String, Feature> instanceFeatures = new HashMap<>();
            for (String featureKey : featureFactories.keySet()) {
                FeatureFactory featureFactory = featureFactories.get(featureKey);
                instanceFeatures.put(featureKey, featureFactory.random());
            }
            String name = "seed_"+i;
            experiments.add(new Experiment(name, instanceFeatures));
        }
        for (int fold = 0; fold < gaConfig.getNFolds(); fold++) {
            for (int i = 0; i < experiments.size(); i++) {
                Experiment ex = experiments.get(i);
                ex.setName(getSeedName(fold, i));
                gadb.addExperiment(ex);
            }
        }
    }


    private static class GAPaths {
        Path judgmentsFile;
        Path dbPath;
        Path experimentFactories;
        Path seedExperiments;
        Path outputDir;
    }
}
