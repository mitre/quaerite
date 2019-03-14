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
package org.mitre.quaerite.core.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class TestMathUtil {

    @Test
    public void testMutate() {
        for (int i = 0; i < 100; i++) {
            float mutated = MathUtil.calcMutatedWeight(2.0f, 0.0f, 10.0f, 1.0f);
            assertTrue(mutated >= 0.0 && mutated <= 10.0);
        }
        for (int i = 0; i < 100; i++) {
            float mutated = MathUtil.calcMutatedWeight(2.0f, 0.0f, 10.0f, 0.8f);
            assertTrue(mutated >= 0.4 && mutated <= 8.4);
        }
        for (int i = 0; i < 100; i++) {
            float mutated = MathUtil.calcMutatedWeight(2.0f, 0.0f, 10.0f, 0.1f);
            assertTrue(mutated >= 1.8 && mutated <= 2.8);
        }

        //test null
        for (int i = 0; i < 100; i++) {
            float mutated = MathUtil.calcMutatedWeight(null, 0.0f, 10.0f, 0.1f);
            assertTrue(mutated >= 4.5 && mutated <= 5.5);
        }

        //test out of bounds below
        for (int i = 0; i < 100; i++) {
            float mutated = MathUtil.calcMutatedWeight(0.0f, 1.0f, 10.0f, 0.1f);
            assertTrue(mutated >= 1.0 && mutated <= 1.9);
        }
    }
}
