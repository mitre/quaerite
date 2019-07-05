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
package org.mitre.quaerite.core.features.factories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.mitre.quaerite.core.features.ParameterizableString;
import org.mitre.quaerite.core.util.MathUtil;

public class TestParameterizableStringFactory {

    @Test
    public void testBadIndex() {

        String paramString1 = "recip(rord(creationDate),[1,2,3],[10,100,1000],[$3])";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new ParameterizableStringFactory("bf", "0", TestParam.class, paramString1),
                "failed to throw exception");
        assertTrue(e.getMessage().contains("you need to have specified a value for $3"));

        String paramString2 = "recip(rord(creationDate),[$1])";
        e = assertThrows(IllegalArgumentException.class,
                () -> new ParameterizableStringFactory("bf", "0", TestParam.class, paramString2),
                "failed to throw exception");
        assertTrue(e.getMessage().contains("you need to have specified a value for $1"));

        String paramString3 = "recip(rord(creationDate),[1,2,3][$-1])";
        e = assertThrows(IllegalArgumentException.class,
                () -> new ParameterizableStringFactory("bf", "0", TestParam.class, paramString3),
                "failed to throw exception");
        assertTrue(e.getMessage().contains("references are 1-based and must be >= 1"));

    }

    @Test
    public void testRandom() throws Exception {
        String paramString = "recip(rord(creationDate),[1,2,3],[10,100,1000],[$2])";
        ParameterizableStringFactory<TestParam> fact = new ParameterizableStringFactory("bf", "0",
                TestParam.class, paramString);
        Matcher m = Pattern.compile(
                "^recip\\(rord\\(creationDate\\),([-\\.\\d]+),([-\\.\\d]+),([-\\.\\d]+)\\)").matcher("");

        for (int i = 0; i < 100; i++) {
            TestParam t = fact.random();
            m.reset(t.toString());
            if (m.find()) {
                float f1 = getFloat(m, 1);
                float f2 = getFloat(m, 2);
                float f3 = getFloat(m, 3);
                assertTrue(f1 >= 1.0f && f1 <= 3.0f);
                assertTrue(f2 >= 10.0f && f2 <= 1000.0f);
                assertEquals(f2, f3, 0.000001);
            } else {
                fail(
                        String.format(Locale.US, "couldn't find pattern in %s", t.toString())
                );
            }
        }
    }

    @Test
    public void testPermute() throws Exception {
        String paramString = "recip(rord(creationDate),[1,2,3],[10,100,1000],[$2],[1,5,10])";
        ParameterizableStringFactory<TestParam> fact = new ParameterizableStringFactory("bf",
                "0",
                TestParam.class, paramString);
        List<TestParam> permuted = fact.permute(1000);
        assertEquals(27, permuted.size());
        Set<TestParam> set = new HashSet<>();
        set.addAll(permuted);
        assertEquals(27, set.size());

        paramString = "recip(rord(creationDate),[1,2,3])";
        fact = new ParameterizableStringFactory("bf", "0",
                TestParam.class, paramString);
        permuted = fact.permute(1000);
        assertEquals(3, permuted.size());

        paramString = "recip(rord(creationDate),[1])";
        fact = new ParameterizableStringFactory("bf", "0",
                TestParam.class, paramString);
        permuted = fact.permute(1000);
        assertEquals(1, permuted.size());

        paramString = "recip(rord(creationDate))";
        fact = new ParameterizableStringFactory("bf", "0",
                TestParam.class, paramString);
        permuted = fact.permute(1000);
        assertEquals(1, permuted.size());

    }

    @Test
    public void testCrossOver() throws Exception {
        String paramString = "recip(rord(creationDate),[1,2,3],[10,100,1000],[$2])";
        TestParam t1 = new TestParam("bf", "0", "recip(rord(creationDate),2,100,100)");
        TestParam t2 = new TestParam("bf", "0", "recip(rord(creationDate),1,1000,1000)");

        ParameterizableStringFactory<TestParam> fact = new ParameterizableStringFactory("bf",
                "0", TestParam.class, paramString);

        for (int i = 0; i < 100; i++) {
            Pair<TestParam, TestParam> pair = fact.crossover(t1, t2);
            for (TestParam t : new TestParam[]{pair.getLeft(), pair.getRight()}) {
                List<Float> floats = extractFloats(t.toString());
                assertTrue(
                        MathUtil.equals(floats.get(0), 1f, 0.00001f) ||
                                MathUtil.equals(floats.get(0), 2f, 0.00001f)
                );
                assertTrue(
                        MathUtil.equals(floats.get(1), 100f, 0.00001f) ||
                                MathUtil.equals(floats.get(1), 1000f, 0.00001f)
                );
                assertTrue(
                        MathUtil.equals(floats.get(2), 100f, 0.00001f) ||
                                MathUtil.equals(floats.get(2), 1000f, 0.00001f)
                );
            }

        }
    }

    @Test
    public void testMutate() throws Exception {
        String paramString = "recip(rord(creationDate),[1,2,3],[10,100,1000],[$2])";

        TestParam t = new TestParam("bf", "0",
                "recip(rord(creationDate),1,100,100)");

        ParameterizableStringFactory<TestParam> fact = new ParameterizableStringFactory("bf",
                "0", TestParam.class, paramString);

        int diffs = 0;
        for (int i = 0; i < 100; i++) {
            TestParam mutated = fact.mutate(t, 0.8, 0.8);
            float[] vals = mutated.getValues();
            assertTrue(vals[0] >= 1.0f && vals[0] <= 3.0f);
            assertTrue(vals[1] >= 10f && vals[1] <= 1000f);
            if (!MathUtil.equals(1, mutated.getValues()[0], 0.1f)
                    && !MathUtil.equals(100, mutated.getValues()[1], 0.1f)) {
                diffs++;
            }
        }
        assertTrue(diffs > 10);
    }

    @Test
    public void testFormats() throws Exception {
        String paramString = "[-.0000000001]";
        ParameterizableStringFactory<TestParam> fact = new ParameterizableStringFactory("bf",
                "0", TestParam.class, paramString);
        assertEquals("-1.0E-10", fact.random().toString());

        paramString = "[5000.1]";
        fact = new ParameterizableStringFactory("bf", "0", TestParam.class, paramString);
        assertEquals("5000.1", fact.random().toString());

        paramString = "[10.123423]";
        fact = new ParameterizableStringFactory("bf", "0", TestParam.class, paramString);
        assertEquals("10.123", fact.random().toString());

        paramString = "[0.123423]";
        fact = new ParameterizableStringFactory("bf", "0", TestParam.class, paramString);
        assertEquals("0.123", fact.random().toString());

        paramString = "[7.1]";
        fact = new ParameterizableStringFactory("bf", "0", TestParam.class, paramString);
        assertEquals("7.1", fact.random().toString());

    }

    List<Float> extractFloats(String s) {
        Matcher m = Pattern.compile(
                "^recip\\(rord\\(creationDate\\),([-\\.\\d]+),([-\\.\\d]+),([-\\.\\d]+)\\)").matcher(s);
        List<Float> floats = new ArrayList<>();
        if (m.find()) {
            for (int i = 1; i < 4; i++) {
                floats.add(Float.parseFloat(m.group(i)));
            }
        }
        return floats;
    }

    private float getFloat(Matcher m, int index) {
        return Float.parseFloat(m.group(index));
    }

    private static final class TestParam extends ParameterizableString {

        public TestParam(String name, String id, String string) {
            super(name, id, string, null);
        }

        public TestParam(String name, String id, String string, float[] values) {
            super(name, id, string, values);
        }
    }
}
