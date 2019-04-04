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

import static org.mitre.quaerite.core.serializers.FeatureFactorySerializer.VALUES_KEY;

import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

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
import org.apache.log4j.Logger;
import org.mitre.quaerite.core.features.Feature;
import org.mitre.quaerite.core.features.FloatFeature;
import org.mitre.quaerite.core.features.PF;
import org.mitre.quaerite.core.features.QF;
import org.mitre.quaerite.core.features.StringFeature;
import org.mitre.quaerite.core.features.StringListFeature;
import org.mitre.quaerite.core.features.TIE;
import org.mitre.quaerite.core.features.WeightableField;
import org.mitre.quaerite.core.features.WeightableListFeature;
import org.mitre.quaerite.core.queries.DisMaxQuery;
import org.mitre.quaerite.core.queries.EDisMaxQuery;
import org.mitre.quaerite.core.queries.LuceneQuery;
import org.mitre.quaerite.core.queries.MultiMatchQuery;
import org.mitre.quaerite.core.queries.Query;
import org.mitre.quaerite.core.queries.QueryOperator;
import org.mitre.quaerite.core.util.JsonUtil;

public class QuerySerializer extends AbstractFeatureSerializer
        implements JsonSerializer<Query>, JsonDeserializer<Query> {

    static Logger LOG = Logger.getLogger(QuerySerializer.class);

    @Override
    public Query deserialize(JsonElement jsonElement, Type type,
                         JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject root = jsonElement.getAsJsonObject();
        String queryType = JsonUtil.getSingleChildName(root);
        if (queryType.equals("edismax")) {
            return buildEDisMax(root.get(queryType).getAsJsonObject());
        } else if (queryType.equals("dismax")) {
            return buildDisMax(root.get(queryType).getAsJsonObject());
        } else if (queryType.equals("lucene")) {
            JsonObject obj = root.get(queryType).getAsJsonObject();
            String defaultField = (obj.has("defaultField")) ? obj.get("defaultField").getAsString() : "";
            String qOpString = (obj.has("q.op"))? obj.get("q.op").getAsString() : null;
            String queryString = obj.get("queryString").getAsString();

            QueryOperator.OPERATOR qop = (qOpString == null) ? LuceneQuery.DEFAULT_QUERY_OPERATOR :
                    (qOpString.equalsIgnoreCase("AND") ? QueryOperator.OPERATOR.AND : QueryOperator.OPERATOR.OR);
            return new LuceneQuery(defaultField, queryString, qop);
        } else {
            throw new IllegalArgumentException("I regret I don't yet support: "+queryType);
        }
    }


    Query buildEDisMax(JsonObject obj) {
        Query q = new EDisMaxQuery();
        deserializeDisMax((DisMaxQuery)q, obj);
        return q;
    }

    Query buildDisMax(JsonObject obj) {
        Query q = new DisMaxQuery();
        deserializeDisMax((DisMaxQuery)q, obj);
        return q;
    }

    void deserializeDisMax(DisMaxQuery q, JsonObject obj) {
        PF pf = new PF();
        for (String f : toStringList(obj.get("pf"))) {
            pf.add(new WeightableField(f));
        }
        q.setPF(pf);
        //TODO -- stub add in bq and bf
        /*
        if (obj.has("bq")) {
            BQ bq = new BQ(obj.get("bq").getAsString());
        }

        BF bf = new BF();
        */
        deserializeMultiMatch((MultiMatchQuery)q, obj);
    }

    void deserializeMultiMatch(MultiMatchQuery q, JsonObject obj) {
        QF qf = new QF();
        int fields = 0;
        for (String f : toStringList(obj.get("qf"))) {
            qf.add(new WeightableField(f));
            fields++;
        }
        if (fields == 0) {
            throw new IllegalArgumentException("must specify qf parameter in a multimatch with at least one field");
        }
        q.setQF(qf);
        if (obj.has("tie")) {
            TIE tie = new TIE(obj.get("tie").getAsFloat());
            q.setTie(tie);
        }
    }

    private Feature buildParam(String parameterName, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonArray() && ((JsonArray)element).size() == 0) {
            return null;
        }
        Class paramClass = determineClass(parameterName);
        if (WeightableListFeature.class.isAssignableFrom(paramClass)) {
            return buildWeightableListFeature(parameterName, element);
        } else if (StringFeature.class.isAssignableFrom(paramClass)) {
            return buildStringFeature(parameterName, element);
        } else if (FloatFeature.class.isAssignableFrom(paramClass)) {
            return buildFloatFeature(parameterName, element);
        } else if (StringListFeature.class.isAssignableFrom(paramClass)) {
            return buildStringListFeature(parameterName, element);
        } else {
            throw new IllegalArgumentException("I regret I don't know how to build: "+parameterName);
        }
    }

    private Feature buildStringListFeature(String name, JsonElement element) {
        try {
            Class clazz = Class.forName(getClassName(name));
            if (!(StringListFeature.class.isAssignableFrom(clazz))) {
                throw new IllegalArgumentException(getClassName(name) + " must be assignable from WeightableListFeatureFactory");
            }
            int minSetSize = 0;
            int maxSetSize = 0;
            List<String> strings = new ArrayList<>();
            if (element.isJsonObject()) {
                JsonObject obj = (JsonObject)element;
                if (obj.has(FeatureFactorySerializer.MIN_SET_SIZE_KEY)) {
                    minSetSize = obj.get(FeatureFactorySerializer.MIN_SET_SIZE_KEY).getAsInt();
                }
                if (obj.has(FeatureFactorySerializer.MAX_SET_SIZE_KEY)) {
                    maxSetSize = obj.get(FeatureFactorySerializer.MAX_SET_SIZE_KEY).getAsInt();
                }
                if (!obj.has(VALUES_KEY)) {
                    throw new IllegalArgumentException(name + " must have a "+VALUES_KEY);
                }
                strings = toStringList(obj.get(VALUES_KEY));
            } else {
                strings = toStringList(element);
                minSetSize = 0;
                maxSetSize = strings.size();
            }
            Constructor constructor = null;

            constructor = clazz.getConstructor(List.class, int.class, int.class);
            return (StringListFeature)constructor.newInstance(strings, minSetSize, maxSetSize);
        } catch (Exception e) {
            throw new JsonParseException(e.getMessage());
        }
    }

    private Feature buildFloatFeature(String name, JsonElement element) {
        try {
            Class clazz = Class.forName(getClassName(name));
        if (!(FloatFeature.class.isAssignableFrom(clazz))) {
            throw new IllegalArgumentException(getClassName(name) + " must be assignable from WeightableListFeatureFactory");
        }
        Constructor constructor = null;

            constructor = clazz.getConstructor(float.class);
            return (FloatFeature)constructor.newInstance(element.getAsFloat());
        } catch (Exception e) {
            throw new JsonParseException(e.getMessage());
        }
    }

    private Feature buildStringFeature(String name, JsonElement element) {
        try {
        Class clazz = Class.forName(getClassName(name));
        if (!(FloatFeature.class.isAssignableFrom(clazz))) {
            throw new IllegalArgumentException(getClassName(name) + " must be assignable from WeightableListFeatureFactory");
        }
        Constructor constructor = null;
            constructor = clazz.getConstructor(Float.class);
            return (StringFeature)constructor.newInstance(element.getAsString());
        } catch (Exception e) {
            throw new JsonParseException(e.getMessage());
        }
    }

    private WeightableListFeature buildWeightableListFeature(String name, JsonElement element) {
        WeightableListFeature weightableListFeature;
        try {
            Class clazz = Class.forName(getClassName(name));
            if (!(WeightableListFeature.class.isAssignableFrom(clazz))) {
                throw new IllegalArgumentException(getClassName(name) + " must be assignable from WeightableListFeatureFactory");
            }
            weightableListFeature = (WeightableListFeature)clazz.newInstance();
        } catch (Exception e) {
            throw new JsonParseException(e.getMessage());
        }

        if (element.isJsonPrimitive()) {
            weightableListFeature.add(new WeightableField(element.getAsString()));
        } else if (element.isJsonArray()) {
            for (JsonElement e : (JsonArray)element) {
                if (e.isJsonNull()) {
                    LOG.warn("Json null in list for: "+name+" ?! Skipping.");
                    continue;
                }
                weightableListFeature.add(new WeightableField(e.getAsString()));
            }
        }
        return weightableListFeature;
    }


    @Override
    public JsonElement serialize(Query query, Type type, JsonSerializationContext jsonSerializationContext) {
        JsonObject jsonObject = new JsonObject();
        if (query instanceof EDisMaxQuery) {
            jsonObject.add("edismax", serializeEDisMax((EDisMaxQuery)query));
        } else if (query instanceof DisMaxQuery) {
            jsonObject.add("dismax", serializeDisMax((DisMaxQuery)query));
        } else if (query instanceof LuceneQuery) {
            jsonObject.add("lucene", serializeLucene((LuceneQuery)query));
        } else {
            throw new IllegalArgumentException("I'm sorry, I don't yet support: "+query.getClass());
        }
        return jsonObject;
    }

    private JsonElement serializeLucene(LuceneQuery query) {
        JsonObject object = new JsonObject();
        object.addProperty("queryString", query.getQueryString());
        object.addProperty("defaultField", query.getDefaultField());
        object.addProperty("q.op", query.getQueryOperator().toString());
        return object;
    }

    private JsonElement serializeDisMax(DisMaxQuery query) {
        JsonObject obj = new JsonObject();
        serializeDisMaxComponents(query, obj);
        serializeMultiMatchComponents((MultiMatchQuery)query, obj);
        return obj;
    }

    private JsonElement serializeEDisMax(EDisMaxQuery query) {
        JsonObject obj = new JsonObject();
        serializeEDisMaxComponents(query, obj);
        serializeDisMaxComponents((DisMaxQuery)query, obj);
        serializeMultiMatchComponents((MultiMatchQuery)query, obj);
        return obj;
    }

    private void serializeEDisMaxComponents(EDisMaxQuery query, JsonObject obj) {
        //stub
    }

    private void serializeDisMaxComponents(DisMaxQuery query, JsonObject obj) {
        //stub
    }

    private void serializeMultiMatchComponents(MultiMatchQuery query, JsonObject obj) {
        obj.add("qf", serializeFeature(query.getQF()));
        if (query.getTIE() != null) {
            obj.add("tie", serializeFeature(query.getTIE()));
        }
    }

    private JsonElement serializeFeature(Feature feature) {
        if (feature == null) {
            return new JsonArray();
        }

        if (feature instanceof WeightableListFeature) {
            JsonObject ret = new JsonObject();
            JsonElement jsonFields;
            List<WeightableField> fields = ((WeightableListFeature)feature).getWeightableFields();
            if (fields.size() > 1) {
                jsonFields = new JsonArray();
                for (WeightableField f : ((WeightableListFeature) feature).getWeightableFields()) {
                    ((JsonArray)jsonFields).add(f.toString());
                }
            } else if (fields.size() == 1){
                jsonFields = new JsonPrimitive(fields.get(0).toString());
            } else {
                jsonFields = JsonNull.INSTANCE;
            }
            return jsonFields;
        } else if (feature instanceof FloatFeature ||
            feature instanceof StringFeature) {
                return new JsonPrimitive(feature.toString());

        } else if (feature instanceof StringListFeature) {
            StringListFeature stringListFeature = (StringListFeature)feature;
            List<String> strings = stringListFeature.getAll();
            JsonElement valuesElement;
            if (strings.size() == 0) {
                valuesElement = JsonNull.INSTANCE;
            } else if (strings.size() == 1) {
                valuesElement = new JsonPrimitive(strings.get(0));
            } else {
                valuesElement = new JsonArray();
                for (String s : strings) {
                    ((JsonArray)valuesElement).add(s);
                }
            }
            return valuesElement;
        } else {
            throw new IllegalArgumentException("not yet implemented: "+feature);
        }
    }
}


