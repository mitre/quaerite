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
package org.mitre.quaerite.analysis;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.mutable.MutableLong;
import org.mitre.quaerite.core.util.MapUtil;

public class EquivalenceSet implements Comparable<EquivalenceSet> {
    private long total = 0;
    private Map<String, MutableLong> counts = new HashMap<>();


    public void addTerm(String term, long count) {
        if (counts.containsKey(term)) {
            counts.get(term).add(count);
        } else {
            counts.put(term, new MutableLong(count));
        }
        total += count;
    }

    public Map<String, MutableLong> getSortedMap() {
        return MapUtil.sortByDescendingValue(counts);
    }

    public Map<String, MutableLong> getMap() {
        return counts;
    }

    public long getTotalCount() {
        return total;
    }

    @Override
    public int compareTo(EquivalenceSet o) {
        if (this.counts.size() == o.counts.size()) {
            return Long.compare(this.total, o.total);
        }
        return Integer.compare(this.counts.size(), o.counts.size());
    }

    @Override
    public String toString() {
        return "EquivalenceSet{" +
                "total=" + total +
                ", counts=" + counts +
                '}';
    }
}
