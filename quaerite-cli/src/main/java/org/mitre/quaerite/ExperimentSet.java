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
package org.mitre.quaerite;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.mitre.quaerite.features.serializers.FeatureSetsSerializer;
import org.mitre.quaerite.features.sets.FeatureSets;
import org.mitre.quaerite.scorecollectors.ScoreCollector;
import org.mitre.quaerite.scorecollectors.ScoreCollectorListSerializer;

public class ExperimentSet {

    private static Gson GSON = new GsonBuilder().setPrettyPrinting()
            .registerTypeHierarchyAdapter(ScoreCollector.class, new ScoreCollectorListSerializer.ScoreCollectorSerializer())
            .registerTypeHierarchyAdapter(FeatureSets.class, new FeatureSetsSerializer())
            .create();

    private transient int maxRows = -1;
    private List<ScoreCollector> scoreCollectors = new ArrayList<>();
    private Map<String, Experiment> experiments = new LinkedHashMap<>();

    public void addExperiment(String name, Experiment experiment) {
        experiments.put(name, experiment);
    }

    public void addScoreCollector(ScoreCollector scoreCollector) {
        int atN = scoreCollector.getK();
        if (atN > maxRows) {
            maxRows = atN;
        }
        scoreCollectors.add(scoreCollector);
    }

    public Map<String, Experiment> getExperiments() {
        return experiments;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public String toJson(List<String> experiments) {
        ExperimentSet tmpExperimentSet = new ExperimentSet();
        for (ScoreCollector scoreCollector : scoreCollectors) {
            tmpExperimentSet.addScoreCollector(scoreCollector);
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
        return GSON.fromJson(reader, ExperimentSet.class);
    }

    public Experiment getExperiment(String experimentName) {
        return experiments.get(experimentName);
    }

    public List<ScoreCollector> getScoreCollectors() {
        return scoreCollectors;
    }

    public int getMaxRows() {
        if (maxRows < 0 ) {
            for (ScoreCollector scorer : scoreCollectors) {
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
                ", scoreCollectors=" + scoreCollectors +
                ", experiments=" + experiments +
                '}';
    }
}
