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
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.util.FastMath;
import org.mitre.quaerite.core.features.StringListFeature;
import org.mitre.quaerite.core.features.WeightableField;
import org.mitre.quaerite.core.features.WeightableListFeature;
import org.mitre.quaerite.core.util.MathUtil;


public class StringListFeatureFactory<T extends StringListFeature>
        extends AbstractFeatureFactory<T> {
    private static final float DEFAULT_MIN = 0.0f;
    private static final float DEFAULT_MAX = 1.0f;

    //parameterize these
    private static final float DEFAULT_PROBABILITY_INSERT = 0.5f;
    private static final float DEFAULT_PROBABILITY_MODIFY = 0.5f;

    private enum MUTATE_OPERATION {
        INSERT,
        REMOVE
    }


    final List<String> features;

    final int maxSetSize;
    final int minSetSize;
    final Constructor<T> constructor;

    public StringListFeatureFactory(String name, Class clazz, List<String> features,
                                    int minSetSize, int maxSetSize) throws Exception {
        super(name);
        if (minSetSize > -1 && maxSetSize > -1 && minSetSize > maxSetSize) {
            throw new IllegalArgumentException("minSetSize ("+minSetSize
                    +") must be > maxSetSize ("+maxSetSize+")");
        }
        if (minSetSize > features.size()) {
            throw new IllegalArgumentException("minSetSize ("+minSetSize+
                    ") must be <= factories size ("+features.size()+")" );
        }

        if (maxSetSize > features.size()) {
            throw new IllegalArgumentException("maxSetSize ("+maxSetSize+
                    ") must be <= factories size ("+features.size()+")" );
        }

        this.features = features;
        this.minSetSize = (minSetSize < 0) ? 0 : minSetSize;
        this.maxSetSize = (maxSetSize < 0) ? features.size() : maxSetSize;

        constructor = clazz.getConstructor(List.class, int.class, int.class);

    }

    public StringListFeature getFeatures() {
        return newInstance(features, minSetSize, maxSetSize);
    }

    @Override
    public T random() {
        int numFields = (minSetSize == maxSetSize) ? minSetSize :
            minSetSize + MathUtil.RANDOM.nextInt(maxSetSize-minSetSize);

        List<String> tmp = new ArrayList<>();
        tmp.addAll(features);
        Collections.shuffle(tmp);
        List<String> ret = new ArrayList<>();
        for (int i = 0; i < numFields; i++) {
            ret.add(tmp.get(i));
        }
        return newInstance(ret, minSetSize, maxSetSize);
    }

    @Override
    public List<T> permute(int maxSize) {
        List<T> collector = new ArrayList<>();
        List<String> currFeatures = new ArrayList<>();
        recurse(0, 0, maxSize, currFeatures, collector);
        return collector;
    }

    private void recurse(int i, int depth, int maxSize,
                         List<String> currFeatures,
                         List<T> collector) {

        if (maxSetSize > -1 && currFeatures.size() >= maxSetSize) {
            return;
        }
        if (i >= features.size()) {
            return;
        }

        if (collector.size() >= maxSize) {
            return;
        }
        List<String> base = new ArrayList<>();
        if (currFeatures.size() > 0) {
            base.addAll(currFeatures);
        }

        base.add(features.get(i));
        if (base.size() >= minSetSize) {
            collector.add(newInstance(base, minSetSize, maxSize));
        }
        recurse(i + 1, depth+1, maxSize, base, collector);
    }

    @Override
    public T mutate(T stringListFeature, double probability, double amplitude) {
        Set<String> mutated = new HashSet<>();

        int numMutations = (int) FastMath.floor(amplitude * features.size());
        numMutations = (numMutations == 0) ? 1 : numMutations;

        mutated.addAll(stringListFeature.getAll());
        for (int i = 0; i < numMutations; i++) {
            MUTATE_OPERATION op = nextMutateOperation();
            switch (op) {
                case INSERT:
                    //do the inverse of insert if mutated is already as full as it can get
                    if (mutated.size() >= maxSetSize) {
                        remove(mutated);
                    } else {
                        insert(mutated, amplitude);
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
        List<String> ret = new ArrayList<>();
        while (mutated.size() < minSetSize) {
            insert(mutated, amplitude);
        }
        for (String s : mutated) {
            ret.add(s);
        }
        return newInstance(ret, minSetSize, maxSetSize);
    }

    private void insert(Set<String> mutated, double amplitude) {
        int i = MathUtil.RANDOM.nextInt(maxSetSize);
        mutated.add(features.get(i));
    }

    private void remove(Set<String> mutated) {
        if (mutated.size() == 0) {
            return;
        }
        ArrayList<String> tmp = new ArrayList<>();
        tmp.addAll(mutated);
        Collections.shuffle(tmp);
        int index = MathUtil.RANDOM.nextInt(tmp.size());
        tmp.remove(index);
        mutated.clear();
        mutated.addAll(tmp);
    }

    static MUTATE_OPERATION nextMutateOperation() {
        double r = MathUtil.RANDOM.nextDouble();
        if ((r -= DEFAULT_PROBABILITY_INSERT) < 0.0) {
            return MUTATE_OPERATION.INSERT;
        } else {
            return MUTATE_OPERATION.REMOVE;
        }
    }

    private T newInstance(List<String> list, int minSetSize, int maxSetSize) {
        try {
            return constructor.newInstance(list, minSetSize, maxSetSize);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
