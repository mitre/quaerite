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
package org.mitre.quaerite.connectors;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mitre.quaerite.core.FacetResult;
import org.mitre.quaerite.core.ResultSet;

public class SolrClient extends SearchClient {

    private static final String DEFAULT_HANDLER = "select";
    private static final String JSON_RESPONSE = "&wt=json";
    private static Set<String> SYS_INTERNAL_FIELDS;

    static {
        Set<String> tmp = new HashSet<>();
        tmp.add("_version_");
        SYS_INTERNAL_FIELDS = Collections.unmodifiableSet(tmp);
    }

    static Logger LOG = Logger.getLogger(SolrClient.class);

    private static final Gson GSON = new Gson();

    private final String url;
    private String idField;

    /**
     * @param url url to Solr including /collection
     */
    protected SolrClient(String url) throws IOException, SearchClientException {
        this.url = url;
    }

    @Override
    public ResultSet search(QueryRequest query) throws SearchClientException, IOException {

        String url = generateRequestURL(query);
        long start = System.currentTimeMillis();
        JsonElement jsonElement = getJson(url);
        long elapsed = System.currentTimeMillis() - start;
        return translateResponse(elapsed, jsonElement);
    }


    private ResultSet translateResponse(long totalTime, JsonElement root) throws IOException {
        //TODO: figure out what queryTime means/is as diff from total
        long queryTime = 0;
        List<String> ids = new ArrayList();
        JsonObject response = (JsonObject) ((JsonObject) root).get("response");
        long totalHits = response.get("numFound").getAsLong();
        if (response.has("docs")) {
            JsonArray docs = (JsonArray) response.get("docs");
            for (JsonElement docElement : docs) {
                String id = ((JsonObject) docElement).get("id").getAsString();
                ids.add(id);
            }
        }
        return new ResultSet(totalHits, queryTime, totalTime, ids);
    }

