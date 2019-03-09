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
package org.mitre.quaerite.features.sets;

import java.util.ArrayList;
import java.util.List;

import org.mitre.quaerite.features.Feature;
import org.mitre.quaerite.features.WeightableField;
import org.mitre.quaerite.features.WeightableListFeature;
import org.mitre.quaerite.util.MathUtil;

public abstract class WeightableFeatureSet<T> implements FeatureSet<WeightableListFeature> {
    private static final float DEFAULT_MIN = 0.0f;
    private static final float DEFAULT_MAX = 1.0f;


    transient WeightableListFeature features;
    transient float min;
    transient float max;

    final List<String> fields;
    final List<Float> defaultWeights;

    public WeightableFeatureSet(List<String> fields, List<Float> defaultWeights) {
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
    }
    private static WeightableListFeature convert(List<String> fields) {
        WeightableListFeature ret = new WeightableListFeature();
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
    public Feature random() {
        WeightableListFeature ret = new WeightableListFeature();
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
    public List<Feature> permute(int maxSize) {
        List<Feature> collector = new ArrayList<>();
        WeightableListFeature currFeatures = new WeightableListFeature();
        for (WeightableField f : features.getWeightableFields()) {
            if (f.hasWeight()) {
                currFeatures.add(f);
            }
        }
        if (currFeatures.size() > 0) {
            collector.add(currFeatures);
        }
        recurse(0, maxSize, currFeatures, collector);
        return collector;
    }

    private void recurse(int i, int maxSize,
                         WeightableListFeature currFeatures,
                         List<Feature> collector) {
        if (i >= fields.size()) {
            return;
        }
        if (collector.size() >= maxSize) {
            return;
        }
        WeightableListFeature base = new WeightableListFeature();
        base.addAll(currFeatures.getWeightableFields());
        if (features.get(i).hasWeight()) {
            recurse(i+1, maxSize, base, collector);
        } else {
            for (Float f : defaultWeights) {
                if (f > 0.0f) {
                    WeightableListFeature tmp = new WeightableListFeature();
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
    public WeightableListFeature mutate(WeightableListFeature weightableListFeature, double probability, double amplitude) {
        WeightableListFeature mutated = new WeightableListFeature();
        for (WeightableField f : features.getWeightableFields()) {
            if (MathUtil.RANDOM.nextDouble() <= probability) {
                WeightableField mutatedFeature =
                        new WeightableField(f.getFeature(),
                                MathUtil.calcMutatedWeight(min, max, amplitude));
                mutated.add(mutatedFeature);
            } else {
                mutated.add(f);
            }
        }
        return mutated;
    }
}
