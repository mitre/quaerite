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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class SearchClientFactory {

    public static SearchClient getClient(String url) throws IOException, SearchClientException {

        Matcher m = Pattern.compile("(https?://[^/]+)").matcher(url);
        if (!m.find()) {
            throw new SearchClientException("Couldn't find domain in this url:"+url);
        }
        String solrSystem = m.group(1)+"/solr/admin/info/system?wt=json";
        try {
            byte[] bytes = HttpUtils.get(solrSystem);
            try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
                JsonElement root = new JsonParser().parse(reader);
                JsonObject lucene = root.getAsJsonObject().getAsJsonObject("lucene");
                String version = lucene.getAsJsonPrimitive("solr-spec-version").getAsString();
                int firstPeriod = version.indexOf(".");
                if (firstPeriod < 0) {
                    throw new SearchClientException("couldn't find version major version: "+ version);
                }
                int major = Integer.parseInt(version.substring(0, firstPeriod));
                if (major < 7) {
                    return new Solr4Client(url);
                } else {
                    return new SolrClient(url);
                }
            }
        } catch (SearchClientException e) {

        }
        String es = m.group(1);
        byte[] bytes = HttpUtils.get(es);
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            JsonObject version = root.getAsJsonObject("version");
            String number = version.get("number").getAsString();
            String major = number.substring(0,1);
            if (major.equals("6")) {
                return new ES6Client(url);
            } else if (major.equals("7")) {
                return new ESClient(url);
            } else {
                throw new IllegalArgumentException("I regret that I don't yet support: "+number);
            }
        } catch (IOException e) {
            throw new SearchClientException("Couldn't find right client for: "+url);
        }
    }
}
