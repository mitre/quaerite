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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.mitre.quaerite.core.features.Feature;
import org.mitre.quaerite.core.queries.Query;
import org.mitre.quaerite.core.util.MathUtil;

public class QueryFactory<T extends Query> extends AbstractFeatureFactory<T> {

    public static final String NAME = "query";
    Map<String, Method> methodCache = new HashMap<>();
    List<FeatureFactory> factories = new ArrayList<>();
    private final Class clazz;
    public QueryFactory(String name, Class clazz) {
        super(name);
        this.clazz = clazz;
    }


    @Override
    public List<T> permute(int maxSize) {
        List<T> queries = new ArrayList<>();
        T q = newInstance();
        recurseFactory(0, factories, queries, q, maxSize);
        return queries;

    }

    private void recurseFactory(int factoryIndex, List<FeatureFactory> factories, List<T> queries,
                                Query query, int maxSize) {
        if (queries.size() >= maxSize) {
            return;
        }
        if (factoryIndex == factories.size()) {
            queries.add((T)query);
            return;
        }

        FeatureFactory featureFactory = factories.get(factoryIndex);
        for (Object feature : featureFactory.permute(maxSize)) {
            Query cp = (Query)query.deepCopy();
            setFeature(cp, feature);
            recurseFactory(factoryIndex+1, factories, queries, cp, maxSize);
        }
    }


    @Override
    public T random() {
        T q = newInstance();
        for (FeatureFactory factory : factories) {
            Feature f = factory.random();
            setFeature(q, f);
        }
        return (T)q;
    }

    @Override
    public T mutate(T query, double probability, double amplitude) {
        int numMods = (int)Math.ceil(factories.size()*probability);
        List<FeatureFactory> tmp = new ArrayList<>(factories);
        Collections.shuffle(tmp);
        T cp = (T)query.deepCopy();
        for (int i = 0; i < numMods; i++) {
            FeatureFactory fact = factories.get(i);
            fact.mutate(getFeature(cp, (AbstractFeatureFactory)fact), probability, amplitude);
        }
        return cp;
    }

    @Override
    public Pair<T, T> crossover(T parentA, T parentB) {
        T childA = (T)parentA.deepCopy();
        T childB = (T)parentB.deepCopy();
        for (int i = 0; i < factories.size(); i++) {
            FeatureFactory fact = factories.get(i);
            Feature featA = getFeature(childA, (AbstractFeatureFactory)fact);
            Feature featB = getFeature(childB, (AbstractFeatureFactory)fact);
            Pair<Feature, Feature> p = fact.crossover(featA, featB);
            if (MathUtil.RANDOM.nextFloat() < 0.5) {
                setFeature(childA, p.getLeft());
                setFeature(childB, p.getRight());
            } else {
                setFeature(childA, p.getRight());
                setFeature(childB, p.getLeft());
            }
        }
        return Pair.of(childA, childB);
    }

    private List<Method> getFeaturesFromGetters(T parentB) {
        List<Method> ret = new ArrayList<>();
        for (Method m : parentB.getClass().getMethods()) {
            if (m.getName().startsWith("get")) {
                ret.add(m);
            }
        }
        return ret;
    }

    public void add(FeatureFactory factory) {
        factories.add(factory);
    }

    private void setFeature(Query q, Object obj) {
        String className = obj.getClass().getSimpleName();
        String name = "set"+className.substring(0,1).toUpperCase(Locale.US)+className.substring(1);
        Method method = null;
        if (! methodCache.containsKey(name)) {
            try {
                method = q.getClass().getMethod(name, obj.getClass());
            } catch (NoSuchMethodException e) {
                for (Method m : q.getClass().getMethods()) {
                    if (m.getName().equalsIgnoreCase(name)) {
                        try {
                            method = q.getClass().getMethod(m.getName(), obj.getClass());
                            break;
                        } catch (NoSuchMethodException ex) {
                            //
                        }
                    }
                }
            }
        } else {
            method = methodCache.get(name);
        }
        if (method == null) {
            throw new RuntimeException("I regret I couldn't find a method for: "+name);
        }
        try {
            method.invoke(q, obj);
            methodCache.put(name, method);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Feature getFeature(Query q, AbstractFeatureFactory obj) {
        String className = obj.getName();
        String name = "get"+className.substring(0,1).toUpperCase(Locale.US)+className.substring(1);
        Method method = null;
        if (! methodCache.containsKey(name)) {
            try {
                method = q.getClass().getMethod(name);
            } catch (NoSuchMethodException e) {
                for (Method m : q.getClass().getMethods()) {
                    if (m.getName().equalsIgnoreCase(name)) {
                        try {
                            method = q.getClass().getMethod(m.getName());
                            break;
                        } catch (NoSuchMethodException ex) {
                            //
                        }
                    }
                }
            }
        } else {
            method = methodCache.get(name);
        }
        if (method == null) {
            throw new RuntimeException("I regret I couldn't find a method for: "+name);
        }
        Feature ret = null;
        try {
            ret = (Feature)method.invoke(q);
            methodCache.put(name, method);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ret;
    }

    T newInstance() {
        try {
            return (T)clazz.newInstance();
        } catch (InstantiationException|IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
