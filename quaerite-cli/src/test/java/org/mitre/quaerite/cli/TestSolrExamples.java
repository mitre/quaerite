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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
public class TestSolrExamples {
    //TODO -- make all the working files tmp files/put in tmp directory
    //turn these into actual tests that check the output
    static Path CWD = Paths.get("../quaerite-examples/example_files");


    @Test
    public void runGASolr() throws Exception {
        for (int i = 1; i <= 5; i++) {
            Path featuresPath = CWD.resolve("solr/experiment_features_solr_"+i+".json");
            System.out.println("running: "+featuresPath);
            RunGA.main(
                    new String[]{
                            "-db", "C:/data/quaerite/test_db5",
                            "-f", featuresPath.toAbsolutePath().toString(),
                            "-j", CWD.resolve("movie_judgments.csv").toAbsolutePath().toString(),
                            "-o", "C:/data/quaerite/examples/ga_output_solr_"+i
                    }
            );
        }
    }

    @Test
    public void runGenerateRandom() throws Exception {
        for (int i = 1; i <= 5; i++) {
            Path featuresPath = CWD.resolve("solr/experiment_features_solr_"+i+".json");
            System.out.println("running: "+featuresPath);
            GenerateExperiments.main(
                    new String[]{
                            "-f", featuresPath.toAbsolutePath().toString(),
                            "-e", "C:/data/quaerite/examples/rand_solr_experiments_"+i+".json",
                            "-r", "10"
                    }
            );
        }

        for (int i = 1; i <= 5; i++) {
            Path exPath = Paths.get("C:/data/quaerite/examples/solr/rand_solr_experiments_"+i+".json");
            System.out.println("running experiments: "+exPath);
            RunExperiments.main(
                    new String[]{
                            "-db", "C:/data/quaerite/test_db"+(i+10),
                            "-e", "C:/data/quaerite/examples/rand_solr_experiments_"+i+".json",
                            "-j", CWD.resolve("movie_judgments.csv").toAbsolutePath().toString(),
                            "-r", "C:/data/quaerite/examples/experiments_output_solr_"+i
                    }
            );
        }

    }

    @Test
    public void runExperimentsSolr() throws Exception {
        for (int i = 1; i <= 2; i++) {
            Path experimentsPath = CWD.resolve("solr/experiments_solr_"+i+".json");
            System.out.println("running: " + experimentsPath);

            RunExperiments.main(
                    new String[]{
                            "-db", "C:/data/quaerite/test_db"+(i+5),
                            "-e", experimentsPath.toAbsolutePath().toString(),
                            "-j", CWD.resolve("movie_judgments.csv").toAbsolutePath().toString(),
                            "-r", "C:/data/quaerite/examples/experiments_output_solr_"+i
                    }
            );
        }
    }


}
