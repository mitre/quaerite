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
package org.mitre.quaerite.features;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.mitre.quaerite.util.MathUtil;

public class ParamsMap {

    Map<String, Feature> map = new HashMap<>();

    public void put(String name, Feature feature) {
        map.put(name, feature);
    }

    public Map<String, Feature> getParams() {
        return map;
    }

    public Pair<ParamsMap, ParamsMap> crossover(ParamsMap parentA, ParamsMap parentB) {
        //TODO: pick up here.
        ParamsMap childA = new ParamsMap();
        ParamsMap childB = new ParamsMap();
        Set<String> paramsSet = new HashSet<>();
        paramsSet.addAll(parentA.map.keySet());
        paramsSet.addAll(parentB.map.keySet());

        List<String> params = new ArrayList<>(paramsSet);
        int crossoverPoint =
                (params.size() > 1) ?
                        MathUtil.RANDOM.nextInt(1, params.size() - 1) :
                        0;

        for (int i = 0; i < crossoverPoint; i++) {
            String param = params.get(i);
            if (parentA.map.containsKey(param)
                    && parentB.map.containsKey(param)) {
                Pair<Feature, Feature> children =
                        parentA.map.get(param).crossover(parentB.map.get(param));
                childA.put(param, children.getLeft());
                childB.put(param, children.getRight());
            } else {
                if (parentA.map.containsKey(param)) {
                    childA.put(param, parentA.map.get(param));
                }
                if (parentB.map.containsKey(param)) {
                    childB.put(param, parentB.map.get(param));
                }
            }
        }

        for (int i = crossoverPoint; i < params.size(); i++) {
            String param = params.get(i);
            if (parentA.map.containsKey(param)
                    && parentB.map.containsKey(param)) {
                Pair<Feature, Feature> children =
                        parentA.map.get(param).crossover(parentB.map.get(param));
                childA.put(param, children.getRight());
                childB.put(param, children.getLeft());
            } else {
                if (parentA.map.containsKey(param)) {
                    childB.put(param, parentA.map.get(param));
                }
                if (parentB.map.containsKey(param)) {
                    childA.put(param, parentB.map.get(param));
                }
            }
        }

        return Pair.of(childA, childB);
    }
}
