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



import org.apache.commons.lang3.tuple.Pair;
import org.mitre.quaerite.core.util.MathUtil;

public class SimpleStringFeature extends StringFeature<SimpleStringFeature> {

    public SimpleStringFeature(String name, String feature) {
        super(name, feature);
    }

    @Override
    public SimpleStringFeature deepCopy() {
        return new SimpleStringFeature(getName(), getFeature());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimpleStringFeature)) return false;
        return super.equals(o);
    }
}
