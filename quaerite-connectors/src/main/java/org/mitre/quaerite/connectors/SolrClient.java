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

import static org.mitre.quaerite.core.features.CustomHandler.DEFAULT_HANDLER;

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
import java.util.Locale;
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
import org.mitre.quaerite.core.SearchResultSet;
import org.mitre.quaerite.core.features.CustomHandler;
import org.mitre.quaerite.core.features.QF;
import org.mitre.quaerite.core.features.QueryOperator;
import org.mitre.quaerite.core.features.WeightableField;
import org.mitre.quaerite.core.queries.DisMaxQuery;
import org.mitre.quaerite.core.queries.EDisMaxQuery;
import org.mitre.quaerite.core.queries.LuceneQuery;
import org.mitre.quaerite.core.queries.MatchAllDocsQuery;
import org.mitre.quaerite.core.queries.Query;
import org.mitre.quaerite.core.queries.TermQuery;
import org.mitre.quaerite.core.queries.TermsQuery;
import org.mitre.quaerite.core.stats.TokenDF;

/**
 * This should work with versions >= Solr 7.x
 */
public class SolrClient extends SearchClient {

    protected static final String JSON_RESPONSE = "&wt=json";
    private static Set<String> SYS_INTERNAL_FIELDS;

    static {
        Set<String> tmp = new HashSet<>();
        tmp.add("_version_");
        SYS_INTERNAL_FIELDS = Collections.unmodifiableSet(tmp);
    }

    static Logger LOG = Logger.getLogger(SolrClient.class);

    static final Gson GSON = new Gson();
    private static String DEFAULT_ID_FIELD = "id";

    final String url;
    String idField;

    /**
     * @param url url to Solr including /collection
     */
    protected SolrClient(String url) throws IOException, SearchClientException {
        this.url = url;
    }

    @Override
    public SearchResultSet search(QueryRequest query) throws SearchClientException, IOException {

        String url = generateRequestURL(query);
        if (LOG.isTraceEnabled()) {
            LOG.trace(url);
        }
        long start = System.currentTimeMillis();
        JsonResponse response = getJson(url);
        if (LOG.isTraceEnabled()) {
            LOG.trace(response);
        }
        if (response.getStatus() != 200) {
            throw new SearchClientException(response.getMsg());
        }
        long elapsed = System.currentTimeMillis() - start;
        return translateResponse(elapsed, response.getJson());
    }


    private SearchResultSet translateResponse(long totalTime, JsonElement root) throws IOException {
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
        return new SearchResultSet(totalHits, queryTime, totalTime, ids);
    }

    String generateRequestURL(QueryRequest queryRequest) {
        StringBuilder sb = new StringBuilder();
        sb.append(url);
        if (!url.endsWith("/")) {
            sb.append("/");
        }
        CustomHandler handler = queryRequest.getCustomHandler();
        handler = (handler == null) ? DEFAULT_HANDLER : handler;
        sb.append(handler.getHandler());
        sb.append("?");

        /* TODO: make this configurable; turn off for now
            if (!StringUtils.isBlank(queryRequest.getCustomHandler())) {
                sb.append("qq=");
            } else { */
        Query query = queryRequest.getQuery();
        if (query instanceof EDisMaxQuery) {
            addEdisMaxParams((EDisMaxQuery) query, handler, sb);
        } else if (query instanceof DisMaxQuery) {
            sb.append("defType=dismax");
            addDisMaxParams((DisMaxQuery) query, handler, sb);
        } else if (query instanceof MatchAllDocsQuery) {
            sb.append("&q=").append(encode("*:*"));
        } else if (query instanceof LuceneQuery) {
            sb.append("&q=");
            appendLuceneQuery((LuceneQuery) query, sb);
        } else if (query instanceof TermsQuery) {
            sb.append("&q=");
            appendTermsQuery((TermsQuery) query, sb);
        } else if (query instanceof TermQuery) {
            sb.append("&q=");
            appendTermQuery((TermQuery) query, sb);
        } else {
            throw new IllegalArgumentException("Sorry, I don't yet support: " + queryRequest.getQuery());
        }

        if (queryRequest.getFieldsToRetrieve().size() > 0) {
            sb.append("&fl=").append(StringUtils.join(queryRequest.getFieldsToRetrieve(), ','));
        }
        sb.append("&start=").append(queryRequest.getStart());

        sb.append("&rows=" + queryRequest.getNumResults());

        if (queryRequest.getSortField() != null) {
            sb.append("&sort=").append(encode(queryRequest.getSortField())).append(encode(" "))
                    .append(queryRequest.getSortOrder().toString().toLowerCase(Locale.US));
        }
        if (queryRequest.getFilterQueries().size() > 0) {
            for (Query q : queryRequest.getFilterQueries()) {
                appendFilterQuery(q, sb);
            }
        }

        if (queryRequest.getFacetField() != null) {
            sb.append("&facet=true");
            //TODO: parameterize
            sb.append("&facet.missing=true");
            sb.append("&facet.limit=100000");
            sb.append("&facet.field=").append(encode(queryRequest.getFacetField()));
        }
        sb.append(JSON_RESPONSE);
        return sb.toString();
    }

