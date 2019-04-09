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

import org.junit.jupiter.api.Test;

public class TestExamples {


    @Test
    public void testES1() throws Exception {
        Path cwd = Paths.get("../quaerite-examples/example_files");

        GenerateExperiments.main(new String[]{
                "-f", cwd.resolve("es/experiment_features_es_1.json").toAbsolutePath().toString(),
                "-e", "C:/data/quaerite/examples/experiments_es_1.json",
                "-r", "10"
        });
        RunExperiments.main(
                new String[]{
                        "-db", "C:/data/quaerite/test_db3",
                        "-e", "C:/data/quaerite/examples/experiments_es_1.json",
                        "-j", cwd.resolve("movie_judgments.csv").toAbsolutePath().toString(),
                        "-r", "C:/data/quaerite/examples/reports"
                }
        );
    }
}
