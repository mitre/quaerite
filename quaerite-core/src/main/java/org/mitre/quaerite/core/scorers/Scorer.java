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
package org.mitre.quaerite.core.scorers;

import static org.mitre.quaerite.core.QueryInfo.DEFAULT_QUERY_SET;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.mitre.quaerite.core.QueryInfo;


public abstract class Scorer {
    private final String name;
    private final int atN;

    public Scorer(String name, int atN) {
        this.name = (atN > -1) ? name+"_"+atN : name;
        this.atN = atN;
    }

    public int getAtN() {
        return atN;
    }
    public String getName() {
        return name;
    }

    private final Object[] lock = new Object[0];

    ConcurrentHashMap<QueryInfo, Double> scores = new ConcurrentHashMap<>();
    //really just a concurrent hash set, the integer is a dummy value
    ConcurrentHashMap<String, Integer> querySets = new ConcurrentHashMap<>();
    /**
     * This needs to be thread safe
     * @param queryInfo
     * @param score
     */
    void addScore(QueryInfo queryInfo, double score) {
        scores.put(queryInfo, score);
        QueryInfo defaultQueryInfo = null;
        //also keep track of all results together
        if (!queryInfo.getQuerySet().equals(DEFAULT_QUERY_SET)) {
            defaultQueryInfo = new QueryInfo(
                    queryInfo.getQueryId(),
                    DEFAULT_QUERY_SET, queryInfo.getQueryStrings(),
                    queryInfo.getQueryCount());
            scores.put(defaultQueryInfo, score);
            querySets.put(DEFAULT_QUERY_SET, 1);
        }
        querySets.put(queryInfo.getQuerySet(), 1);
    }

    /**
     *
     * @param querySet queryset
     * @return map of statistic_name/values for the specified queryset
     */
    public abstract Map<String, Double> getSummaryStatistics(String querySet);

    /**
     *
     * @return list of statistics names
     */
    public abstract List<String> getStatistics();

    /**
     * format this stat name's values for the report
     *
     * @param statName
     * @param scoreValues
     * @return
     */
    public abstract String format(String statName, Map<String, Double> scoreValues);
    /**
     *
     * @return the name of the primary statistic for this scorer
     */
    public abstract String getPrimaryStatisticName();

    public Map<QueryInfo, Double> getScores() {
        Map<QueryInfo, Double> ret = new HashMap<>();
        ret.putAll(scores);
        return Collections.unmodifiableMap(ret);
    }

    public Set<QueryInfo> getQueryInfos(String querySet) {
        Set<QueryInfo> ret = new HashSet<>();
        for (QueryInfo q : scores.keySet()) {
            if (q.getQuerySet().equals(querySet)) {
                ret.add(q);
            }
        }
        return ret;
    }


    public int getSize() {
        return scores.size();
    }


    public Collection<? extends String> getQuerySets() {
        return querySets.keySet();
    }

    public void reset() {
        scores.clear();
        querySets.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Scorer)) return false;
        Scorer that = (Scorer) o;
        return Objects.equals(scores, that.scores);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scores, querySets);
    }
}
