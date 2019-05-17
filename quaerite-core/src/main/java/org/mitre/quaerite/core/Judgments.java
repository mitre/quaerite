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

import static java.util.stream.Collectors.toMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;

/**
 * This class captures the judgments about a single specific query
 * The id is the document id in Solr
 */
public class Judgments {
    private static Gson GSON = new Gson();

    public static final double NO_JUDGMENT = -1.0;
    private final QueryInfo queryInfo;
    private final Map<String, Double> judgments = new HashMap<>();
    private Map<String, Double> sorted;
    private volatile boolean updated = true;

    private final Object[] lock = new Object[0];

    /**
     *
     */
    public Judgments(QueryInfo queryInfo) {
        this.queryInfo = queryInfo;
    }

    public void addJudgment(String id, double relevance) {
        judgments.put(id, relevance);
        synchronized (lock) {
            updated = true;
        }
    }

    public double getJudgment(String id) {
        return judgments.containsKey(id) ?
                judgments.get(id) : NO_JUDGMENT;
    }

    public boolean containsJudgment(String id) {
        return judgments.containsKey(id);
    }

    public Map<String, Double> getSortedJudgments() {
        synchronized (lock) {
            if (sorted == null || updated == true) {
                sorted = judgments.entrySet()
                        .stream()
                        .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                        .collect(
                                toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2,
                                        LinkedHashMap::new));
            }
            updated = false;
        }
        return sorted;
    }

    public int size() {
        return judgments.size();
    }

    public static Judgments fromJson(String s) {
        return GSON.fromJson(s, Judgments.class);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public QueryInfo getQueryInfo() {
        return queryInfo;
    }

    public QueryStrings getQueryStrings() {
        return queryInfo.getQueryStrings();
    }

    public String getQuerySet() {
        return queryInfo.getQuerySet();
    }

    public int getQueryCount() {
        return queryInfo.getQueryCount();
    }

}