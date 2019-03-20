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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.mitre.quaerite.core.Experiment;
import org.mitre.quaerite.core.GAConfig;
import org.mitre.quaerite.core.stats.ExperimentScorePair;

public class MathUtil {

    public static ThreadLocalRandom RANDOM = ThreadLocalRandom.current();
    //used in the fitness proportion calculation
    //the smaller this value, the less weight goes to the less fit
    private static final double EPSILON = 0.001;

    public static float calcMutatedWeight(Float currentValue, float min, float max, double amplitude) {
        if (amplitude < 0 || amplitude > 1.0) {
            throw new IllegalArgumentException("amplitude must be >= 0 and <= 1");
        }
        if (Math.abs(max-min) < 0.0000001) {
            return min;
        }
        float curr;
        if (currentValue == null) {
            curr = (max+min)/2f;
        } else {
            curr = currentValue;
        }
        curr = (curr < min) ? min: curr;
        curr = (curr > max) ? max : curr;

        float distBelow = (float)amplitude*(curr-min);
        float distAbove = (float)amplitude*(max-curr);

        float adjustedMin = curr-distBelow;
        float adjustedMax = curr+distAbove;
        adjustedMin = (adjustedMin < min) ? min : adjustedMin;
        adjustedMax = (adjustedMax > max) ? max : adjustedMax;

        return getRandomFloat(adjustedMin, adjustedMax);
    }

    public static float getRandomFloat(float min, float max) {
        //TODO -- fix potential overflow/underflow
        return min + RANDOM.nextFloat() * (max - min);
    }

    public static List<ExperimentScorePair> calcFitnessProportions(List<ExperimentScorePair> scorePairs) {
        if (scorePairs == null || scorePairs.size() == 0) {
            return Collections.EMPTY_LIST;
        }
        double min = scorePairs.get(0).getScore();
        for (ExperimentScorePair p : scorePairs) {
            if (p.getScore() < 0.0) {
                throw new IllegalArgumentException("Can't handle negative fitness scores...currently");
            }
            if (p.getScore() < min) {
                min = p.getScore();
            }
        }

        double denom = 0.0;
        for (ExperimentScorePair p : scorePairs) {
            denom += (p.getScore()-min + EPSILON);
        }
        List<ExperimentScorePair> fitnessProportions = new ArrayList<>();
        for (ExperimentScorePair p : scorePairs) {
            double fp = (p.getScore()-min+EPSILON)/denom;
            fitnessProportions.add(new ExperimentScorePair(p.getExperiment(), fp));
        }
        return fitnessProportions;
    }

    public static Experiment select(List<ExperimentScorePair> fitnessProportions) {
        return select(fitnessProportions, RANDOM);
    }

    static Experiment select(List<ExperimentScorePair> fitnessProportions, Random random) {
        double r = random.nextDouble();
        for (ExperimentScorePair p : fitnessProportions) {
            if ((r -= p.getScore()) < 0.0) {
                return p.getExperiment();
            }
        }
        return fitnessProportions.get(0).getExperiment();
    }

    public static GAOperation nextGAOperation(GAConfig gaConfig) {
        return nextGAOperation(gaConfig, RANDOM);
    }

    static GAOperation nextGAOperation(GAConfig gaConfig, Random random) {
        double r = random.nextDouble();
        if ((r -= gaConfig.getCrossoverProbability()) < 0.0) {
            return GAOperation.CROSSOVER;
        } else if ((r -= gaConfig.getMutationProbability()) < 0.0) {
            return GAOperation.MUTATE;
        } else {
            return GAOperation.REPRODUCE;
        }
    }
}
