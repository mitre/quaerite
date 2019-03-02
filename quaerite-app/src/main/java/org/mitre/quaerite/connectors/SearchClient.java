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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;


import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.mitre.quaerite.FacetResult;
import org.mitre.quaerite.ResultSet;

public abstract class SearchClient {

    public abstract ResultSet search(QueryRequest query) throws SearchClientException, IOException;
    public abstract FacetResult facet(QueryRequest query) throws SearchClientException, IOException;


    protected byte[] get(String url) throws SearchClientException {
        //overly simplistic...need to add proxy, etc., but good enough for now
        URI uri = null;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        CloseableHttpClient httpClient = HttpClients.createDefault();
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
        HttpResponse httpResponse = null;
        try {
            httpResponse = httpClient.execute(target, httpGet);
        } catch (IOException e) {
            throw new SearchClientException(e);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (httpResponse.getStatusLine().getStatusCode() != 200) {
            throw new SearchClientException("Bad status code: "+httpResponse.getStatusLine().getStatusCode());
        }
        try {
            IOUtils.copy(httpResponse.getEntity().getContent(), bos);
        } catch (IOException e) {
            throw new SearchClientException(e);
        }
        return bos.toByteArray();
    }

    protected static String encode(String s) throws IllegalArgumentException {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
