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
package org.mitre.quaerite.features.serializers;

import static org.mitre.quaerite.features.serializers.FeatureSetsSerializer.FIELDS_KEY;

import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import org.mitre.quaerite.features.Feature;
import org.mitre.quaerite.features.FloatFeature;
import org.mitre.quaerite.features.ParamsMap;
import org.mitre.quaerite.features.StringFeature;
import org.mitre.quaerite.features.sets.FeatureSet;
import org.mitre.quaerite.features.sets.FloatFeatureSet;
import org.mitre.quaerite.features.sets.StringFeatureSet;
import org.mitre.quaerite.features.sets.WeightableFeatureSet;

public class ParamsSerializer extends AbstractFeatureSerializer
        implements JsonSerializer<ParamsMap>, JsonDeserializer<ParamsMap> {

    @Override
    public ParamsMap deserialize(JsonElement jsonElement, Type type,
                         JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        ParamsMap paramsMap = new ParamsMap();
        for (String name : jsonObject.keySet()) {
            paramsMap.put(name, buildParams(name, jsonObject.get(name)));
        }
        return paramsMap;
    }

    private Set<Feature> buildParams(String parameterName, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Collections.EMPTY_SET;
        }
        Class featureSetClass = determineClass(parameterName);
        if (WeightableFeatureSet.class.isAssignableFrom(featureSetClass)) {
            return buildWeightableFeatures(element);
        } else if (StringFeatureSet.class.isAssignableFrom(featureSetClass)) {
            return buildStringFeatures(element);
        } else if (FloatFeatureSet.class.isAssignableFrom(featureSetClass)) {
            return buildFloatFeatures(element);
        } else {
            throw new IllegalArgumentException("I regret I don't know how to build: "+parameterName);
        }
    }

    private Set<Feature> buildFloatFeatures(JsonElement element) {
        Set<Feature> features = new LinkedHashSet<>();
        for (Float f : toFloatList(element)) {
            features.add(new FloatFeature(f));
        }
        return features;
    }

    private Set<Feature> buildStringFeatures(JsonElement element) {
        Set<Feature> features = new LinkedHashSet<>();
        features.addAll(toStringFeatureList(element));
        return features;
    }

    private Set<Feature> buildWeightableFeatures(JsonElement element) {
        Set<Feature> features = new LinkedHashSet<>();
        features.addAll(toWeightableList(element));
        return features;
    }


    @Override
    public JsonElement serialize(ParamsMap paramsMap, Type type, JsonSerializationContext jsonSerializationContext) {
        JsonObject jsonObject = new JsonObject();
        for (Map.Entry<String, Set<Feature>> e : paramsMap.getParams().entrySet()) {
            String name = e.getKey();
            jsonObject.add(name.toLowerCase(Locale.US), serializeFeatureSet(e.getValue()));
        }
        return jsonObject;
    }

    private JsonElement serializeFeatureSet(Set<Feature> features) {
        if (features == null || features.size() == 0) {
            return new JsonArray();
        }
        Feature feature0 = (Feature)features.toArray()[0];
        Class clazz = feature0.getClass();
        if (WeightableFeatureSet.class.isAssignableFrom(clazz)) {
            JsonObject ret = new JsonObject();
            ret.add(FIELDS_KEY, featureListJsonArr(features));
            return ret;
        } else if (StringFeature.class.isAssignableFrom(clazz)
            || FloatFeature.class.isAssignableFrom(clazz)) {
            if (features.size() == 1) {
                return new JsonPrimitive(feature0.toString());
            } else {
                return featureListJsonArr(features);
            }
        } else {
            throw new IllegalArgumentException("not yet implemented: "+features);
        }
    }

    /****** Helper method to get the className of the object to be deserialized *****/
    private FeatureSet buildWeightableFeatureSet(String clazzName, List<String> fields, List<Float> defaultWeights) {
        if (!clazzName.contains(".")) {
            clazzName = getClassName(clazzName);
        }
        try {
            Class cl = Class.forName(clazzName);
            if (!(WeightableFeatureSet.class.isAssignableFrom(cl))) {
                throw new IllegalArgumentException(clazzName + " must be assignable from WeightableFeatureSet");
            }
            Constructor con = cl.getConstructor(List.class, List.class);
            return (FeatureSet) con.newInstance(fields, defaultWeights);
        } catch (Exception e) {
            e.printStackTrace();
            throw new JsonParseException(e.getMessage());
        }
    }
}


