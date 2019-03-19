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

import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.mitre.quaerite.core.features.FloatFeature;
import org.mitre.quaerite.core.features.StringFeature;
import org.mitre.quaerite.core.features.WeightableListFeature;
import org.mitre.quaerite.core.features.factories.FeatureFactories;
import org.mitre.quaerite.core.features.factories.FeatureFactory;
import org.mitre.quaerite.core.features.factories.FloatFeatureFactory;
import org.mitre.quaerite.core.features.factories.StringFeatureFactory;
import org.mitre.quaerite.core.features.factories.WeightableListFeatureFactory;


public class FeatureFactorySerializer extends AbstractFeatureSerializer
        implements JsonSerializer<FeatureFactories>, JsonDeserializer<FeatureFactories> {

    //    static Type FIELD_TYPES = new TypeToken<ArrayList<String>>(){}.getType();
    static String FIELDS_KEY = "fields";
    static String VALUES_KEY = "values";
    static String DEFAULT_WEIGHT_KEY = "defaultWeights";
    static String MAX_SET_SIZE_KEY = "maxSetSize";

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
            return buildWeightableFeatureFactory(paramName, featureSetObj.get(FIELDS_KEY),
                    featureSetObj.get(DEFAULT_WEIGHT_KEY),
                    featureSetObj.get(MAX_SET_SIZE_KEY));
        } else if (FloatFeature.class.isAssignableFrom(clazz)) {
            return buildFloatFeatureFactory(paramName, jsonFeatureFactory);
        } else if (StringFeature.class.isAssignableFrom(clazz)) {
            return buildStringFeatureFactory(paramName, jsonFeatureFactory);
        } else {
            throw new IllegalArgumentException("Sorry, I can't yet handle: "+paramName);
        }

    }

    private FeatureFactory buildFloatFeatureFactory(String name, JsonElement floatArr) {
        List<Float> values = toFloatList(floatArr);
        return new FloatFeatureFactory(name, values);
    }

    private FeatureFactory buildStringFeatureFactory(String paramName, JsonElement valuesElement) {
        //TODO -- pick up here
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

    private FeatureFactory buildWeightableFeatureFactory(String paramName,
                                                         JsonElement fieldsElement,
                                                         JsonElement defaultWeightsElement,
                                                         JsonElement maxSetSize) {
        List<String> fields = toStringList(fieldsElement);
        List<Float> defaultWeights = toFloatList(defaultWeightsElement);
        int sz = -1;
        if (maxSetSize != null && ! maxSetSize.isJsonNull() && maxSetSize.isJsonPrimitive()) {
            sz = maxSetSize.getAsInt();
        }
        return new WeightableListFeatureFactory(paramName, fields, defaultWeights, sz);
    }
}


