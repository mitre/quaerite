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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;
import org.mitre.quaerite.util.MathUtil;

public class WeightableFeature implements Feature<WeightableFeature> {

    public static Float UNSPECIFIED_WEIGHT = null;

    public static float DEFAULT_WEIGHT = 0;
    private static final Pattern WEIGHT_PATTERN =
            Pattern.compile("(.*?)\\^((?:\\d+)(?:\\.\\d+)?)");

    private final String feature;
    private final Float weight;
    private final transient DecimalFormat df = new DecimalFormat("#.#",
            DecimalFormatSymbols.getInstance(Locale.US));

    public WeightableFeature(String s) {
        Matcher m = WEIGHT_PATTERN.matcher(s);
        if (m.matches()) {
            feature = m.group(1);
            weight = Float.parseFloat(m.group(2));
        } else {
            feature = s;
            weight = UNSPECIFIED_WEIGHT;
        }
    }

    public WeightableFeature(String feature, float weight) {
        this.feature = feature;
        this.weight = weight;
    }



    @Override
    public String toString() {
        if (weight != null) {
            return feature+"^"+df.format(weight);
        }
        return feature;
    }

    public boolean hasWeight() {
        return weight != null;
    }

    public String getFeature() {
        return feature;
    }

    public Float getWeight() {
        return weight;
    }

    @Override
    public Pair<Set<WeightableFeature>, Set<WeightableFeature>> crossover(Set<WeightableFeature> parentA, Set<WeightableFeature> parentB) {
        Map<String, WeightableFeature> parentAWeights = new HashMap<>();
        Map<String, WeightableFeature> parentBWeights = new HashMap<>();
        Set<String> fieldSet = new HashSet<>();
        fieldSet.addAll(parentAWeights.keySet());
        fieldSet.addAll(parentBWeights.keySet());
        List<String> fields = new ArrayList<>(fieldSet);
        int crossoverPoint =
                (fields.size() > 1) ?
                    MathUtil.RANDOM.nextInt(1, fields.size()-1) :
                        0;
        Set<WeightableFeature> childA = new HashSet<>();
        Set<WeightableFeature> childB = new HashSet<>();
        for (int i = 0; i < crossoverPoint; i++) {
            String fieldName = fields.get(i);
            if (parentAWeights.containsKey(fieldName)) {
                childA.add(parentAWeights.get(fieldName));
            }
            if (parentBWeights.containsKey(fieldName)) {
                childB.add(parentBWeights.get(fieldName));
            }
        }
        for (int i = crossoverPoint; i < fields.size(); i++) {
            String fieldName = fields.get(i);
            if (parentAWeights.containsKey(fieldName)) {
                childB.add(parentAWeights.get(fieldName));
            }
            if (parentBWeights.containsKey(fieldName)) {
                childA.add(parentBWeights.get(fieldName));
            }
        }
        return Pair.of(childA, childB);
    }
}
