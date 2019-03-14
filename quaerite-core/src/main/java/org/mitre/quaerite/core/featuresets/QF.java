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

import java.util.List;

import org.mitre.quaerite.core.features.WeightableField;

public class QF extends WeightableFeatureSet<WeightableField> {

    private static final String QF = "qf";

    public QF(List<String> fields, List<Float> defaultWeights) {
        super(fields, defaultWeights);
    }

    @Override
    public String getParameter() {
        return QF;
    }

    @Override
    public String toString() {

        return "QF{" +
                "featuresets=" + features +
                ", defaultWeights=" + defaultWeights +
                ", fields=" + fields +
                ", min=" + min +
                ", max=" + max +
                '}';
    }
}