    private void appendFilterQuery(Query q, StringBuilder sb) {
        sb.append("&fq=");
        if (q instanceof TermsQuery) {
            appendTermsQuery((TermsQuery) q, sb);
        } else if (q instanceof TermQuery) {
            appendTermQuery((TermQuery) q, sb);
        } else if (q instanceof LuceneQuery) {
            appendLuceneQuery((LuceneQuery) q, sb);
        }
    }

    private void appendLuceneQuery(LuceneQuery q, StringBuilder sb) {
        StringBuilder tmp = new StringBuilder("{!lucene");
        if (! StringUtils.isBlank(q.getDefaultField())) {
            tmp.append(" df=").append(q.getDefaultField());
        }
        tmp.append(" q.op=");
        tmp.append(q.getQueryOperator()).append("}").append(q.getQueryString());
        sb.append(encode(tmp.toString()));
    }

    private void appendTermQuery(TermQuery q, StringBuilder sb) {
        StringBuilder tmp = new StringBuilder("{!raw f=")
                .append(q.getField()).append("}").append(q.getTerm());
        sb.append(encode(tmp.toString()));
    }

    protected void appendTermsQuery(TermsQuery tq, StringBuilder sb) {
        StringBuilder tmp = new StringBuilder("{!terms f=").append(tq.getField()).append("}");
        tmp.append(StringUtils.join(tq.getTerms(), ","));
        sb.append(encode(tmp.toString()));
    }

    private void addEdisMaxParams(EDisMaxQuery query, CustomHandler handler, StringBuilder sb) {
        sb.append("defType=edismax");
        int i = 0;
        if (query.getPf2() != null) {
            sb.append("&pf2=");
            for (WeightableField f : query.getPf2().getWeightableFields()) {
                if (i++ > 0) {
                    sb.append(encode(" "));
                }
                sb.append(encode(f.toString()));
            }
        }
        i = 0;
        if (query.getPf3() != null) {
            sb.append("&pf3=");
            for (WeightableField f : query.getPf3().getWeightableFields()) {
                if (i++ > 0) {
                    sb.append(encode(" "));
                }
                sb.append(encode(f.toString()));
            }
        }
        if (query.getPs2() != null) {
            sb.append("&ps2=" + query.getPs2().getValue());
        }
        if (query.getPs3() != null) {
            sb.append("&ps2=" + query.getPs3().getValue());
        }

        addDisMaxParams((DisMaxQuery) query, handler, sb);
    }

    private void addDisMaxParams(DisMaxQuery query, CustomHandler handler,
                                 StringBuilder sb) {

        sb.append("&").append(handler.getCustomQueryKey())
                .append("=").append(encode(query.getQueryString()));
        QF qf = query.getQF();
        sb.append("&qf=");
        int i = 0;
        for (WeightableField f : qf.getWeightableFields()) {
            if (i++ > 0) {
                sb.append(encode(" "));
            }
            sb.append(encode(f.toString()));
        }
        if (query.getTie() != null) {
            sb.append("&tie=").append(query.getTie().toString());
        }
        QueryOperator qop = query.getQueryOperator();
        if (qop.getOperator() == QueryOperator.OPERATOR.UNSPECIFIED) {
            return;
        } else if (qop.getOperator() == QueryOperator.OPERATOR.AND) {
            sb.append("&q.op=AND");
        } else {
            sb.append("&q.op=OR");
            if (qop.getMM() == QueryOperator.MM.NONE) {
                return;
            } else if (qop.getMM() == QueryOperator.MM.INTEGER) {
                sb.append("&mm=").append(qop.getInt());
            } else if (qop.getMM() == QueryOperator.MM.FLOAT) {
                sb.append("&mm=").append(encode(
                        String.format(Locale.US,
                                "%.0f%s",
                                qop.getMmFloat() * 100f, "%")));
            }
        }
        if (query.getBQ() != null) {
            for (String bq : query.getBQ().getAll()) {
                sb.append("&bq=").append(encode(bq));
            }
        }
        if (query.getBF() != null) {
            for (String bf : query.getBF().getAll()) {
                sb.append("&bq=").append(encode(bf));
            }
        }
        i = 0;
        if (query.getPF() != null) {
            sb.append("&pf=");
            for (WeightableField f : query.getPF().getWeightableFields()) {
                if (i++ > 0) {
                    sb.append(encode(" "));
                }
                sb.append(encode(f.toString()));
            }
        }
        if (query.getPS() != null) {
            sb.append("&ps=" + query.getPS().getValue());
        }
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
        JsonResponse response = postJson(url +
                "/update/json?commitWithin=10000", json);
        if (response.getStatus() != 200) {
            throw new SearchClientException(response.getMsg());
        }
    }

