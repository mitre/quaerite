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
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.log4j.Logger;
import org.mitre.quaerite.core.queries.LuceneQuery;
import org.mitre.quaerite.core.queries.TermsQuery;
import org.mitre.quaerite.core.util.ConnectionConfig;

/**
 * This should work on versions of Solr back to Solr 4.x
 */
public class Solr4Client extends SolrClient {

    static Logger LOG = Logger.getLogger(Solr4Client.class);

    private final int minorVersion;
    /**
     * @param url url to Solr including /collection
     */
    protected Solr4Client(ConnectionConfig connectionConfig, String url, int minorVersion) throws IOException, SearchClientException {
        super(connectionConfig, url);
        this.minorVersion = minorVersion;
    }


    @Override
    public List<StoredDocument> getDocs(String idField, Set<String> ids,
                                        Set<String> whiteListFields,
                                        Set<String> blackListFields)
            throws IOException, SearchClientException {
        //have to use old school url to make requests
        //because json request option isn't backwards compatible to 4.x
        //If we have different Solr clients supporting diff versions,
        //add in json for this for more modern versions of Solr.
        StringBuilder sb = new StringBuilder();
        List<StoredDocument> documents = new ArrayList<>();
        int i = 0;
        sb.append(idField + ":(");
        for (String id : ids) {
            if (i++ > 0) {
                sb.append(" OR ");
            }
            sb.append("\"" + id + "\"");
            if (sb.length() > 1000) {
                sb.append(")");
                QueryRequest q = new QueryRequest(new LuceneQuery(idField, sb.toString()));
                q.setNumResults(i);
                q.addFieldsToRetrieve(whiteListFields);
                String url = generateRequestURL(q);
                List<StoredDocument> localDocs = _getDocs(url, blackListFields);
                documents.addAll(localDocs);
                i = 0;
                sb.setLength(0);
                sb.append(idField + ":(");
            }
        }
        if (sb.length() > 0) {
            sb.append(")");
            QueryRequest q = new QueryRequest(new LuceneQuery(idField, sb.toString()));
            q.setNumResults(i);
            q.addFieldsToRetrieve(whiteListFields);
            String url = generateRequestURL(q);
            List<StoredDocument> localDocs = _getDocs(url, blackListFields);
            documents.addAll(localDocs);
        }
        return documents;
    }

    private List<StoredDocument> _getDocs(String requestUrl, Set<String> blackListFields) {
        List<StoredDocument> documents = new ArrayList<>();
        JsonResponse fullResponse = null;
        try {
            fullResponse = getJson(requestUrl);
        } catch (IOException | SearchClientException e) {
            LOG.warn("problem with " + baseUrl + " and " + requestUrl +
                    " :: " + fullResponse.getMsg());
            return Collections.EMPTY_LIST;
        }

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
                                document.addNonBlankField(key,
                                        ((JsonArray) value).get(j).getAsString());
                            }
                        } else {
                            document.addNonBlankField(key, value.getAsString());
                        }
                    }
                }
                LOG.trace("getting doc from solr: " +
                        document.getFields().get("id"));
                documents.add(document);
            }
        }
        return documents;
    }

    @Override
    public void deleteAll() throws SearchClientException, IOException {
        //json is not back compat to 4.5.x
        //String json = "{ \"delete\": {\"query\":\"*:*\"} }";
        String xml = "<delete><query>*:*</query></delete>";
        JsonResponse jsonResponse = postJson(baseUrl +
                "update?&commit=true&stream.body=" +
                encode(xml) + JSON_RESPONSE, xml);
        if (jsonResponse.getStatus() != 200) {
            throw new SearchClientException(jsonResponse.getMsg());
        }
    }

    /**
     * Actual terms query parser didn't come into Solr until 4.11
     * For compatibility with earlier 4.x, we must translate this to Lucene parser
     *
     * @param tq
     * @param sb
     */
    protected void appendTermsQuery(TermsQuery tq, StringBuilder sb) {
        StringBuilder tmp = new StringBuilder();
        // StringBuilder("{!lucene f=").append(tq.getField()).append("}");
        tmp.append("(");
        int i = 0;
        for (String t : tq.getTerms()) {
            if (i++ > 0) {
                tmp.append(" ");
            }
            tmp.append(tq.getField()).append(":");
            tmp.append("\"").append(t).append("\"");
        }
        tmp.append(")");
        sb.append(encode(tmp.toString()));
        sb.append("&q.op=OR");
    }

    @Override
    public Set<String> getCopyFields() throws IOException, SearchClientException {
        //is this when the schema api was introduced?
        //TODO: if necessary, figure out how to do this back in the day
        if (minorVersion < 10) {
            LOG.warn("can't get copy fields via schema in < 4.10");
            return Collections.EMPTY_SET;
        }
        return super.getCopyFields();
    }

    @Override
    public synchronized String getDefaultIdField()
            throws IOException, SearchClientException {
        //is this when the schema api was introduced?
        //TODO: if necessary, figure out how to do this back in the day
        if (minorVersion < 10) {
            LOG.warn("Can't get default id field with schema API, returning 'id'");
            return "id";
        }
        return super.getDefaultIdField();
    }
}
