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
package org.mitre.quaerite.core.serializers;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.mitre.quaerite.core.features.Boost;
import org.mitre.quaerite.core.features.CustomHandler;
import org.mitre.quaerite.core.features.FloatFeature;
import org.mitre.quaerite.core.features.Fuzziness;
import org.mitre.quaerite.core.features.IntFeature;
import org.mitre.quaerite.core.features.MultiMatchType;
import org.mitre.quaerite.core.features.NegativeBoost;
import org.mitre.quaerite.core.features.PS2;
import org.mitre.quaerite.core.features.QueryOperator;
import org.mitre.quaerite.core.features.StringListFeature;
import org.mitre.quaerite.core.features.StringFeature;
import org.mitre.quaerite.core.features.WeightableListFeature;
import org.mitre.quaerite.core.features.factories.BoostingQueryFactory;
import org.mitre.quaerite.core.features.factories.CustomHandlerFactory;
import org.mitre.quaerite.core.features.factories.FeatureFactories;
import org.mitre.quaerite.core.features.factories.FeatureFactory;
import org.mitre.quaerite.core.features.factories.FloatFeatureFactory;
import org.mitre.quaerite.core.features.factories.IntFeatureFactory;
import org.mitre.quaerite.core.features.factories.QueryFactory;
import org.mitre.quaerite.core.features.factories.QueryOperatorFeatureFactory;
import org.mitre.quaerite.core.features.factories.StringListFeatureFactory;
import org.mitre.quaerite.core.features.factories.StringFeatureFactory;
import org.mitre.quaerite.core.features.factories.WeightableListFeatureFactory;
import org.mitre.quaerite.core.queries.BoostingQuery;
import org.mitre.quaerite.core.queries.DisMaxQuery;
import org.mitre.quaerite.core.queries.EDisMaxQuery;
import org.mitre.quaerite.core.queries.MultiFieldQuery;
import org.mitre.quaerite.core.queries.MultiMatchQuery;
import org.mitre.quaerite.core.queries.Query;
import org.mitre.quaerite.core.util.JsonUtil;


