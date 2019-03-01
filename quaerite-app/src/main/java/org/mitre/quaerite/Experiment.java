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

public class Experiment {

    private static Gson GSON = new Gson();
    private final String name;
    private final String solrUrl;
    private final String customHandler;
    Map<String, Set<String>> params = new HashMap<>();
    Set<String> filterQueries = new HashSet<>();

    public Experiment(String name, String solrUrl) {
        this(name, solrUrl, null);
    }
    public Experiment(String name, String solrUrl, String customHandler) {
        this.customHandler = customHandler;
        this.name = name;
        this.solrUrl = solrUrl;
    }

    public void addParam(String key, String value) {
        if (key.equals("q")) {
            throw new IllegalArgumentException("query is specified during initialization, not as a standard param!");
        }
        if (key.equals("fq")) {
            throw new IllegalArgumentException("set fqs specially: setFilterQuery(fq)");
        }
        Set<String> set = params.get(key);
        if (set == null) {
            set = new HashSet<>();
        }
        set.add(value);
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

    public Map<String, String[]> getParams() {
        Map<String, String[]> ret = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : params.entrySet()) {
            ret.put(e.getKey(), e.getValue().toArray(new String[e.getValue().size()]));
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

    public String getSolrUrl() {
        return solrUrl;
    }

    public Set getParams(String key) {
        return Collections.unmodifiableSet(params.get(key));
    }

    @Override
    public String toString() {
        return "Experiment{" +
                "name='" + name + '\'' +
                ", solrUrl='" + solrUrl + '\'' +
                ", customHandler='" + customHandler + '\'' +
                ", params=" + params +
                ", filterQueries=" + filterQueries +
                '}';
    }
}
