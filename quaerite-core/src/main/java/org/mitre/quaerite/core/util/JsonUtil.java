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
package org.mitre.quaerite.core.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class JsonUtil {

    public static long getPrimitive(JsonElement root, String key, long dflt) {
        if (! root.isJsonObject()) {
            return dflt;
        }
        JsonElement el = ((JsonObject)root).get(key);
        if (! el.isJsonPrimitive()) {
            return dflt;
        }
        return ((JsonPrimitive)el).getAsLong();
    }

    public static String getPrimitive(JsonElement root, String key, String dflt) {
        if (! root.isJsonObject()) {
            return dflt;
        }
        JsonElement el = ((JsonObject)root).get(key);
        if (! el.isJsonPrimitive()) {
            return dflt;
        }
        return ((JsonPrimitive)el).getAsString();
    }

    public static List<String> jsonArrToStringList(JsonElement v) {
        List<String> vals = new ArrayList<>();
        if (! v.isJsonArray()) {
            return vals;
        }
        for (JsonElement e : ((JsonArray)v)) {
            if (e.isJsonPrimitive()) {
                vals.add(e.getAsString());
            }
        }
        return vals;
    }

    public static String getSingleChildName(JsonObject object) {
        if (object.keySet().size() != 1) {
            throw new IllegalArgumentException(
                    "Expected only a single child, but found: "+object.keySet().size()+
                    " -> "+object);
        }
        for (String s : object.keySet()) {
            return s;
        }
        throw new IllegalArgumentException("Expected only a single child, but found: "+object.keySet().size());
    }

    public static String getSingleChildNameNot(JsonObject object, String ... names) {
        Set<String> ignore = new HashSet<>();
        for (String n : names) {
            ignore.add(n);
        }
        for (String n : object.keySet()) {
            if (! ignore.contains(n)) {
                return n;
            }
        }
        return null;
    }
}