    private String generateRequestURL(QueryRequest query) {
        StringBuilder sb = new StringBuilder();
        sb.append(url);
        if (!url.endsWith("/")) {
            sb.append("/");
        }
        String handler = query.getCustomHandler();
        handler = StringUtils.isBlank(handler) ? DEFAULT_HANDLER : handler;
        sb.append(handler);
        sb.append("?");

        /* TODO: make this configurable; turn off for now
            if (!StringUtils.isBlank(query.getCustomHandler())) {
                sb.append("qq=");
            } else { */
        sb.append("q=");

        sb.append(encode(query.getQuery()));
        if (!query.getParameters().containsKey("defType")) {
            sb.append("&defType=edismax");
        }
        if (query.getFields().size() > 0) {
            sb.append("&fl=").append(StringUtils.join(query.getFields(), ','));
        }
        if (!query.getParameters().containsKey("start")) {
            sb.append("&start=0");
        }
        sb.append("&rows=" + query.getNumResults());
        for (Map.Entry<String, List<String>> e : query.getParameters().entrySet()) {
            for (String value : e.getValue()) {
                sb.append("&");
                sb.append(encode(e.getKey()));
                sb.append("=").append(encode(value));
            }
        }
        /*
        //        solrQuery.setFacetMissing(true);
        solrQuery.setFacet(true);
        solrQuery.setFacetLimit(10000);
         */

        if (query.getFacetField() != null) {
            sb.append("&facet=true");
            //TODO: parameterize
            sb.append("&facet.missing=true");
            sb.append("&facet.limit=100000");
            sb.append("&facet.field=").append(encode(query.getFacetField()));
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
        JsonObject response = (JsonObject) ((JsonObject) root).get("response");
        long totalDocs = response.get("numFound").getAsLong();
        JsonObject facetCounts = (JsonObject) ((JsonObject) root).get("facet_counts");
        JsonObject facetFields = (JsonObject) facetCounts.get("facet_fields");
        //TODO: expand to a list in the future
        String facetField = query.getFacetField();
        JsonArray arr = (JsonArray) facetFields.get(facetField);

        Map<String, Long> counts = new HashMap<>();
        for (int i = 0; i < arr.size() - 1; i += 2) {
            JsonElement valueElement = arr.get(i);
            String value = null;
            if (valueElement.isJsonNull()) {
                value = "null";
            } else {
                value = valueElement.getAsString();
            }
            long count = arr.get(i + 1).getAsLong();
            counts.put(value, count);
        }
        return new FacetResult(totalDocs, counts);
    }

    @Override
    public void addDocuments(List<StoredDocument> buildDocuments) throws IOException, SearchClientException {
        List<Map<String, Object>> data = new ArrayList<>();
        for (StoredDocument d : buildDocuments) {
            data.add(d.getFields());
        }
        String json = GSON.toJson(data);
        JsonResponse response = postJson(url + "/update/json?commitWithin=10000", json);
        if (response.getStatus() != 200) {
            throw new SearchClientException(response.getMsg());
        }
    }

    @Override
    public List<StoredDocument> getDocs(String idField, Set<String> ids,
                                        Set<String> whiteListFields, Set<String> blackListFields) throws IOException, SearchClientException {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        sb.append(idField + ":(");
        for (String id : ids) {
            if (i++ > 0) {
                sb.append(" OR ");
            }
            sb.append("\"" + id + "\"");
        }

        sb.append(")");
        Map<String, String> qRequest = new HashMap<>();
        qRequest.put("query", sb.toString());
        qRequest.put("limit", Integer.toString(i + 10));
        if (whiteListFields.size() > 0) {
            String fields = StringUtils.join(whiteListFields, ",");
            qRequest.put("fields", fields);
        }
        String json = GSON.toJson(qRequest);
        JsonResponse fullResponse = postJson(url + "/select", json);
        if (fullResponse.getStatus() != 200) {
            LOG.warn("problem with " + url + " and " + json);
            return Collections.EMPTY_LIST;
        }
        List<StoredDocument> documents = new ArrayList<>();
        JsonElement root = fullResponse.getJson();
        JsonObject response = (JsonObject) ((JsonObject) root).get("response");
        long totalHits = response.get("numFound").getAsLong();
        if (response.has("docs")) {
            JsonArray docs = (JsonArray) response.get("docs");
            for (JsonElement docElement : docs) {
                StoredDocument document = new StoredDocument();
                JsonObject docObj = (JsonObject) docElement;
                for (String key : docObj.keySet()) {
                    if (!blackListFields.contains(key)) {
                        JsonElement value = docObj.get(key);
                        if (value.isJsonArray()) {
                            for (int j = 0; j < ((JsonArray) value).size(); j++) {
                                document.addNonBlankField(key, ((JsonArray) value).get(j).getAsString());
                            }
                        } else {
                            document.addNonBlankField(key, value.getAsString());
                        }
                    }
                }
                documents.add(document);
            }
        }
        return documents;
    }


    @Override
    public Set<String> getCopyFields() throws IOException, SearchClientException {
        StringBuilder sb = new StringBuilder();
        sb.append(url);
        if (!url.endsWith("/")) {
            sb.append("/");
        }
        sb.append("schema/copyfields?wt=json");
        JsonElement root = getJson(sb.toString());
        JsonArray copyFields = (JsonArray) ((JsonObject) root).get("copyFields");
        Set<String> dests = new HashSet<>();
        for (int i = 0; i < copyFields.size(); i++) {
            JsonObject entry = (JsonObject) copyFields.get(i);
            String dest = entry.get("dest").getAsString();
            dests.add(dest);
        }
        return dests;
    }

    @Override
    public synchronized String getIdField() throws IOException, SearchClientException {
        if (idField == null) {
            JsonElement root = getJson(url + "/schema/uniquekey");
            idField = ((JsonObject) root).get("uniqueKey").getAsString();
        }
        return idField;
    }

    @Override
    public void deleteAll() throws SearchClientException, IOException {
        String json = "{ \"delete\": {\"query\":\"*:*\"} }";
        postJson(url + "/update?&commitWithin=10000", json);
    }

    @Override
    public IdGrabber getIdGrabber(ArrayBlockingQueue<Set<String>> ids, int batchSize,
                                  int copierThreads, Collection<String> filterQueries) throws IOException, SearchClientException {
        return new SolrIdGrabber(getIdField(), ids, batchSize, copierThreads, filterQueries);
    }

    @Override
    public Set<String> getSystemInternalFields() {
        return SYS_INTERNAL_FIELDS;
    }

    class SolrIdGrabber extends IdGrabber {

        public SolrIdGrabber(String idField, ArrayBlockingQueue<Set<String>> ids, int batchSize, int copierThreads, Collection<String> filterQueries) {
            super(idField, ids, batchSize, copierThreads, filterQueries);
        }

        @Override
        public Integer call() throws Exception {
            int start = 0;
            int totalAdded = 0;
            int idSize = 10000;
            try {
                QueryRequest queryRequest = buildQueryRequest(idField, start, idSize, filterQueries);
                ResultSet rs = search(queryRequest);
                while (rs.size() > 0) {
                    Set<String> set = new HashSet<>();
                    for (int i = 0; i < rs.size(); i++) {
                        set.add(rs.get(i));
                        if (set.size() > batchSize) {
                            totalAdded += addSet(set);
                            set = new HashSet<>();
                        }
                    }
                    if (set.size() > 0) {
                        totalAdded += addSet(set);
                    }
                    LOG.info("ids added: " + totalAdded);
                    start += idSize;
                    queryRequest = buildQueryRequest(idField, start, idSize, filterQueries);
                    rs = search(queryRequest);
                }
                LOG.debug("id grabber is finishing" + start);
            } finally {
                addPoison();
            }
            return -1;
        }

        private QueryRequest buildQueryRequest(String idField, int start, int numResults, Collection<String> filterQueries) {
            QueryRequest queryRequest = new QueryRequest("*:*");
            queryRequest.setNumResults(numResults);

            queryRequest.addParameter("start", Integer.toString(start));
            for (String fq : filterQueries) {
                queryRequest.addParameter("fq", fq);
            }
            queryRequest.addParameter("sort", idField + " asc");
            queryRequest.addParameter("fl", idField);
            return queryRequest;
        }


        private int addSet(Set<String> set) throws InterruptedException {
            int sz = set.size();
            boolean added = ids.offer(set, 1, TimeUnit.SECONDS);
            LOG.debug("id adder: " + added + " " + ids.size());
            while (!added) {
                added = ids.offer(set, 1, TimeUnit.SECONDS);
                LOG.debug("waiting to add");
            }
            return sz;
        }
    }


}
