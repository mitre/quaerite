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

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.mitre.quaerite.Experiment;
import org.mitre.quaerite.ExperimentFeatures;
import org.mitre.quaerite.ExperimentSet;
import org.mitre.quaerite.features.Feature;

import org.mitre.quaerite.features.sets.FeatureSet;
import org.mitre.quaerite.features.sets.FeatureSets;
import org.mitre.quaerite.scorecollectors.ScoreCollector;

public class GenerateExperiments {

    enum MODE {
        PERMUTE,
        RANDOM
    }

    private static final int DEFAULT_MAX = 1000;

    static Options OPTIONS = new Options();

    static {
        OPTIONS.addOption(
                Option.builder("i")
                        .longOpt("input_features")
                        .hasArg()
                        .desc("experiment features json file")
                        .required().build()
        );
        OPTIONS.addOption(
                Option.builder("o")
                        .longOpt("output_experiments")
                        .hasArg()
                        .desc("experiments file")
                        .required().build()
        );
        OPTIONS.addOption(
                Option.builder("p")
                        .longOpt("permute")
                        .hasArg(false)
                        .desc("all permutations (default)")
                        .required(false).build()
        );
        OPTIONS.addOption(
                Option.builder("r")
                        .longOpt("random")
                        .hasArg(true)
                        .desc("generate x random experiments based on features")
                        .required(false).build()
        );
        OPTIONS.addOption(
                Option.builder("m")
                        .longOpt("max")
                        .hasArg(true)
                        .desc("maximum number of experiments to generate (default is 1000)")
                        .required(false).build()
        );
    }
    private int experimentCount = 0;
    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(
                    "java -jar org.mitre.quaerite.cli.GenerateExperiments",
                    OPTIONS);
            return;
        }
        int max = DEFAULT_MAX;
        if (commandLine.hasOption("m")) {
            max = Integer.parseInt(commandLine.getOptionValue("m"));
        } else if (commandLine.hasOption("r")) {
            max = Integer.parseInt(commandLine.getOptionValue("r"));
        }
        MODE mode = MODE.PERMUTE;
        if (commandLine.hasOption("r")) {
            mode = MODE.RANDOM;
        }
        Path input = Paths.get(commandLine.getOptionValue('i'));
        Path output = Paths.get(commandLine.getOptionValue("o"));
        GenerateExperiments generateExperiments = new GenerateExperiments();
        generateExperiments.execute(input, output, new GenerateConfig(mode, max));
    }

    private void execute(Path input, Path output, GenerateConfig generateConfig) throws Exception {
        ExperimentFeatures experimentFeatures = null;

        try (Reader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
             experimentFeatures = ExperimentFeatures.fromJson(reader);
        }
        ExperimentSet experimentSet = new ExperimentSet();
        for (ScoreCollector scoreCollector : experimentFeatures.getScoreCollectors()) {
            experimentSet.addScoreCollector(scoreCollector);
        }
        if (generateConfig.mode == MODE.PERMUTE) {
            FeatureSets featureSets = experimentFeatures.getFeatureSets();
            Set<String> featureKeySet = featureSets.keySet();
            List<String> featureKeys = new ArrayList<>(featureKeySet);
            Map<String, Feature> instanceFeatures = new HashMap<>();
            recurse(0, featureKeys, featureSets, instanceFeatures, experimentSet, generateConfig.max);
        } else {
            generateRandom(experimentFeatures.getFeatureSets(),
                    experimentSet, generateConfig.max);
        }

        try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write(experimentSet.toJson());
            writer.flush();
        }
    }

    private void generateRandom(FeatureSets featureSets, ExperimentSet experimentSet, int max) {
        for (int i = 0; i < max; i++) {
            Map<String, Feature> instanceFeatures = new HashMap<>();
            for (String featureKey : featureSets.keySet()) {
                FeatureSet featureSet = featureSets.get(featureKey);
                instanceFeatures.put(featureKey, featureSet.random());
            }
            addExperiments(instanceFeatures, experimentSet);
        }
    }

    private void recurse(int i, List<String> featureKeys,
                         FeatureSets featureSets,
                         Map<String, Feature> instanceFeatures,
                         ExperimentSet experimentSet, int max) {
        if (i >= featureKeys.size()) {
            addExperiments(instanceFeatures, experimentSet);
            return;
        }
        if (experimentSet.getExperiments().size() >= max) {
            return;
        }
        String featureName = featureKeys.get(i);
        FeatureSet featureSet = featureSets.get(featureName);
        boolean hadContents = false;
        List<Feature> permutations = featureSet.permute(1000);
        for (Feature feature : permutations) {
            instanceFeatures.put(featureName, feature);
            recurse(i+1, featureKeys, featureSets, instanceFeatures, experimentSet, max);
            hadContents = true;
        }
        if (! hadContents) {
            recurse(i+1, featureKeys, featureSets, instanceFeatures, experimentSet, max);
        }
    }

    private void addExperiments(Map<String, Feature> features,
                                ExperimentSet experimentSet) {
        String experimentName = "experiment_"+experimentCount++;
        String searchServerUrl = features.get("urls").toString();
        String customHandler = features.get("customHandlers").toString();

        Experiment experiment = (customHandler == null) ?
                new Experiment(experimentName, searchServerUrl) :
                new Experiment(experimentName, searchServerUrl, customHandler);
        for (Map.Entry<String, Feature> e : features.entrySet()) {
            if (!e.getKey().equals("urls") && !e.getKey().equals("customHandlers")) {
                experiment.addParam(e.getKey(), e.getValue());
            }
        }
        experimentSet.addExperiment(experimentName, experiment);
    }

    private String getOnlyString(Set<Feature> features) {
        if (features == null) {
            return null;
        }
        if (features.size() != 1) {
            throw new IllegalArgumentException("features must have only one value: "+ features.size());
        }
        for (Feature f : features) {
            return f.toString();
        }
        return "";
    }

    private static class GenerateConfig {
        final MODE mode;
        final int max;

        public GenerateConfig(MODE mode, int max) {
            this.mode = mode;
            this.max = max;
        }
    }


}
