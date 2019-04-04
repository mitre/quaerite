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
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.tuple.Pair;
import org.mitre.quaerite.core.features.FloatFeature;
import org.mitre.quaerite.core.util.MathUtil;


public class FloatFeatureFactory<T extends FloatFeature>
        extends AbstractFeatureFactory<T> {

    private final Random random = new Random();
    private final float min;
    private final float max;
    List<Float> floats;

    public FloatFeatureFactory(String name, List<Float> floats) {
        super(name);
        this.floats = floats;
        if (floats.size() > 0) {
            float tmpMin = floats.get(0);
            float tmpMax = floats.get(0);
            for (Float f : floats) {
                if (f < tmpMin) {
                    tmpMin = f;
                }
                if (f > tmpMax) {
                    tmpMax = f;
                }
            }
            min = tmpMin;
            max = tmpMax;
        } else {
            min = Float.NaN;
            max = Float.NaN;
        }
    }

    @Override
    public T random() {
        float f = floats.get(random.nextInt(floats.size()));
        return (T)new FloatFeature(getName(), f);
    }

    @Override
    public List<T> permute(int maxSize) {
        List<T> ret = new ArrayList<>();
        for (float f : floats) {
            ret.add((T)new FloatFeature(getName(), f));
        }
        return ret;
    }


    public List<Float> getFloats() {
        return floats;
    }

    @Override
    public T mutate(T floatFeature, double probability, double amplitude) {
        if (MathUtil.RANDOM.nextDouble() <= probability) {
            return (T)new FloatFeature(
                    floatFeature.getName(), MathUtil.calcMutatedWeight(floatFeature.getValue(), min, max, amplitude));
        } else {
            return floatFeature;
        }
    }

    @Override
    public Pair<T, T> crossover(T parentA, T parentB) {
        if (MathUtil.RANDOM.nextFloat() > 0.5) {
            return Pair.of(parentB, parentA);
        } else {
            return Pair.of(parentA, parentB);
        }
    }
}
