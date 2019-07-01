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
package org.mitre.quaerite.core.features;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WeightableField {

    public static Float UNSPECIFIED_WEIGHT = null;

    public static float DEFAULT_WEIGHT = 0;
    private static final Pattern WEIGHT_PATTERN =
            Pattern.compile("(.*?)\\^((?:\\d+)(?:\\.\\d+)?)");

    private final String feature;
    private final Float weight;
    private final transient DecimalFormat df = new DecimalFormat("#.#",
            DecimalFormatSymbols.getInstance(Locale.US));

    public WeightableField(String s) {
        Matcher m = WEIGHT_PATTERN.matcher(s);
        if (m.matches()) {
            feature = m.group(1);
            weight = Float.parseFloat(m.group(2));
        } else {
            feature = s;
            weight = UNSPECIFIED_WEIGHT;
        }
    }

    public WeightableField(String feature, float weight) {
        this.feature = feature;
        this.weight = weight;
    }

    @Override
    public String toString() {
        if (weight != null && weight != 1.0f) {
            return feature + "^" + df.format(weight);
        }
        return feature;
    }

    public boolean hasWeight() {
        return weight != null;
    }

    public String getFeature() {
        return feature;
    }

    public Float getWeight() {
        return weight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WeightableField)) return false;
        WeightableField that = (WeightableField) o;
        return Objects.equals(feature, that.feature) &&
                Objects.equals(weight, that.weight);
    }

    @Override
    public int hashCode() {
        return Objects.hash(feature, weight);
    }
}
