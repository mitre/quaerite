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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mitre.quaerite.core.Experiment;
import org.mitre.quaerite.core.ExperimentSet;
import org.mitre.quaerite.core.queries.LuceneQuery;
import org.mitre.quaerite.core.queries.Query;
import org.mitre.quaerite.core.queries.QueryOperator;
import org.mitre.quaerite.core.scoreaggregators.NDCGAggregator;
import org.mitre.quaerite.core.scoreaggregators.ScoreAggregator;


public class TestGenerateSolrExperiments {
    static Path JSON;
    static Path EXPERIMENTS;
    @BeforeAll
    public static void setUp() throws Exception {
        JSON = Files.createTempFile("quaerite-features", ".json");
        EXPERIMENTS = Files.createTempFile("quaerite-experiments", ".json");
        Files.copy(
                TestGenerateSolrExperiments.class.getClass().getResourceAsStream("/test-documents/experiment_features_solr_1.json"),
                JSON, StandardCopyOption.REPLACE_EXISTING);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        Files.delete(JSON);
        Files.delete(EXPERIMENTS);
    }

    @Test
    public void testSimple() throws Exception {
        GenerateExperiments.main(new String[]{
                "-f", JSON.toAbsolutePath().toString(),
                "-e", EXPERIMENTS.toAbsolutePath().toString()});

        ExperimentSet set = null;
        try (Reader reader = Files.newBufferedReader(EXPERIMENTS, StandardCharsets.UTF_8)) {
            set = ExperimentSet.fromJson(reader);
        }
        assertEquals(1920, set.getExperiments().size());
        assertEquals(1, set.getScoreAggregators().size());

        ScoreAggregator scoreAggregator = set.getScoreAggregators().get(0);
        assertEquals(NDCGAggregator.class, scoreAggregator.getClass());

        List<Experiment> experiments = new ArrayList<>(set.getExperiments().values());
        for (int i = 0; i < 10; i++) {
            List<Query> filterQueries = experiments.get(i).getFilterQueries();
            assertEquals(3, filterQueries.size());
            assertEquals(LuceneQuery.class, filterQueries.get(0).getClass());
            LuceneQuery q = (LuceneQuery)filterQueries.get(0);
            assertEquals("xyz:fox", q.getQueryString());
            assertEquals("text", q.getDefaultField());
            assertEquals(QueryOperator.OPERATOR.AND, q.getQueryOperator());
        }
    }
}
