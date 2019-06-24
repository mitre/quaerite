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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("ES tmdb instance has to be running")
public class TestESExamples {
    //TODO -- make all the working files tmp files/put in tmp directory
    //turn these into actual tests that check the output
    static Path CWD = Paths.get("../quaerite-examples/example_files");

    @Test
    public void testGenerateRunESRand() throws Exception {

        GenerateExperiments.main(new String[]{
                "-f", CWD.resolve("es/experiment_features_es_1.json").toAbsolutePath().toString(),
                "-e", "C:/data/quaerite/examples/experiments_es_1.json",
                "-r", "10"
        });
        RunExperiments.main(
                new String[]{
                        "-db", "C:/data/quaerite/examples/es_dbs/" + UUID.randomUUID().toString(),
                        "-e", "C:/data/quaerite/examples/experiments_es_1.json",
                        "-j", CWD.resolve("movie_judgments.csv").toAbsolutePath().toString(),
                        "-r", "C:/data/quaerite/examples/reports"
                }
        );
    }

    @Test
    public void testGenerateRunESPermute() throws Exception {

        GenerateExperiments.main(new String[]{
                "-f", CWD.resolve("es/experiment_features_es_1.json").toAbsolutePath().toString(),
                "-e", "C:/data/quaerite/examples/experiments_es_1.json",
                "-p",
                "-m", "50"
        });
        RunExperiments.main(
                new String[]{
                        "-db", "C:/data/quaerite/examples/es_dbs/" + UUID.randomUUID().toString(),
                        "-e", "C:/data/quaerite/examples/experiments_es_1.json",
                        "-j", CWD.resolve("movie_judgments.csv").toAbsolutePath().toString(),
                        "-r", "C:/data/quaerite/examples/reports"
                }
        );
    }

    @Test
    public void runGAES1() throws Exception {
        RunGA.main(
                new String[]{
                        "-db", "C:/data/quaerite/examples/es_dbs/" + UUID.randomUUID().toString(),
                        "-f", CWD.resolve("es/experiment_features_es_1.json").
                        toAbsolutePath().toString(),
                        "-j", CWD.resolve("movie_judgments.csv").toAbsolutePath().toString(),
                        "-o", "C:/data/quaerite/examples/ga_output_es"
                }
        );
    }

    @Test
    public void runExperimentsES() throws Exception {
        for (int i = 1; i <= 4; i++) {
            Path experimentsPath = CWD.resolve("es/experiments_es_" + i + ".json");
            System.out.println("running: " + experimentsPath);

            RunExperiments.main(
                    new String[]{
                            "-db", "C:/data/quaerite/examples/es_dbs/" + UUID.randomUUID().toString(),
                            "-e", experimentsPath.toAbsolutePath().toString(),
                            "-j", CWD.resolve("movie_judgments.csv").toAbsolutePath().toString(),
                            "-r", "C:/data/quaerite/examples/experiments_output_es_" + i
                    }
            );
        }
    }
}
