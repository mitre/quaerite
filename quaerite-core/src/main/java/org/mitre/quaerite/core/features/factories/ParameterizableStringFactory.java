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

import java.lang.reflect.Constructor;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.util.FastMath;
import org.mitre.quaerite.core.features.ParameterizableString;
import org.mitre.quaerite.core.util.MathUtil;


public class ParameterizableStringFactory<T extends ParameterizableString>
        extends AbstractFeatureFactory<T> {

    private static final Pattern PARAMETER_PATTERN =
            Pattern.compile("\\[\\s*([$-\\.\\d, ]+)\\s*\\]");

    private static final Pattern INSTANCE_PATTERN =
            Pattern.compile("\\A\\s*([-\\.\\d]+)");

    private final transient DecimalFormat integerFormat = new DecimalFormat("#",
            DecimalFormatSymbols.getInstance(Locale.US));
    private final transient DecimalFormat standardFormat = new DecimalFormat("#.#",
            DecimalFormatSymbols.getInstance(Locale.US));
    private final transient DecimalFormat kindaSmallFormat = new DecimalFormat("#.###",
            DecimalFormatSymbols.getInstance(Locale.US));
    private final transient DecimalFormat scientificFormat = new DecimalFormat("0.0E0",
            DecimalFormatSymbols.getInstance(Locale.US));


    private final String[] stringBits;

    //key = index into valueSets[] to get the float value
    //value = the list of indices for which slots that value should
    //be placed into stringBits
    private final Map<Integer, List<Integer>> parameterIndexMap;
    private final FloatSet[] valueSets;


    final Constructor<T> constructor;
    private final String factoryId;

    public ParameterizableStringFactory(String name, String factoryId, Class clazz, String paramString) {

        super(name);
        try {
            constructor = clazz.getConstructor(String.class, String.class, String.class, float[].class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        int last = 0;
        //this is the number of unique values, used as the key for
        //the parameterIndexMap
        int uniqCounter = 0;
        //when linearized, this is the index where the value will go
        int counter = 0;
        List<FloatSet> floatSetList = new ArrayList<>();
        List<String> stringBitList = new ArrayList<>();
        Map<Integer, List<Integer>> tmpParamIndexMap = new HashMap<>();
        Matcher m = PARAMETER_PATTERN.matcher(paramString);
        while (m.find()) {
            String stringBit = paramString.substring(last, m.start());
            stringBitList.add(stringBit);
            String valueBit = m.group(1);
            if (valueBit.startsWith("$")) {
                int valueIndex = Integer.parseInt(valueBit.substring(1));
                if (valueIndex < 1) {
                    throw new IllegalArgumentException("I'm sorry, but references " +
                            "are 1-based and must be >= 1");
                }
                int zeroBasedIndex = valueIndex - 1;
                List<Integer> indices = tmpParamIndexMap.get(zeroBasedIndex);
                if (indices == null) {
                    throw new IllegalArgumentException(
                            String.format(Locale.US, "I'm sorry, but you " +
                                    "need to have specified a value for $%s", valueIndex));
                }
                indices.add(counter);
            } else {
                FloatSet floatSet = new FloatSet(valueBit);
                floatSetList.add(floatSet);
                List<Integer> indices = tmpParamIndexMap.get(uniqCounter);
                if (indices == null) {
                    indices = new ArrayList<>();
                }
                indices.add(counter);
                tmpParamIndexMap.put(uniqCounter, indices);
                uniqCounter++;
            }
            counter++;
            last = m.end();

        }
        stringBitList.add(paramString.substring(last));
        parameterIndexMap = Collections.unmodifiableMap(tmpParamIndexMap);
        stringBits = stringBitList.toArray(new String[stringBitList.size()]);
        valueSets = new FloatSet[floatSetList.size()];
        for (int i = 0; i < floatSetList.size(); i++) {
            valueSets[i] = floatSetList.get(i);
        }
        this.factoryId = factoryId;
    }


    public String getFactoryId() {
        return factoryId;
    }

    private float[] getValues(ParameterizableString pString) {
        //sanity checks
        assertEqualFactoryIds(this.getFactoryId(), pString.getFactoryId());

        if (pString.hasValues()) {
            assertCorrectValuesLength(pString.getValues());
            return pString.getValues();
        }

        if (! getFactoryId().equals(pString.getFactoryId())) {
            throw new IllegalArgumentException(
                    String.format(Locale.US,
                            "Factory ids must match! %s != %s",
                            getFactoryId(), pString.getFactoryId()));
        }

        String instanceString = pString.toString();
        List<Float> instanceValues = new ArrayList<>();
        int lastIndex = 0;
        for (int i = 0; i < stringBits.length - 1; i++) {
            int offset = instanceString.indexOf(stringBits[i], lastIndex);
            lastIndex = offset + stringBits[i].length();
            if (offset < 0) {
                throw new IllegalArgumentException(
                        String.format(Locale.US,
                                "I regret I could not find \"%s\" after " +
                                " index %s", stringBits[i], lastIndex)
                );
            }
            String rest = instanceString.substring(lastIndex);
            Matcher m = INSTANCE_PATTERN.matcher(rest);
            if (m.find()) {
                String numBit = m.group(1);
                instanceValues.add(Float.parseFloat(numBit));
                lastIndex += m.group(1).length();
            } else {
                throw new IllegalArgumentException(
                        String.format(Locale.US,
                                "Couldn't find a value for slot: %s",
                                i)
                );
            }

        }

        float[] values = new float[valueSets.length];
        for (Map.Entry<Integer, List<Integer>> e : parameterIndexMap.entrySet()) {
            List<Integer> instanceIndices = e.getValue();
            Float firstValue = instanceValues.get(instanceIndices.get(0));
            int firstIndex = instanceIndices.get(0);
            for (int i = 1; i < instanceIndices.size(); i++) {
                int currIndex = instanceIndices.get(i);
                Float otherValue = instanceValues.get(currIndex);
                if (Math.abs(otherValue - firstValue) > 0.0001) {
                    throw new IllegalArgumentException(
                            String.format(Locale.US,
                                    "values at index %s and %s should be the same (or within 0.0001)," +
                                    "but I see %s and %s",
                                    firstIndex, currIndex, firstValue, otherValue
                                    )
                    );
                }
            }
            values[e.getKey()] = firstValue;
        }
        assertCorrectValuesLength(values);
        assertValuesWithinRange(values);
        return values;
    }

    private void assertValuesWithinRange(float[] values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] > valueSets[i].getMax()) {
                throw new IllegalArgumentException(
                        String.format(Locale.US,
                                "value %s must be <= %s",
                                values[i], valueSets[i].getMax()));

            } else if (values[i] < valueSets[i].getMin()) {
                throw new IllegalArgumentException(
                        String.format(Locale.US,
                                "value %s must be > %s",
                                values[i], valueSets[i].getMin()));
            }
        }
    }

    private void assertCorrectValuesLength(float[] values) {
        if (values.length != valueSets.length) {
            throw new IllegalArgumentException(
                    String.format(Locale.US,
                            "values arrays must have same length: %s, %s",
                            values.length, valueSets.length)
            );
        }
    }

    private static void assertEqualFactoryIds(String factoryIdA, String factoryIdB) {
        if (!factoryIdA.equals(factoryIdB)) {
            throw new IllegalArgumentException(
                    String.format(Locale.US,
                            "Must have same factory ids: %s, %s",
                            factoryIdA, factoryIdB));
        }
    }

    @Override
    public T random() {
        float[] newVals = new float[valueSets.length];
        for (int i = 0; i < valueSets.length; i++) {
            newVals[i] = valueSets[i].random();
        }
        return newInstance(newVals);
    }

    @Override
    public List<T> permute(int maxSize) {
        Set<T> collector = new HashSet<>();
        List<Float> currValues = new ArrayList<>();
        recurse(0, maxSize, currValues, collector);
        return new ArrayList<>(collector);
    }

    private void recurse(int i, int maxSize,
                         List<Float> currValues,
                         Set<T> collector) {

        if (collector.size() >= maxSize) {
            return;
        }
        if (currValues.size() == valueSets.length) {
            collector.add(newInstance(currValues));
            return;
        }
        for (Float f : valueSets[i].floats) {
            List<Float> tmp = clone(currValues);
            tmp.add(f);
            recurse(i + 1, maxSize, tmp, collector);

        }
    }

    private List<Float> clone(List<Float> currValues) {
        List<Float> ret = new ArrayList<>();
        for (float f : currValues) {
            ret.add(f);
        }
        return ret;
    }

    @Override
    public T mutate(T parameterizableString, double probability, double amplitude) {
        float[] values = getValues(parameterizableString);
        int numMutations = (int) FastMath.floor(amplitude * values.length);
        numMutations = (numMutations == 0) ? 1 : numMutations;
        for (int i = 0; i < values.length; i++) {
            if (MathUtil.RANDOM.nextDouble() <= probability) {
                values[i] = MathUtil.calcMutatedWeight(values[i],
                        valueSets[i].getMin(), valueSets[i].getMax(), amplitude);
            }
        }
        return newInstance(values);
    }

    @Override
    public Pair<T, T> crossover(T parentA, T parentB) {
        assertEqualFactoryIds(parentA.getFactoryId(), parentB.getFactoryId());
        assertEqualFactoryIds(getFactoryId(), parentA.getFactoryId());
        float[] valsA = getValues(parentA);
        float[] valsB = getValues(parentB);
        if (valsA.length != valsB.length) {
            throw new IllegalArgumentException(
                    String.format(Locale.US,
                            "Can't have diff val lengths: %s %s",
                            valsA.length, valsB.length)
            );
        }

        float[] childA = new float[valsA.length];
        float[] childB = new float[valsB.length];

        for (int i = 0; i < childA.length; i++) {
            //childA could == childB and that is ok
            //this does not guarantee equal distribution of values
            //from the two parents
            if (MathUtil.RANDOM.nextFloat() > 0.5) {
                childA[i] = valsA[i];
            } else {
                childA[i] = valsB[i];
            }
            if (MathUtil.RANDOM.nextFloat() > 0.5) {
                childB[i] = valsA[i];
            } else {
                childB[i] = valsB[i];
            }
        }
        return Pair.of(newInstance(childA), newInstance(childB));
    }

    private void insert(Set<String> mutated, double amplitude) {
        //int i = MathUtil.RANDOM.nextInt(maxSetSize);
        //mutated.add(factories.get(i));
    }

    private void remove(Set<String> mutated) {
        if (mutated.size() == 0) {
            return;
        }
        ArrayList<String> tmp = new ArrayList<>();
        tmp.addAll(mutated);
        Collections.shuffle(tmp);
        int index = MathUtil.RANDOM.nextInt(tmp.size());
        tmp.remove(index);
        mutated.clear();
        mutated.addAll(tmp);
    }

    private T newInstance(List<Float> values) {
        float[] f = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            f[i] = values.get(i);
        }
        return newInstance(f);
    }


    private T newInstance(float[] values) {
        try {
            return constructor.newInstance(getName(), factoryId, toString(values), values);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private String toString(float[] values) {
        StringBuilder sb = new StringBuilder();
        float[] linValues = linearizeValues(values);
        for (int i = 0; i < stringBits.length - 1; i++) {
            sb.append(stringBits[i]);
            sb.append(format(linValues[i]));
        }
        sb.append(stringBits[stringBits.length - 1]);
        return sb.toString();
    }

    private String format(float linValue) {
        float abs = FastMath.abs(linValue);
        float decimalPart = abs - (int)abs;
        if (abs > 10000 || abs < 0.001) {
            return scientificFormat.format(linValue);
        } else if (decimalPart < 0.000000001) {
            return integerFormat.format(linValue);
        } else if (abs < 100 && decimalPart > 0.001) {
            return kindaSmallFormat.format(linValue);
        } else {
            return standardFormat.format(linValue);
        }
    }

    private float[] linearizeValues(float[] values) {
        float[] linValues = new float[stringBits.length - 1];

        for (Map.Entry<Integer, List<Integer>> e : parameterIndexMap.entrySet()) {
            for (Integer stringIndex : e.getValue()) {
                linValues[stringIndex] = values[e.getKey()];
            }
        }
        return linValues;
    }

    private static class FloatSet {
        Set<Float> floats = new HashSet<>();
        float min = Float.MIN_VALUE;
        float max = Float.MAX_VALUE;

        FloatSet(String string) {
            String[] numberStrings = string.trim().split("[ ,]+");
            if (numberStrings.length == 0) {
                throw new IllegalArgumentException("Sorry, but this must have a number >" +
                        string);
            }
            for (String numberString : numberStrings) {
                add(Float.parseFloat(numberString));
            }
        }
        void add(float f) {
            if (floats.size() == 0) {
                min = f;
                max = f;
            } else {
                if (f < min) {
                    min = f;
                }
                if (f > max) {
                    max = f;
                }
            }
            floats.add(f);
        }

        float getMin() {
            return min;
        }

        float getMax() {
            return max;
        }

        float random() {
            return MathUtil.getRandomFloat(min, max);
        }
    }
}
