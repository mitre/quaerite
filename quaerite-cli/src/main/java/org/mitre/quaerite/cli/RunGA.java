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
import org.apache.commons.math3.util.FastMath;
import org.apache.log4j.Logger;
import org.mitre.quaerite.core.Experiment;
import org.mitre.quaerite.core.ExperimentFeatures;
import org.mitre.quaerite.core.ExperimentSet;
import org.mitre.quaerite.db.ExperimentDB;
import org.mitre.quaerite.db.ExperimentScorePair;
import org.mitre.quaerite.core.features.Feature;
import org.mitre.quaerite.core.features.ParamsMap;
import org.mitre.quaerite.core.featuresets.FeatureSet;
import org.mitre.quaerite.core.featuresets.FeatureSets;
import org.mitre.quaerite.core.scorecollectors.DistributionalScoreCollector;
import org.mitre.quaerite.core.scorecollectors.ScoreCollector;
import org.mitre.quaerite.core.scorecollectors.SummingScoreCollector;

public class RunGA extends AbstractExperimentRunner {

    static Logger LOG = Logger.getLogger(RunGA.class);

    static Options OPTIONS = new Options();

    private static String GEN_PREFIX = "gen_";

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

        ExperimentDB experimentDB = ExperimentDB.openAndDrop(gaConfig.dbPath);

        loadJudgments(experimentDB, gaConfig.judgmentsFile, "id", true);

        ExperimentFeatures experimentFeatures = null;

        try (Reader reader = Files.newBufferedReader(gaConfig.features, StandardCharsets.UTF_8)) {
            experimentFeatures = ExperimentFeatures.fromJson(reader);
        }

        if (gaConfig.seedExperiments != null) {
            addExperiments(experimentDB, gaConfig.seedExperiments, false, true);
        }
        experimentDB = ExperimentDB.open(gaConfig.dbPath);

        if (gaConfig.seedExperiments == null) {
            experimentDB.addScoreCollectors(experimentFeatures.getScoreCollectors());
            generateRandom(experimentFeatures.getFeatureSets(), experimentDB, gaConfig.population);
            //write out the seed generation
        }
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
        scoreSeed(experimentDB, gaConfig);

        for (int i = 0 ; i < gaConfig.generations; i++) {
            runGeneration(i, experimentDB, experimentFeatures.getFeatureSets(), gaConfig);
        }
    }

    private void scoreSeed(ExperimentDB experimentDB, GAConfig gaConfig) throws SQLException, IOException {
        ExperimentSet experimentSet = experimentDB.getExperiments();
        for (String experimentName : experimentSet.getExperiments().keySet()) {
            runExperiment(experimentName, experimentDB, false);
        }
        List<ExperimentScorePair> scores = experimentDB.getNBestResults(
                "*", 10, gaConfig.scorerName);

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

    private void runGeneration(int generation, ExperimentDB experimentDB,
                               FeatureSets featureSets, GAConfig gaConfig) throws SQLException, IOException {
        List<String> experimentNames = generateNewExperiments(generation, experimentDB, featureSets, gaConfig);
        LOG.info("starting generation "+generation);
        for (String experimentName : experimentNames) {
            runExperiment(experimentName, experimentDB, false);
        }
        List<ExperimentScorePair> scores = experimentDB.getNBestResults(
                GEN_PREFIX+generation+"_*", 10, gaConfig.scorerName);

        for (ExperimentScorePair esp : scores) {
            System.out.println("experiment '"+esp.getExperimentName()+"': "+threePlaces.format(esp.getScore()));

        }
        System.out.println("");
        ExperimentSet experimentSet = experimentDB.getExperiments();
        String json = experimentSet.toJson(experimentNames);

        Files.write(gaConfig.outputDir.resolve("gen_"+generation+"_experiments.json"),
                json.getBytes(StandardCharsets.UTF_8));
    }

    private List<String> generateNewExperiments(int generation,
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
        List<Experiment> experiments = experimentDB.getNBestExperiments(listLength, gaConfig.scorerName);
        int expCount = 0;
        List<String> nextGenExpNames = new ArrayList<>();
        while (nextGenExpNames.size() < gaConfig.population) {
            for (int i = 0; i < experiments.size() - 1; i++) {
                for (int j = i + 1; j < experiments.size(); j++) {
                    Pair<ParamsMap, ParamsMap> pair = experiments.get(i).getAllFeatures()
                            .crossover(experiments.get(j).getAllFeatures());

                    String nameA = getExperimentName(generation, expCount++);
                    ParamsMap paramsMapA = pair.getLeft();
                    LOG.debug(experiments.get(i) +
                            "\n+\n" + experiments.get(j) + "\n->\n"+paramsMapA);
                    paramsMapA = paramsMapA.mutate(featureSets, gaConfig.mutationProbability, gaConfig.mutationAmplitude);
                    LOG.debug("mutated: "+paramsMapA);
                    Experiment childA = new Experiment(nameA, paramsMapA);

                    experimentDB.addExperiment(childA);
                    nextGenExpNames.add(nameA);

                    if (expCount >= gaConfig.population) {
                        return nextGenExpNames;
                    }

                    String nameB = getExperimentName(generation, expCount++);
                    ParamsMap paramsMapB = pair.getRight();
                    LOG.debug(experiments.get(i) +
                            "\n+\n" + experiments.get(j) + "\n->\n"+paramsMapB);
                    paramsMapB = paramsMapB.mutate(featureSets, gaConfig.mutationProbability, gaConfig.mutationAmplitude);
                    Experiment childB = new Experiment(nameB, paramsMapB);

                    LOG.debug("mutated: "+paramsMapB);

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

    private String getExperimentName(int generation, int i) {
        return GEN_PREFIX+generation+"_"+i;
    }

    private void generateRandom(FeatureSets featureSets, ExperimentDB experimentDB, int max) throws SQLException {
        for (int i = 0; i < max; i++) {
            Map<String, Feature> instanceFeatures = new HashMap<>();
            for (String featureKey : featureSets.keySet()) {
                FeatureSet featureSet = featureSets.get(featureKey);
                instanceFeatures.put(featureKey, featureSet.random());
            }
            String name = "seed_"+i;
            experimentDB.addExperiment(
                    new Experiment(name, instanceFeatures), false);
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
        float mutationProbability;
        float mutationAmplitude;
        String scorerName;
    }
}
