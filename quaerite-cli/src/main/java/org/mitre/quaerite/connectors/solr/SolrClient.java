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
package org.mitre.quaerite.connectors.solr;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpPost;
import org.mitre.quaerite.FacetResult;
import org.mitre.quaerite.ResultSet;
import org.mitre.quaerite.connectors.QueryRequest;
import org.mitre.quaerite.connectors.SearchClient;
import org.mitre.quaerite.connectors.SearchClientException;
import org.mitre.quaerite.connectors.StoredDocument;

public class SolrClient extends SearchClient {
    private static final String DEFAULT_HANDLER = "select";
    private static final String JSON_RESPONSE = "&wt=json";
    private static final Gson GSON = new Gson();

    private final String url;

    /**
     *
     * @param url url to Solr including /collection
     */
    public SolrClient(String url) {
        this.url = url;
    }

    @Override
    public ResultSet search(QueryRequest query) throws SearchClientException, IOException {
        String url = generateRequestURL(query);
        long start = System.currentTimeMillis();
        byte[] response = get(url);
        long elapsed = System.currentTimeMillis()-start;
        return translateResponse(elapsed, response);
    }

    private ResultSet translateResponse(long totalTime, byte[] bytes) throws IOException {
        JsonParser parser = new JsonParser();
        JsonElement root = null;
        String jsonString = new String(bytes, StandardCharsets.UTF_8);
        try (Reader reader = new BufferedReader(
                new InputStreamReader(
                        new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            root = parser.parse(reader);
        }
        //TODO: figure out what queryTime means/is as diff from total
        long queryTime = 0;
        List<String> ids = new ArrayList();
        JsonObject response = (JsonObject)((JsonObject)root).get("response");
        long totalHits =response.get("numFound").getAsLong();
        if (response.has("docs")) {
            JsonArray docs = (JsonArray)response.get("docs");
            for (JsonElement docElement : docs) {
                String id = ((JsonObject)docElement).get("id").getAsString();
                ids.add(id);
            }
        }
        return new ResultSet(totalHits, queryTime, totalTime, ids);
    }

    private String generateRequestURL(QueryRequest query) {
        StringBuilder sb = new StringBuilder();
        sb.append(url);
        if (! url.endsWith("/")) {
            sb.append("/");
        }
        String handler = query.getCustomHandler();
        handler = (handler == null) ? DEFAULT_HANDLER : handler;
        sb.append(handler);
        sb.append("?");

        try {
            if (!StringUtils.isBlank(query.getCustomHandler())) {
                sb.append("qq=");
            } else {
                sb.append("q=");
            }
            sb.append(URLEncoder.encode(query.getQuery(), StandardCharsets.UTF_8.name()));
            sb.append("&defType=edismax");
            sb.append("&fl=")
                    .append(query.getIdField());
            sb.append("&start=0&rows="+query.getNumResults());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
        for (Map.Entry<String, List<String>> e : query.getParameters().entrySet()) {
            for (String value: e.getValue()) {
                try {
                    sb.append("&");
                    sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8.name()));
                    sb.append("=").append(URLEncoder.encode(value, StandardCharsets.UTF_8.name()));
                } catch (UnsupportedEncodingException ex) {
                    throw new IllegalArgumentException(ex);
                }
            }
        }
        /*
        //        solrQuery.setFacetMissing(true);
        solrQuery.setFacet(true);
        solrQuery.setFacetLimit(10000);
         */

        if (query.getFacetFields().size() > 0) {
            sb.append("&facet=true");
            //TODO: parameterize
            sb.append("&facet.missing=true");
            sb.append("&facet.limit=10000");
            for (String field : query.getFacetFields()) {
                sb.append("&facet.field=").append(encode(field));
            }
        }
        sb.append(JSON_RESPONSE);
        return sb.toString();
    }

    @Override
    public FacetResult facet(QueryRequest query) throws SearchClientException, IOException {
        String url = generateRequestURL(query);
        byte[] bytes = get(url);
        JsonParser parser = new JsonParser();
        JsonElement root = null;
        try (Reader reader = new BufferedReader(
                new InputStreamReader(
                        new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            root = parser.parse(reader);
        }
        JsonObject response = (JsonObject)((JsonObject)root).get("response");
        long totalDocs = response.get("numFound").getAsLong();
        JsonObject facetCounts = (JsonObject)((JsonObject)root).get("facet_counts");
        JsonObject facetFields = (JsonObject)facetCounts.get("facet_fields");
        //TODO: hardcoded to expect only 1
        String facetField = query.getFacetFields().get(0);
        JsonArray arr = (JsonArray) facetFields.get(facetField);

        Map<String, Long> counts = new HashMap<>();
        for (int i = 0; i < arr.size()-1; i += 2) {
            JsonElement valueElement = arr.get(i);
            String value = null;
            if (valueElement.isJsonNull()) {
                value = "null";
            } else {
                value = valueElement.getAsString();
            }
            long count = arr.get(i+1).getAsLong();
            counts.put(value, count);
        }
        return new FacetResult(totalDocs, counts);
    }

    @Override
    public void addDocument(StoredDocument buildDocument) throws IOException {
        String json = GSON.toJson(buildDocument.getFields());
        postJson(url+"/update/json/docs?commitWithin=1000", json);
    }

    @Override
    public void addDocuments(List<StoredDocument> buildDocuments) throws IOException {
        List<Map<String, Object>> data = new ArrayList<>();
        for (StoredDocument d : buildDocuments) {
            data.add(d.getFields());
        }
        String json = GSON.toJson(data);
        postJson(url+"/update/json/docs?commitWithin=1000", json);
    }
}
