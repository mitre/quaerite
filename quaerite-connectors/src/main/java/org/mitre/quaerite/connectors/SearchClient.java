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

import java.io.Closeable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.mitre.quaerite.core.FacetResult;
import org.mitre.quaerite.core.ResultSet;

public abstract class SearchClient implements Closeable {

    public abstract ResultSet search(QueryRequest query) throws SearchClientException, IOException;
    public abstract FacetResult facet(QueryRequest query) throws SearchClientException, IOException;

    private final CloseableHttpClient httpClient;

    public SearchClient() {
        httpClient = HttpClients.createDefault();
    }
    protected byte[] get(String url) throws SearchClientException {
        //overly simplistic...need to add proxy, etc., but good enough for now
        URI uri = null;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        HttpHost target = new HttpHost(uri.getHost(), uri.getPort());
        HttpGet httpGet = null;
        try {
            String get = uri.getPath();
            if (!StringUtils.isBlank(uri.getQuery())) {
                get += "?"+uri.getRawQuery();
            }
            httpGet = new HttpGet(get);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        //this is required because of connection already bound exceptions
        //on windows. :(
        //httpGet.setHeader("Connection", "close");

        //try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            try(CloseableHttpResponse httpResponse = httpClient.execute(target, httpGet)) {
                if (httpResponse.getStatusLine().getStatusCode() != 200) {
                    EntityUtils.consumeQuietly(httpResponse.getEntity());
                    throw new SearchClientException("Bad status code: "+httpResponse.getStatusLine().getStatusCode());
                }
                return EntityUtils.toByteArray(httpResponse.getEntity());
            }
         catch (IOException e) {
            throw new SearchClientException(e);
        }
    }

    protected int postJson(String url, String json) throws IOException {
        HttpPost httpPost = new HttpPost(url);
        ByteArrayEntity entity = new ByteArrayEntity(json.getBytes(StandardCharsets.UTF_8));

        httpPost.setEntity(entity);

        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json; charset=utf-8");
        //this was required because of connection already bound exceptions on windows :(
        //httpPost.setHeader("Connection", "close");

        //try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                EntityUtils.consume(response.getEntity());
                return response.getStatusLine().getStatusCode();
          //  }
        } finally {
            httpPost.releaseConnection();
        }
    }

    protected static String encode(String s) throws IllegalArgumentException {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public abstract void addDocument(StoredDocument buildDocument) throws IOException;

    public void close() throws IOException {
        //no-op
        httpClient.close();
    }

    public abstract void addDocuments(List<StoredDocument> buildDocuments) throws IOException;
}
