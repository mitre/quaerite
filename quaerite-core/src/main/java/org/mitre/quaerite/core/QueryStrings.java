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
 *
 */
package org.mitre.quaerite.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * This class represents user input in a single query box (default),
 * or in several query boxes as in an Elastic BoostingQuery (positive, negative)
 * or in a Boolean Query or in a geo query
 */
public class QueryStrings {

    public static final String DEFAULT_QUERY_NAME = "query";


    Map<String, String> map = new HashMap<>();
    String id = null;
    boolean idIsUpdateable;

    public QueryStrings() {
        idIsUpdateable = true;
    }

    public QueryStrings(String queryId) {
        this.id = queryId;
        idIsUpdateable = false;
    }
    /**
     * Default helper method that calls {@link #addQueryString(String, String)}
     * with {@link #DEFAULT_QUERY_NAME}.
     *
     * @param queryString
     */
    public void setQuery(String queryString) {
        addQueryString(DEFAULT_QUERY_NAME, queryString);
    }

    /**
     * Default helper method that calls {@link #getStringByName(String)}
     * with {@link #DEFAULT_QUERY_NAME}.
     */
    public String getQuery() {
        return getStringByName(DEFAULT_QUERY_NAME);
    }

    public void addQueryString(String name, String queryString) {
        if (map.containsKey(name)) {
            throw new IllegalArgumentException("string with name="+name+
                    " already exists!");
        }
        map.put(name, queryString);
        updateId();
    }

    private void updateId() {
        if (! idIsUpdateable) {
            return;
        }
        //if this is only a single query string request
        //use the query string by itself
        if (map.keySet().size() == 1 && map.keySet().contains(QueryStrings.DEFAULT_QUERY_NAME)) {
            id = map.get(QueryStrings.DEFAULT_QUERY_NAME);
            return;
        }

        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (i++ > 0) {
                sb.append(";");
            }
            sb.append(e.getKey()+":"+e.getValue());
        }
        id = sb.toString();
    }


    public String getStringByName(String name) {
        if (map.containsKey(name)) {
            return map.get(name);
        }
        return StringUtils.EMPTY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueryStrings)) return false;
        QueryStrings that = (QueryStrings) o;
        return map.equals(that.map);
    }

    @Override
    public int hashCode() {
        return Objects.hash(map);
    }

    @Override
    public String toString() {
        return "QueryStrings{" +
                "map=" + map +
                '}';
    }

    public int size() {
        return map.size();
    }

    public Set<String> names() {
        return map.keySet();
    }

    public String getId() {
        return id;
    }
}
