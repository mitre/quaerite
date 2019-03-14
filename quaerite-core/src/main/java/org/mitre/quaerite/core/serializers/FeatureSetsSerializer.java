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
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.mitre.quaerite.core.features.StringFeature;
import org.mitre.quaerite.core.features.WeightableListFeature;
import org.mitre.quaerite.core.featuresets.FeatureSet;
import org.mitre.quaerite.core.featuresets.FeatureSets;
import org.mitre.quaerite.core.featuresets.FloatFeatureSet;
import org.mitre.quaerite.core.featuresets.StringFeatureSet;
import org.mitre.quaerite.core.featuresets.WeightableFeatureSet;


public class FeatureSetsSerializer extends AbstractFeatureSerializer
        implements JsonSerializer<FeatureSets>, JsonDeserializer<FeatureSets> {

    //    static Type FIELD_TYPES = new TypeToken<ArrayList<String>>(){}.getType();
    static String FIELDS_KEY = "fields";
    static String VALUES_KEY = "values";
    static String DEFAULT_WEIGHT_KEY = "defaultWeights";

    @Override
    public FeatureSets deserialize(JsonElement jsonElement, Type type,
                                                                    JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        Map<String, FeatureSet> featureSetMap = new HashMap<>();
        for (String name : jsonObject.keySet()) {
            featureSetMap.put(name, buildFeatureSet(name, jsonObject.get(name)));
        }
        return new FeatureSets(featureSetMap);
    }

    private FeatureSet buildFeatureSet(String name, JsonElement featureSet) {
        Class clazz = determineClass(name);
        if (WeightableFeatureSet.class.isAssignableFrom(clazz)) {
            JsonObject featureSetObj = (JsonObject)featureSet;
            return buildWeightableFeatureSet(clazz, featureSetObj.get(FIELDS_KEY),
                    featureSetObj.get(DEFAULT_WEIGHT_KEY));
        } else if (FloatFeatureSet.class.isAssignableFrom(clazz)) {
            return buildFloatFeatureSet(clazz, featureSet);
        } else if (StringFeatureSet.class.isAssignableFrom(clazz)) {
            return buildStringFeatureSet(clazz, featureSet);
        } else {
            throw new IllegalArgumentException("Sorry, I can't yet handle: "+name);
        }

    }

    private FeatureSet buildFloatFeatureSet(Class clazz, JsonElement floatArr) {
        try {
            List<Float> values = toFloatList(floatArr);
            if (!(FeatureSet.class.isAssignableFrom(clazz))) {
                throw new IllegalArgumentException(clazz.getName() + " must be assignable from FeatureSet");
            }
            Constructor con = clazz.getConstructor(List.class);
            return (FeatureSet) con.newInstance(values);
        } catch (Exception e) {
            e.printStackTrace();
            throw new JsonParseException(e.getMessage());
        }
    }

    private FeatureSet buildStringFeatureSet(Class clazz, JsonElement valuesElement) {
        List<StringFeature> values = FeatureSetsSerializer.toStringFeatureList(valuesElement);
        try {
            if (!(FeatureSet.class.isAssignableFrom(clazz))) {
                throw new IllegalArgumentException(clazz.getName() + " must be assignable from FeatureSet");
            }
            Constructor con = clazz.getConstructor(List.class);
            return (FeatureSet) con.newInstance(values);
        } catch (Exception e) {
            throw new JsonParseException(e.getMessage());
        }
    }

    @Override
    public JsonElement serialize(FeatureSets featureSets, Type type, JsonSerializationContext jsonSerializationContext) {
        JsonObject jsonObject = new JsonObject();
        for (Map.Entry<String, FeatureSet> e : featureSets.getFeatureSets().entrySet()) {
            String name = e.getKey();
            jsonObject.add(name.toLowerCase(Locale.US), serializeFeatureSet(e.getValue()));
        }
        return jsonObject;
    }

    private JsonElement serializeFeatureSet(FeatureSet featureSet) {

        if (featureSet instanceof FloatFeatureSet) {
            return floatListToJsonArr(((FloatFeatureSet)featureSet).getFloats());
        } else if (featureSet instanceof WeightableFeatureSet) {
            JsonObject ret = new JsonObject();
            ret.add(FIELDS_KEY, weightFeatureToJsonArray(
                    ((WeightableFeatureSet) featureSet).getFeatures()));
            ret.add(DEFAULT_WEIGHT_KEY,
                    floatListToJsonArr(((WeightableFeatureSet) featureSet).getDefaultWeights()));
            return ret;
        } else if (featureSet instanceof StringFeatureSet) {
            return stringListToJsonArr(((StringFeatureSet)featureSet).getStrings());
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

    private FeatureSet buildWeightableFeatureSet(Class clazz,
                                                 JsonElement fieldsElement, JsonElement defaultWeightsElement) {
        List<String> fields = toStringList(fieldsElement);
        List<Float> defaultWeights = toFloatList(defaultWeightsElement);
        try {
            if (!(WeightableFeatureSet.class.isAssignableFrom(clazz))) {
                throw new IllegalArgumentException(clazz.getName() + " must be assignable from WeightableFeatureSet");
            }
            Constructor con = clazz.getConstructor(List.class, List.class);
            return (FeatureSet) con.newInstance(fields, defaultWeights);
        } catch (Exception e) {
            e.printStackTrace();
            throw new JsonParseException(e.getMessage());
        }
    }
}


