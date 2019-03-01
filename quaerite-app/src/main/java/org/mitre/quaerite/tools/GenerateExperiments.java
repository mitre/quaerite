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

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.StringUtils;
import org.mitre.quaerite.Experiment;
import org.mitre.quaerite.ExperimentFeatures;
import org.mitre.quaerite.ExperimentSet;
import org.mitre.quaerite.scorecollectors.ScoreCollector;

public class GenerateExperiments {

    static Options OPTIONS = new Options();
    static {
        OPTIONS.addOption(
                Option.builder("i")
                .longOpt("input_features")
                .hasArg()
                .desc("experiment features json file")
                .required().build());
        OPTIONS.addOption(
                Option.builder("o")
                .longOpt("output_experiments")
                .hasArg()
                .desc("experiments file")
                .required().build()
        );
    }
    private int experimentCount = 0;
    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new GnuParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(
                    "java -jar org.mitre.quaerite.tools.GenerateExperiments",
                    OPTIONS);
            return;
        }

        Path input = Paths.get(commandLine.getOptionValue('i'));
        Path output = Paths.get(commandLine.getOptionValue("o"));
        GenerateExperiments generateExperiments = new GenerateExperiments();
        generateExperiments.execute(input, output);
    }

    private void execute(Path input, Path output) throws Exception {
        ExperimentFeatures experimentFeatures = null;

        try (Reader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
             experimentFeatures = ExperimentFeatures.fromJson(reader);
        }
        ExperimentSet experimentSet = new ExperimentSet();
        for (ScoreCollector scoreCollector : experimentFeatures.getScoreCollectors()) {
            experimentSet.addScoreCollector(scoreCollector);
        }
        for (String solrUrl : experimentFeatures.getSolrUrls()) {
            for (String customHandler : experimentFeatures.getCustomHandlers()) {
                addExperiments(solrUrl, customHandler, experimentFeatures, experimentSet);
            }
        }
        try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write(experimentSet.toJson());
            writer.flush();
        }
    }

    private void addExperiments(String solrUrl,
                                String customHandler,
                                ExperimentFeatures experimentFeatures,
                                ExperimentSet experimentSet) {
        //fix this mess
        for (String qfs : experimentFeatures.getQf().getFeatures()) {
            for (String pfs : experimentFeatures.getPF().getFeatures()) {
                for (String pf2s : experimentFeatures.getPF2().getFeatures()) {
                    for (String pf3s : experimentFeatures.getPF3().getFeatures()) {
                        if (!qfs.contains(",")) {
                            addExperiments(solrUrl, customHandler,
                                    qfs, pfs, pf2s, pf3s, "0.0", experimentFeatures, experimentSet);
                        } else {
                            for (String tie : experimentFeatures.getTie().getFeatures()) {
                                addExperiments(solrUrl, customHandler,
                                        qfs, pfs, pf2s, pf3s, tie, experimentFeatures, experimentSet);

                            }
                        }
                    }
                }
            }
        }
    }

    private void addExperiments(String solrUrl, String customHandler,
                                String qfs, String pfs, String pf2s,
                                String pf3s, String tie,
                                ExperimentFeatures experimentFeatures,
                                ExperimentSet experimentSet) {
        String experimentName = "experiment_"+experimentCount++;
        Experiment experiment = (StringUtils.isBlank(customHandler)) ?
                new Experiment(experimentName, solrUrl) :
                new Experiment(experimentName, solrUrl, customHandler);
        for (String qf : qfs.split(",")) {
            experiment.addParam("qf", qf);
        }

        if (! StringUtils.isBlank(pfs)) {
            for (String pf : pfs.split(",")) {
                experiment.addParam("pf", pf);
            }
        }

        if (! StringUtils.isBlank(pf2s)) {
            for (String pf2 : pf2s.split(",")) {
                experiment.addParam("pf2", pf2);
            }
        }

        if (! StringUtils.isBlank(pf3s)) {
            for (String pf3 : pf3s.split(",")) {
                experiment.addParam("pf", pf3);
            }
        }
        if (! StringUtils.isBlank(tie)) {
            experiment.addParam("tie", tie);
        }
        experimentSet.addExperiment(experimentName, experiment);

    }

}
