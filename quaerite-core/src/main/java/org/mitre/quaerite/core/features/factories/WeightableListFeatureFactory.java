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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mitre.quaerite.core.features.WeightableField;
import org.mitre.quaerite.core.features.WeightableListFeature;
import org.mitre.quaerite.core.util.MathUtil;


public class WeightableListFeatureFactory<T extends WeightableListFeature>
        extends AbstractFeatureFactory<T> {
    private static final float DEFAULT_MIN = 0.0f;
    private static final float DEFAULT_MAX = 1.0f;


    transient WeightableListFeature features;
    transient Map<String, WeightableField> featureMap;
    transient float min;
    transient float max;

    final List<String> fields;
    final List<Float> defaultWeights;

    public WeightableListFeatureFactory(String name, List<String> fields, List<Float> defaultWeights) {
        super(name);
        this.fields = fields;
        this.defaultWeights = defaultWeights;
        this.features = convert(fields);
        if (defaultWeights.size() == 0) {
            min = DEFAULT_MIN;
            max = DEFAULT_MAX;
        } else {
            float tmpMin = defaultWeights.get(0);
            float tmpMax = defaultWeights.get(0);
            for (Float w : defaultWeights) {
                if (w < tmpMin) {
                    tmpMin = w;
                }
                if (w > tmpMax) {
                    tmpMax = w;
                }
            }
            min = tmpMin;
            max = tmpMax;
        }
        featureMap = new HashMap<>();
        for (WeightableField f : features.getWeightableFields()) {
            featureMap.put(f.getFeature(), f);
        }
    }

    private T convert(List<String> fields) {
        T ret = (T)new WeightableListFeature(getName());

        for (String f : fields) {
            ret.add(new WeightableField(f));
        }
        return ret;
    }

    public WeightableListFeature getFeatures() {
        return features;
    }

    public List<Float> getDefaultWeights() {
        return defaultWeights;
    }

    @Override
    public T random() {
        T ret = (T)new WeightableListFeature(getName());
        for (WeightableField field : features.getWeightableFields()) {
            if (field.hasWeight()) {
                ret.add(field);
            } else {
                ret.add(new WeightableField(field.getFeature(),
                        MathUtil.getRandomFloat(min, max)));
            }
        }
        return ret;
    }

    @Override
    public List<T> permute(int maxSize) {
        List<T> collector = new ArrayList<>();
        WeightableListFeature currFeatures = new WeightableListFeature(getName());
        for (WeightableField f : features.getWeightableFields()) {
            if (f.hasWeight()) {
                currFeatures.add(f);
            }
        }
        if (currFeatures.size() > 0) {
            collector.add((T)currFeatures);
        }
        recurse(0, maxSize, currFeatures, collector);
        return collector;
    }

    private void recurse(int i, int maxSize,
                         WeightableListFeature currFeatures,
                         List<T> collector) {
        if (i >= fields.size()) {
            return;
        }
        if (collector.size() >= maxSize) {
            return;
        }
        WeightableListFeature base = new WeightableListFeature(getName());
        base.addAll(currFeatures.getWeightableFields());
        if (features.get(i).hasWeight()) {
            recurse(i + 1, maxSize, base, collector);
        } else {
            for (Float f : defaultWeights) {
                if (f > 0.0f) {
                    T tmp = (T)new WeightableListFeature(getName());
                    tmp.addAll(base.getWeightableFields());
                    tmp.add(
                            new WeightableField(features.get(i).getFeature(), f));
                    collector.add(tmp);
                    recurse(i + 1, maxSize, tmp, collector);
                } else {
                    recurse(i + 1, maxSize, base, collector);
                }
            }
        }
    }

    @Override
    public T mutate(T weightableListFeature, double probability, double amplitude) {
        T mutated = (T)new WeightableListFeature(getName());
        Set<String> added = new HashSet<>();

        for (WeightableField weightableField : weightableListFeature.getWeightableFields()) {
            if (featureMap.containsKey(weightableField.getFeature()) &&
                featureMap.get(weightableField.getFeature()).hasWeight()) {
                mutated.add(featureMap.get(weightableField.getFeature()));
                added.add(weightableField.getFeature());
            } else {
                if (MathUtil.RANDOM.nextDouble() < probability) {
                    Float baseline = weightableField.getWeight();
                    if (baseline == null) {
                        baseline = min;
                    }
                    WeightableField mutatedFeature =
                            new WeightableField(weightableField.getFeature(),
                                    MathUtil.calcMutatedWeight(baseline, min, max, amplitude));
                    mutated.add(mutatedFeature);
                    added.add(weightableField.getFeature());
                } else {
                    //inverse probability of dropping a field
                    if (MathUtil.RANDOM.nextDouble() > probability) {
                        mutated.add(weightableField);
                        added.add(weightableField.getFeature());
                    } else {
                        //drop this feature
                    }
                }
            }
        }
        //consider adding 1 field
        //TODO: might want to apply amplitude to how many fields to drop and/or add
        if (MathUtil.RANDOM.nextDouble() < probability) {
            Set<String> candidateFields = new HashSet<>();
            for (String k : featureMap.keySet()) {
                if (!added.contains(k)) {
                    candidateFields.add(k);
                }
            }
            List<String> candidateFieldList = new ArrayList<>(candidateFields);
            if (candidateFieldList.size() > 0) {
                String field = candidateFieldList.get(
                        MathUtil.RANDOM.nextInt(candidateFieldList.size()));
                WeightableField mutatedFeature =
                        new WeightableField(field,
                                MathUtil.calcMutatedWeight(min, min, max, amplitude));
                mutated.add(mutatedFeature);
            }
        }
        return mutated;
    }
}
