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
import org.mitre.quaerite.core.features.CustomHandler;
import org.mitre.quaerite.core.util.MathUtil;

/**
 * For Solr...users can specify a custom handler
 * that modifies the query server side. (see Hossman's Hidden Gems).
 * https://home.apache.org/~hossman/rev2016/
 *
 * These custom handlers might use a different key for the query, e.g. "qq".
 */
public class CustomHandlerFactory implements FeatureFactory<CustomHandler> {

    public static final String NAME = "customHandlers";
    public static final String CUSTOM_QUERY_KEY = "customQueryKey";
    public static final String DEFAULT_QUERY_KEY = "q";


    List<CustomHandler> customHandlerList = new ArrayList<>();

    public CustomHandlerFactory() {
    }

    public void add(CustomHandler ch) {
        customHandlerList.add(ch);
    }

    @Override
    public List<CustomHandler> permute(int maxSize) {
        return new ArrayList<>(customHandlerList);
    }

    @Override
    public CustomHandler random() {
        int i = MathUtil.RANDOM.nextInt(0, customHandlerList.size());
        return customHandlerList.get(i);
    }

    @Override
    public CustomHandler mutate(CustomHandler feature, double probability, double amplitude) {
        return feature;
    }

    @Override
    public Pair<CustomHandler, CustomHandler> crossover(CustomHandler parentA, CustomHandler parentB) {
        return Pair.of(parentA.deepCopy(), parentB.deepCopy());
    }

    public List<CustomHandler> getCustomHandlers() {
        return new ArrayList<>(customHandlerList);
    }
}
