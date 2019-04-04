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
import org.mitre.quaerite.core.queries.Query;

public class QueryFactory<T extends Query> extends AbstractFeatureFactory<T> {

    List<FeatureFactory> factories = new ArrayList<>();
    public QueryFactory(String name) {
        super(name);
    }


    @Override
    public List<T> permute(int maxSize) {
        return null;
    }

    @Override
    public T random() {
        return null;
    }

    @Override
    public T mutate(T feature, double probability, double amplitude) {
        return null;
    }

    @Override
    public Pair<T, T> crossover(T parentA, T parentB) {
        return null;
    }

    public void add(FeatureFactory factory) {
        factories.add(factory);
    }
}
