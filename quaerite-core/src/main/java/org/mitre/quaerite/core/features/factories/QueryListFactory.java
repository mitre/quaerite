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
package org.mitre.quaerite.core.features.factories;

import java.util.ArrayList;
import java.util.List;


import org.apache.commons.lang3.tuple.Pair;
import org.mitre.quaerite.core.queries.EDisMaxQuery;
import org.mitre.quaerite.core.queries.Query;

public class QueryListFactory extends AbstractFeatureFactory<Query> {

    public static String NAME = "queries";

    List<QueryFactory> queryFactories = new ArrayList<>();

    public QueryListFactory() {
        super(NAME);
    }

    @Override
    public List<Query> permute(int maxSize) {
        List<Query> ret = new ArrayList<>();
        for (QueryFactory qf : queryFactories) {
            ret.addAll(qf.permute(maxSize));
            if (ret.size() > maxSize) {
                return ret;
            }
        }
        return ret;
    }

    @Override
    public Query random() {
        throw new IllegalArgumentException("not yet implemented");
    }

    @Override
    public Query mutate(Query feature, double probability, double amplitude) {
        throw new IllegalArgumentException("not yet implemented");
    }

    @Override
    public Pair<Query, Query> crossover(Query parentA, Query parentB) {
        throw new IllegalArgumentException("not yet implemented");
    }

    public void add(QueryFactory queryFactory) {
        queryFactories.add(queryFactory);
    }

    public QueryFactory<? extends Query> get(int i) {
        return queryFactories.get(i);
    }
}
