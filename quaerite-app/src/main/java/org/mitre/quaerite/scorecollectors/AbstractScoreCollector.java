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
package org.mitre.quaerite.scorecollectors;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.mitre.quaerite.Judgments;
import org.mitre.quaerite.QueryInfo;
import org.mitre.quaerite.ResultSet;
import org.mitre.quaerite.scorers.AbstractRankScorer;


public abstract class AbstractScoreCollector implements ScoreCollector {

    static int extractAtK(Map<String, String> params) {
        if (params.size() > 1) {
            throw new IllegalArgumentException("can't have more than one param for now");
        }
        String atK = params.get("atK");
        if (atK == null) {
            throw new IllegalArgumentException("must have atK param");
        }
        return Integer.parseInt(atK);
    }

    private final AbstractRankScorer scorer;
    private final Object[] lock = new Object[0];

    ConcurrentHashMap<QueryInfo, Double> scores = new ConcurrentHashMap<>();

    //key is the queryset, the set contains the queryInfos that are in that querySet
    ConcurrentHashMap<String, Set<QueryInfo>> querySets = new ConcurrentHashMap<>();

    public AbstractScoreCollector(AbstractRankScorer scorer) {
        this.scorer = scorer;
    }


    /**
     * NOTE: This needs to be thread safe!
     *
     * @param judgments
     * @param resultSet
     */
    @Override
    public void add(Judgments judgments, ResultSet resultSet) {
        double result = scorer.score(judgments, resultSet);
        scores.put(judgments.getQueryInfo(), result);
        QueryInfo defaultQueryInfo = null;
        //also keep track of all results together
        if (!judgments.getQuerySet().equals(QueryInfo.DEFAULT_QUERY_SET)) {
            defaultQueryInfo = new QueryInfo(QueryInfo.DEFAULT_QUERY_SET, judgments.getQuery(), judgments.getQueryCount());
            scores.put(defaultQueryInfo, result);
        }
        synchronized (lock) {
            Set<QueryInfo> queryInfos = querySets.get(judgments.getQueryInfo().getQuerySet());
            if (queryInfos == null) {
                queryInfos = new HashSet<QueryInfo>();
            }
            queryInfos.add(judgments.getQueryInfo());
            querySets.put(judgments.getQuerySet(), queryInfos);

            if (defaultQueryInfo != null) {
                Set<QueryInfo> defaultQueryInfos = querySets.get(defaultQueryInfo.getQuerySet());
                if (defaultQueryInfos == null) {
                    defaultQueryInfos = new HashSet<>();
                }
                defaultQueryInfos.add(defaultQueryInfo);
                querySets.put(defaultQueryInfo.getQuerySet(), defaultQueryInfos);
            }

        }
    }

    @Override
    public Map<QueryInfo, Double> getScores() {
        Map<QueryInfo, Double> ret = new HashMap<>();
        ret.putAll(scores);
        return Collections.unmodifiableMap(ret);
    }

    @Override
    public int getK() {
        return scorer.getAtN();
    }

    @Override
    public String getName() {
        return scorer.getName();
    }

    public int getSize() {
        return scores.size();
    }


    @Override
    public Collection<? extends String> getQuerySets() {
        return querySets.keySet();
    }
}