public class FeatureFactorySerializer extends AbstractFeatureSerializer
        implements JsonSerializer<FeatureFactories>, JsonDeserializer<FeatureFactories> {

    //    static Type FIELD_TYPES = new TypeToken<ArrayList<String>>(){}.getType();
    static String FIELDS_KEY = "fields";
    static String VALUES_KEY = "values";
    static String DEFAULT_WEIGHT_KEY = "defaultWeights";
    static String MIN_SET_SIZE_KEY = "minSetSize";
    static String MAX_SET_SIZE_KEY = "maxSetSize";
    private static final Pattern PERCENT_MATCHER = Pattern.compile("(-?\\d+(\\.\\d+)?)%");

    @Override
    public FeatureFactories deserialize(JsonElement jsonElement, Type type,
                                        JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        Map<String, FeatureFactory> featureSetMap = new HashMap<>();
        for (String name : jsonObject.keySet()) {
            featureSetMap.put(name, buildFeatureFactory(name, jsonObject.get(name)));
        }
        return new FeatureFactories(featureSetMap);
    }

    private FeatureFactory buildFeatureFactory(String paramName, JsonElement jsonFeatureFactory) {
        Class clazz = determineClass(paramName);
        if (WeightableListFeature.class.isAssignableFrom(clazz)) {
            JsonObject featureSetObj = (JsonObject)jsonFeatureFactory;
            return buildWeightableFeatureFactory(paramName, featureSetObj);
        } else if (FloatFeature.class.isAssignableFrom(clazz)) {
            return buildFloatFeatureFactory(paramName, jsonFeatureFactory);
        } else if (IntFeature.class.isAssignableFrom(clazz)) {
            return buildIntFeatureFactory(paramName, jsonFeatureFactory);
        } else if (StringFeature.class.isAssignableFrom(clazz)) {
            return buildStringFeatureFactory(paramName, jsonFeatureFactory);
        } else if (StringListFeature.class.isAssignableFrom(clazz)) {
            return buildStringListFeatureFactory(paramName, jsonFeatureFactory);
        } else if (Query.class.isAssignableFrom(clazz)) {
            return createQueryFactory((JsonObject)jsonFeatureFactory);
        } else if (CustomHandler.class.isAssignableFrom(clazz)) {
            return buildCustomHandlerFactory(jsonFeatureFactory.getAsJsonObject());
        } else {
            throw new IllegalArgumentException("Sorry, I can't yet handle: " + paramName);
        }
    }


    private FeatureFactory buildCustomHandlerFactory(JsonObject obj) {
        CustomHandlerFactory customHandlerFactory = new CustomHandlerFactory();
        for (String handler : obj.keySet()) {
            JsonObject child = obj.get(handler).getAsJsonObject();
            String customQueryKey = null;
            if (child.has(CustomHandlerFactory.CUSTOM_QUERY_KEY)) {
                customQueryKey = child.get(CustomHandlerFactory.CUSTOM_QUERY_KEY).getAsString();
            }
            customHandlerFactory.add(new CustomHandler(handler, customQueryKey));
        }
        return  customHandlerFactory;
    }


    private QueryFactory createQueryFactory(JsonObject qRoot) {
        String name = JsonUtil.getSingleChildName(qRoot);
        JsonObject childRoot = qRoot.get(name).getAsJsonObject();
        if (name.equals("edismax")) {
            return buildEDisMaxFactory(childRoot);
        } else if (name.equals("dismax")) {
            return buildDisMaxFactory(childRoot);
        } else if (name.equals("multi_match")) {
            return buildMultiMatchFactory(childRoot);
        } else if (name.equals("boosting")) {
            return buildBoostingFactory(childRoot);
        }else {
            //TODO: add boolean query
            throw new IllegalArgumentException("I regret I don't yet support: "+name);
        }
    }

    private QueryFactory buildBoostingFactory(JsonObject childRoot) {
        QueryFactory positive = createQueryFactory(childRoot.getAsJsonObject("positive"));
        QueryFactory negative = createQueryFactory(childRoot.getAsJsonObject("negative"));

        FloatFeatureFactory<NegativeBoost> negBoostFactory =
                    new FloatFeatureFactory<>(NegativeBoost.class, toFloatList(childRoot.get("negativeBoost")));
        return new BoostingQueryFactory(positive, negative, negBoostFactory);
    }

    private QueryFactory buildMultiMatchFactory(JsonObject childRoot) {
        QueryFactory<MultiMatchQuery> factory = new QueryFactory<>("multi_match", MultiMatchQuery.class);
        addMultiFieldFeatures(factory, childRoot);
        List<String> types = toStringList(childRoot.get("type"));
        if (types.size() == 0) {
            throw new IllegalArgumentException("Must specify at least one type for a multi_match: 'best_match', etc.");
        }

        StringFeatureFactory<MultiMatchType> typeFactory =
                new StringFeatureFactory<>("multiMatchType", MultiMatchType.class, types);
        factory.add(typeFactory);
        if (childRoot.has("boost")) {
            FloatFeatureFactory<Boost> boostFactory =
                    new FloatFeatureFactory<>(Boost.class, toFloatList(childRoot.get("boost")));
            factory.add(boostFactory);
        }
        if (childRoot.has("fuzziness")) {
            FloatFeatureFactory<Fuzziness> fuzzFactory =
                    new FloatFeatureFactory<>(Fuzziness.class, toFloatList(childRoot.get("fuzziness")));
            factory.add(fuzzFactory);
        }
        return factory;
    }

    private QueryFactory buildDisMaxFactory(JsonObject object) {
        QueryFactory<DisMaxQuery> factory = new QueryFactory<>("dismax", DisMaxQuery.class);
        addDismaxFeatures(factory, object);
        return factory;
    }

    private QueryFactory buildEDisMaxFactory(JsonObject obj) {
        //TODO -- PICK UP HERE
        QueryFactory<EDisMaxQuery> factory = new  QueryFactory<>("edismax", EDisMaxQuery.class);
        if (obj.has("pf2")) {
            factory.add(buildFeatureFactory("pf2", obj.get("pf2")));
        }
        if (obj.has("pf3")) {
            factory.add(buildFeatureFactory("pf3", obj.get("pf3")));
        }
        if (obj.has("ps2")) {
            factory.add(buildFeatureFactory("ps2", obj.get("ps2")));
        }
        if (obj.has("ps3")) {
            factory.add(buildFeatureFactory("ps3", obj.get("ps3")));
        }
        addDismaxFeatures(factory, obj);
        return factory;
    }

    private void addDismaxFeatures(QueryFactory<? extends DisMaxQuery> factory, JsonObject obj) {
        if (obj.has("pf")) {
            factory.add(buildFeatureFactory("pf", obj.get("pf")));
        }
        if (obj.has("ps")) {
            factory.add(buildFeatureFactory("ps", obj.get("ps")));
        }
        if (obj.has("bq")) {
            factory.add(buildFeatureFactory("bq", obj.get("bq")));
        }
        if (obj.has("bf")) {
            factory.add(buildFeatureFactory("bf", obj.get("bf")));
        }
        addMultiFieldFeatures(factory, obj);
    }

    private void addMultiFieldFeatures(QueryFactory<? extends MultiFieldQuery> factory, JsonObject obj) {
        factory.add(buildWeightableFeatureFactory("qf", obj.get("qf").getAsJsonObject()));
        if (obj.has("tie")) {
            factory.add(buildFloatFeatureFactory("tie", obj.get("tie")));
        }
        if (obj.has("q.op")) {
            factory.add(buildQueryOperatorFeatureFactory(obj.get("q.op").getAsJsonObject()));
        }
    }

    private FeatureFactory buildQueryOperatorFeatureFactory(JsonObject jsonObj) {
        List<Integer> integers = null;
        List<Float> floats = null;
        if (jsonObj.has("mmInts")) {
            integers = new ArrayList<>();
            for (JsonElement el : ((JsonArray)jsonObj.get("mmInts"))) {
                integers.add(el.getAsInt());
            }
        }
        if (jsonObj.has("mmFloats")) {
            floats = new ArrayList<>();
            for (JsonElement el : ((JsonArray)jsonObj.get("mmFloats"))) {
                String mmString = el.getAsString();
                Matcher percentMatcher = PERCENT_MATCHER.matcher(mmString);
                if (percentMatcher.reset(mmString).find()) {
                    float f = Float.parseFloat(percentMatcher.group(1)) / 100.0f;
                    floats.add(f);
                } else {
                    floats.add(Float.parseFloat(mmString));
                }
            }
        }
        Set<QueryOperator.OPERATOR> operators = new HashSet<>();
        if (jsonObj.has("operators")) {
            for (JsonElement el : jsonObj.get("operators").getAsJsonArray()) {
                if (el.getAsString().equals("or")) {
                    operators.add(QueryOperator.OPERATOR.OR);
                } else if (el.getAsString().equals("and")) {
                    operators.add(QueryOperator.OPERATOR.AND);
                } else {
                    throw new IllegalArgumentException(
                            "I'm sorry, but I was expecting 'and' or 'or'. "+
                                    "I don't understand: "+el
                    );
                }
            }
        } else if (floats != null || integers != null) {
            operators.add(QueryOperator.OPERATOR.OR);
        }
        return new QueryOperatorFeatureFactory(operators, integers, floats);

    }

    private FeatureFactory buildStringListFeatureFactory(String paramName, JsonElement jsonFeatureFactory) {
        List<String> fields = null;
        int minSetSize = -1;
        int maxSetSize = -1;
        if (jsonFeatureFactory.isJsonArray()) {
            fields = toStringList(jsonFeatureFactory);
        } else {
            if (! jsonFeatureFactory.isJsonObject()) {
                throw new IllegalArgumentException("Expected array or json object for: "+paramName);
            }
            JsonObject obj = (JsonObject)jsonFeatureFactory;
            if (obj.has(MIN_SET_SIZE_KEY)) {
                minSetSize = obj.get(MIN_SET_SIZE_KEY).getAsInt();
            }

            if (obj.has(MAX_SET_SIZE_KEY)) {
                maxSetSize = obj.get(MAX_SET_SIZE_KEY).getAsInt();
            }

            if (obj.has(FIELDS_KEY)) {
                fields = toStringList(obj.get(VALUES_KEY));
            } else {
                throw new IllegalArgumentException(paramName +" param requires a '"+
                        VALUES_KEY+"'");
            }
        }
        try {
            return new StringListFeatureFactory(paramName,
                    Class.forName(getClassName(paramName)), fields, minSetSize, maxSetSize);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
    private FeatureFactory buildIntFeatureFactory(String name, JsonElement intArr) {
        List<Integer> values = toIntList(intArr);
        Class clazz = null;
        try {
            clazz = Class.forName(getClassName(name));
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
        return new IntFeatureFactory(clazz, values);

    }

    private FeatureFactory buildFloatFeatureFactory(String name, JsonElement floatArr) {
        List<Float> values = toFloatList(floatArr);
        Class clazz = null;
        try {
            clazz = Class.forName(getClassName(name));
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
        return new FloatFeatureFactory(clazz, values);
    }

    private FeatureFactory buildStringFeatureFactory(String paramName, JsonElement valuesElement) {
        List<String> values = toStringList(valuesElement);
        try {
            return new StringFeatureFactory(paramName,
                    Class.forName(getClassName(paramName)), values);
        } catch (Exception e) {
            throw new JsonParseException(e.getMessage());
        }
    }

    @Override
    public JsonElement serialize(FeatureFactories featureFactories, Type type, JsonSerializationContext jsonSerializationContext) {
        JsonObject jsonObject = new JsonObject();
        for (Map.Entry<String, FeatureFactory> e : featureFactories.getFeatureFactories().entrySet()) {
            String name = e.getKey();
            jsonObject.add(name.toLowerCase(Locale.US), serializeFeatureSet(e.getValue()));
        }
        return jsonObject;
    }

    private JsonElement serializeFeatureSet(FeatureFactory featureFactory) {

        if (featureFactory instanceof FloatFeatureFactory) {
            return floatListToJsonArr(((FloatFeatureFactory) featureFactory).getFloats());
        } else if (featureFactory instanceof WeightableListFeatureFactory) {
            JsonObject ret = new JsonObject();
            ret.add(FIELDS_KEY, weightFeatureToJsonArray(
                    ((WeightableListFeatureFactory) featureFactory).getFeatures()));
            ret.add(DEFAULT_WEIGHT_KEY,
                    floatListToJsonArr(((WeightableListFeatureFactory) featureFactory).getDefaultWeights()));
            return ret;
        } else if (featureFactory instanceof StringFeatureFactory) {
            return stringListToJsonArr(((StringFeatureFactory) featureFactory).getStrings());
        } else {
            throw new IllegalArgumentException("not yet implemented");
        }
    }

    private JsonElement stringListToJsonArr(List<StringFeature> strings) {
        if (strings.size() == 1) {
            return new JsonPrimitive(strings.get(0).toString());
        } else {
            JsonArray ret = new JsonArray();
            for (StringFeature f : strings) {
                ret.add(f.toString());
            }
            return ret;
        }
    }

    private JsonElement weightFeatureToJsonArray(WeightableListFeature features) {
        if (features.size() == 1) {
            return new JsonPrimitive(features.get(0).toString());
        } else {
            JsonArray ret = new JsonArray();
            for (int i = 0; i < features.size(); i++) {
                ret.add(new JsonPrimitive(features.get(0).toString()));
            }
            return ret;
        }
    }

    private FeatureFactory buildWeightableFeatureFactory(String paramName, JsonObject obj) {
        List<String> fields = toStringList(obj.get(FIELDS_KEY).getAsJsonArray());

        List<Float> defaultWeights = toFloatList(obj.get(DEFAULT_WEIGHT_KEY));
        int maxSetSizeInt = -1;
        if (obj.has(MAX_SET_SIZE_KEY)) {
            JsonElement maxSetSize = obj.get(MAX_SET_SIZE_KEY);
            if (!maxSetSize.isJsonNull() && maxSetSize.isJsonPrimitive()) {
                maxSetSizeInt = maxSetSize.getAsInt();
            }
        }
        int minSetSizeInt = -1;
        if (obj.has(MIN_SET_SIZE_KEY)) {
            JsonElement minSetSize = obj.get(MIN_SET_SIZE_KEY);
            if (!minSetSize.isJsonNull() && minSetSize.isJsonPrimitive()) {
                minSetSizeInt = minSetSize.getAsInt();
            }
        }
        Class clazz = null;
        try {
            clazz = Class.forName(getClassName(paramName));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException();
        }
        return new WeightableListFeatureFactory(paramName, clazz, fields, defaultWeights, minSetSizeInt, maxSetSizeInt);
    }
}


