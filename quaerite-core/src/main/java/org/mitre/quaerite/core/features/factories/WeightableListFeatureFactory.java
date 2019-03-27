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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.util.FastMath;
import org.mitre.quaerite.core.features.WeightableField;
import org.mitre.quaerite.core.features.WeightableListFeature;
import org.mitre.quaerite.core.util.MathUtil;


public class WeightableListFeatureFactory<T extends WeightableListFeature>
        extends AbstractFeatureFactory<T> {
    private static final float DEFAULT_MIN = 0.0f;
    private static final float DEFAULT_MAX = 1.0f;

    //parameterize these
    private static final float DEFAULT_PROBABILITY_INSERT = 0.3f;
    private static final float DEFAULT_PROBABILITY_REMOVE = 0.1f;
    private static final float DEFAULT_PROBABILITY_MODIFY = 0.6f;

    private enum MUTATE_OPERATION {
        INSERT,
        REMOVE,
        MODIFY
    }


    transient WeightableListFeature features;
    transient Map<String, WeightableField> featureMap;
    transient float min;
    transient float max;

    final List<String> fields;
    final List<Float> defaultWeights;
    final int maxSetSize;

    public WeightableListFeatureFactory(String name, List<String> fields, List<Float> defaultWeights, int maxSetSize) {
        super(name);
        this.maxSetSize = maxSetSize;
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
        if (maxSetSize > -1) {
            List<WeightableField> tmp = new ArrayList<>();
            tmp.addAll(features.getWeightableFields());
            Collections.shuffle(tmp, MathUtil.RANDOM);
            for (int i = 0; i < maxSetSize && i < tmp.size(); i++) {
                WeightableField field = tmp.get(i);
                if (field.hasWeight()) {
                    ret.add(field);
                } else {
                    ret.add(new WeightableField(field.getFeature(),
                            MathUtil.getRandomFloat(min, max)));
                }
            }
        } else {
            for (WeightableField field : features.getWeightableFields()) {
                if (field.hasWeight()) {
                    ret.add(field);
                } else {
                    ret.add(new WeightableField(field.getFeature(),
                            MathUtil.getRandomFloat(min, max)));
                }
            }
        }
        return ret;
    }

    @Override
    public List<T> permute(int maxSize) {
        List<T> collector = new ArrayList<>();
        WeightableListFeature currFeatures = new WeightableListFeature(getName());
        recurse(0, 0, maxSize, currFeatures, collector);
        return collector;
    }

    private void recurse(int i, int depth, int maxSize,
                         WeightableListFeature currFeatures,
                         List<T> collector) {

        if (maxSetSize > -1 && currFeatures.size() >= maxSetSize) {
            return;
        }
        if (i >= fields.size()) {
            return;
        }

        if (collector.size() >= maxSize) {
            return;
        }
        WeightableListFeature base = new WeightableListFeature(getName());
        if (currFeatures.getWeightableFields().size() > 0) {
            base.addAll(currFeatures.getWeightableFields());
        }

        if (features.get(i).hasWeight()) {
            base.add(features.get(i));
            collector.add((T)base);
            recurse(i + 1, depth+1, maxSize, base, collector);
        } else {
            int newDepth = depth+1;
            for (Float f : defaultWeights) {
                if (f > 0.0f) {
                    T tmp = (T)new WeightableListFeature(getName());
                    tmp.addAll(base.getWeightableFields());
                    tmp.add(
                            new WeightableField(features.get(i).getFeature(), f));
                    collector.add(tmp);
                    recurse(i + 1, newDepth, maxSize, tmp, collector);
                } else {
                    recurse(i + 1, depth, maxSize, base, collector);
                }
            }
        }
    }

    @Override
    public T mutate(T weightableListFeature, double probability, double amplitude) {
        List<WeightableField> mutated = new ArrayList<>();

        int numMutations = (int) FastMath.floor(amplitude * featureMap.size());
        numMutations = (numMutations == 0) ? 1 : numMutations;

        mutated.addAll(weightableListFeature.getWeightableFields());
        for (int i = 0; i < numMutations; i++) {
            MUTATE_OPERATION op = nextMutateOperation();
            switch (op) {
                case INSERT:
                    //do the inverse of insert if mutated is already as full as it can get
                    if (mutated.size() == featureMap.size() ||
                            (maxSetSize > -1 && mutated.size() >= maxSetSize)) {
                        remove(mutated);
                    } else {
                        insert(mutated, amplitude);
                    }
                    break;
                case MODIFY:
                    if (mutated.size() == 0) {
                        insert(mutated, amplitude);
                    } else {
                        modify(mutated, amplitude);
                    }
                    break;
                case REMOVE:
                    //do the inverse if nothing can be removed
                    if (mutated.size() == 0) {
                        insert(mutated, amplitude);
                    } else {
                        remove(mutated);
                    }
                    break;
            }

        }
        //make sure there's at least one value
        if (mutated.size() == 0) {
            insert(mutated, amplitude);
        }
        T ret = (T)new WeightableListFeature(getName());
        ret.addAll(mutated);
        return ret;
    }

    private void modify(List<WeightableField> mutated, double amplitude) {
        int index = MathUtil.RANDOM.nextInt(mutated.size());
        WeightableField existing = mutated.get(index);

        WeightableField mutatedFeature =
                new WeightableField(existing.getFeature(),
                        MathUtil.calcMutatedWeight(existing.getWeight(), min, max, amplitude));

        mutated.set(index, mutatedFeature);
    }

    private void insert(List<WeightableField> mutated, double amplitude) {
        //if there's an 'insert' operation, what are the candidates
        List<String> newFeatures = new ArrayList<>();
        Set<String> existingFeatures = new HashSet<>();
        for (WeightableField f : mutated) {
            existingFeatures.add(f.getFeature());
        }
        for (String n : featureMap.keySet()) {
            if (!existingFeatures.contains(n)) {
                newFeatures.add(n);
            }
        }
        if (newFeatures.size() == 0) {
            return;
        }
        String newFeatureName = newFeatures.get(MathUtil.RANDOM.nextInt(newFeatures.size()));
        float mid = (float)((double)min/(double)max);
        mutated.add(
                new WeightableField(newFeatureName,
                        MathUtil.calcMutatedWeight(mid, min, max, amplitude)));
    }

    private void remove(List<WeightableField> mutated) {
        if (mutated.size() == 0) {
            return;
        }
        int index = MathUtil.RANDOM.nextInt(mutated.size());
        mutated.remove(index);
    }

    static MUTATE_OPERATION nextMutateOperation() {
        double r = MathUtil.RANDOM.nextDouble();
        if ((r -= DEFAULT_PROBABILITY_INSERT) < 0.0) {
            return MUTATE_OPERATION.INSERT;
        } else if ((r -= DEFAULT_PROBABILITY_MODIFY) < 0.0) {
            return MUTATE_OPERATION.MODIFY;
        } else {
            return MUTATE_OPERATION.REMOVE;
        }
    }
}
