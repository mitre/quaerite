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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.mitre.quaerite.features.Feature;
import org.mitre.quaerite.features.ParamsMap;
import org.mitre.quaerite.features.serializers.FeatureSetsSerializer;
import org.mitre.quaerite.features.serializers.ParamsSerializer;
import org.mitre.quaerite.features.sets.FeatureSets;
import org.mitre.quaerite.scorecollectors.ScoreCollector;
import org.mitre.quaerite.scorecollectors.ScoreCollectorListSerializer;

public class Experiment {

    private static Gson GSON = new GsonBuilder().setPrettyPrinting()
            .registerTypeAdapter(ParamsMap.class, new ParamsSerializer())
            .create();
    private final String name;
    private final String searchServerUrl;
    private final String customHandler;
    ParamsMap params = new ParamsMap();
    Set<String> filterQueries = new HashSet<>();

    public Experiment(String name, String searchServerUrl) {
        this(name, searchServerUrl, null);
    }
    public Experiment(String name, String searchServerUrl, String customHandler) {
        this.customHandler = customHandler;
        this.name = name;
        this.searchServerUrl = searchServerUrl;
    }

    public void addParam(String key, Feature feature) {
        if (key.equals("q")) {
            throw new IllegalArgumentException("query is specified during initialization, not as a standard param!");
        }
        if (key.equals("fq")) {
            throw new IllegalArgumentException("set fqs specially: setFilterQuery(fq)");
        }
        Set<Feature> set = params.getParams().get(key);
        if (set == null) {
            set = new HashSet<>();
        }
        set.add(feature);
        params.put(key, set);
    }

    public void addFilterQuery(String fq) {
        if (filterQueries == null) {
            filterQueries = new HashSet<>();
        }
        filterQueries.add(fq);
    }

    public Collection<String> getFilterQueries() {
        if (filterQueries == null) {
            filterQueries = new HashSet<>();
        }
        return Collections.unmodifiableCollection(filterQueries);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public Map<String, Set<Feature>> getParams() {
        //defensively copy
        Map<String, Set<Feature>> ret = new HashMap<>();
        for (Map.Entry<String, Set<Feature>> e : params.getParams().entrySet()) {
            ret.put(e.getKey(), e.getValue());
        }
        return ret;
    }

    public String getCustomHandler() {
        return customHandler;
    }

    public static Experiment fromJson(String s) {
        return GSON.fromJson(s, Experiment.class);
    }

    public String getName() {
        return name;
    }

    public String getSearchServerUrl() {
        return searchServerUrl;
    }

    public Set getParams(String key) {
        return Collections.unmodifiableSet(params.getParams().get(key));
    }

    @Override
    public String toString() {
        return "Experiment{" +
                "name='" + name + '\'' +
                ", searchServerUrl='" + searchServerUrl + '\'' +
                ", customHandler='" + customHandler + '\'' +
                ", params=" + params +
                ", filterQueries=" + filterQueries +
                '}';
    }
}
