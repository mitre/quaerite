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
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.mitre.quaerite.Experiment;
import org.mitre.quaerite.ExperimentSet;
import org.mitre.quaerite.db.ExperimentDB;
import org.mitre.quaerite.scorecollectors.ScoreCollector;

public abstract class AbstractCLI {

    static int getInt(CommandLine commandLine, String opt, int dfault) {
        if (commandLine.hasOption(opt)) {
            return Integer.parseInt(commandLine.getOptionValue(opt));
        }
        return dfault;
    }

    static float getFloat(CommandLine commandLine, String opt, float def) {
        if (commandLine.hasOption(opt)) {
            return Float.parseFloat(commandLine.getOptionValue(opt));
        }
        return def;
    }

    static Path getPath(CommandLine commandLine, String opt, boolean mustExist) {
        if (! commandLine.hasOption(opt)) {
            throw new IllegalArgumentException("commandline must have option: "+opt);
        }
        Path p = Paths.get(commandLine.getOptionValue(opt));
        if (mustExist && !Files.exists(p)) {
            throw new IllegalArgumentException("File "+p+" must exist");
        }
        return p;
    }

    static boolean getBoolean(CommandLine commandLine, String opt) {
        if (commandLine.hasOption(opt)) {
            return true;
        }
        return false;
    }

    static String getString(CommandLine commandLine, String opt, String dfault) {
        if (commandLine.hasOption(opt)) {
            return commandLine.getOptionValue("opt");
        }
        return dfault;
    }


    static void addExperiments(Path file, Path dbDir, boolean merge, boolean freshStart) throws SQLException, IOException {
        try (ExperimentDB experimentDB = ExperimentDB.open(dbDir)) {
            if (freshStart) {
                experimentDB.clearExperiments();
                experimentDB.clearScorers();
            }
            ExperimentSet experiments = null;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                experiments = ExperimentSet.fromJson(reader);
            }
            for (Experiment experiment : experiments.getExperiments().values()) {
                experimentDB.addExperiment(experiment, merge);
            }
            List<ScoreCollector> scoreCollectors = experiments.getScoreCollectors();
            if (scoreCollectors != null) {
                for (ScoreCollector scoreCollector : scoreCollectors) {
                    experimentDB.addScoreCollector(scoreCollector);
                }
            }
        }
    }


}
