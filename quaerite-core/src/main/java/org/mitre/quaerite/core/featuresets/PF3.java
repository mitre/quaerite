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
import java.util.Collections;
import java.util.List;


public class PF3 extends WeightableFeatureSet {

    public static PF3 EMPTY = new PF3(Collections.emptyList(), Collections.emptyList());

    private static final String PF = "pf3";

    List<String> features;
    List<String> fields = new ArrayList<>();
    List<Float> weights = new ArrayList<>();

    public PF3(List<String> fields, List<Float> weights) {
        super(fields, weights);
    }
    @Override
    public String toString() {
        return "PF3{" +
                "featuresets=" + getFeatures() +
                ", fields=" + fields +
                ", weights=" + weights +
                '}';
    }

    @Override
    public String getParameter() {
        return PF;
    }
}
