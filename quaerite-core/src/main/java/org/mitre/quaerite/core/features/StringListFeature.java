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
import java.util.List;
import java.util.Objects;

public abstract class StringListFeature<T extends StringListFeature> extends AbstractFeature<T> {

    private final List<String> strings;

    public StringListFeature(String name, List<String> features) {
        super(name);
        this.strings = features;
    }
    public int size() {
        return strings.size();
    }
/*
    @Override
    public Pair<T, T> crossover(T parentB) {
        Set<String> union = new HashSet<>();
        union.addAll(strings);
        union.addAll(parentB.getAll());
        List<String> unionList = new ArrayList<>();
        unionList.addAll(union);
        Collections.shuffle(unionList);
        if (minSetSize == maxSetSize) {
            List<String> listA = new ArrayList<>();
            listA.addAll(strings);
            T childA = build(listA, minSetSize, maxSetSize);
            List<String> listB = new ArrayList<>();
            listB.addAll(strings);
            T childB = build(listB, minSetSize, maxSetSize);
            return Pair.of((T)childA, (T)childB);
        } else {
            int numFeaturesA = MathUtil.RANDOM.nextInt(minSetSize, maxSetSize);
            List<String> listA = new ArrayList<>();

            for (int i = 0; i < numFeaturesA; i++) {
                listA.add(unionList.get(i));
            }
            T childA = build(listA, minSetSize, maxSetSize);

            Collections.shuffle(unionList);
            List<String> listB = new ArrayList<>();
            int numFeaturesB = MathUtil.RANDOM.nextInt(minSetSize, maxSetSize);
            for (int i = 0; i < numFeaturesB; i++) {
                listB.add(unionList.get(i));
            }
            T childB = build(listB, minSetSize, maxSetSize);
            return Pair.of(childA, childB);
        }
    }
*/
    @Override
    public T deepCopy() {
        List<String> clone = new ArrayList<>();
        clone.addAll(strings);
        //deep copy
        return build(clone);
    }

    public abstract T build(List<String> strings);

    public List<String> getAll() {
        List<String> ret = new ArrayList<>();
        ret.addAll(strings);
        return ret;
    }

    public String get(int i) {
        return strings.get(i);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StringListFeature)) return false;
        StringListFeature<?> that = (StringListFeature<?>) o;
        return Objects.equals(strings, that.strings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(strings);
    }

    @Override
    public String toString() {
        return "StringListFeature{" +
                "strings=" + strings +
                '}';
    }
}
