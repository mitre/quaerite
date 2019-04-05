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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.mitre.quaerite.core.queries.EDisMaxQuery;
import org.mitre.quaerite.core.queries.Query;

public class QueryFactory<T extends Query> extends AbstractFeatureFactory<T> {

    Map<String, Method> methodCache = new HashMap<>();
    List<FeatureFactory> factories = new ArrayList<>();
    public QueryFactory(String name) {
        super(name);
    }


    @Override
    public List<T> permute(int maxSize) {
        List<T> queries = new ArrayList<>();
        EDisMaxQuery q = new EDisMaxQuery();
        recurseFactory(0, factories, queries, q, maxSize);
        return queries;

    }

    private void recurseFactory(int factoryIndex, List<FeatureFactory> factories, List<T> queries,
                                Query query, int maxSize) {
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
        throw new IllegalArgumentException("not yet implemented");
    }

    @Override
    public T mutate(T feature, double probability, double amplitude) {
        throw new IllegalArgumentException("not yet implemented");
    }

    @Override
    public Pair<T, T> crossover(T parentA, T parentB) {
        throw new IllegalArgumentException("not yet implemented");
    }

    public void add(FeatureFactory factory) {
        factories.add(factory);
    }

    private T buildQuery(Map<String, Object> params) {
        if (getName().equals("edismax")) {
            EDisMaxQuery q = new EDisMaxQuery();
            for (Map.Entry<String, Object> e : params.entrySet()) {
                setFeature(q, e.getKey());
            }
            return (T)q;
        }
        throw new IllegalArgumentException("only supports edismax for now");
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

}
