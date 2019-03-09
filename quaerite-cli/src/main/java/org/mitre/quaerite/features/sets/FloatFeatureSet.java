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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.mitre.quaerite.features.Feature;
import org.mitre.quaerite.features.FloatFeature;
import org.mitre.quaerite.util.MathUtil;

abstract public class FloatFeatureSet implements FeatureSet<FloatFeature> {

    private final Random random = new Random();
    private final float min;
    private final float max;
    List<Float> floats;

    public FloatFeatureSet(List<Float> floats) {
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
    public Set<Feature> random() {
        float f = floats.get(random.nextInt(floats.size()));
        return Collections.singleton(new FloatFeature(f));
    }

    @Override
    public List<Set<Feature>> permute(int maxSize) {
        List<Set<Feature>> ret = new ArrayList<>();
        for (Feature f : getEachDefaultFeature()) {
            ret.add(Collections.singleton(f));
        }
        return ret;
    }


    @Override
    public Set<Feature> getEachDefaultFeature() {
        Set<Feature> ret = new HashSet<>();
        for (float f : floats) {
            ret.add(new FloatFeature(f));
        }
        return ret;
    }

    public List<Float> getFloats() {
        return floats;
    }

    @Override
    public Set<FloatFeature> mutate(Set<FloatFeature> features, double probability, double amplitude) {
        if (floats.size() == 0 || features.size() == 0) {
            return Collections.emptySet();
        }
        if (MathUtil.RANDOM.nextDouble() <= probability) {
            return
                    Collections.singleton(
                            new FloatFeature(MathUtil.calcMutatedWeight(min, max, amplitude)));
        } else {
            return features;
        }
    }
}
