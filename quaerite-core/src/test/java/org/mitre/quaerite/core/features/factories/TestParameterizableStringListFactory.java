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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.mitre.quaerite.core.features.ParameterizableString;
import org.mitre.quaerite.core.features.ParameterizableStringListFeature;
import org.mitre.quaerite.core.util.MathUtil;

public class TestParameterizableStringListFactory {

    @Test
    public void testRandom() throws Exception {

        String paramString1 = "abc[1,2,3],[4,5,6],[$2])";
        String paramString2 = "def[7,8,9],[10,11,12],[$2])";
        String paramString3 = "ghi[13,14,15],[16,17,18],[$2])";
        List<ParameterizableStringFactory> factories = new ArrayList<>();
        factories.add(new ParameterizableStringFactory("bf", "0",
                TestParam.class, paramString1));
        factories.add(new ParameterizableStringFactory("bf", "0",
                TestParam.class, paramString2));
        factories.add(new ParameterizableStringFactory("bf", "0",
                TestParam.class, paramString3));
        ParameterizableStringListFactory parameterizableStringListFactory =
                new ParameterizableStringListFactory(
                "name", TestParamList.class, factories, 1, 3);

        int min = -1;
        int max = -1;
        for (int i = 0; i < 100; i++) {
            List<ParameterizableString> strings =
                    parameterizableStringListFactory.random().getParameterizableStrings();
            if (i == 0) {
                min = strings.size();
                max = strings.size();
            } else {
                if (strings.size() < min) {
                    min = strings.size();
                }
                if (strings.size() > max) {
                    max = strings.size();
                }
            }
        }
        assertEquals(1, min);
        assertEquals(3, max);
    }


    @Test
    public void testPermute() {

        String paramString1 = "abc[1,2,3],[4,5,6],[$2])";
        String paramString2 = "def[7,8,9],[10,11,12],[$2])";
        String paramString3 = "ghi[13,14,15],[16,17,18],[$2])";
        List<ParameterizableStringFactory> factories = new ArrayList<>();
        factories.add(new ParameterizableStringFactory("bf", "0",
                TestParam.class, paramString1));
        factories.add(new ParameterizableStringFactory("bf", "0",
                TestParam.class, paramString2));
        factories.add(new ParameterizableStringFactory("bf", "0",
                TestParam.class, paramString3));
        ParameterizableStringListFactory parameterizableStringListFactory =
                new ParameterizableStringListFactory(
                "name", TestParamList.class, factories, 1, 3);
        List<ParameterizableStringListFeature> features = parameterizableStringListFactory.permute(10000);
        List<String> strings = new ArrayList<>();
        assertEquals(918, features.size());
    }

    @Test
    public void testCrossOver() {
        String paramString = "recip(rord(creationDate),[1,2,3],[10,100,1000],[$2])";
        TestParam t1 = new TestParam("bf", "0",
                "recip(rord(creationDate),2,100,100)");
        TestParam t2 = new TestParam("bf", "0",
                "recip(rord(creationDate),1,1000,1000)");

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
    public void testMutate() {
        String paramString = "recip(rord(creationDate),[1,2,3],[10,100,1000],[$2])";
        TestParam t = new TestParam("bf", "0",
                "recip(rord(creationDate),2,100,100)");

        ParameterizableStringFactory<TestParam> fact = new ParameterizableStringFactory("bf",
                "0", TestParam.class, paramString);

        int diffs = 0;
        for (int i = 0; i < 100; i++) {
            TestParam mutated = fact.mutate(t, 0.8, 0.8);
            float[] vals = mutated.getValues();
            assertTrue(vals[0] >= 1.0f && vals[0] <= 3.0f);
            assertTrue(vals[1] >= 10f && vals[1] <= 1000f);
            if (!MathUtil.equals(2, mutated.getValues()[0], 0.1f)
                    && !MathUtil.equals(100, mutated.getValues()[1], 0.1f)) {
                diffs++;
            }
        }
        assertTrue(diffs > 10);
    }

    @Test
    public void testFormats() {
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

    private static final class TestParamList extends ParameterizableStringListFeature {

        public TestParamList(List<ParameterizableString> strings) {
            super("tp");
            for (ParameterizableString s : strings) {
                add(s);
            }
        }
    }
}
