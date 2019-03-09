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
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.mitre.quaerite.features.serializers.FeatureSetsSerializer;

import org.mitre.quaerite.features.sets.FeatureSets;
import org.mitre.quaerite.scorecollectors.ScoreCollector;
import org.mitre.quaerite.scorecollectors.ScoreCollectorListSerializer;

public class ExperimentFeatures {


    List<ScoreCollector> scoreCollectors;
    FeatureSets featureSets;

    public static ExperimentFeatures fromJson(Reader reader) {
        Gson gson = new GsonBuilder().setPrettyPrinting()
                .registerTypeHierarchyAdapter(ScoreCollector.class, new ScoreCollectorListSerializer.ScoreCollectorSerializer())
                .registerTypeAdapter(FeatureSets.class, new FeatureSetsSerializer())
                .create();
        return gson.fromJson(reader, ExperimentFeatures.class);
    }

    public List<ScoreCollector> getScoreCollectors() {
        return scoreCollectors;
    }

    @Override
    public String toString() {
        return "ExperimentFeatures{" +
                "scoreCollectors=" + scoreCollectors +
                ", featureSets=" + featureSets +
                '}';
    }

    public FeatureSets getFeatureSets() {
        return featureSets;
    }
}
