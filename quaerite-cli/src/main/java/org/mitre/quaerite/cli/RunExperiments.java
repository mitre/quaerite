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
import org.mitre.quaerite.db.ExperimentDB;

public class RunExperiments extends AbstractExperimentRunner {

    public static String DEFAULT_ID_FIELD = "id";

    static Logger LOG = Logger.getLogger(RunExperiments.class);

    static Options OPTIONS = new Options();

    static {
        OPTIONS.addOption(
                Option.builder("db")
                        .hasArg()
                        .required()
                        .desc("database folder (required)").build()
        );
        OPTIONS.addOption(
                Option.builder("e")
                        .longOpt("experiments")
                        .hasArg(true)
                        .required(true)
                        .desc("experiments .json file)").build()
        );

        OPTIONS.addOption(
                Option.builder("r")
                        .longOpt("reportsDir")
                        .hasArg()
                        .required(false)
                        .desc("directory for reports (optional; default 'reports'").build()
        );

        OPTIONS.addOption(
                Option.builder("j")
                        .longOpt("judgments")
                        .hasArg(true)
                        .required(false)
                        .desc("judgment .csv file (optional as long as judgements have been loaded earlier!)").build()
        );
        OPTIONS.addOption(
                Option.builder("id")
                        .hasArg()
                        .required(false)
                        .desc("field name for id field for judgments file (optional; default: 'id')").build()
        );

        OPTIONS.addOption(
                Option.builder("freshStart")
                        .required(false)
                        .hasArg(false)
                        .desc("delete all existing scores (optional; used only in iterative mode)").build()
        );

        OPTIONS.addOption(
                Option.builder("x")
                        .longOpt("experiment")
                        .required(false)
                        .hasArg()
                        .desc("run an already loaded experiment by name (optional; default=all)").build()
        );

        OPTIONS.addOption(
                Option.builder("l")
                        .longOpt("latest")
                        .hasArg(false)
                        .required(false)
                        .desc("run the most recently added experiment (optional; default=false)").build()
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
            System.err.println(e.getMessage());
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("java -jar org.mitre.eval.RunExperiments", OPTIONS);
            return;
        }

        Path dbDir = Paths.get(commandLine.getOptionValue("db"));
        String experimentName = (commandLine.hasOption("experiment")) ? commandLine.getOptionValue("experiment") : "";
        boolean freshStart = getBoolean(commandLine, "freshStart");
        boolean latest = getBoolean(commandLine, "latest");
        int numThreads = getInt(commandLine, "n", DEFAULT_NUM_THREADS);

        Path judgments = getPath(commandLine, "j", false);
        Path experiments = getPath(commandLine, "e", false);
        Path reportDir = getPath(commandLine, "r", false);
        reportDir = (reportDir == null) ? Paths.get(DumpResults.DEFAULT_REPORT_DIR) : reportDir;
        String idField = getString(commandLine, "id", DEFAULT_ID_FIELD);
        RunExperiments runExperiments = new RunExperiments(numThreads);

        try (ExperimentDB experimentDB = ExperimentDB.open(dbDir)) {
            if (judgments != null && experiments != null) {
                loadJudgments(experimentDB, judgments, idField, true);
                addExperiments(experimentDB, experiments, false, true);
                freshStart = false;
            } else if (judgments != null) {
                loadJudgments(experimentDB, judgments, idField, true);
            } else if (experiments != null) {
                addExperiments(experimentDB, experiments, true, freshStart);
            }
            runExperiments.run(experimentDB, experimentName, freshStart, latest);

            LOG.info("starting to write reports to: "+reportDir);
            dumpResults(experimentDB, experimentDB.getQuerySets(),
                    experimentDB.getExperiments().getScoreCollectors(), reportDir);
        }
        LOG.info("completed running and reporting experiments");
    }


    private void run(ExperimentDB experimentDB, String experimentName, boolean freshStart, boolean latest) throws SQLException, IOException {
        ExperimentSet experimentSet = experimentDB.getExperiments();
        if (freshStart) {
            experimentDB.clearScores();
        }

        if (latest) {
            experimentName = experimentDB.getLatestExperiment();
            experimentDB.clearScores(experimentName);
        }

        if (StringUtils.isBlank(experimentName)) {
            batchStart = System.currentTimeMillis();
            int finished = 0;
            for (String eName : experimentSet.getExperiments().keySet()) {
                runExperiment(eName, experimentDB);
                long elapsed = System.currentTimeMillis() - batchStart;
                finished++;
                LOG.info("Finished " + finished + " in " +
                        (double) elapsed / (double) 1000 + " seconds");
                double perExperiment = (double) elapsed / (double) finished;
                int togo = experimentSet.getExperiments().entrySet().size() - finished;
                if (togo > 0) {
                    LOG.info("Still have " + togo + " to go; estimate: " +
                            ((double) togo * perExperiment) / (double) 1000 + " seconds\n\n");
                }
            }
        } else {
            Experiment experiment = experimentDB.getExperiments().getExperiment(experimentName);
            if (experiment == null) {
                LOG.warn("I'm sorry, but I couldn't find this experiment:" + experimentName);
                return;
            }
            experimentDB.clearScores(experimentName);
            runExperiment(experimentName, experimentDB);
        }
    }
}