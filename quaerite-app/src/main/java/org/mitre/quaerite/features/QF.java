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
import java.util.List;

public class QF implements FeatureSet {
    private static final String QF = "qf";

    List<String> features;
    List<String> fields = new ArrayList<>();
    List<Float> weights = new ArrayList<>();

    public QF(List<String> fields, List<Float> weights) {
        this.fields = fields;
        this.weights = weights;
        init();
    }

    private void init() {
        features = new ArrayList<>();
        features.addAll(fields);
        for (int i = 0; i < fields.size()-1; i++) {
            for (int j = i+1; j < fields.size(); j++) {
                String combo = fields.get(i) + "," + fields.get(j);
                features.add(combo);
            }
        }

//        StringBuilder sb = new StringBuilder();
  //      recurse(0, fields, weights, sb);
    }

    private void recurse(int i, List<String> fields, List<Float> weights, StringBuilder sb) {
        if (i >= fields.size()) {
            return;
        }
        String base = sb.toString();
        for (Float f : weights) {
            StringBuilder feature = new StringBuilder(base);
            if (f > 0.0f) {
                if (feature.length() > 0) {
                    feature.append(",");
                }
                feature.append(fields.get(i)).append("^").append(f);
                features.add(feature.toString());
            }
            recurse(i+1, fields, weights, feature);
        }
    }


    @Override
    public String getParameter() {
        return QF;
    }

    @Override
    public List<String> getFeatures() {
        if (features == null) {
            init();
        }
        if (features.size() == 0) {
            features.add("");
        }
        return features;
    }

    @Override
    public String toString() {
        return "QF{" +
                "features=" + getFeatures() +
                ", fields=" + fields +
                ", weights=" + weights +
                '}';
    }
}
