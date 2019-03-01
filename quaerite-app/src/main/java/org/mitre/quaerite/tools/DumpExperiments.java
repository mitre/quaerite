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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.mitre.quaerite.ExperimentSet;
import org.mitre.quaerite.db.ExperimentDB;

public class DumpExperiments {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static Options OPTIONS = new Options();
    static {
        OPTIONS.addOption("db", "db", true, "database folder");
        OPTIONS.addOption("f", "file", true, "json file with experiment(s)");
    }
    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new GnuParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(
                    "java -jar org.mitre.quaerite.tools.AddExperiments",
                    OPTIONS);
            return;
        }
        Path file = Paths.get(commandLine.getOptionValue("f"));
        Path dbDir = Paths.get(commandLine.getOptionValue("db"));
        dump(file, dbDir);
    }

    private static void dump(Path file, Path dbDir) throws SQLException, IOException {
        try (ExperimentDB experimentDB = ExperimentDB.open(dbDir)) {
            ExperimentSet experimentSet = experimentDB.getExperiments();
            try(BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write(experimentSet.toJson());
                writer.flush();
            }
        }
    }

}
