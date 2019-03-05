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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TIE extends WeightableFeatureSet {
    private static final String TIE = "tie";

    public static List<String> FIELD =
            Collections.singletonList("tie");

    public TIE(List<String> fields, List<Float> ties) {
        super(FIELD, ties);
        if (fields == null) {
            //great, expected, do nothing
        } else if (fields.size() > 0) {
            throw new IllegalArgumentException("can't have a field for 'tie'");
        }
    }
    @Override
    public Set<Feature> random() {
        float f = defaultWeights.get(random.nextInt(defaultWeights.size()));
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
        for (float f : defaultWeights) {
            ret.add(new FloatFeature(f));
        }
        return ret;
    }

    @Override
    public String getParameter() {
        return TIE;
    }
}
