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
import org.mitre.quaerite.core.FacetResult;
import org.mitre.quaerite.core.QueryStrings;
import org.mitre.quaerite.core.SearchResultSet;
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
import org.mitre.quaerite.core.util.ConnectionConfig;

//@Disabled("need to have Solr tmdb instance running")
public class TestSolrClient {

    private static Query ALL_DOCS = new MatchAllDocsQuery();
    private static String TMDB_URL = "http://localhost:8983/solr/tmdb";

    @Test
    public void testCopyFields() throws Exception {
        SolrClient client = (SolrClient)SearchClientFactory.getClient(TMDB_URL);
        Set<String> copyFieldDests = client.getCopyFields();
        assertTrue(copyFieldDests.contains("tsss_directors"));
        assertTrue(copyFieldDests.contains("tsss_cast"));
        assertEquals(20, copyFieldDests.size());
    }


    @Test
    public void testFacets() throws Exception {
        SolrClient client = new SolrClient(
                ConnectionConfig.DEFAULT_CONNECTION_CONFIG,
                TMDB_URL);
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

        EDisMaxQuery q = new EDisMaxQuery("red");
        q.getQF().add(new WeightableField("title"));
        testRed(q);

        DisMaxQuery disMaxQuery = new DisMaxQuery("red");
        disMaxQuery.getQF().add(new WeightableField("title"));
        testRed(disMaxQuery);

        TermQuery tq = new TermQuery("title", "red");
        testRed(tq);

        TermsQuery tsq = new TermsQuery("title", Collections.singletonList("red"));
        testRed(tsq);

        LuceneQuery luceneQuery = new LuceneQuery("", "title:red");
        testRed(luceneQuery);

        luceneQuery = new LuceneQuery("title", "red");
        testRed(luceneQuery);
    }

    private void testRed(Query q) throws Exception {
        SearchClient client = SearchClientFactory.getClient(TMDB_URL);
        QueryRequest queryRequest = new QueryRequest(q);
        queryRequest.setFacetLimit(20000);
        queryRequest.setFacetField("production_companies_facet");
        FacetResult result = client.facet(queryRequest);
        Map<String, Long> counts = result.getFacetCounts();
        assertEquals(15905, counts.size());
        assertEquals(2, counts.get("summit entertainment"));
        assertEquals(5, counts.get("paramount pictures"));
    }

    @Test
    public void testQuery() throws Exception {
        SearchClient client = SearchClientFactory.getClient(TMDB_URL);
        QueryRequest queryRequest = new QueryRequest(new LuceneQuery("title",
                "psycho"), null, "id");
        SearchResultSet searchResultSet = client.search(queryRequest);
        assertEquals(9, searchResultSet.getTotalHits());
        for (String id : new String[]{
                "539", "11252", "1359", "10576", "12662", "27723", "35683",
                "10726", "214250"
        }) {
            assertTrue(searchResultSet.getIds().contains(id));
        }
    }

    @Test
    public void testGetDocs() throws Exception {
        Set<String> ids = new HashSet<>();
        ids.addAll(Arrays.asList("539 11252 1359 10576 12662".split(" ")));
        SearchClient searchClient = SearchClientFactory.getClient(TMDB_URL);
        List<StoredDocument> docs = searchClient.getDocs("id", ids,
                Collections.EMPTY_SET, Collections.EMPTY_SET);
        assertEquals(5, docs.size());
        StoredDocument doc1359 = null;
        for (int i = 0; i < docs.size(); i++) {
            StoredDocument sd = docs.get(i);
            if (sd.getFields().get("id").equals("1359")) {
                doc1359 = sd;
                break;
            }
        }
        String overview = (String) doc1359.getFields().get("overview");
        assertTrue(overview.startsWith("A wealthy New"));
        List<String> cast = (List) doc1359.getFields().get("cast");
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
        List<StoredDocument> docs = searchClient.getDocs("id", ids,
                whiteListFields, Collections.EMPTY_SET);
        assertEquals(5, docs.size());
        StoredDocument doc1359 = null;
        for (int i = 0; i < docs.size(); i++) {
            StoredDocument sd = docs.get(i);
            assertEquals(2, sd.getFields().size());
            if (sd.getFields().get("id").equals("1359")) {
                doc1359 = sd;
            }
        }
        String title = (String) doc1359.getFields().get("original_title");
        assertEquals("American Psycho", title);
    }

