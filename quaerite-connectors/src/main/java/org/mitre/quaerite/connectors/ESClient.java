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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mitre.quaerite.core.FacetResult;
import org.mitre.quaerite.core.ResultSet;
import org.mitre.quaerite.core.features.WeightableField;
import org.mitre.quaerite.core.queries.BooleanClause;
import org.mitre.quaerite.core.queries.BooleanQuery;
import org.mitre.quaerite.core.queries.BoostingQuery;
import org.mitre.quaerite.core.queries.LuceneQuery;
import org.mitre.quaerite.core.queries.MatchAllDocsQuery;
import org.mitre.quaerite.core.queries.MultiMatchQuery;
import org.mitre.quaerite.core.queries.Query;
import org.mitre.quaerite.core.queries.TermQuery;
import org.mitre.quaerite.core.queries.TermsQuery;
import org.mitre.quaerite.core.stats.TokenDF;
import org.mitre.quaerite.core.util.JsonUtil;

public class ESClient extends SearchClient {
    private static final String _ID = "_id";
    private static final String _DOC = "_doc";
    private static final Gson GSON = new Gson();

    private static Set<String> SYS_INTERNAL_FIELDS;

    static {
        Set<String> tmp = new HashSet<>();
        tmp.add("_version_");
        SYS_INTERNAL_FIELDS = Collections.unmodifiableSet(tmp);
    }


    static Logger LOG = Logger.getLogger(ESClient.class);

    private final String url;//must include esbase and es collection; must end in /
    private final String esBase;//must end in /
    private final String esCollection;//has no /


    public ESClient(String url) {
        String tmp = url;
        if (!url.endsWith("/")) {
            tmp = tmp + "/";
        }
        this.url = tmp;
        String base = tmp.substring(0, tmp.length() - 1);
        int indexOf = base.lastIndexOf("/");
        if (indexOf < 0) {
            throw new IllegalArgumentException("can't find / before collection name; " +
                    "should be, e.g.: http://localhost:9200/my_collection");
        }
        this.esBase = base.substring(0, indexOf + 1);
        this.esCollection = base.substring(indexOf + 1);
    }

    @Override
    public ResultSet search(QueryRequest query) throws SearchClientException, IOException {
        long start = System.currentTimeMillis();
        String jsonQuery = buildJsonQuery(query, Collections.EMPTY_LIST);
        JsonResponse json = postJson(url + "_search", jsonQuery);
        if (json.getStatus() != 200) {
            throw new SearchClientException(json.getMsg());
        }
        JsonElement root = json.getJson();
        return scrapeIds(root, start);
    }

