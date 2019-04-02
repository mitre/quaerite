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

import java.io.Reader;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.mitre.quaerite.core.features.factories.FeatureFactories;
import org.mitre.quaerite.core.features.factories.QueryListFactory;
import org.mitre.quaerite.core.scoreaggregators.ScoreAggregator;
import org.mitre.quaerite.core.scoreaggregators.ScoreAggregatorListSerializer;
import org.mitre.quaerite.core.serializers.FeatureFactorySerializer;

public class ExperimentFactory {


    private GAConfig gaConfig = new GAConfig();

    Map<String, List<String>> fixedParameters;
    List<ScoreAggregator> scoreAggregators;
    FeatureFactories featureFactories;

    public static ExperimentFactory fromJson(Reader reader) {
        Gson gson = new GsonBuilder().setPrettyPrinting()
                .registerTypeHierarchyAdapter(ScoreAggregator.class, new ScoreAggregatorListSerializer.ScoreAggregatorSerializer())
                .registerTypeAdapter(FeatureFactories.class, new FeatureFactorySerializer())
                .create();
        return gson.fromJson(reader, ExperimentFactory.class);
    }
    private transient ScoreAggregator trainScoreAggregator;
    private transient ScoreAggregator testScoreAggregator;

    public List<ScoreAggregator> getScoreAggregators() {
        return scoreAggregators;
    }

    @Override
    public String toString() {
        return "ExperimentFactory{" +
                "gaConfig=" + gaConfig +
                ", fixedParameters=" + fixedParameters +
                ", scoreAggregators=" + scoreAggregators +
                ", featureFactories=" + featureFactories +
                ", trainScoreAggregator=" + trainScoreAggregator +
                ", testScoreAggregator=" + testScoreAggregator +
                '}';
    }

    public FeatureFactories getFeatureFactories() {
        return featureFactories;
    }

    public ScoreAggregator getTrainScoreAggregator() {
        if (trainScoreAggregator == null) {
            if (scoreAggregators.size() == 0) {
                trainScoreAggregator = scoreAggregators.get(0);
            } else {
                boolean found = false;
                for (ScoreAggregator scoreAggregator : scoreAggregators) {
                    if (scoreAggregator.getUseForTrain()) {
                        if (found) {
                            throw new IllegalArgumentException("Can't have more than one train score aggregator!");
                        }
                        trainScoreAggregator = scoreAggregator;
                        found = true;
                    }
                }
            }
        }
        return trainScoreAggregator;
    }

    public ScoreAggregator getTestScoreAggregator() {
        if (testScoreAggregator == null) {
            if (scoreAggregators.size() == 1) {
                testScoreAggregator = scoreAggregators.get(0);
            } else {
                boolean found = false;
                for (ScoreAggregator scoreAggregator : scoreAggregators) {
                    if (scoreAggregator.getUseForTest()) {
                        if (found) {
                            throw new IllegalArgumentException("Can't have more than one test score aggregator!");
                        }
                        testScoreAggregator = scoreAggregator;
                        found = true;
                    }
                }
            }
        }
        return testScoreAggregator;
    }

    public GAConfig getGAConfig() {
        return gaConfig;
    }

    public Map<String, List<String>> getFixedParameters() {
        return fixedParameters;
    }
}
