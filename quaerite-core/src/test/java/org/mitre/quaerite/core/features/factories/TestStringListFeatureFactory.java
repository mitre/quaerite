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
package org.mitre.quaerite.core.features.factories;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.mitre.quaerite.core.features.FQ;

public class TestStringListFeatureFactory {

    @Test
    public void testStringFeature() throws Exception {
        int minSetSize = 1;
        int maxSetSize = 3;
        List<String> lA = new ArrayList<>();
        lA.add("a");
        lA.add("b");
        lA.add("c");
        StringListFeatureFactory<FQ> factoryA = new StringListFeatureFactory<FQ>("fq", FQ.class, lA, minSetSize, maxSetSize);

        List<String> lB = new ArrayList<>();
        lB.addAll(lA);
        StringListFeatureFactory<FQ> factoryB = new StringListFeatureFactory<FQ>("fq", FQ.class, lB, minSetSize, maxSetSize);
        for (int i = 0; i < 1000000; i++) {
            FQ fqA = factoryA.random();
            FQ fqB = factoryB.random();
            Pair<FQ, FQ> pair = factoryA.crossover(fqA, fqB);

            if (pair.getLeft().getAll().size() > maxSetSize || pair.getLeft().getAll().size() < minSetSize) {
                fail(pair.getLeft().toString() + " not within set size range");
            }
            if (pair.getRight().getAll().size() > maxSetSize || pair.getRight().getAll().size() < minSetSize) {
                fail(pair.getRight().toString() + " not within set size range");
            }

        }
    }
}
