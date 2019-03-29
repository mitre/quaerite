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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mitre.quaerite.connectors.QueryRequest;
import org.mitre.quaerite.connectors.SearchClient;
import org.mitre.quaerite.connectors.SearchClientFactory;
import org.mitre.quaerite.connectors.SolrClient;
import org.mitre.quaerite.connectors.StoredDocument;
import org.mitre.quaerite.core.FacetResult;

@Disabled("need to have Solr tmdb instance running")
public class TestSolrClient {

    private static String ALL_DOCS = "*:*";
    private static String TMDB_URL = "http://localhost:8983/solr/tmdb";

    @Test
    public void testCopyFields() throws Exception {
        SolrClient client = new SolrClient(TMDB_URL);
        Set<String> copyFieldDests = client.getCopyFields();
        assertTrue(copyFieldDests.contains("tsss_directors"));
        assertTrue(copyFieldDests.contains("tsss_cast"));
        assertEquals(19, copyFieldDests.size());
    }


    @Test
    public void testFacets() throws Exception {
        SolrClient client = new SolrClient(TMDB_URL);
        QueryRequest queryRequest = new QueryRequest(ALL_DOCS);
        queryRequest.setFacetField("genres_facet");
        queryRequest.setFacetLimit(20000);

        FacetResult result = client.facet(queryRequest);
        Map<String, Long> counts = result.getFacetCounts();
        assertEquals(21, counts.size());
        assertEquals(13582, counts.get("drama"));
        assertEquals(243, counts.get("tv movie"));


        queryRequest = new QueryRequest(ALL_DOCS);
        queryRequest.setFacetField("production_companies_facet");
        queryRequest.setFacetLimit(20000);
        result = client.facet(queryRequest);
        counts = result.getFacetCounts();
        assertEquals(15905, counts.size());
        assertEquals(1, counts.get("haile gerima"));
        assertEquals(90, counts.get("dreamworks skg"));

        queryRequest = new QueryRequest("red");
        queryRequest.setFacetLimit(20000);
        queryRequest.addParameter("qf", "title");
        queryRequest.setFacetField("production_companies_facet");
        result = client.facet(queryRequest);
        counts = result.getFacetCounts();
        assertEquals(15905, counts.size());
        assertEquals(2, counts.get("summit entertainment"));
        assertEquals(5, counts.get("paramount pictures"));
    }

    @Test
    public void testQuery() throws Exception {
        SearchClient client = SearchClientFactory.getClient(TMDB_URL);
        QueryRequest queryRequest = new QueryRequest("title:psycho", null, "id");
        System.out.println(client.search(queryRequest));
    }

    @Test
    public void testGetDocs() throws Exception {
        Set<String> ids = new HashSet<>();
        ids.addAll(Arrays.asList("539 11252 1359 10576 12662".split(" ")));
        SearchClient searchClient = SearchClientFactory.getClient(TMDB_URL);
        List<StoredDocument> docs = searchClient.getDocs("id", ids, Collections.EMPTY_SET, Collections.EMPTY_SET);
        assertEquals(5, docs.size());
        StoredDocument doc1359 = null;
        for (int i = 0; i < docs.size(); i++) {
            StoredDocument sd = docs.get(i);
            if (sd.getFields().get("id").equals("1359")) {
                doc1359 = sd;
                break;
            }
        }
        String overview = (String)doc1359.getFields().get("overview");
        assertTrue(overview.startsWith("A wealthy New"));
        List<String> cast = (List)doc1359.getFields().get("cast");
        assertEquals(19, cast.size());
        assertEquals("Christian Bale", cast.get(0));
        assertEquals("Willem Dafoe", cast.get(1));
    }

    @Test
    public void testGetDocsWhiteList() throws Exception {
        Set<String> ids = new HashSet<>();
        ids.addAll(Arrays.asList("539 11252 1359 10576 12662".split(" ")));
        SearchClient searchClient = SearchClientFactory.getClient(TMDB_URL);
        Set<String> whiteListFields = new HashSet<>();
        whiteListFields.add("original_title");
        whiteListFields.add("id");
        List<StoredDocument> docs = searchClient.getDocs("id", ids, whiteListFields, Collections.EMPTY_SET);
        assertEquals(5, docs.size());
        StoredDocument doc1359 = null;
        for (int i = 0; i < docs.size(); i++) {
            StoredDocument sd = docs.get(i);
            assertEquals(2, sd.getFields().size());
            if (sd.getFields().get("id").equals("1359")) {
                doc1359 = sd;
            }
        }
        String title = (String)doc1359.getFields().get("original_title");
        assertEquals("American Psycho", title);
    }
}
