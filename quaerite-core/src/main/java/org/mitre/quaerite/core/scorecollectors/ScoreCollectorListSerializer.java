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
 *
 */
package org.mitre.quaerite.core.scorecollectors;

import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

public class ScoreCollectorListSerializer {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().
            registerTypeHierarchyAdapter(ScoreCollector.class, new ScoreCollectorSerializer()).create();

    static Type SCORE_COLLECTOR_TYPE = new TypeToken<ArrayList<ScoreCollector>>(){}.getType();

    static Map<String, Class> CLASSES = new ConcurrentHashMap<>();
    private static String DEFAULT_CLASS_NAME_SPACE = "org.mitre.quaerite.core.scorecollectors.";

    public static class ScoreCollectorSerializer<T> implements JsonSerializer, JsonDeserializer {

        private static final String CLASSNAME = "class";
        private static final String PARAMS = "params";
        private final Gson internalGson = new GsonBuilder().setPrettyPrinting().create();

        public T deserialize(JsonElement jsonElement, Type type,
                             JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {

            JsonObject jsonObject = jsonElement.getAsJsonObject();
            JsonPrimitive prim = (JsonPrimitive) jsonObject.get(CLASSNAME);
            String className = prim.getAsString();
            JsonObject params = jsonObject.getAsJsonObject(PARAMS);
            return buildScoreCollector(className, mapify(params));
        }

        private Map<String, String> mapify(JsonObject params) {
            if (params == null) {
                return null;
            }
            Map<String, String> ret = new HashMap<>();
            for (String k : params.keySet()) {
                String v = params.get(k).getAsString();
                ret.put(k, v);
            }
            return ret;
        }

        public JsonElement serialize(Object o, Type type, JsonSerializationContext jsonSerializationContext) {
            JsonObject jsonObject = new JsonObject();
            String clazzName = o.getClass().getName();
            if (clazzName.startsWith(DEFAULT_CLASS_NAME_SPACE)) {
                clazzName = clazzName.substring(DEFAULT_CLASS_NAME_SPACE.length());
            }
            jsonObject.addProperty(CLASSNAME, clazzName);
            if (((AbstractScoreCollector)o).getK() > -1) {
                JsonObject params = new JsonObject();
                params.add("atK",
                        new JsonPrimitive(Integer.toString(((AbstractScoreCollector) o).getK())));
                jsonObject.add("params", params);
            }
            return jsonObject;
        }
        /****** Helper method to get the className of the object to be deserialized *****/
        public T buildScoreCollector(String clazzName, Map<String, String> params) {
            if (! clazzName.contains(".")) {
                clazzName = DEFAULT_CLASS_NAME_SPACE+clazzName;
            }
            try {
                Class cl = Class.forName(clazzName);
                if (! (ScoreCollector.class.isAssignableFrom(cl))) {
                    throw new IllegalArgumentException(clazzName + " must be assignable from AbstractScoreCollector");
                }
                Constructor con = cl.getConstructor(Map.class);
                ScoreCollector scoreCollector = (ScoreCollector)con.newInstance(params);
                if (params != null) {
                    if (params.containsKey("useForTest")) {
                        String val = params.get("useForTest");
                        if (val.equalsIgnoreCase("true")) {
                            scoreCollector.setUseForTest();
                        }
                    }
                    if (params.containsKey("useForTrain")) {
                        String val = params.get("useForTrain");
                        if (val.equalsIgnoreCase("true")) {
                            scoreCollector.setUseForTrain();
                        }
                    }
                    if (params.containsKey("exportPMatrix")) {
                        String val = params.get("exportPMatrix");
                        if (val.equalsIgnoreCase("true")) {
                            scoreCollector.setExportPMatrix();
                        }
                    }
                }
                return (T)scoreCollector;
            } catch (Exception e) {
                throw new JsonParseException(e.getMessage());
            }
        }
    }

    public static String toJson(List<ScoreCollector> scoreCollectors) {
        return GSON.toJson(scoreCollectors);
    }

    public static List<ScoreCollector> fromJsonList(String json) {
        return GSON.fromJson(json, SCORE_COLLECTOR_TYPE);
    }

    public static String toJson(ScoreCollector scoreCollector) {
        return GSON.toJson(scoreCollector);
    }

    public static ScoreCollector fromJson(String json) {
        return GSON.fromJson(json, ScoreCollector.class);
    }
}
