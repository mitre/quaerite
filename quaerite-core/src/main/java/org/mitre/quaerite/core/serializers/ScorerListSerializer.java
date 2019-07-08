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
import org.mitre.quaerite.core.scorers.AbstractJudgmentScorer;
import org.mitre.quaerite.core.scorers.Scorer;

public class ScorerListSerializer {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().
            registerTypeHierarchyAdapter(Scorer.class, new ScorerSerializer()).create();

    static Type SCORE_AGGREGATOR_TYPE = new TypeToken<ArrayList<Scorer>>() {
    }.getType();

    static Map<String, Class> CLASSES = new ConcurrentHashMap<>();
    private static String DEFAULT_CLASS_NAME_SPACE = "org.mitre.quaerite.core.scorers.";

    public static class ScorerSerializer<T> implements JsonSerializer, JsonDeserializer {

        private static final String CLASSNAME = "class";
        private static final String PARAMS = "params";
        private static final String AT_N = "atN";
        private final Gson internalGson = new GsonBuilder().setPrettyPrinting().create();

        public T deserialize(JsonElement jsonElement, Type type,
                             JsonDeserializationContext jsonDeserializationContext)
                throws JsonParseException {

            JsonObject jsonObject = jsonElement.getAsJsonObject();
            JsonPrimitive prim = (JsonPrimitive) jsonObject.get(CLASSNAME);
            String className = prim.getAsString();
            int atN = -1;
            if (jsonObject.has(AT_N)) {
                atN = jsonObject.get(AT_N).getAsJsonPrimitive().getAsInt();
            }
            JsonObject params = jsonObject.getAsJsonObject(PARAMS);
            return buildScorer(className, atN, mapify(params));
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
            } else if (! clazzName.contains(".")) {
                throw new IllegalArgumentException(
                        "custom scorers must not be in the default package:" + clazzName);
            }
            jsonObject.addProperty(CLASSNAME, clazzName);
            JsonObject params = new JsonObject();
            Scorer scorer = (Scorer) o;
            if (scorer.getAtN() > -1) {
                jsonObject.add("atN",
                        new JsonPrimitive(Integer.toString(scorer.getAtN())));
            }
            if (scorer instanceof AbstractJudgmentScorer) {
                AbstractJudgmentScorer jScorer = (AbstractJudgmentScorer) scorer;
                Map<String, String> origParams = jScorer.getParams();
                for (Map.Entry<String, String> e : origParams.entrySet()) {
                    params.add(e.getKey(), new JsonPrimitive(e.getValue()));
                }
                if (jScorer.getExportPMatrix()) {
                    params.add("exportPMatrix", new JsonPrimitive(true));
                }
                if (jScorer.getUseForTest()) {
                    params.add("useForTest", new JsonPrimitive(true));
                }
                if (jScorer.getUseForTrain()) {
                    params.add("useForTrain", new JsonPrimitive(true));
                }
                if (params.size() > 0) {
                    jsonObject.add("params", params);
                }
            }
            return jsonObject;
        }

        /****** Helper method to get the className of the object to be deserialized *****/
        public T buildScorer(String clazzName, int atN, Map<String, String> params) {
            if (!clazzName.contains(".")) {
                clazzName = DEFAULT_CLASS_NAME_SPACE + clazzName;
            }
            try {
                Class cl = Class.forName(clazzName);
                if (!(Scorer.class.isAssignableFrom(cl))) {
                    throw new IllegalArgumentException(clazzName + " must be assignable from Scorer");
                }

                Constructor con = null;
                Scorer scorer = null;
                if (params != null) {
                    try {
                        con = cl.getConstructor(int.class, Map.class);
                        scorer = (Scorer) con.newInstance(atN, params);
                    } catch (NoSuchMethodException e) {
                        //swallow;
                    }
                }
                if (scorer == null) {
                    try {
                        con = cl.getConstructor(int.class);
                        scorer = (Scorer) con.newInstance(atN);
                    } catch (NoSuchMethodException e) {
                        //try zero argument constructor
                        con = cl.getConstructor();
                        scorer = (Scorer) con.newInstance();
                    }
                }
                if (params != null) {
                    if (params.containsKey("useForTest")) {
                        String val = params.get("useForTest");
                        if (val.equalsIgnoreCase("true")) {
                            ((AbstractJudgmentScorer)scorer).setUseForTest();
                        }
                    }
                    if (params.containsKey("useForTrain")) {
                        String val = params.get("useForTrain");
                        if (val.equalsIgnoreCase("true")) {
                            ((AbstractJudgmentScorer)scorer).setUseForTrain();
                        }
                    }
                    if (params.containsKey("exportPMatrix")) {
                        String val = params.get("exportPMatrix");
                        if (val.equalsIgnoreCase("true")) {
                            ((AbstractJudgmentScorer)scorer).setExportPMatrix();
                        }
                    }
                }
                return (T) scorer;
            } catch (Exception e) {
                e.printStackTrace();
                throw new JsonParseException(e.getMessage());
            }
        }
    }

    public static String toJson(List<Scorer> scorers) {
        return GSON.toJson(scorers);
    }

    public static List<Scorer> fromJsonList(String json) {
        return GSON.fromJson(json, SCORE_AGGREGATOR_TYPE);
    }

    public static String toJson(Scorer scorer) {
        return GSON.toJson(scorer);
    }

    public static Scorer fromJson(String json) {
        return GSON.fromJson(json, Scorer.class);
    }

}

