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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mitre.quaerite.connectors.QueryRequest;
import org.mitre.quaerite.connectors.SearchClient;
import org.mitre.quaerite.connectors.SearchClientFactory;
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
        queryRequest.addFacetField("genres_facet");
        FacetResult result = client.facet(queryRequest);
        Map<String, Long> counts = result.getFacetCounts();
        assertEquals(21, counts.size());
        assertEquals(9316, counts.get("drama"));
        assertEquals(207, counts.get("tv movie"));


        queryRequest = new QueryRequest(ALL_DOCS);
        queryRequest.addFacetField("production_companies_facet");
        result = client.facet(queryRequest);
        counts = result.getFacetCounts();
        assertEquals(13026, counts.size());
        assertEquals(1, counts.get("haile gerima"));
        assertEquals(72, counts.get("dreamworks skg"));
    }

    @Test
    public void testQuery() throws Exception {
        SearchClient client = SearchClientFactory.getClient(TMDB_URL);
        QueryRequest queryRequest = new QueryRequest("title:psycho", null, "id");
        System.out.println(client.search(queryRequest));
    }
}
