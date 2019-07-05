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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ParameterizableStringListFeature extends AbstractFeature<ParameterizableStringListFeature> {

    List<ParameterizableString> parameterizableStrings;
    private transient Map<String, ParameterizableString> idMap = new HashMap<>();

    public ParameterizableStringListFeature(String name) {
        super(name);
        parameterizableStrings = new ArrayList<>();
    }

    public void add(ParameterizableString parameterizableString) {
        parameterizableStrings.add(parameterizableString);
        idMap.put(parameterizableString.getFactoryId(), parameterizableString);
    }

    public void addAll(Collection<ParameterizableString> all) {
        for (ParameterizableString p : all) {
            add(p.deepCopy());
        }
    }

    public List<ParameterizableString> getParameterizableStrings() {
        return parameterizableStrings;
    }

    public int size() {
        return parameterizableStrings.size();
    }

    public ParameterizableString get(String id) {
        return idMap.get(id);
    }

    public ParameterizableString get(int i) {
        return parameterizableStrings.get(i);
    }

    @Override
    public ParameterizableStringListFeature deepCopy() {
        //deep copy
        ParameterizableStringListFeature clone = new ParameterizableStringListFeature(getName());
        for (ParameterizableString parameterizableString : parameterizableStrings) {
            clone.add(parameterizableString.deepCopy());
        }
        return clone;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParameterizableStringListFeature)) return false;
        ParameterizableStringListFeature that = (ParameterizableStringListFeature) o;
        return Objects.equals(parameterizableStrings, that.parameterizableStrings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterizableStrings);
    }

    @Override
    public String toString() {
        return "ParameterizableStringListFeature{" +
                "parameterizableStrings=" + parameterizableStrings +
                '}';
    }
}
