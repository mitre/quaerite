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
package org.mitre.quaerite.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.mitre.quaerite.core.Experiment;
import org.mitre.quaerite.core.GAConfig;
import org.mitre.quaerite.core.stats.ExperimentScorePair;

public class TestMathUtil {

    @Test
    public void testMutate() {
        for (int i = 0; i < 100; i++) {
            float mutated = MathUtil.calcMutatedWeight(2.0f, 0.0f, 10.0f, 1.0f);
            assertTrue(mutated >= 0.0 && mutated <= 10.0);
        }
        for (int i = 0; i < 100; i++) {
            float mutated = MathUtil.calcMutatedWeight(2.0f, 0.0f, 10.0f, 0.8f);
            assertTrue(mutated >= 0.4 && mutated <= 8.4);
        }
        for (int i = 0; i < 100; i++) {
            float mutated = MathUtil.calcMutatedWeight(2.0f, 0.0f, 10.0f, 0.1f);
            assertTrue(mutated >= 1.8 && mutated <= 2.8);
        }

        //test null
        for (int i = 0; i < 100; i++) {
            float mutated = MathUtil.calcMutatedWeight(null, 0.0f, 10.0f, 0.1f);
            assertTrue(mutated >= 4.5 && mutated <= 5.5);
        }

        //test out of bounds below
        for (int i = 0; i < 100; i++) {
            float mutated = MathUtil.calcMutatedWeight(0.0f, 1.0f, 10.0f, 0.1f);
            assertTrue(mutated >= 1.0 && mutated <= 1.9);
        }
    }

    @Test
    public void testFitnessProportions() throws Exception {
        List<ExperimentScorePair> scores = new ArrayList<>();
        scores.add(new ExperimentScorePair(ex("a"), 0.7));
        scores.add(new ExperimentScorePair(ex("b"), 0.6));
        scores.add(new ExperimentScorePair(ex("c"), 0.5));
        scores.add(new ExperimentScorePair(ex("d"), 0.4));
        scores.add(new ExperimentScorePair(ex("e"), 0.3));
        scores.add(new ExperimentScorePair(ex("f"), 0.2));
        scores.add(new ExperimentScorePair(ex("g"), 0.1));
        scores.add(new ExperimentScorePair(ex("h"), 0.1));

        List<ExperimentScorePair> fitnessProportions = MathUtil.calcFitnessProportions(scores);
        int multiplier = 10000;
        List<Experiment> experiments = new ArrayList<>();
        Random random = new Random(100);
        for (int i = 0; i < multiplier; i++) {
            experiments.add(MathUtil.select(fitnessProportions, random));
        }
        Map<String, Double> counts = new HashMap<>();
        for (Experiment e : experiments) {
            Double c = counts.get(e.getName());
            if (c == null) {
                c = 0.0;
            }
            c++;
            counts.put(e.getName(), c);
        }
        for (int i = 0; i < fitnessProportions.size(); i++) {
            String name = fitnessProportions.get(i).getExperiment().getName();
            double fp = fitnessProportions.get(i).getScore();
            assertEquals(fp, counts.get(name) / (double)multiplier, 0.01);
        }
    }

    @Test
    public void testGAOperations() throws Exception {
        GAConfig gaConfig = new ProxyGAConfig(0.1f, 0.2f, 0.7f);

        Map<GAOperation, Double> counts = new HashMap<>();
        int multiplier = 10000;
        Random random = new Random(100);
        for (int i = 0; i < multiplier; i++) {
            GAOperation op = MathUtil.nextGAOperation(gaConfig, random);
            Double d = counts.get(op);
            if (d == null) {
                d = 0.0;
            }
            d++;
            counts.put(op, d);
        }

        assertEquals(gaConfig.getCrossoverProbability(),
                counts.get(GAOperation.CROSSOVER) / (double)multiplier, 0.01);
        assertEquals(gaConfig.getMutationProbability(),
                counts.get(GAOperation.MUTATE) / (double)multiplier, 0.01);
        assertEquals(gaConfig.getReproductionProbability(),
                counts.get(GAOperation.REPRODUCE) / (double)multiplier, 0.01);

    }


    private static Experiment ex(String name) {
        return new ExperimentProxy(name);
    }

    private static class ExperimentProxy extends Experiment {

        public ExperimentProxy(String name) {
            super(name, "");
        }
    }

    private static class ProxyGAConfig extends GAConfig {
        private float c;
        private float m;
        private float r;
        private ProxyGAConfig(float c, float m, float r) {
            this.c = c;
            this.m = m;
            this.r = r;
        }
        @Override
        public float getMutationProbability() {
            return m;
        }

        @Override
        public float getCrossoverProbability() {
            return c;
        }

        @Override
        public float getReproductionProbability() {
            return r;
        }
    }
}