    private ResultSet scrapeIds(JsonElement root, long start) throws IOException, SearchClientException {
        long queryTime = JsonUtil.getPrimitive(root, "took", -1l);
        JsonObject hits = (JsonObject) ((JsonObject) root).get("hits");
        long totalHits = getTotalHits(hits);
        JsonArray hitArray = (JsonArray) hits.get("hits");
        List<String> ids = new ArrayList<>();
        for (JsonElement el : hitArray) {
            JsonObject hit = (JsonObject) el;
            String id = JsonUtil.getPrimitive(hit, getDefaultIdField(), "");
            if (!StringUtils.isBlank(id)) {
                ids.add(id);
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        return new ResultSet(totalHits, queryTime, elapsed, ids);

    }

    protected long getTotalHits(JsonObject hits) {
        JsonObject total = hits.getAsJsonObject("total");
        long val = total.get("value").getAsJsonPrimitive().getAsLong();
        String rel = total.get("relation").getAsString();
        if (!rel.equals("eq")) {
            LOG.warn("totalhits may not be accurate: "+total.toString());
        }
        return val;
    }

    private String buildJsonQuery(QueryRequest query, List<String> fieldsToRetrieve) {
        Map<String, Object> queryMap = getQueryMap(query, fieldsToRetrieve);
        String json = GSON.toJson(queryMap);
        return json;
    }

    protected Map<String, Object> getQueryMap(QueryRequest queryRequest, List<String> fieldsToRetrieve) {
        Query fullQuery = queryRequest.getQuery();
        Query q = queryRequest.getQuery();
        if (queryRequest.getFilterQueries().size() > 0) {
            fullQuery = new BooleanQuery();
            ((BooleanQuery)fullQuery).addClause(
                    new BooleanClause(null, BooleanClause.OCCUR.SHOULD, q));
            for (Query filterQuery : queryRequest.getFilterQueries()) {
                ((BooleanQuery)fullQuery).addClause(
                        new BooleanClause(null, BooleanClause.OCCUR.FILTER, filterQuery));
            }
        }

        Map<String, Object> queryMap = buildQuery(fullQuery);
        Map<String, Object> overallMap = wrapAMap("query", queryMap);

        if (fieldsToRetrieve.size() > 0) {
            overallMap.put("_source", fieldsToRetrieve);
        }
        overallMap.put("size", queryRequest.getNumResults());
        overallMap.put("from", queryRequest.getStart());
        overallMap.put("track_total_hits", true);
        return overallMap;
    }

    private Map<String, Object> buildQuery(Query query) {
        Map<String, Object> queryMap = new HashMap<>();
        if (query instanceof MultiMatchQuery) {
            queryMap = getMultiMatchMap((MultiMatchQuery)query);
        } else if (query instanceof TermsQuery) {
            Map<String, List<String>> tQ = new HashMap<>();
            TermsQuery termsQuery = (TermsQuery)query;
            tQ.put(termsQuery.getField(), termsQuery.getTerms());
            queryMap = wrapAMap("terms", tQ);
        } else if (query instanceof TermQuery) {
            Map<String, String> tQ = new HashMap<>();
            TermQuery termQuery = (TermQuery)query;
            tQ.put(termQuery.getField(), termQuery.getTerm());
            queryMap = wrapAMap("term", tQ);
        } else if (query instanceof MatchAllDocsQuery) {
            queryMap = wrapAMap("match_all", Collections.EMPTY_MAP);
        } else if (query instanceof LuceneQuery) {
            //TODO -- replace this with a true query_string query
            //that allows multiple fields, etc.
            Map<String, String> lQ = new HashMap<>();
            LuceneQuery luceneQuery = (LuceneQuery) query;
            lQ.put("default_field", luceneQuery.getDefaultField());
            lQ.put("query", luceneQuery.getQueryString());
            lQ.put("default_operator", luceneQuery.getQueryOperator().toString());
            queryMap = wrapAMap("query_string", lQ);
        } else if (query instanceof BooleanQuery) {
            return getBooleanMap((BooleanQuery)query);
        } else if (query instanceof BoostingQuery) {
            return getBoostingMap((BoostingQuery)query);
        } else {
            throw new IllegalArgumentException("I regret I don't yet know how to handle queries of type: "+ query.getClass());
        }
        return queryMap;
    }

    private Map<String, Object> getBoostingMap(BoostingQuery query) {
        //short circuit if there is no negative boost query
        if (StringUtils.isBlank(query.getNegativeQuery().getQueryString())) {
            return buildQuery(query.getPositiveQuery());
        }
        Map<String, Object> queryMap = wrapAMap(
                "positive", buildQuery(query.getPositiveQuery()),
                "negative", buildQuery(query.getNegativeQuery())
        );
        queryMap.put("negative_boost", query.getNegativeBoost().getValue());
        return wrapAMap("boosting", queryMap);
    }

    private Map<String, Object> getBooleanMap(BooleanQuery bq) {
        Map<String, Object> queryMap = new LinkedHashMap<>();
        for (BooleanClause.OCCUR occur : BooleanClause.OCCUR.values()) {
            List<Map<String, Object>> clauses = new ArrayList<>();
            for (BooleanClause clause : bq.get(occur)) {
                clauses.add(buildQuery(clause.getQuery()));
            }
            if (clauses.size() > 0) {
                queryMap.put(occur.toString().toLowerCase(Locale.US), clauses);
            }
        }
        return wrapAMap("bool", queryMap);
    }

    private Map<String, Object> getMultiMatchMap(MultiMatchQuery query) {
        String type = query.getMultiMatchType().getFeature();
        Map<String, Object> queryMap = new LinkedHashMap<>();
        queryMap.put("query", query.getQueryString());
        queryMap.put("type", type);
        List<String> fields = new ArrayList<>();
        for (WeightableField f : query.getQF().getWeightableFields()) {
            fields.add(f.toString());
        }
        queryMap.put("fields", fields);
        if (query.getTie().getValue() > 0.0f) {
            queryMap.put("tie_breaker", query.getTie().getValue());
        }
        if (query.getBoost().getValue() != 1.0f) {
            queryMap.put("boost", query.getBoost().getValue());
        }
        if (!"phrase".equals(type) && !"cross_fields".equals(type)) {
            if (query.getFuzziness().getValue() > 0.0f) {
                queryMap.put("fuzziness", query.getFuzziness().getValue());
            }
        }

        return wrapAMap("multi_match", queryMap);
    }

    @Override
    public FacetResult facet(QueryRequest query) throws SearchClientException, IOException {
        String jsonRequest = buildFacetRequest(query);
        JsonResponse jsonResponse = postJson(url + "_search", jsonRequest);
        if (jsonResponse.getStatus() != 200) {
            throw new SearchClientException(jsonResponse.getMsg());
        }
        JsonObject root = (JsonObject) jsonResponse.getJson();
        long totalDocs = JsonUtil.getPrimitive(root.get("hits"), "total", -1l);
        JsonObject aggs = (JsonObject) root.get("aggregations");
        JsonObject fieldAggs = (JsonObject) aggs.get(query.getFacetField());
        JsonArray buckets = fieldAggs.getAsJsonArray("buckets");
        Map<String, Long> counts = new HashMap<>();
        for (JsonElement el : buckets) {
            String key = JsonUtil.getPrimitive(el, "key", "");
            long cnt = JsonUtil.getPrimitive(el, "doc_count", -1l);
            counts.put(key, cnt);
        }
        return new FacetResult(totalDocs, counts);
    }

    private String buildFacetRequest(QueryRequest query) {
        Map<String, Object> aggsMap =
                wrapAMap("aggregations",
                        wrapAMap(query.getFacetField(),
                                wrapAMap("terms",
                                        wrapAMap("field", query.getFacetField(),
                                                "missing", "null",
                                                "min_doc_count", "0",
                                                "size", Integer.toString(query.getFacetLimit())
                                        )
                                )
                        )
                );
        aggsMap.put("size", "0");

        if (query.getQuery() != null && ! (query.getQuery() instanceof MatchAllDocsQuery)) {
            Map<String, Object> queryMap = getQueryMap(query, Collections.EMPTY_LIST);
            aggsMap.put("query", queryMap.get("query"));
        }
        return GSON.toJson(aggsMap);
    }

    @Override
    public void addDocuments(List<StoredDocument> documents) throws IOException, SearchClientException {
        StringBuilder sb = new StringBuilder();
        for (StoredDocument sd : documents) {
            Map<String, Object> fields = sd.getFields();
            Map<String, Object> tmp = new HashMap<>(fields);
            String id = (String) tmp.remove(_ID);
            String indexJson = getBulkIndexJson(id);
            sb.append(indexJson).append("\n");
            sb.append(GSON.toJson(tmp)).append("\n");
        }
        JsonResponse response = postJson(url + "/_bulk", sb.toString());
        if (response.getStatus() != 200) {
            throw new SearchClientException(response.getMsg());
        }
    }

    private String getBulkIndexJson(String id) {
        JsonObject innerObject = new JsonObject();
        innerObject.add("_type", new JsonPrimitive(_DOC));
        innerObject.add(_ID, new JsonPrimitive(id));
        JsonObject outerObject = new JsonObject();
        outerObject.add("index", innerObject);
        return outerObject.toString();
    }

    @Override
    public List<StoredDocument> getDocs(String idField, Set<String> ids, Set<String> whiteListFields, Set<String> blackListFields) throws IOException, SearchClientException {
        Map<String, Object> map = wrapAMap("ids", ids);
        String storedFields = "";

        if (whiteListFields.size() > 0) {
            storedFields = "?_source=" +
                    encode(StringUtils.join(whiteListFields, ','));
        }

        JsonResponse response = postJson(url + "/_doc/_mget" + storedFields, GSON.toJson(map));
        if (response.getStatus() != 200) {
            throw new SearchClientException(response.getMsg());
        }
        JsonArray docs = (JsonArray) ((JsonObject) response.getJson()).get("docs");
        List<StoredDocument> documents = new ArrayList<>();
        for (JsonElement el : docs) {
            String id = JsonUtil.getPrimitive(el, _ID, "");
            StoredDocument document = new StoredDocument();
            document.addNonBlankField("id", id);
            JsonObject src = (JsonObject) ((JsonObject) el).get("_source");
            for (String k : src.keySet()) {
                if (!blackListFields.contains(k)) {
                    JsonElement v = src.get(k);
                    if (v.isJsonPrimitive()) {
                        document.addNonBlankField(k, v.getAsString());
                    } else if (v.isJsonArray()) {
                        for (String val : JsonUtil.jsonArrToStringList(v)) {
                            document.addNonBlankField(k, val);
                        }
                    }
                }
            }
            documents.add(document);
        }
        return documents;
    }

    @Override
    public Collection<? extends String> getCopyFields() throws IOException, SearchClientException {

        //what do we need to do to make this more robust and/or handle wildcarding of templates?
        JsonResponse response = getJson(esBase + "_template/" + esCollection);
        if (response.getStatus() != 200) {
            throw new SearchClientException(response.getMsg());
        }
        JsonElement root = response.getJson();
        if (!root.isJsonObject()) {
            return Collections.EMPTY_SET;
        }
        JsonElement collectionRoot = root.getAsJsonObject().get(esCollection);
        if (!collectionRoot.isJsonObject()) {
            return Collections.EMPTY_SET;
        }
        JsonObject mappings = (JsonObject) collectionRoot.getAsJsonObject().get("mappings");
        Set<String> destFields = new HashSet<>();
        addValuesForKey(mappings, "copy_to", destFields);
        return destFields;
    }

    @Override
    public String getDefaultIdField() throws IOException, SearchClientException {
        return _ID;
    }

    private void addValuesForKey(JsonObject mappings, String key, Set<String> values) {
        if (mappings == null || mappings.isJsonNull()) {
            return;
        }
        for (String k : mappings.keySet()) {
            if (k.equals(key)) {
                //could be anything else!
                JsonElement el = mappings.get(k);
                if (el.isJsonPrimitive()) {
                    values.add(mappings.get(k).getAsJsonPrimitive().getAsString());
                } else if (el.isJsonArray()) {
                    for (JsonElement el2 : el.getAsJsonArray()) {
                        values.add(el2.getAsJsonPrimitive().getAsString());
                    }
                }
            } else if (mappings.get(k).isJsonObject()) {
                addValuesForKey(mappings.get(k).getAsJsonObject(), key, values);
            }
        }
    }



    @Override
    public void deleteAll() throws SearchClientException, IOException {
        Map<String, Object> q = wrapAMap("query",
                wrapAMap("match_all", Collections.EMPTY_MAP));
        JsonResponse response = postJson(url + "_delete_by_query", GSON.toJson(q));
        if (response.getStatus() != 200) {
            throw new SearchClientException(response.getMsg());
        }
    }

    @Override
    public IdGrabber getIdGrabber(ArrayBlockingQueue<Set<String>> ids,
                                  int batchSize, int copierThreads, Collection<Query> filterQueries) throws IOException, SearchClientException {
        return new ESIdGrabber(getDefaultIdField(), ids, batchSize, copierThreads, filterQueries);
    }

    @Override
    public Set<String> getSystemInternalFields() {
        return SYS_INTERNAL_FIELDS;
    }

    @Override
    public List<String> analyze(String field, String string) {
        //todo stub
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<TokenDF> getTerms(String field, String lower, int limit, int minCount) throws IOException, SearchClientException {
        //todo stub
        return Collections.EMPTY_LIST;
    }

    protected String getESBase() {
        return esBase;
    }

    protected String getESCollection() {
        return esCollection;
    }

    protected String getUrl() {
        return url;
    }

    //list: String, Object, String, Object,
    //where the String is the key and the object is the value
    private Map<String, Object> wrapAMap(Object... args) {
        Map<String, Object> ret = new HashMap<>();
        for (int i = 0; i < args.length - 1; i += 2) {
            String key = (String) args[i];
            Object value = args[i + 1];
            ret.put(key, value);
        }
        return ret;
    }

    private class ESIdGrabber extends IdGrabber {

        public ESIdGrabber(String idField, ArrayBlockingQueue<Set<String>> ids, int batchSize,
                           int copierThreads, Collection<Query> filterQueries) {
            super(idField, ids, batchSize, copierThreads, filterQueries);
        }

        @Override
        public Integer call() throws Exception {
            try {
                Map<String, Object> q = wrapAMap("query",
                        wrapAMap("match_all", Collections.EMPTY_MAP));
                q.put("size", Integer.toString(batchSize));
                q.put("stored_fields", Collections.EMPTY_LIST);
                JsonResponse response = null;
                response = postJson(url + "_search?scroll=5m", GSON.toJson(q));
                JsonObject root = (JsonObject) response.getJson();
                String scrollId = JsonUtil.getPrimitive(root, "_scroll_id", "");
                ResultSet resultSet = scrapeIds(root, System.currentTimeMillis());

                Map<String, String> nextScroll = new HashMap<>();
                nextScroll.put("scroll", "5m");
                nextScroll.put("scroll_id", scrollId);

                while (resultSet.size() > 0) {
                    Set<String> set = new HashSet<>();
                    set.addAll(resultSet.getIds());
                    LOG.debug("adding "+set.size());
                    addSet(ids, set);
                    String u = esBase + "_search/scroll";
                    response = postJson(u, GSON.toJson(nextScroll));
                    root = (JsonObject) response.getJson();
                    resultSet = scrapeIds(root, System.currentTimeMillis());
                }
            } finally {
                LOG.debug("id grabber adding poison");
                addPoison();
            }
            return -1;

        }

    }
}
