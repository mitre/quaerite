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
package org.mitre.quaerite.core.featuresets;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.mitre.quaerite.core.features.Feature;
import org.mitre.quaerite.core.features.FloatFeature;
import org.mitre.quaerite.core.util.MathUtil;


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
    public Feature random() {
        float f = floats.get(random.nextInt(floats.size()));
        return new FloatFeature(f);
    }

    @Override
    public List<Feature> permute(int maxSize) {
        List<Feature> ret = new ArrayList<>();
        for (float f : floats) {
            ret.add(new FloatFeature(f));
        }
        return ret;
    }


    public List<Float> getFloats() {
        return floats;
    }

    @Override
    public FloatFeature mutate(FloatFeature floatFeature, double probability, double amplitude) {
        if (MathUtil.RANDOM.nextDouble() <= probability) {
            return new FloatFeature(MathUtil.calcMutatedWeight(floatFeature.getValue(), min, max, amplitude));
        } else {
            return floatFeature;
        }
    }
}
