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

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
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
    final int minSetSize;
    final int maxSetSize;
    final Class clazz;

    public WeightableListFeatureFactory(String name, Class clazz,
                                        List<String> fields,
                                        List<Float> defaultWeights,
                                        int minSetSize, int maxSetSize) {
        super(name);
        this.clazz = clazz;
        this.minSetSize = minSetSize;
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
        T ret = newInstance(getName());

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
        T ret = (T)newInstance(getName());
        if (maxSetSize > -1) {
            List<WeightableField> tmp = new ArrayList<>();
            tmp.addAll(features.getWeightableFields());
            Collections.shuffle(tmp, MathUtil.RANDOM);
            int numFeatures = MathUtil.RANDOM.nextInt(minSetSize,
                    Math.min(tmp.size(), maxSetSize) + 1);
            for (int i = 0; i < numFeatures; i++) {
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
        Set<T> collector = new HashSet<>();
        WeightableListFeature currFeatures = newInstance(getName());
        recurse(0, 0, maxSize, currFeatures, collector);
        return new ArrayList<>(collector);
    }

    private void recurse(int i, int depth, int maxSize,
                         WeightableListFeature currFeatures,
                         Set<T> collector) {

        if (maxSetSize > -1 && currFeatures.size() >= maxSetSize) {
            return;
        }
        if (i >= fields.size()) {
            return;
        }

        if (collector.size() >= maxSize) {
            return;
        }

        T base = newInstance(getName());
        if (currFeatures.getWeightableFields().size() > 0) {
            base.addAll(currFeatures.getWeightableFields());
        }

        if (features.get(i).hasWeight()) {
            base.add(features.get(i));
            if (base.size() >= minSetSize) {
                collector.add((T) base);
            }
            recurse(i + 1, depth + 1, maxSize, base, collector);
        } else {
            int newDepth = depth + 1;
            for (Float f : defaultWeights) {
                if (f > 0.0f) {
                    T tmp = (T)newInstance(getName());
                    tmp.addAll(base.getWeightableFields());
                    tmp.add(
                            new WeightableField(features.get(i).getFeature(), f));
                    if (tmp.size() >= minSetSize) {
                        collector.add(tmp);
                    }
                    recurse(i + 1, newDepth, maxSize, tmp, collector);
                } else {
                    recurse(i + 1, depth, maxSize, base, collector);
                }
            }
        }
    }

    private T newInstance(String name) {
        Constructor constructor = null;
        try {
            constructor = clazz.getConstructor();
            return (T)constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException();
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
                    if (mutated.size() < minSetSize) {
                        insert(mutated, amplitude);
                    } else {
                        modify(mutated, amplitude);
                    }
                    break;
                case REMOVE:
                    //do the inverse if nothing can be removed
                    if (mutated.size() < minSetSize) {
                        insert(mutated, amplitude);
                    } else {
                        remove(mutated);
                    }
                    break;
            }

        }
        //make sure there's at least one value
        while (mutated.size() < minSetSize) {
            insert(mutated, amplitude);
        }
        T ret = newInstance(getName());
        ret.addAll(mutated);
        return ret;
    }

    @Override
    public Pair<T, T> crossover(T parentA, T parentB) {
        T childA = newInstance(parentA.getName());
        T childB = newInstance(parentB.getName());
        Map<String, List<WeightableField>> union = new HashMap<>();
        for (WeightableField f : parentA.getWeightableFields()) {
            List<WeightableField> fields = union.get(f.getFeature());
            if (fields == null) {
                fields = new ArrayList<>();
            }
            fields.add(f);
            union.put(f.getFeature(), fields);
        }
        for (WeightableField f : parentB.getWeightableFields()) {
            List<WeightableField> fields = union.get(f.getFeature());
            if (fields == null) {
                fields = new ArrayList<>();
            }
            fields.add(f);
            union.put(f.getFeature(), fields);
        }
        List<String> keys = new ArrayList<>(union.keySet());
        Collections.shuffle(keys);
        for (int i = 0; i < parentA.getWeightableFields().size() && i < keys.size(); i++) {
            String k = keys.get(i);
            List<WeightableField> fields = union.get(k);
            int index = MathUtil.RANDOM.nextInt(0, fields.size());
            childA.add(fields.get(index));
        }
        Collections.shuffle(keys);
        for (int i = 0; i < parentB.getWeightableFields().size() && i < keys.size(); i++) {
            String k = keys.get(i);
            List<WeightableField> fields = union.get(k);
            int index = MathUtil.RANDOM.nextInt(0, fields.size());
            childB.add(fields.get(index));
        }
        return Pair.of(childA, childB);
    }

    private void modify(List<WeightableField> mutated, double amplitude) {
        if (mutated.size() == 0) {
            return;
        }
        int index = MathUtil.RANDOM.nextInt(mutated.size());
        WeightableField existing = mutated.remove(index);

        float weight = MathUtil.calcMutatedWeight(existing.getWeight(), min, max, amplitude);
        if (! MathUtil.equals(weight, 0.0f, 0.01f)) {
            WeightableField mutatedFeature =
                    new WeightableField(existing.getFeature(), weight);
            mutated.add(mutatedFeature);
        }
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
        float mid = (float)((double)min / (double)max);
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
