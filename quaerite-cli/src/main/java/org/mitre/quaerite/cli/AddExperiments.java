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
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.mitre.quaerite.Experiment;
import org.mitre.quaerite.ExperimentSet;
import org.mitre.quaerite.db.ExperimentDB;
import org.mitre.quaerite.scorecollectors.ScoreCollector;

public class AddExperiments extends AbstractCLI {

    static Options OPTIONS = new Options();

    static {
        OPTIONS.addOption(
                Option.builder("db")
                        .longOpt("database")
                        .hasArg()
                        .required()
                        .desc("database folder").build()
        );
        OPTIONS.addOption(
                Option.builder("f")
                        .longOpt("file")
                        .hasArg()
                        .required()
                        .desc("json file with experiment(s)").build()
        );
        OPTIONS.addOption(Option.builder("freshStart")
                .hasArg(false)
                .required(false)
                .desc("freshStart").build()
        );
        OPTIONS.addOption(Option.builder("m")
                .longOpt("merge")
                .hasArg(false)
                .required(false)
                .desc("silently overwrite existing experiments " +
                        "by name if they already exist").build()
        );
    }

    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(
                    "java -jar org.mitre.quaerite.cli.AddExperiments",
                    OPTIONS);
            return;
        }
        Path file = getPath(commandLine, "f", true);
        Path dbDir = getPath(commandLine, "db", false);
        boolean freshStart = getBoolean(commandLine, "freshStart");
        boolean merge = getBoolean(commandLine, "m");
        addExperiments(file, dbDir, merge, freshStart);
    }

}
