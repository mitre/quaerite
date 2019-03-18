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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.mitre.quaerite.core.ExperimentFeatures;
import org.mitre.quaerite.core.ExperimentSet;
import org.mitre.quaerite.core.JudgmentList;
import org.mitre.quaerite.core.Judgments;
import org.mitre.quaerite.db.ExperimentDB;
import org.mitre.quaerite.db.ExperimentScorePair;
import org.mitre.quaerite.core.features.Feature;
import org.mitre.quaerite.core.features.ParamsMap;
import org.mitre.quaerite.core.featuresets.FeatureSet;
import org.mitre.quaerite.core.featuresets.FeatureSets;
import org.mitre.quaerite.core.scorecollectors.DistributionalScoreCollector;
import org.mitre.quaerite.core.scorecollectors.ScoreCollector;
import org.mitre.quaerite.core.scorecollectors.SummingScoreCollector;
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
                        .longOpt("features")
                        .hasArg()
                        .desc("experiment featuresets json file")
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
                Option.builder("sc")
                        .longOpt("scorer")
                        .required(false)
                        .hasArg()
                        .desc("scorer to select if there are multiple scorers").build()
        );

        OPTIONS.addOption(
                Option.builder("p")
                        .longOpt("population")
                        .hasArg()
                        .required(false)
                        .desc("population size at each generation (default = 20)").build()
        );

        OPTIONS.addOption(
                Option.builder("mp")
                        .longOpt("mutation_probability")
                        .hasArg()
                        .required(false)
                        .desc("probability of mutation; must be >=0.0 and <= 1.0 (default = 0.3)").build()
        );

        OPTIONS.addOption(
                Option.builder("ma")
                        .longOpt("mutation_amplitude")
                        .hasArg()
                        .required(false)
                        .desc("amplitude of mutation; must be >=0.0 and <= 1.0 (default = 0.8)").build()
        );

        OPTIONS.addOption(
                Option.builder("g")
                        .longOpt("generations")
                        .hasArg(true)
                        .required(false)
                        .desc("how many generations to run (default=10)").build()
        );
        OPTIONS.addOption(
                Option.builder("n")
                        .longOpt("numThreads")
                        .hasArg(true)
                        .required(false)
                        .desc("number of threads to use in running experiments").build()
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

    public RunGA(int numThreads) {
        super(numThreads);
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
        GAConfig gaConfig = new GAConfig();
        gaConfig.generations = getInt(commandLine, "g", 20);
        gaConfig.mutationProbability = getFloat(commandLine, "mp", 0.3f);
        gaConfig.mutationAmplitude = getFloat(commandLine, "ma", 0.8f);
        gaConfig.dbPath = getPath(commandLine, "db", false);
        gaConfig.features = getPath(commandLine, "f", true);
        gaConfig.seedExperiments = getPath(commandLine, "e", false);
        gaConfig.outputDir = getPath(commandLine, "o", false);
        gaConfig.population = getInt(commandLine,"p", 20);
        gaConfig.scorerName = getString(commandLine, "sc", null);
        gaConfig.judgmentsFile = getPath(commandLine, "j", true);
        gaConfig.nfolds = getInt(commandLine, "nfolds", DEFAULT_NFOLDS);
        if (gaConfig.outputDir == null) {
            gaConfig.outputDir = Paths.get("ga_experiments");
        }
        int threads = getInt(commandLine, "n", 8);
        RunGA runGA = new RunGA(threads);
        runGA.execute(gaConfig);
    }


    private void execute(GAConfig gaConfig) throws IOException, SQLException {

        if (! Files.isDirectory(gaConfig.outputDir)) {
            Files.createDirectories(gaConfig.outputDir);
        }

        GADB gaDb = GADB.openAndDrop(gaConfig.dbPath);

        loadJudgments(gaDb, gaConfig.judgmentsFile, "id", true);

        ExperimentFeatures experimentFeatures = null;

        try (Reader reader = Files.newBufferedReader(gaConfig.features, StandardCharsets.UTF_8)) {
            experimentFeatures = ExperimentFeatures.fromJson(reader);
        }

        if (gaConfig.seedExperiments != null) {
            loadSeed(gaDb, gaConfig.seedExperiments, gaConfig.nfolds);
        }

        gaDb = GADB.open(gaConfig.dbPath);

        if (gaConfig.seedExperiments == null) {
            //write out the seed generation
            generateRandomSeeds(experimentFeatures.getFeatureSets(), gaDb, gaConfig.nfolds, gaConfig.population);
        }
        gaDb.addScoreCollectors(experimentFeatures.getScoreCollectors());
        if (gaConfig.scorerName == null) {
            List<ScoreCollector> scoreCollectors = experimentFeatures.getScoreCollectors();
            if (scoreCollectors.size() > 1) {
                throw new IllegalArgumentException("Must specify target scorer on the commandline if there are more than one scorers available;" +
                        "e.g. -sc ndcg_10");
            }
            ScoreCollector scoreCollector = experimentFeatures.getScoreCollectors().get(0);
            if (scoreCollector instanceof DistributionalScoreCollector) {
                gaConfig.scorerName = scoreCollector.getName()+"_mean";
            } else if (scoreCollector instanceof SummingScoreCollector) {
                gaConfig.scorerName = scoreCollector.getName()+"_sum";
            } else {
                throw new IllegalArgumentException("Not yet supported: "+scoreCollector.getClass());
            }

        }
        gaDb.initTrainTest(gaConfig.nfolds);

        for (int i = 0; i < gaConfig.nfolds; i++) {
            runFold(i, gaDb, experimentFeatures, gaConfig);
        }
        System.out.println("--------------------------------");
        System.out.println("FINAL RESULTS ON TESTING:");
        List<ExperimentScorePair> scores = gaDb.getNBestResults(
                TEST_PREFIX, gaConfig.nfolds, gaConfig.scorerName);

        SummaryStatistics summaryStatistics = new SummaryStatistics();
        double[] vals = new double[gaConfig.nfolds];
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


    private void runFold(int fold, GADB gaDb, ExperimentFeatures experimentFeatures, GAConfig gaConfig) throws IOException, SQLException {
        TrainTestJudmentListPair trainTestJudmentListPair = gaDb.getTrainTestJudgmentsByFold(fold);
        JudgmentList trainJudgmentList = trainTestJudmentListPair.getTrain();
        LOG.info("scoring training seed for fold: "+fold);
        scoreSeed(fold, gaDb, trainJudgmentList, gaConfig);
        LOG.info("starting fold "+fold);
        LOG.debug("train size: "+ trainJudgmentList.getJudgmentsList().size());
        for (Judgments j : trainJudgmentList.getJudgmentsList()) {
            LOG.debug("fold " + fold + ", training query: "+j.getQuery());
        }
        LOG.debug("test size: "+ trainTestJudmentListPair.getTest().getJudgmentsList().size());
        for (Judgments j : trainTestJudmentListPair.getTest().getJudgmentsList()) {
            LOG.debug("fold " + fold + ", testing query: "+j.getQuery());
        }

        for (int i = 0 ; i < gaConfig.generations; i++) {
            runGeneration(fold, i, gaDb, experimentFeatures.getFeatureSets(), trainJudgmentList, gaConfig);
        }
        List<ExperimentScorePair> scores = gaDb.getNBestResults(
                TRAIN_PREFIX+FOLD_PREFIX+fold+"_*", 10, gaConfig.scorerName);
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
                TEST_PREFIX+FOLD_PREFIX+fold+"_*", 10, gaConfig.scorerName);
        System.out.println("FOLD "+fold+" TESTING");
        for (ExperimentScorePair esp : scores) {
            System.out.println("experiment '"+esp.getExperimentName()+"': "+threePlaces.format(esp.getScore()));
        }
        System.out.println("");

    }

    private void scoreSeed(int fold, GADB gaDb, JudgmentList trainJudgmentList, GAConfig gaConfig) throws SQLException, IOException {
        ExperimentSet experimentSet = gaDb.getExperiments();

        String trainFoldSeedPrefix = TRAIN_PREFIX+FOLD_PREFIX+fold+"_"+SEED_PREFIX;
        for (String experimentName : experimentSet.getExperiments().keySet()) {
            if (experimentName.startsWith(trainFoldSeedPrefix)) {
                runExperiment(experimentName, gaDb, trainJudgmentList,
                        "seed_test_fold_" + fold, false);
            }
        }

        System.out.println("FOLD "+fold + " TRAINING (SEED)");
        List<ExperimentScorePair> scores = gaDb.getNBestResults(
                trainFoldSeedPrefix, 10, gaConfig.scorerName);

        for (ExperimentScorePair esp : scores) {
            System.out.println("experiment '"+esp.getExperimentName()+"': "
                    +threePlaces.format(esp.getScore()));
        }
        System.out.println("");
        System.out.println("");

        String json = experimentSet.toJson();

        Files.write(gaConfig.outputDir.resolve("seed_experiments.json"),
                json.getBytes(StandardCharsets.UTF_8));

    }

    private void runGeneration(int fold, int generation, ExperimentDB experimentDB,
                               FeatureSets featureSets, JudgmentList judgmentList, GAConfig gaConfig) throws SQLException, IOException {
        List<String> experimentNames = generateNewExperiments(fold, generation, experimentDB, featureSets, gaConfig);
        LOG.info("starting generation "+generation);
        for (String experimentName : experimentNames) {
            runExperiment(experimentName, experimentDB, judgmentList, "foldId_"+fold,
                    false);
        }
        ExperimentSet experimentSet = experimentDB.getExperiments();
        String json = experimentSet.toJson(experimentNames);

        Files.write(gaConfig.outputDir.resolve("fold_"+fold+"_gen_"+generation+"_experiments.json"),
                json.getBytes(StandardCharsets.UTF_8));
    }

    private List<String> generateNewExperiments(int fold, int generation,
                                                ExperimentDB experimentDB,
                                                FeatureSets featureSets,
                                                GAConfig gaConfig) throws SQLException{

        int listLength = calcListLength(gaConfig.population);
        //this prioritizes the more fit in affecting crossover
        listLength = (int)((double)listLength/1.25f);
        listLength = (listLength < 2) ? 2 : listLength;
        //create new experiments
        //this call includes all previous generations -- intergenerational swapping
        //        String previousGenerationsNamePattern = (generation == 0) ? "*" : GEN_PREFIX+(generation-1)+"_*";
        //To limit to the previous generation add the above. to the call to getNBest
        //TODO: parameterize this?
        List<Experiment> experiments = experimentDB.getNBestExperiments(TRAIN_PREFIX+FOLD_PREFIX+fold+"_", listLength, gaConfig.scorerName);
        if (experiments.size() == 0) {
            throw new IllegalArgumentException("Need to have some experiments from seed/last generation!");
        }        int expCount = 0;
        List<String> nextGenExpNames = new ArrayList<>();
        while (nextGenExpNames.size() < gaConfig.population) {
            for (int i = 0; i < experiments.size() - 1; i++) {
                for (int j = i + 1; j < experiments.size(); j++) {
                    Pair<ParamsMap, ParamsMap> pair = experiments.get(i).getAllFeatures()
                            .crossover(experiments.get(j).getAllFeatures());

                    String nameA = getTrainExperimentName(fold, generation, expCount++);
                    ParamsMap paramsMapA = pair.getLeft();
                    LOG.trace(experiments.get(i) +
                            "\n+\n" + experiments.get(j) + "\n->\n"+paramsMapA);
                    paramsMapA = paramsMapA.mutate(featureSets, gaConfig.mutationProbability, gaConfig.mutationAmplitude);
                    LOG.trace("mutated: "+paramsMapA);
                    Experiment childA = new Experiment(nameA, paramsMapA);

                    experimentDB.addExperiment(childA);
                    nextGenExpNames.add(nameA);

                    if (expCount >= gaConfig.population) {
                        return nextGenExpNames;
                    }

                    String nameB = getTrainExperimentName(fold, generation, expCount++);
                    ParamsMap paramsMapB = pair.getRight();
                    LOG.trace(experiments.get(i) +
                            "\n+\n" + experiments.get(j) + "\n->\n"+paramsMapB);
                    paramsMapB = paramsMapB.mutate(featureSets, gaConfig.mutationProbability, gaConfig.mutationAmplitude);
                    Experiment childB = new Experiment(nameB, paramsMapB);

                    LOG.trace("mutated: "+paramsMapB);

                    nextGenExpNames.add(nameB);
                    experimentDB.addExperiment(
                            childB);
                    if (expCount >= gaConfig.population) {
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

    private void generateRandomSeeds(FeatureSets featureSets, GADB gadb, int folds, int population) throws SQLException {
        List<Experiment> experiments = new ArrayList<>();
        for (int i = 0; i < population; i++) {
            Map<String, Feature> instanceFeatures = new HashMap<>();
            for (String featureKey : featureSets.keySet()) {
                FeatureSet featureSet = featureSets.get(featureKey);
                instanceFeatures.put(featureKey, featureSet.random());
            }
            String name = "seed_"+i;
            experiments.add(new Experiment(name, instanceFeatures));
        }
        for (int fold = 0; fold < folds; fold++) {
            for (int i = 0; i < experiments.size(); i++) {
                Experiment ex = experiments.get(i);
                ex.setName(getSeedName(fold, i));
                gadb.addExperiment(ex);
            }
        }
    }


    private static class GAConfig {
        Path judgmentsFile;
        Path dbPath;
        Path features;
        Path seedExperiments;
        Path outputDir;
        int population;
        int generations;
        int nfolds;
        float mutationProbability;
        float mutationAmplitude;
        String scorerName;
    }
}
