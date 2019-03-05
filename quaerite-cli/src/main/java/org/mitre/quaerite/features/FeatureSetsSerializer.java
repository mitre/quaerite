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
package org.mitre.quaerite.features;

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
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class FeatureSetsSerializer<T> implements JsonSerializer, JsonDeserializer {

    //    static Type FIELD_TYPES = new TypeToken<ArrayList<String>>(){}.getType();
    static String FIELDS_KEY = "fields";
    static String DEFAULT_WEIGHT_KEY = "defaultWeights";
    static String DEFAULT_CLASS_NAME_SPACE = "org.mitre.quaerite.features.";

    public T deserialize(JsonElement jsonElement, Type type,
                         JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        Map<String, FeatureSet> featureSetMap = new HashMap<>();
        for (String name : jsonObject.keySet()) {
            featureSetMap.put(name, buildFeatureSet(name, jsonObject.get(name)));
        }
        return (T) new FeatureSets(featureSetMap);
    }

    private FeatureSet buildFeatureSet(String name, JsonElement featureSet) {
        if (featureSet.isJsonArray()) {
            return buildSimpleFeatureSet(name, toSimpleFeatureList((JsonArray)featureSet));
        }
        JsonObject featureSetObj = (JsonObject)featureSet;
        if (featureSetObj.has(DEFAULT_WEIGHT_KEY)) {
            return buildWeightableFeatureSet(name,
                    toStringList((JsonArray) featureSetObj.get(FIELDS_KEY)),
                    toFloatList((JsonArray) featureSetObj.get(DEFAULT_WEIGHT_KEY)));
        } else {
            return buildStringListFeatureSet(name, toStringList((JsonArray) featureSetObj.get(FIELDS_KEY)));
        }

    }
    //TODO -- simplify this
    private FeatureSet buildStringListFeatureSet(String clazzName, List<String> values) {
        if (!clazzName.contains(".")) {
            clazzName = getClassName(clazzName);
        }
        try {
            Class cl = Class.forName(clazzName);
            if (!(FeatureSet.class.isAssignableFrom(cl))) {
                throw new IllegalArgumentException(clazzName + " must be assignable from FeatureSet");
            }
            Constructor con = cl.getConstructor(List.class);
            return (FeatureSet) con.newInstance(values);
        } catch (Exception e) {
            e.printStackTrace();
            throw new JsonParseException(e.getMessage());
        }
    }

    private FeatureSet buildSimpleFeatureSet(String clazzName, List<SimpleFeature> values) {
        if (!clazzName.contains(".")) {
            clazzName = getClassName(clazzName);
        }
        try {
            Class cl = Class.forName(clazzName);
            if (!(FeatureSet.class.isAssignableFrom(cl))) {
                throw new IllegalArgumentException(clazzName + " must be assignable from FeatureSet");
            }
            Constructor con = cl.getConstructor(List.class);
            return (FeatureSet) con.newInstance(values);
        } catch (Exception e) {
            e.printStackTrace();
            throw new JsonParseException(e.getMessage());
        }
    }

    public JsonElement serialize(Object o, Type type, JsonSerializationContext jsonSerializationContext) {
        JsonObject jsonObject = new JsonObject();
        for (Map.Entry<String, FeatureSet> e : ((FeatureSets) o).featureSets.entrySet()) {
            String name = e.getKey();
            jsonObject.add(name.toLowerCase(Locale.US), serializeFeatureSet(e.getValue()));
        }
        return jsonObject;
    }

    private JsonElement serializeFeatureSet(FeatureSet featureSet) {
        JsonObject ret = new JsonObject();
        if (featureSet instanceof WeightableFeatureSet) {

            ret.add(FIELDS_KEY, featureListJsonArr(((WeightableFeatureSet) featureSet).getFeatures()));
            ret.add(DEFAULT_WEIGHT_KEY,
                    floatListToJsonArr(((WeightableFeatureSet) featureSet).getDefaultWeights()));
        } else if (featureSet instanceof SimpleFeatureSet) {
            ret.add(FIELDS_KEY, featureListJsonArr(((WeightableFeatureSet) featureSet).getFeatures()));
        } else {
            throw new IllegalArgumentException("not yet implemented");
        }
        return ret;
    }

    private JsonArray featureListJsonArr(List<WeightableFeature> features) {
        JsonArray arr = new JsonArray();
        for (WeightableFeature w : features) {
            arr.add(w.toString());
        }
        return arr;
    }

    private JsonArray floatListToJsonArr(List<Float> defaultWeights) {
        JsonArray arr = new JsonArray();
        for (Float f : defaultWeights) {
            arr.add(f);
        }
        return arr;
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

    private String getClassName(String clazzName) {
        if (!clazzName.contains(".")) {
            if (clazzName.length() < 5) {
                clazzName = clazzName.toUpperCase(Locale.US);
            } else {
                clazzName = clazzName.substring(0,1).toUpperCase(Locale.US)+
                        clazzName.substring(1);
            }
            return DEFAULT_CLASS_NAME_SPACE + clazzName;
        }
        return clazzName;
    }

    private List<String> toStringList(JsonArray stringArr) {
        if (stringArr == null) {
            return null;
        }
        List<String> ret = new ArrayList<>();
        for (JsonElement el : stringArr) {
            ret.add(el.getAsJsonPrimitive().getAsString());
        }
        return ret;
    }

    private List<SimpleFeature> toSimpleFeatureList(JsonArray stringArr) {
        if (stringArr == null) {
            return null;
        }
        List<SimpleFeature> ret = new ArrayList<>();
        for (JsonElement el : stringArr) {
            ret.add(new SimpleFeature(el.getAsJsonPrimitive().getAsString()));
        }
        return ret;
    }


    private List<Float> toFloatList(JsonArray floatArr) {
        List<Float> ret = new ArrayList<>();
        for (JsonElement el : floatArr) {
            ret.add(el.getAsJsonPrimitive().getAsFloat());
        }
        return ret;
    }
}


