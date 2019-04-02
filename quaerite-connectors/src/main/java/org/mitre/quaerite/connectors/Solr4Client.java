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
import org.mitre.quaerite.core.stats.TokenDF;

/**
 * This should work on versions of Solr back to Solr 4.x
 */
public class Solr4Client extends SolrClient {

    static Logger LOG = Logger.getLogger(Solr4Client.class);

    /**
     * @param url url to Solr including /collection
     */
    protected Solr4Client(String url) throws IOException, SearchClientException {
        super(url);
    }


    @Override
    public List<StoredDocument> getDocs(String idField, Set<String> ids,
                                        Set<String> whiteListFields, Set<String> blackListFields) throws IOException, SearchClientException {
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
                QueryRequest q = new QueryRequest(sb.toString());
                q.setNumResults(i);
                q.addFields(whiteListFields);
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
            QueryRequest q = new QueryRequest(sb.toString());
            q.setNumResults(i);
            q.addFields(whiteListFields);
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
        } catch (IOException|SearchClientException e) {
            LOG.warn("problem with " + url + " and " + requestUrl + " :: "+fullResponse.getMsg());
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
                                document.addNonBlankField(key, ((JsonArray) value).get(j).getAsString());
                            }
                        } else {
                            document.addNonBlankField(key, value.getAsString());
                        }
                    }
                }
                LOG.trace("getting doc from solr: "+document.getFields().get("id"));
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
        JsonResponse jsonResponse = postJson(url + "/update?&commit=true&stream.body="+encode(xml)+JSON_RESPONSE, xml);
        if (jsonResponse.getStatus() != 200) {
            throw new SearchClientException(jsonResponse.getMsg());
        }
    }
}
