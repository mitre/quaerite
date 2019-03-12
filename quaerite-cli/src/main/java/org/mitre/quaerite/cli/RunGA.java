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
import org.mitre.quaerite.Experiment;
import org.mitre.quaerite.ExperimentFeatures;
import org.mitre.quaerite.ExperimentSet;
import org.mitre.quaerite.db.ExperimentDB;
import org.mitre.quaerite.db.ExperimentScorePair;
import org.mitre.quaerite.features.Feature;
import org.mitre.quaerite.features.ParamsMap;
import org.mitre.quaerite.features.sets.FeatureSet;
import org.mitre.quaerite.features.sets.FeatureSets;

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
                Option.builder("i")
                        .longOpt("input_features")
                        .hasArg()
                        .desc("experiment features json file")
                        .required().build()
        );
        OPTIONS.addOption(
                Option.builder("s")
                        .longOpt("seed")
                        .required(false)
                        .hasArg()
                        .desc("specify initial seed experiments (json file) as the first generation; " +
                                "if not specified, first generation is randomly generated from the features file").build()
        );
        OPTIONS.addOption(
                Option.builder("sc")
                        .longOpt("scorer")
                        .required(false)
                        .hasArg()
                        .desc("scorer to use if there are multiple scorers").build()
        );

        OPTIONS.addOption(
                Option.builder("p")
                        .longOpt("population")
                        .hasArg()
                        .required(false)
                        .desc("population size at each generation (default = 100)").build()
        );

        OPTIONS.addOption(
                Option.builder("mp")
                        .longOpt("mutation_probability")
                        .hasArg()
                        .required(false)
                        .desc("probability of mutation; must be >=0.0 and <= 1.0 (default = 0.1)").build()
        );

        OPTIONS.addOption(
                Option.builder("ma")
                        .longOpt("mutation_amplitude")
                        .hasArg()
                        .required(false)
                        .desc("amplitude of mutation; must be >=0.0 and <= 1.0 (default = 0.1)").build()
        );

        OPTIONS.addOption(
                Option.builder("g")
                        .longOpt("generations")
                        .hasArg(true)
                        .required(false)
                        .desc("how many generations to run").build()
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
                        .required(true)
                        .desc("output directory for experiment files and results").build()
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
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("java -jar org.mitre.quaerite.cli.RunGA", OPTIONS);
            return;
        }
        GAConfig gaConfig = new GAConfig();
        gaConfig.generations = getInt(commandLine, "g", 10);
        gaConfig.mutationProbability = getFloat(commandLine, "mp", 0.1f);
        gaConfig.mutationAmplitude = getFloat(commandLine, "ma", 0.1f);
        gaConfig.dbPath = getPath(commandLine, "db", false);
        gaConfig.features = getPath(commandLine, "i", true);
        gaConfig.seedExperiments = getPath(commandLine, "s", false);
        gaConfig.outputDir = getPath(commandLine, "o", false);
        gaConfig.population = getInt(commandLine,"p", 100);
        gaConfig.scorerName = getString(commandLine, "sc", null);
        gaConfig.judgmentsFile = getPath(commandLine, "j", true);
        int threads = getInt(commandLine, "n", 8);
        RunGA runGA = new RunGA(threads);
        runGA.execute(gaConfig);
    }


    private void execute(GAConfig gaConfig) throws IOException, SQLException {
        loadJudgments(gaConfig.judgmentsFile, "id", gaConfig.dbPath, true);

        ExperimentFeatures experimentFeatures = null;

        try (Reader reader = Files.newBufferedReader(gaConfig.features, StandardCharsets.UTF_8)) {
            experimentFeatures = ExperimentFeatures.fromJson(reader);
        }

        if (gaConfig.seedExperiments != null) {
            addExperiments(gaConfig.seedExperiments, gaConfig.dbPath, false, true);
        }
        ExperimentDB experimentDB = ExperimentDB.open(gaConfig.dbPath);
        if (gaConfig.seedExperiments == null) {
            experimentDB.addScoreCollectors(experimentFeatures.getScoreCollectors());
            generateRandom(experimentFeatures.getFeatureSets(), experimentDB, gaConfig.population);
            //write out the seed generation
        }
        scoreSeed(experimentDB);

        for (int i = 0 ; i < gaConfig.generations; i++) {
            runGeneration(i, experimentDB, experimentFeatures.getFeatureSets(), gaConfig);
        }
    }

    private void scoreSeed(ExperimentDB experimentDB) throws SQLException {
        ExperimentSet experimentSet = experimentDB.getExperiments();
        for (String experimentName : experimentSet.getExperiments().keySet()) {
            runExperiment(experimentName, experimentDB);
        }
    }

    private void runGeneration(int generation, ExperimentDB experimentDB,
                               FeatureSets featureSets, GAConfig gaConfig) throws SQLException {
        List<String> experimentNames = generateNewExperiments(generation, experimentDB, featureSets, gaConfig);
        System.out.println("starting generation "+generation);
        for (String experimentName : experimentNames) {
            System.out.println("running: "+experimentName);
            runExperiment(experimentName, experimentDB);
        }
        List<ExperimentScorePair> scores = experimentDB.getNBestResults(
                GEN_PREFIX+generation+"_*", 3, gaConfig.scorerName);

        for (ExperimentScorePair esp : scores) {
            System.out.println(esp);
        }
        System.out.println("");
    }

    private List<String> generateNewExperiments(int generation,
                                                ExperimentDB experimentDB,
                                                FeatureSets featureSets,
                                                GAConfig gaConfig) throws SQLException{
        String genPat = (generation == 0) ? "*" : GEN_PREFIX+(generation-1)+"_*";

        int listLength = calcListLength(gaConfig.population);
        //create new experiments
        List<Experiment> experiments = experimentDB.getNBestExperiments(genPat, listLength, gaConfig.scorerName);
        int expCount = 0;
        List<String> nextGenExpNames = new ArrayList<>();
        for (int i = 0; i < experiments.size()-1; i++) {
            for (int j = i+1; j < experiments.size(); j++) {
                Pair<ParamsMap, ParamsMap> pair =  experiments.get(i).getAllFeatures()
                        .crossover(experiments.get(j).getAllFeatures());

                String nameA = getExperimentName(generation, expCount++);
                Experiment childA = new Experiment(nameA,
                        pair.getLeft().mutate(featureSets, gaConfig.mutationProbability, gaConfig.mutationAmplitude));

                experimentDB.addExperiment(
                        childA);
                nextGenExpNames.add(nameA);

                String nameB = getExperimentName(generation, expCount++);
                Experiment childB = new Experiment(nameA,
                        pair.getRight().mutate(featureSets, gaConfig.mutationProbability, gaConfig.mutationAmplitude));

                nextGenExpNames.add(nameB);
                experimentDB.addExperiment(
                        childB);
                if (expCount > gaConfig.population) {
                    return nextGenExpNames;
                }
            }
        }
        return nextGenExpNames;
    }

    static int calcListLength(int population) {
        //TODO: derive the correct formula...
        //this was determined via experimentation
        //and returns a list _slightly_ longer than necessary
        return (int)FastMath.ceil(Math.sqrt(population) *
                (1.5-(0.01*Math.log10(population))));

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
