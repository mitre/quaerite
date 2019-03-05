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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public abstract class WeightableFeatureSet implements FeatureSet {
    private static final float DEFAULT_MIN = 0.0f;
    private static final float DEFAULT_MAX = 1.0f;

    transient final Random random;
    transient List<WeightableFeature> features = new ArrayList<>();
    transient float min;
    transient float max;

    final List<String> fields;
    final List<Float> defaultWeights;

    public WeightableFeatureSet(List<String> fields, List<Float> defaultWeights) {
        this.random = new Random();
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
    private static List<WeightableFeature> convert(List<String> fields) {
        List<WeightableFeature> ret = new ArrayList<>();
        for (String f : fields) {
            ret.add(new WeightableFeature(f));
        }
        return ret;
    }

    public List<WeightableFeature> getFeatures() {
        return features;
    }

    public List<Float> getDefaultWeights() {
        return defaultWeights;
    }

    float getRandomFloat(float min, float max) {
        //TODO -- fix potential overflow/underflow
        return min + random.nextFloat() * (max - min);
    }

    @Override
    public Set<Feature> random() {
        Set<Feature> ret = new HashSet<>();
        for (WeightableFeature feature : features) {
            if (feature.hasWeight()) {
                ret.add(feature);
            } else {
                ret.add(new WeightableFeature(feature.getFeature(), getRandomFloat(min, max)));
            }
        }
        return ret;
    }

    @Override
    public List<Set<Feature>> permute(int maxSize) {
        List<Set<Feature>> collector = new ArrayList<>();
        Set<Feature> currFeatures = new HashSet<>();
        for (WeightableFeature f : features) {
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

    @Override
    public Set<Feature> getEachDefaultFeature() {
        Set<Feature> ret = new HashSet<>();
        ret.addAll(features);
        return ret;
    }

    private void recurse(int i, int maxSize,
                         Set<Feature> currFeatures,
                         List<Set<Feature>> collector) {
        if (i >= fields.size()) {
            return;
        }
        if (collector.size() >= maxSize) {
            return;
        }
        Set base = new HashSet();
        base.addAll(currFeatures);
        if (features.get(i).hasWeight()) {
            recurse(i+1, maxSize, base, collector);
        } else {
            for (Float f : defaultWeights) {
                if (f > 0.0f) {
                    Set<Feature> tmp = new HashSet<>();
                    tmp.addAll(base);
                    tmp.add(
                            new WeightableFeature(features.get(i).getFeature(), f));
                    collector.add(tmp);
                    recurse(i + 1, maxSize, tmp, collector);
                } else {
                    recurse(i + 1, maxSize, base, collector);
                }
            }
        }

    }
}
