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
 *
 */
package org.mitre.quaerite.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mitre.quaerite.core.features.Feature;
import org.mitre.quaerite.core.features.QF;
import org.mitre.quaerite.core.features.WeightableField;
import org.mitre.quaerite.core.features.WeightableListFeature;
import org.mitre.quaerite.core.scoreaggregators.NDCGAggregator;
import org.mitre.quaerite.core.scoreaggregators.ScoreAggregator;

public class TestExperimentSet {


    @Test
    public void testLoadingExperiments() throws Exception {

        ExperimentSet experimentSet = null;
        try (Reader reader =
                new BufferedReader(new InputStreamReader(
                        getClass().getResourceAsStream("/test-documents/experiments.json"),
                        StandardCharsets.UTF_8))) {
            experimentSet = ExperimentSet.fromJson(reader);

        }
        assertEquals(8, experimentSet.getScoreAggregators().size());
        Map<String, Experiment> map = experimentSet.getExperiments();
        Experiment peopleTitle = map.get("people_title");
        Map<String, Feature> features = peopleTitle.getParams();
        Feature qf = features.get("qf");
        assertEquals(QF.class, qf.getClass());
        List<WeightableField> fields = ((WeightableListFeature)qf).getWeightableFields();
        assertEquals(2, fields.size());
        assertEquals("people", fields.get(0).getFeature());
        assertEquals("title", fields.get(1).getFeature());

        List<ScoreAggregator> scoreAggregators = experimentSet.getScoreAggregators();
        for (ScoreAggregator scoreAggregator : scoreAggregators) {
            if (scoreAggregator instanceof NDCGAggregator) {
                assertTrue(scoreAggregator.getExportPMatrix());
                assertTrue(scoreAggregator.getUseForTrain());
                assertFalse(scoreAggregator.getUseForTest());
            } else {
                assertFalse(scoreAggregator.getExportPMatrix());
                assertFalse(scoreAggregator.getUseForTrain());
                assertFalse(scoreAggregator.getUseForTest());
            }
        }
    }

    @Test
    public void testLoadingExperimentConfig() throws Exception {
        ExperimentSet experimentSet = null;
        try (Reader reader =
                     new BufferedReader(new InputStreamReader(
                             getClass().getResourceAsStream("/test-documents/experiments2.json"),
                             StandardCharsets.UTF_8))) {
            experimentSet = ExperimentSet.fromJson(reader);
        }
        assertEquals(20, experimentSet.getExperimentConfig().getNumThreads());
        assertEquals("customIdField", experimentSet.getExperimentConfig().getIdField());

        String json = experimentSet.toJson();
        ExperimentSet revivified = ExperimentSet.fromJson(new StringReader(json));
        assertEquals(20, revivified.getExperimentConfig().getNumThreads());
        assertEquals("customIdField", revivified.getExperimentConfig().getIdField());

    }

}
