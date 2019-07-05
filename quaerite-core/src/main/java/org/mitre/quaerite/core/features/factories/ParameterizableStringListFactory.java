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
import org.mitre.quaerite.core.features.ParameterizableString;
import org.mitre.quaerite.core.features.ParameterizableStringListFeature;
import org.mitre.quaerite.core.util.MathUtil;


public class ParameterizableStringListFactory<T extends ParameterizableStringListFeature>
        extends AbstractFeatureFactory<T> {

    //parameterize these
    private static final float DEFAULT_PROBABILITY_INSERT = 0.5f;
    private static final float DEFAULT_PROBABILITY_MODIFY = 0.5f;

    private enum MUTATE_OPERATION {
        INSERT,
        REMOVE,
        MUTATE
    }

    final List<ParameterizableStringFactory> factories;

    final int maxSetSize;
    final int minSetSize;
    final Constructor<T> constructor;

    private transient Map<String, ParameterizableStringFactory> factoryMap = new HashMap<>();

    public ParameterizableStringListFactory(String name, Class clazz,
                                            List<ParameterizableStringFactory> factories,
                                            int minSetSize, int maxSetSize) {
        super(name);
        if (minSetSize > -1 && maxSetSize > -1 && minSetSize > maxSetSize) {
            throw new IllegalArgumentException("minSetSize (" + minSetSize
                    + ") must be <= maxSetSize (" + maxSetSize + ")");
        }
        if (minSetSize > factories.size()) {
            throw new IllegalArgumentException("minSetSize (" + minSetSize +
                    ") must be <= factories size (" + factories.size() + ")");
        }

        if (maxSetSize > factories.size()) {
            throw new IllegalArgumentException("maxSetSize (" + maxSetSize +
                    ") must be <= factories size (" + factories.size() + ")");
        }

        this.factories = factories;
        this.minSetSize = (minSetSize < 0) ? 0 : minSetSize;
        this.maxSetSize = (maxSetSize < 0) ? this.factories.size() : maxSetSize;

        try {
            constructor = clazz.getConstructor(List.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        for (ParameterizableStringFactory f : factories) {
            factoryMap.put(f.getFactoryId(), f);
        }

    }


    @Override
    public T random() {
        int numFields = MathUtil.RANDOM.nextInt(minSetSize, maxSetSize + 1);

        List<ParameterizableStringFactory> tmp = new ArrayList<>();
        tmp.addAll(factories);
        Collections.shuffle(tmp);
        List<ParameterizableString> ret = new ArrayList<>();
        for (int i = 0; i < numFields; i++) {
            ret.add(tmp.get(i).random());
        }
        return (T)newInstance(ret);
    }

    @Override
    public List<T> permute(int maxSize) {
        Set<T> collector = new HashSet<>();
        T currFeatures = (T)newInstance(Collections.EMPTY_LIST);
        for (int i = 0; i < factories.size(); i++) {
            recurse(i, maxSize, currFeatures, collector);
        }
        return new ArrayList<>(collector);
    }

    private void recurse(int i, int maxSize,
                         ParameterizableStringListFeature currFeatures,
                         Set<T> collector) {

        if (maxSetSize > -1 && currFeatures.size() >= maxSetSize) {
            return;
        }
        if (i >= factories.size()) {
            return;
        }

        if (collector.size() >= maxSize) {
            return;
        }

        T base = (T)newInstance(Collections.EMPTY_LIST);
        if (currFeatures.getParameterizableStrings().size() > 0) {
            base.addAll(currFeatures.getParameterizableStrings());
        }

        ParameterizableStringFactory fact = factories.get(i);
        for (Object obj : fact.permute(maxSize)) {
            ParameterizableString p = (ParameterizableString) obj;
            T tmp = (T) newInstance(base.getParameterizableStrings());
            tmp.add(p);
            if (tmp.getParameterizableStrings().size() >= minSetSize) {
                collector.add(tmp);
            }
            recurse(i + 1, maxSize, tmp, collector);
        }

    }

    @Override
    public T mutate(ParameterizableStringListFeature listFeature,
                                                   double probability, double amplitude) {
        List<ParameterizableString> mutated = new ArrayList<>();

        int numMutations = (int) FastMath.floor(amplitude * factories.size());
        numMutations = (numMutations == 0) ? 1 : numMutations;

        for (int i = 0; i < listFeature.size(); i++) {
            mutated.add(listFeature.get(i));
        }

        for (int i = 0; i < numMutations; i++) {
            MUTATE_OPERATION op = nextMutateOperation();
            switch (op) {
                case INSERT:
                    //do the inverse of insert if mutated is already as full as it can get
                    if (mutated.size() == factoryMap.size() ||
                            (maxSetSize > -1 && mutated.size() >= maxSetSize)) {
                        remove(mutated);
                    } else {
                        insert(mutated, amplitude);
                    }
                    break;
                case MUTATE:
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
        List<ParameterizableString> ret = new ArrayList<>();
        while (mutated.size() < minSetSize) {
            insert(mutated, amplitude);
        }
        for (ParameterizableString s : mutated) {
            ret.add(s);
        }
        return (T)newInstance(ret);
    }

    @Override
    public Pair<T, T> crossover(T parentA, T parentB) {

        Set<String> union = new HashSet<>();
        for (ParameterizableString p : parentA.getParameterizableStrings()) {
            union.add(p.getFactoryId());
        }
        for (ParameterizableString p : parentB.getParameterizableStrings()) {
            union.add(p.getFactoryId());
        }

        //this crossover allows for more from one parent than another
        //we can change if we want exclusive passing of traits
        List<String> uniques = new ArrayList<>(union);
        Collections.shuffle(uniques);
        List<String> childA = new ArrayList<>();
        int numA = MathUtil.RANDOM.nextInt(minSetSize, maxSetSize);
        int sz = numA >= uniques.size() ? uniques.size() : numA;

        for (int i = 0; i < sz; i++) {
            childA.add(uniques.get(i));
        }
        Collections.shuffle(uniques);
        List<String> childB = new ArrayList<>();
        int numB = MathUtil.RANDOM.nextInt(minSetSize, maxSetSize);
        sz = numB >= uniques.size() ? uniques.size() : numB;
        for (int i = 0; i < sz; i++) {
            childB.add(uniques.get(i));
        }

        return Pair.of(
                (T)buildCrossOver(childA, parentA, parentB),
                (T)buildCrossOver(childB, parentA, parentB)
        );
    }

    private T buildCrossOver(List<String> factoryIds, T parentA, T parentB) {
        List<ParameterizableString> result = new ArrayList<>();
        for (String id : factoryIds) {
            ParameterizableString pA = parentA.get(id);
            ParameterizableString pB = parentB.get(id);
            if (pA != null && pB != null) {
                Pair<ParameterizableString, ParameterizableString> pair =
                        factoryMap.get(id).crossover(pA, pB);
                result.add(pair.getLeft());
            } else if (pA == null) {
                result.add(pB);
            } else if (pB == null) {
                result.add(pA);
            } else {
                throw new IllegalArgumentException("Both pA and pB cannot be null!!!");
            }
        }
        return (T)newInstance(result);
    }


    private void modify(List<ParameterizableString> mutated, double amplitude) {
        if (mutated.size() == 0) {
            return;
        }
        int index = MathUtil.RANDOM.nextInt(mutated.size());

        ParameterizableStringFactory fact = factoryMap.get(mutated.get(index).getFactoryId());
        fact.mutate(mutated.get(index), 1.0, amplitude);
    }

    private void insert(List<ParameterizableString> mutated, double amplitude) {
        //if there's an 'insert' operation, what are the candidates
        List<String> newFeatures = new ArrayList<>();
        Set<String> existingFeatures = new HashSet<>();
        for (ParameterizableString f : mutated) {
            existingFeatures.add(f.getFactoryId());
        }
        for (String id : factoryMap.keySet()) {
            if (!existingFeatures.contains(id)) {
                newFeatures.add(id);
            }
        }
        if (newFeatures.size() == 0) {
            return;
        }
        String newId = newFeatures.get(MathUtil.RANDOM.nextInt(newFeatures.size()));
        ParameterizableStringFactory fact = factoryMap.get(newId);
        mutated.add(fact.random());
    }

    private void remove(List<ParameterizableString> mutated) {
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
            return MUTATE_OPERATION.MUTATE;
        } else {
            return MUTATE_OPERATION.REMOVE;
        }
    }

    private T newInstance(List<ParameterizableString> strings) {
        try {
            return (T) constructor.newInstance(strings);
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }
}