    @Override
    public List<StoredDocument> getDocs(String idField, Set<String> ids,
                                        Set<String> whiteListFields,
                                        Set<String> blackListFields)
            throws IOException, SearchClientException {
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
        JsonResponse response = getJson(sb.toString());
        if (response.getStatus() != 200) {
            throw new SearchClientException(response.getMsg());
        }
        JsonElement root = response.getJson();
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
    public synchronized String getDefaultIdField()
            throws IOException, SearchClientException {
        if (idField == null) {
            JsonResponse jsonResponse = getJson(url + "/schema/uniquekey");
            if (jsonResponse.getStatus() != 200) {
                throw new SearchClientException(jsonResponse.getMsg());
            }
            JsonElement root = jsonResponse.getJson();
            idField = ((JsonObject) root).get("uniqueKey").getAsString();
        }
        return idField;
    }

    @Override
    public void deleteAll() throws SearchClientException, IOException {
        String json = "{ \"delete\": {\"query\":\"*:*\"} }";
        postJson(url + "/update?&commit=true", json);
    }

    @Override
    public IdGrabber getIdGrabber(ArrayBlockingQueue<Set<String>> ids, int batchSize,
                                  int copierThreads, Collection<Query> filterQueries)
            throws IOException, SearchClientException {
        return new SolrIdGrabber(getDefaultIdField(), ids,
                batchSize, copierThreads, filterQueries);
    }

    @Override
    public Set<String> getSystemInternalFields() {
        return SYS_INTERNAL_FIELDS;
    }

    @Override
    public List<String> analyze(String field, String string)
            throws IOException, SearchClientException {
        StringBuilder request = new StringBuilder();
        request.append(url);
        request.append("/analysis/field?wt=json")
                .append("&analysis.fieldname=").append(encode(field));
        request.append("&analysis.fieldvalue=").append(encode(string));
        JsonResponse jsonResponse = getJson(request.toString());
        if (jsonResponse.getStatus() != 200) {
            throw new SearchClientException(jsonResponse.getMsg());
        }
        JsonObject root = jsonResponse.getJson().getAsJsonObject();
        JsonObject analysisNode = root.get("analysis").getAsJsonObject();
        JsonObject fieldNamesNode = analysisNode.get("field_names").getAsJsonObject();
        JsonObject fieldNode = fieldNamesNode.get(field).getAsJsonObject();
        JsonArray indexArr = fieldNode.getAsJsonArray("index");
        JsonArray lastStep = indexArr.get(indexArr.size() - 1).getAsJsonArray();
        List<String> tokens = new ArrayList<>();
        for (JsonElement el : lastStep) {
            String t = el.getAsJsonObject().get("text").getAsString();
            tokens.add(t);
        }
        return tokens;
    }

    @Override
    public List<TokenDF> getTerms(String field, String lower,
                                  int limit, int minCount) throws IOException, SearchClientException {
        StringBuilder request = new StringBuilder();
        request.append(url).append("/terms?terms=true");
        request.append("&terms.fl=").append(encode(field));
        request.append("&limit=" + limit);
        if (!StringUtils.isBlank(lower)) {
            request.append("&terms.lower=").append(encode(lower));
        }
        if (minCount > 0) {
            request.append("&terms.mincount=" + minCount);
        }
        request.append("&terms.lower.incl=false");
        request.append("&terms.sort=index");
        request.append(JSON_RESPONSE);
        JsonResponse jsonResponse = getJson(request.toString());
        if (jsonResponse.getStatus() != 200) {
            throw new SearchClientException(jsonResponse.getMsg());
        }
        List<TokenDF> termDFList = new ArrayList<>();
        JsonObject termObj = jsonResponse.getJson().getAsJsonObject()
                .getAsJsonObject("terms");

        JsonArray fieldArr = termObj.getAsJsonArray(field);

        for (int i = 0; i < fieldArr.size(); i += 2) {
            termDFList.add(new TokenDF(
                    fieldArr.get(i).getAsString(),
                    fieldArr.get(i + 1).getAsLong()
            ));
        }

        return termDFList;
    }

    class SolrIdGrabber extends IdGrabber {

        public SolrIdGrabber(String idField, ArrayBlockingQueue<Set<String>> ids,
                             int batchSize, int copierThreads,
                             Collection<Query> filterQueries) {
            super(idField, ids, batchSize, copierThreads, filterQueries);
        }

        @Override
        public Integer call() throws Exception {
            int start = 0;
            int totalAdded = 0;
            int idSize = 10000;
            try {
                QueryRequest queryRequest =
                        buildQueryRequest(idField, start, idSize, filterQueries);
                SearchResultSet rs = search(queryRequest);
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
                    queryRequest = buildQueryRequest(idField, start, idSize,
                            filterQueries);
                    rs = search(queryRequest);
                }
                LOG.debug("id grabber is finishing" + start);
            } finally {
                addPoison();
            }
            return -1;
        }

        private QueryRequest buildQueryRequest(String idField, int start,
                                               int numResults,
                                               Collection<Query> filterQueries) {
            QueryRequest queryRequest = new QueryRequest(new MatchAllDocsQuery());
            queryRequest.setNumResults(numResults);

            queryRequest.setStart(start);
            queryRequest.addFilterQueries(filterQueries);
            queryRequest.setSort(idField, QueryRequest.SORT_ORDER.ASC);
            queryRequest.addFieldsToRetrieve(idField);
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
