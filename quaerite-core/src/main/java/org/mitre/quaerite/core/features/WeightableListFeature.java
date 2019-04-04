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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.mitre.quaerite.core.util.MathUtil;

public class WeightableListFeature extends AbstractFeature<WeightableListFeature> {

    List<WeightableField> weightableFields;
    public WeightableListFeature(String name) {
        super(name);
        weightableFields = new ArrayList<>();
    }
/*
    @Override
    public Pair<WeightableListFeature, WeightableListFeature> crossover(WeightableListFeature parentB) {
        Map<String, WeightableField> parentAWeights = new HashMap<>();
        Map<String, WeightableField> parentBWeights = new HashMap<>();
        for (WeightableField f : weightableFields) {
            parentAWeights.put(f.getFeature(), f);
        }

        for (WeightableField f : parentB.getWeightableFields()) {
            parentBWeights.put(f.getFeature(), f);
        }

        Set<String> fieldSet = new HashSet<>();
        fieldSet.addAll(parentAWeights.keySet());
        fieldSet.addAll(parentBWeights.keySet());
        List<String> fields = new ArrayList<>(fieldSet);
        int crossoverPoint =
                        MathUtil.RANDOM.nextInt(fields.size());
        WeightableListFeature childA = new WeightableListFeature(getName());
        WeightableListFeature childB = new WeightableListFeature(getName());
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

 */

    public void add(WeightableField weightableField) {
        weightableFields.add(weightableField);
    }

    public void addAll(Collection all) {
        weightableFields.addAll(all);
    }

    public List<WeightableField> getWeightableFields() {
        return weightableFields;
    }

    public int size() {
        return weightableFields.size();
    }

    public WeightableField get(int i) {
        return weightableFields.get(i);
    }

    @Override
    public WeightableListFeature deepCopy() {
        //deep copy
        WeightableListFeature clone = new WeightableListFeature(getName());
        for (WeightableField field : weightableFields) {
            if (field.hasWeight()) {
                clone.add(new WeightableField(field.getFeature(), field.getWeight()));
            } else {
                clone.add(new WeightableField(field.getFeature()));
            }
        }
        return clone;
    }

    @Override
    public String toString() {
        return "WeightableListFeature{" +
                "weightableFields=" + weightableFields +
                '}';
    }
}