    @Test
    public void testTerms() throws Exception {
        SearchClient client = SearchClientFactory.getClient(TMDB_URL);
        String lower = "";
        Set<TokenDF> allTerms = new HashSet<>();
        while (true) {
            List<TokenDF> terms = client.getTerms("production_companies_facet",
                    lower, 100, 0);
            if (terms.size() == 0) {
                break;
            }
            lower = terms.get(terms.size() - 1).getToken();
            allTerms.addAll(terms);
        }
        assertEquals(15904, allTerms.size());
        boolean found = false;
        for (TokenDF tdf : allTerms) {
            if (tdf.getToken().equals("13 productions")) {
                assertEquals(3l, tdf.getDf());
                found = true;
            }
        }
        assertTrue(found);

        allTerms = new HashSet<>();
        lower = "";
        while (true) {
            List<TokenDF> terms = client.getTerms("production_companies_facet_lc",
                    lower, 100, 0);
            if (terms.size() == 0) {
                break;
            }
            lower = terms.get(terms.size() - 1).getToken();
            allTerms.addAll(terms);
        }
        assertEquals(15938, allTerms.size());

    }

    @Test
    public void testAnalysis() throws Exception {
        String s = "THE Quick brün FOX";
        SearchClient client = SearchClientFactory.getClient(TMDB_URL);
        List<String> tokens = client.analyze("title", s);
        assertEquals(3, tokens.size());
        assertEquals("brün", tokens.get(1));
    }

    @Test
    public void testQueryOperator() throws Exception {
        QueryStrings qStrings = new QueryStrings();
        qStrings.setQuery("black mirror white christmas");
        EDisMaxQuery q = new EDisMaxQuery();
        QF qf = new QF();
        qf.add(new WeightableField("title"));
        q.setQF(qf);

        q.setQueryOperator(new QueryOperator(QueryOperator.OPERATOR.AND));
        q.setQueryStrings(qStrings);

        SearchClient searchClient = SearchClientFactory.getClient(TMDB_URL);
        SearchResultSet rs = searchClient.search(new QueryRequest(q));
        assertEquals(1, rs.getTotalHits());
        assertTrue(rs.getIds().contains("374430"));

        q.setQueryOperator(new QueryOperator(QueryOperator.OPERATOR.OR));
        rs = searchClient.search(new QueryRequest(q));
        assertEquals(372, rs.getTotalHits());
        assertTrue(rs.getIds().contains("374430"));

        q.setQueryOperator(new QueryOperator(QueryOperator.OPERATOR.OR, 0.25f));
        rs = searchClient.search(new QueryRequest(q));
        assertEquals(372, rs.getTotalHits());
        assertTrue(rs.getIds().contains("374430"));

        q.setQueryOperator(new QueryOperator(QueryOperator.OPERATOR.OR, -0.75f));
        rs = searchClient.search(new QueryRequest(q));
        assertEquals(372, rs.getTotalHits());
        assertTrue(rs.getIds().contains("374430"));

        q.setQueryOperator(new QueryOperator(QueryOperator.OPERATOR.OR, 0.5f));
        rs = searchClient.search(new QueryRequest(q));
        assertEquals(14, rs.getTotalHits());
        assertTrue(rs.getIds().contains("374430"));

        q.setQueryOperator(new QueryOperator(QueryOperator.OPERATOR.OR, 2));
        rs = searchClient.search(new QueryRequest(q));
        assertEquals(14, rs.getTotalHits());
        assertTrue(rs.getIds().contains("374430"));

        q.setQueryOperator(new QueryOperator(QueryOperator.OPERATOR.OR, -1));
        rs = searchClient.search(new QueryRequest(q));
        assertEquals(1, rs.getTotalHits());
        assertTrue(rs.getIds().contains("374430"));
    }

    @Test
    @Disabled("need to have a solr instance w basic authentication")
    public void testAuthentication() throws Exception {
        //change user, password and url
        ConnectionConfig connectionConfig =
                new ConnectionConfig("user", "password1234");
        SearchClient client = SearchClientFactory.getClient("url", connectionConfig);
        String s = "THE Quick brün FOX";
        List<String> tokens = client.analyze("tm_title", s);
        System.out.println(tokens);
    }

}
