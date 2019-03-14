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
package org.mitre.quaerite.core.featuresets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.mitre.quaerite.core.ExperimentFeatures;
import org.mitre.quaerite.core.features.Feature;
import org.mitre.quaerite.core.features.WeightableField;
import org.mitre.quaerite.core.features.WeightableListFeature;


public class TestFeatures {
    Gson gson = new Gson();

    @Test
    public void testSimpleQF() throws Exception {
        List<String> fields = new ArrayList<>();
        fields.add("title");
        fields.add("author^1");
        fields.add("content^0.4");
        fields.add("isbn");
        List<Float> defaultWeights = new ArrayList<>();
        defaultWeights.add(0.0f);
        defaultWeights.add(5.0f);
        defaultWeights.add(10.0f);

        QF qf = new QF(fields, defaultWeights);
        //test random
        for (int i = 0; i < 10; i++) {
            boolean foundAuthor = false;
            boolean foundContent = false;
            WeightableListFeature list = (WeightableListFeature)qf.random();
            for (int j = 0; j < list.size(); j++) {
                WeightableField wf = list.get(j);
                if (wf.getFeature().equals("author")) {
                    foundAuthor = true;
                    assertEquals(1.0f, wf.getWeight(), 0.001);
                } else if (wf.getFeature().equals("content")) {
                    foundContent = true;
                    assertEquals(0.4f, wf.getWeight(), 0.001);
                } else {
                    Float weight = wf.getWeight();
                    assertNotNull(weight);
                    assertTrue(weight >= 0.0f && weight <= 10.0f);
                }
            }
            assertTrue(foundAuthor);
            assertTrue(foundContent);
        }

        List<Feature> permutations = qf.permute(200);
        assertEquals(9, permutations.size());
    }

    @Test
    public void testQFDeserialization() throws Exception {
        ExperimentFeatures experimentFeatures = ExperimentFeatures.fromJson(newReader("/test-documents/qf.json"));
        TIE tie = (TIE)experimentFeatures.getFeatureSets().get("tie");
        assertEquals(0.0, tie.getFloats().get(0), 0.001);
        assertEquals(0.1, tie.getFloats().get(1), 0.001);
        assertEquals(0.2, tie.getFloats().get(2), 0.001);

    }

    private Reader newReader(String path) {
        return new BufferedReader(
                new InputStreamReader(
                        this.getClass().getResourceAsStream(path),
                        StandardCharsets.UTF_8
                )
        );
    }
}
