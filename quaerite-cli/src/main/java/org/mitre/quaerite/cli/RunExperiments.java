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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mitre.quaerite.core.Experiment;
import org.mitre.quaerite.core.ExperimentSet;
import org.mitre.quaerite.core.JudgmentList;
import org.mitre.quaerite.core.Judgments;
import org.mitre.quaerite.core.QueryInfo;
import org.mitre.quaerite.core.ResultSet;
import org.mitre.quaerite.connectors.QueryRequest;
import org.mitre.quaerite.connectors.SearchClient;
import org.mitre.quaerite.connectors.SearchClientException;
import org.mitre.quaerite.connectors.SearchClientFactory;
import org.mitre.quaerite.db.ExperimentDB;

public class RunExperiments extends AbstractExperimentRunner {


    static Logger LOG = Logger.getLogger(RunExperiments.class);

    static Options OPTIONS = new Options();

    static {
        OPTIONS.addOption(
                Option.builder("db")
                        .hasArg()
                        .required()
                        .desc("database folder").build()
        );

        OPTIONS.addOption(
                Option.builder("freshStart")
                        .required(false)
                        .hasArg(false)
                        .desc("delete all existing judgments").build()
        );

        OPTIONS.addOption(
                Option.builder("e")
                        .longOpt("experiment")
                        .required(false)
                        .hasArg()
                        .desc("which experiment to run (optional)").build()
        );

        OPTIONS.addOption(
                Option.builder("l")
                        .longOpt("latest")
                        .hasArg(false)
                        .required(false)
                        .desc("rerun just the most recently added experiment").build()
        );

        OPTIONS.addOption(
                Option.builder("n")
                        .longOpt("numThreads")
                        .hasArg(true)
                        .required(false)
                        .desc("number of threads to use in running experiments").build()
        );
    }

    long batchStart = -1l;
    public RunExperiments(int numThreads) {
        super(numThreads);
    }
    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("java -jar org.mitre.eval.RunExperiments", OPTIONS);
            return;
        }

        Path dbDir = Paths.get(commandLine.getOptionValue("db"));
        String experimentName = (commandLine.hasOption("experiment")) ? commandLine.getOptionValue("experiment") : "";
        boolean freshStart = (commandLine.hasOption("freshStart")) ? true : false;
        boolean latest = (commandLine.hasOption("latest")) ? true : false;
        int numThreads = (commandLine.hasOption("n")) ?
                Integer.parseInt(commandLine.getOptionValue("n")) : DEFAULT_NUM_THREADS;
        RunExperiments runExperiments = new RunExperiments(numThreads);
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





}
