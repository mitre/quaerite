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
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.tuple.Pair;
import org.mitre.quaerite.core.features.IntFeature;
import org.mitre.quaerite.core.util.MathUtil;


public class IntFeatureFactory<T extends IntFeature>
        extends AbstractFeatureFactory<T> {

    private final Random random = new Random();
    private final int min;
    private final int max;
    List<Integer> integers;
    private final Class clazz;

    public IntFeatureFactory(Class clazz, List<Integer> integers) {
        super(clazz.getSimpleName());
        this.clazz = clazz;
        this.integers = integers;
        if (integers.size() > 0) {
            int tmpMin = integers.get(0);
            int tmpMax = integers.get(0);
            for (Integer i : integers) {
                if (i < tmpMin) {
                    tmpMin = i;
                }
                if (i > tmpMax) {
                    tmpMax = i;
                }
            }
            min = tmpMin;
            max = tmpMax;
        } else {
            min = Integer.MIN_VALUE;
            max = Integer.MAX_VALUE;
        }
    }

    @Override
    public T random() {
        int i = integers.get(random.nextInt(integers.size()));
        return newInstance(i);
    }



    @Override
    public List<T> permute(int maxSize) {
        List<T> ret = new ArrayList<>();
        for (int i : integers) {
            ret.add(newInstance(i));
        }
        return ret;
    }


    public List<Integer> getIntegers() {
        return integers;
    }

    @Override
    public T mutate(T intFeature, double probability, double amplitude) {
        if (MathUtil.RANDOM.nextDouble() <= probability) {
            return newInstance(MathUtil.calcMutatedWeight(intFeature.getValue(), min, max, amplitude));
        } else {
            return newInstance(intFeature.getValue());
        }
    }

    @Override
    public Pair<T, T> crossover(T parentA, T parentB) {
        if (parentA == null) {
            System.out.println(parentA + " : " + parentB);
        }
        if (MathUtil.RANDOM.nextFloat() > 0.5) {
            return Pair.of(parentB, parentA);
        } else {
            return Pair.of(parentA, parentB);
        }
    }

    private T newInstance(int i) {
        try {
            Constructor cstr = clazz.getConstructor(int.class);
            return (T)cstr.newInstance(i);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public String toString() {
        return "IntFeatureFactory{" +
                "min=" + min +
                ", max=" + max +
                ", integers=" + integers +
                ", clazz=" + clazz +
                '}';
    }
}
