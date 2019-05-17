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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.mitre.quaerite.core.features.Feature;
import org.mitre.quaerite.core.features.NegativeBoost;
import org.mitre.quaerite.core.queries.BoostingQuery;
import org.mitre.quaerite.core.queries.Query;
import org.mitre.quaerite.core.queries.SingleStringQuery;
import org.mitre.quaerite.core.util.MathUtil;

public class BoostingQueryFactory extends QueryFactory<BoostingQuery> {

    public static final String NAME = "boostingQuery";
    private final QueryFactory positive;
    private final QueryFactory negative;
    private final FloatFeatureFactory<NegativeBoost> negativeBoostFactory;

    public BoostingQueryFactory(QueryFactory positive, QueryFactory negative, FloatFeatureFactory<NegativeBoost> negativeBoostFactory) {
        super(NAME, BoostingQuery.class);
        this.positive = positive;
        this.negative = negative;
        this.negativeBoostFactory = negativeBoostFactory;
    }


    @Override
    public List<BoostingQuery> permute(int maxSize) {
        List<BoostingQuery> queries = new ArrayList<>();
        //egads...don't do this with big numbers please.
        for (Object pQ : positive.permute(maxSize)) {
            for (Object nQ : negative.permute(maxSize)) {
                for (NegativeBoost nb : negativeBoostFactory.permute(maxSize)) {
                    queries.add(
                            new BoostingQuery(
                                    (SingleStringQuery)pQ,
                            (SingleStringQuery)nQ,
                                    nb)
                    );
                    if (queries.size() >= maxSize) {
                        return queries;
                    }
                }
            }
        }
        return queries;
    }

    @Override
    public BoostingQuery random() {
        return new BoostingQuery(
                (SingleStringQuery)positive.random(),
                (SingleStringQuery)negative.random(),
                negativeBoostFactory.random()
        );
    }

    @Override
    public BoostingQuery mutate(BoostingQuery query, double probability, double amplitude) {
        if (MathUtil.RANDOM.nextFloat() > probability) {
            return query.deepCopy();
        }
        Query mutatedPositive = positive.mutate(query.getPositiveQuery(), probability, amplitude);
        Query mutatedNegative = negative.mutate(query.getNegativeQuery(), probability, amplitude);
        NegativeBoost mutatedNegativeBoost = negativeBoostFactory.mutate(query.getNegativeBoost(), probability, amplitude);

        return new BoostingQuery(
                (SingleStringQuery)mutatedPositive,
                (SingleStringQuery)mutatedNegative,
                mutatedNegativeBoost);
    }

    @Override
    public Pair<BoostingQuery, BoostingQuery> crossover(BoostingQuery parentA, BoostingQuery parentB) {
        Query positiveA = parentA.getPositiveQuery();
        Query positiveB = parentB.getPositiveQuery();
        Pair<Query, Query> crossedOverPositive = positive.crossover(positiveA, positiveB);
        Pair<Query, Query> crossedOverNegative = negative.crossover(parentA.getNegativeQuery(), parentB.getNegativeQuery());
        Pair<NegativeBoost, NegativeBoost> negativeBoosts =
                negativeBoostFactory.crossover(parentA.getNegativeBoost(), parentB.getNegativeBoost());

        if (MathUtil.RANDOM.nextFloat() < 0.5) {
            BoostingQuery a = new BoostingQuery(
                    (SingleStringQuery)crossedOverPositive.getLeft(),
                    (SingleStringQuery)crossedOverNegative.getRight(),
                    negativeBoosts.getLeft());
            BoostingQuery b = new BoostingQuery(
                    (SingleStringQuery)crossedOverPositive.getRight(),
                    (SingleStringQuery)crossedOverNegative.getLeft(),
                    negativeBoosts.getRight());
            return Pair.of(a, b);
        } else {
            BoostingQuery a = new BoostingQuery(
                    (SingleStringQuery)crossedOverPositive.getRight(),
                    (SingleStringQuery)crossedOverNegative.getLeft(),
                    negativeBoosts.getRight());
            BoostingQuery b = new BoostingQuery(
                    (SingleStringQuery)crossedOverPositive.getLeft(),
                    (SingleStringQuery)crossedOverNegative.getRight(),
                    negativeBoosts.getLeft());
            return Pair.of(a, b);
        }
    }
}
