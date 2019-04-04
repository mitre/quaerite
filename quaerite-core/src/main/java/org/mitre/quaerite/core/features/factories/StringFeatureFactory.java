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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.tuple.Pair;
import org.mitre.quaerite.core.features.StringFeature;
import org.mitre.quaerite.core.util.MathUtil;

/**
 * This implements a basic set of options, where only one
 * option is expected at a time, e.g. server url.
 * Note that permute returns just the list
 */
public class StringFeatureFactory<T extends StringFeature>
        extends AbstractFeatureFactory<T> {
    Random random = new Random();
    final List<StringFeature> features;

    public StringFeatureFactory(String name, Class<?extends StringFeature> clazz, List<String> strings) {
        super(name);
        features = new ArrayList<>();
        for (String s : strings) {
            try {
                features.add(clazz.getConstructor(String.class).newInstance(s));
            } catch (Exception e) {
                throw new IllegalArgumentException("Can't build "+clazz, e);
            }
        }
    }


    protected StringFeatureFactory(String name, List<StringFeature> features) {
        super(name);
        this.features = features;
    }

    @Override
    public List<T> permute(int maxSize) {
        List<T> ret = new ArrayList<>();
        for (StringFeature feature : features) {
            ret.add((T)feature);
            if (ret.size() >= maxSize) {
                return ret;
            }
        }
        return ret;
    }

    @Override
    public T random() {
        int i = random.nextInt(features.size());
        return (T)features.get(i);
    }

    @Override
    public T mutate(T stringFeature, double probability, double amplitude) {
        if (MathUtil.RANDOM.nextDouble() < probability) {
            int i = random.nextInt(features.size());
            return (T)features.get(i);
        }
        return stringFeature;
    }

    @Override
    public Pair<T, T> crossover(T parentA, T parentB) {
        return null;
    }


    public List<StringFeature> getStrings() {
        return features;
    }


}
