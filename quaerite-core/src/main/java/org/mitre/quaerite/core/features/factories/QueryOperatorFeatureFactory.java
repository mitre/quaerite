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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.mitre.quaerite.core.features.QueryOperator;
import org.mitre.quaerite.core.util.MathUtil;

/**
 * this currently only supports the basics
 * and, or and mm, where mm is a positive/negative integer or float.
 * This does not yet support the more interesting syntax
 * options:3&lt;90% or multiple combinations
 */
public class QueryOperatorFeatureFactory<T extends QueryOperator>
        extends AbstractFeatureFactory<T> {

    final Set<QueryOperator.OPERATOR> operatorSet;
    final List<Integer> integers;
    final List<Float> floats;
    private final int minInt;
    private final int maxInt;
    private final float minFloat;
    private final float maxFloat;

    public QueryOperatorFeatureFactory(Set<QueryOperator.OPERATOR> operatorSet,
                                        List<Integer> integers, List<Float> floats) {
        super("q.op");
        this.operatorSet = operatorSet;
        this.integers = integers;
        this.floats = floats;
        if (integers != null && integers.size() > 0) {
            int minInt = integers.get(0);
            int maxInt = integers.get(1);
            for (Integer i : integers) {
                if (i < minInt) {
                    minInt = i;
                }
                if (i > maxInt) {
                    maxInt = i;
                }
            }
            this.minInt = minInt;
            this.maxInt = maxInt;
        } else {
            this.minInt = Integer.MIN_VALUE;
            this.maxInt = Integer.MAX_VALUE;
        }
        if (floats != null && floats.size() > 0) {
            float minFloat = floats.get(0);
            float maxFloat = floats.get(1);
            for (Float f : floats) {
                if (f < minFloat) {
                    minFloat = f;
                }
                if (f > maxFloat) {
                    maxFloat = f;
                }
            }
            if (minFloat < -1.0f || minFloat > 1.0f) {
                throw new IllegalArgumentException("minFloat must be >= -1.0 and <= 1.0:" +minFloat);
            }
            this.minFloat = minFloat;
            this.maxFloat = maxFloat;
        } else {
            this.minFloat = Float.MIN_VALUE;
            this.maxFloat = Float.MAX_VALUE;
        }
    }

    @Override
    public List<T> permute(int maxSize) {
        List<T> ops = new ArrayList<>();
        if (operatorSet.contains(QueryOperator.OPERATOR.OR)) {
            if (integers != null) {
                    for (Integer i : integers) {
                        ops.add((T) new QueryOperator(QueryOperator.OPERATOR.OR, i));
                    }
            }
            if (floats != null) {
                for (Float f : floats) {
                    ops.add((T) new QueryOperator(QueryOperator.OPERATOR.OR, f));
                }
            }
        }
        for (QueryOperator.OPERATOR op : operatorSet) {
                ops.add((T)new QueryOperator(op));
        }
        return ops;
    }

    @Override
    public T random() {
        List<QueryOperator.OPERATOR> ops = new ArrayList<>(operatorSet);
        QueryOperator.OPERATOR op;
        if (ops.size() == 1) {
            op = ops.get(0);
        } else {
            if (MathUtil.RANDOM.nextFloat() < 0.20) {
                op = QueryOperator.OPERATOR.AND;
            } else {
                op = QueryOperator.OPERATOR.OR;
            }
        }
        if (op.equals(QueryOperator.OPERATOR.OR)) {
            if (integers != null && floats != null) {
                float r = MathUtil.RANDOM.nextFloat();
                if (r < 0.4) {
                    return newRandFloat();
                } else if (r < 0.8) {
                    return newRandInt();
                }
            } else if (integers != null) {
                if (MathUtil.RANDOM.nextFloat() < 0.8) {
                    return newRandInt();
                } else {
                    return (T)new QueryOperator(QueryOperator.OPERATOR.OR);
                }
            } else if (floats != null) {
                if (MathUtil.RANDOM.nextFloat() < 0.8) {
                    return newRandFloat();
                } else {
                    return (T)new QueryOperator(QueryOperator.OPERATOR.OR);
                }
            }
            return (T)new QueryOperator(QueryOperator.OPERATOR.OR);
        }
        return (T)new QueryOperator(QueryOperator.OPERATOR.AND);
    }

    private T newRandFloat() {
        return (T)new QueryOperator(QueryOperator.OPERATOR.OR,
                MathUtil.getRandomFloat(minFloat, maxFloat));
    }
    private T newRandInt() {
        return (T)new QueryOperator(QueryOperator.OPERATOR.OR,
                MathUtil.getRandomInt(minInt, maxInt));
    }
    /**
     *
     * @param feature
     * @param probability
     * @param amplitude
     * @return
     */
    @Override
    public T mutate(T feature, double probability, double amplitude) {
        if (MathUtil.RANDOM.nextDouble() > probability) {
            return feature;
        }

        if (operatorSet.size() == 1) {
            return mutateParam(feature.getOperator(), feature, amplitude);
        }
        QueryOperator.OPERATOR op = (MathUtil.RANDOM.nextFloat() < 0.5) ?
                    QueryOperator.OPERATOR.AND : QueryOperator.OPERATOR.OR;
        return mutateParam(op, feature, amplitude);
    }

    //TODO -- fill this in
    private T mutateParam(QueryOperator.OPERATOR operator, T feature, double amplitude) {
        return random();
    }

    @Override
    public Pair<T, T> crossover(T parentA, T parentB) {
        return Pair.of(random(), random());
    }
}
