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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.mitre.quaerite.util.MathUtil;

public class FloatFeature implements Feature<FloatFeature> {
    private final transient DecimalFormat df = new DecimalFormat("#.###",
            DecimalFormatSymbols.getInstance(Locale.US));

    private float v;
    public FloatFeature(float value) {
        this.v = value;
    }

    @Override
    public String toString() {
        return df.format(v);
    }

    @Override
    public Pair<FloatFeature, FloatFeature> crossover(FloatFeature parentB) {
        //order shouldn't matter
        if (MathUtil.RANDOM.nextFloat() > 0.5) {
            return Pair.of(this, parentB);
        } else {
            return Pair.of(parentB, this);
        }
    }

    @Override
    public FloatFeature clone() {
        return new FloatFeature(v);
    }
}
