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
package org.mitre.quaerite.core.features;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.mitre.quaerite.core.features.factories.FeatureFactories;
import org.mitre.quaerite.core.features.factories.FeatureFactory;
import org.mitre.quaerite.core.util.MathUtil;

public class ParamsMap {

    Map<String, Feature> map = new HashMap<>();

    public void put(String name, Feature feature) {
        map.put(name, feature);
    }

    public Map<String, Feature> getParams() {
        return map;
    }

    public ParamsMap mutate(FeatureFactories featureFactories, float probability, float amplitude) {
        ParamsMap ret = new ParamsMap();
        for (Map.Entry<String, Feature> e : map.entrySet()) {
            if (featureFactories.get(e.getKey()) != null) {
                FeatureFactory featureFactory = featureFactories.get(e.getKey());
                Feature mutated = (Feature) featureFactory.mutate(e.getValue(), probability, amplitude);
                ret.put(e.getKey(), mutated);
            } else {
                ret.put(e.getKey(), e.getValue());
            }
        }
        return ret;
    }
/*
    public Pair<ParamsMap, ParamsMap> crossover(ParamsMap parentB) {

        ParamsMap childA = new ParamsMap();
        ParamsMap childB = new ParamsMap();
        Set<String> paramsSet = new HashSet<>();
        paramsSet.addAll(map.keySet());
        paramsSet.addAll(parentB.map.keySet());

        List<String> params = new ArrayList<>(paramsSet);
        int crossoverPoint = MathUtil.RANDOM.nextInt(0, params.size());

        for (int i = 0; i < crossoverPoint; i++) {
            String param = params.get(i);
            if (map.containsKey(param)
                    && parentB.map.containsKey(param)) {
                Pair<Feature, Feature> children =
                        map.get(param).crossover(parentB.map.get(param));
                childA.put(param, children.getLeft());
                childB.put(param, children.getRight());
            } else {
                if (map.containsKey(param)) {
                    childA.put(param, (Feature)map.get(param).deepCopy());
                }
                if (parentB.map.containsKey(param)) {
                    childB.put(param, (Feature)parentB.map.get(param).deepCopy());
                }
            }
        }

        for (int i = crossoverPoint; i < params.size(); i++) {
            String param = params.get(i);
            if (map.containsKey(param)
                    && parentB.map.containsKey(param)) {
                Pair<Feature, Feature> children =
                        map.get(param).crossover(parentB.map.get(param));
                childA.put(param, children.getRight());
                childB.put(param, children.getLeft());
            } else {
                if (map.containsKey(param)) {
                    childB.put(param, (Feature)map.get(param).deepCopy());
                }
                if (parentB.map.containsKey(param)) {
                    childA.put(param, (Feature)parentB.map.get(param).deepCopy());
                }
            }
        }

        return Pair.of(childA, childB);
    }

 */

    @Override
    public String toString() {
        return "ParamsMap{" +
                "map=" + map +
                '}';
    }
}
