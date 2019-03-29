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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.mutable.MutableLong;
import org.junit.jupiter.api.Test;

public class TestEquivalenceSet {
    @Test
    public void testSort() throws Exception {
        EquivalenceSet e1 = new EquivalenceSet();
        e1.addTerm("a", 10);
        e1.addTerm("b", 20);

        EquivalenceSet e2 = new EquivalenceSet();
        e2.addTerm("a", 5);
        e2.addTerm("b", 10);

        List<EquivalenceSet> sets = new ArrayList<>();
        sets.add(e1);
        sets.add(e2);

        Collections.shuffle(sets);
        Collections.sort(sets, Collections.reverseOrder());

        assertEquals(new MutableLong(10), sets.get(0).getSortedMap().get("a"));

        e2.addTerm("c", 1);
        Collections.shuffle(sets);
        Collections.sort(sets, Collections.reverseOrder());

        assertEquals(new MutableLong(5), sets.get(0).getSortedMap().get("a"));

        long first = -1;
        for (Map.Entry<String, MutableLong> e : e1.getSortedMap().entrySet()) {
            first = e.getValue().longValue();
            break;
        }
        assertEquals(20, first);
    }


}
