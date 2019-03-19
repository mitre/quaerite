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
package org.mitre.quaerite.core;

public class GAConfig extends ExperimentConfig {

    public static final int DEFAULT_POPULATION = 20;
    public static final int DEFAULT_GENERATIONS = 10;
    public static final int DEFAULT_NFOLDS = 4;
    public static final float DEFAULT_MUTATION_PROBABILITY = 0.1f;
    public static final float DEFAULT_MUTATION_AMPLITUDE = 0.2f;

    int population = DEFAULT_POPULATION;
    int generations = DEFAULT_GENERATIONS;
    int nFolds = DEFAULT_NFOLDS;
    float mutationProbability = DEFAULT_MUTATION_PROBABILITY;
    float mutationAmplitude = DEFAULT_MUTATION_AMPLITUDE;

    public int getPopulation() {
        return population;
    }

    public int getGenerations() {
        return generations;
    }

    public int getNFolds() {
        return nFolds;
    }

    public float getMutationProbability() {
        return mutationProbability;
    }

    public float getMutationAmplitude() {
        return mutationAmplitude;
    }

    @Override
    public String toString() {
        return "GAConfig{" +
                "population=" + population +
                ", generations=" + generations +
                ", nFolds=" + nFolds +
                ", mutationProbability=" + mutationProbability +
                ", mutationAmplitude=" + mutationAmplitude +
                '}';
    }
}
