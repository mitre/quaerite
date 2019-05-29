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
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.mitre.quaerite.core.queries.Query;
import org.mitre.quaerite.core.scorers.AbstractJudgmentScorer;
import org.mitre.quaerite.core.scorers.Scorer;
import org.mitre.quaerite.core.scorers.SearchResultSetScorer;
import org.mitre.quaerite.core.serializers.QuerySerializer;
import org.mitre.quaerite.core.serializers.ScorerListSerializer;

public class ExperimentSet {

    private static Gson GSON = new GsonBuilder().setPrettyPrinting()
            .registerTypeAdapter(Scorer.class, new ScorerListSerializer.ScorerSerializer<>())
            .registerTypeHierarchyAdapter(Query.class, new QuerySerializer())
            .create();

    private transient int maxRows = -1;
    private transient List<SearchResultSetScorer> searchResultSetScorers = new ArrayList<>();

    private List<Scorer> scorers = new ArrayList<>();
    private Map<String, Experiment> experiments = new LinkedHashMap<>();
    private transient Scorer trainScorer;
    private transient Scorer testScorer;
    private ExperimentConfig experimentConfig;

    public ExperimentSet() {
        experimentConfig = new ExperimentConfig();
    }

    public ExperimentSet(ExperimentConfig experimentConfig) {
        this.experimentConfig = experimentConfig;
    }


    public void addExperiment(Experiment experiment) {
        experiments.put(experiment.getName(), experiment);
    }

    public void addScorer(Scorer scorer) {
        int atN = scorer.getAtN();
        if (atN > maxRows) {
            maxRows = atN;
        }
        scorers.add(scorer);
        if (scorer instanceof AbstractJudgmentScorer &&
                ((AbstractJudgmentScorer)scorer).getUseForTrain()) {
            trainScorer = scorer;
        }
        if (scorer instanceof AbstractJudgmentScorer &&
                ((AbstractJudgmentScorer)scorer).getUseForTest()) {
            testScorer = scorer;
        }
        if (scorer instanceof SearchResultSetScorer) {
            searchResultSetScorers.add((SearchResultSetScorer)scorer);
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
        for (Scorer scorer : scorers) {
            tmpExperimentSet.addScorer(scorer);
        }
        for (String experimentName : experiments) {
            Experiment experiment = getExperiment(experimentName);
            if (experiment != null) {
                tmpExperimentSet.addExperiment(experiment);
            }
        }
        return GSON.toJson(tmpExperimentSet);
    }

    public static ExperimentSet fromJson(Reader reader) {
        ExperimentSet experimentSet = GSON.fromJson(reader, ExperimentSet.class);
        for (Map.Entry<String, Experiment> e : experimentSet.getExperiments().entrySet()) {
            e.getValue().setName(e.getKey());
        }
        int maxRows = experimentSet.getMaxRows();
        if (maxRows < 0) {
            throw new IllegalArgumentException("At least one of the scorers must have " +
                    "the 'atN' param set to a value >= 0");
        }
        return experimentSet;
    }

    public Experiment getExperiment(String experimentName) {
        return experiments.get(experimentName);
    }

    public List<Scorer> getScorers() {
        for (Scorer scorer : scorers) {
            scorer.reset();
        }
        return scorers;
    }

    public List<SearchResultSetScorer> getSearchResultSetScorers() {
        return searchResultSetScorers;
    }

    public int getMaxRows() {
        if (maxRows < 0 ) {
            for (Scorer scorer : scorers) {
                int atN = scorer.getAtN();
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
                ", scorers=" + scorers +
                ", experiments=" + experiments +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExperimentSet)) return false;
        ExperimentSet that = (ExperimentSet) o;
        return maxRows == that.maxRows &&
                scorers.equals(that.scorers) &&
                experiments.equals(that.experiments) &&
                Objects.equals(trainScorer, that.trainScorer) &&
                Objects.equals(testScorer, that.testScorer) &&
                experimentConfig.equals(that.experimentConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxRows, scorers, experiments, trainScorer, testScorer, experimentConfig);
    }
}
