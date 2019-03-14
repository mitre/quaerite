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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.mitre.quaerite.core.features.Feature;
import org.mitre.quaerite.core.features.ParamsMap;
import org.mitre.quaerite.core.features.StringFeature;
import org.mitre.quaerite.core.serializers.ParamsSerializer;


public class Experiment {

    public static final String URL_KEY = "url";
    public static final String CUSTOM_HANDLER_KEY = "customHandler";


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


    public Experiment(String name, ParamsMap features) {
        this(name, features.getParams());
    }

    public Experiment(String name, Map<String, Feature> features) {
        this.name = name;
        this.searchServerUrl = features.get(URL_KEY).toString();
        this.customHandler = features.get(CUSTOM_HANDLER_KEY).toString();

        for (Map.Entry<String, Feature> e : features.entrySet()) {
            if (!e.getKey().equals(URL_KEY) && !e.getKey().equals(CUSTOM_HANDLER_KEY)) {
                addParam(e.getKey(), e.getValue());
            }
        }
    }

    public void addParam(String key, Feature feature) {
        if (key.equals("q")) {
            throw new IllegalArgumentException("query is specified during initialization, not as a standard param!");
        }
        if (key.equals("fq")) {
            throw new IllegalArgumentException("set fqs specially: setFilterQuery(fq)");
        }
        params.put(key, feature);
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

    public Map<String, Feature> getParams() {
        //defensively copy
        Map<String, Feature> ret = new HashMap<>();
        for (Map.Entry<String, Feature> e : params.getParams().entrySet()) {
            ret.put(e.getKey(), e.getValue());
        }
        return ret;
    }

    public ParamsMap getAllFeatures() {
        ParamsMap ret = new ParamsMap();
        for (Map.Entry<String, Feature> e : params.getParams().entrySet()) {
            ret.put(e.getKey(), (Feature)e.getValue().clone());
        }
        ret.put(URL_KEY, new StringFeature(getSearchServerUrl()));
        ret.put(CUSTOM_HANDLER_KEY, new StringFeature(getCustomHandler()));
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

    public Feature getParams(String key) {
        return params.getParams().get(key);
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
