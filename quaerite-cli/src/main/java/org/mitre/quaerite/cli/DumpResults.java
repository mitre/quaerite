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
 *
 */
package org.mitre.quaerite.cli;


import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.mitre.quaerite.core.ExperimentConfig;
import org.mitre.quaerite.core.scoreaggregators.ScoreAggregator;
import org.mitre.quaerite.db.ExperimentDB;

public class DumpResults extends AbstractExperimentRunner {

    public static final String DEFAULT_REPORT_DIR = "reports";
    static Options OPTIONS = new Options();


    static {
        OPTIONS.addOption(
                Option.builder("db")
                        .hasArg().required().desc("database folder").build()
        );

        OPTIONS.addOption(
                Option.builder("r")
                        .longOpt("reportsDir")
                        .hasArg()
                        .required(false)
                        .desc("directory for results files (optional; default: 'reports')").build()
        );
        OPTIONS.addOption(
                Option.builder("q")
                        .longOpt("querySets")
                        .hasArg()
                        .required(false)
                        .desc("querySets to dump in rollups (comma-delimited) (optional; default: all)").build()
        );
        OPTIONS.addOption(
                Option.builder("s")
                        .longOpt("scorers")
                        .hasArg()
                        .required(false)
                        .desc("scorers to dump in statistical " +
                                "significance matrices (comma-delimited)").build()
        );
    }

    public DumpResults() {
        super(new ExperimentConfig());
    }
    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(
                    "java -jar org.mitre.quaerite.cli.DumpResults",
                    OPTIONS);
            return;
        }
        Path outputDir = Paths.get(commandLine.getOptionValue("d"));
        Path dbDir = Paths.get(commandLine.getOptionValue("db"));
        Set<String> scorers = new TreeSet<>();
        if (commandLine.hasOption("s")) {
            scorers.addAll(
                    Arrays.asList(commandLine.getOptionValue("s").split(",")));
        }
        List<String> querySets = new ArrayList<>();
        if (commandLine.hasOption("q")) {
            querySets.addAll(
                    Arrays.asList(commandLine.getOptionValue("q").split(","))
            );
        } else {
            querySets.add("");
        }
        Files.createDirectories(outputDir);
        List<ScoreAggregator> targetScoreAggregators = new ArrayList<>();
        try (ExperimentDB experimentDB = ExperimentDB.open(dbDir)) {
            if (scorers.size() == 0) {
                targetScoreAggregators.addAll(experimentDB.getExperiments().getScoreAggregators());
            } else  {
                for (ScoreAggregator scoreAggregator : experimentDB.getExperiments().getScoreAggregators()) {
                    if (scorers.contains(scoreAggregator.getName())) {
                        targetScoreAggregators.add(scoreAggregator);
                    }
                }
            }
            dumpResults(experimentDB, querySets, targetScoreAggregators, outputDir, false);
        }
    }
}
