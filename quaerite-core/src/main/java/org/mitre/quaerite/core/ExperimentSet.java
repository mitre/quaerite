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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.mitre.quaerite.core.features.ParamsMap;
import org.mitre.quaerite.core.queries.Query;
import org.mitre.quaerite.core.scoreaggregators.ScoreAggregator;
import org.mitre.quaerite.core.scoreaggregators.ScoreAggregatorListSerializer;
import org.mitre.quaerite.core.serializers.QuerySerializer;

public class ExperimentSet {

    private static Gson GSON = new GsonBuilder().setPrettyPrinting()
            .registerTypeAdapter(ScoreAggregator.class, new ScoreAggregatorListSerializer.ScoreAggregatorSerializer())
            .registerTypeHierarchyAdapter(Query.class, new QuerySerializer())
            .create();

    private transient int maxRows = -1;
    private List<ScoreAggregator> scoreAggregators = new ArrayList<>();
    private Map<String, Experiment> experiments = new LinkedHashMap<>();
    private transient ScoreAggregator trainScoreAggregator;
    private transient ScoreAggregator testScoreAggregator;
    private ExperimentConfig experimentConfig;

    public ExperimentSet() {
        experimentConfig = new ExperimentConfig();
    }

    public ExperimentSet(ExperimentConfig experimentConfig) {
        this.experimentConfig = experimentConfig;
    }


    public void addExperiment(String name, Experiment experiment) {
        experiments.put(name, experiment);
    }

    public void addScoreAggregator(ScoreAggregator scoreAggregator) {
        int atN = scoreAggregator.getK();
        if (atN > maxRows) {
            maxRows = atN;
        }
        scoreAggregators.add(scoreAggregator);
        if (scoreAggregator.getUseForTrain()) {
            trainScoreAggregator = scoreAggregator;
        }
        if (scoreAggregator.getUseForTest()) {
            testScoreAggregator = scoreAggregator;
        }
    }

    public Map<String, Experiment> getExperiments() {
        return experiments;
    }

    public ExperimentConfig getExperimentConfig() {
        return experimentConfig;
    }
    public String toJson() {
        return GSON.toJson(this);
    }

    public String toJson(List<String> experiments) {
        ExperimentSet tmpExperimentSet = new ExperimentSet();
        for (ScoreAggregator scoreAggregator : scoreAggregators) {
            tmpExperimentSet.addScoreAggregator(scoreAggregator);
        }
        for (String experimentName : experiments) {
            Experiment experiment = getExperiment(experimentName);
            if (experiment != null) {
                tmpExperimentSet.addExperiment(experimentName, experiment);
            }
        }
        return GSON.toJson(tmpExperimentSet);
    }

    public static ExperimentSet fromJson(Reader reader) {
        ExperimentSet experimentSet = GSON.fromJson(reader, ExperimentSet.class);
        for (Map.Entry<String, Experiment> e : experimentSet.getExperiments().entrySet()) {
            e.getValue().setName(e.getKey());
        }
        return experimentSet;
    }

    public Experiment getExperiment(String experimentName) {
        return experiments.get(experimentName);
    }

    public List<ScoreAggregator> getScoreAggregators() {
        return scoreAggregators;
    }

    public int getMaxRows() {
        if (maxRows < 0 ) {
            for (ScoreAggregator scorer : scoreAggregators) {
                int atN = scorer.getK();
                if (atN > maxRows) {
                    maxRows = atN;
                }
            }
        }
        return maxRows;
    }

    @Override
    public String toString() {
        return "ExperimentSet{" +
                "maxRows=" + maxRows +
                ", scoreAggregators=" + scoreAggregators +
                ", experiments=" + experiments +
                '}';
    }
}
