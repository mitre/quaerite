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

import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TestGenerateExperiments {
    static Path JSON;
    @BeforeAll
    public static void setUp() throws Exception {
        JSON = Files.createTempFile("quaerite-", ".json");
        Files.copy(
                TestGenerateExperiments.class.getClass().getResourceAsStream("/test-documents/qf.json"),
                JSON, StandardCopyOption.REPLACE_EXISTING);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        Files.delete(JSON);
    }

    @Test
    public void testSimple() throws Exception {
        GenerateExperiments.main(new String[]{
                "-i", JSON.toAbsolutePath().toString(),
                "-o", "C:/data/tmp.json"
        });
    }
}
