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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mitre.quaerite.core.FacetResult;
import org.mitre.quaerite.core.ResultSet;
import org.mitre.quaerite.core.features.MultiMatchType;
import org.mitre.quaerite.core.features.TIE;
import org.mitre.quaerite.core.features.WeightableField;
import org.mitre.quaerite.core.queries.BooleanClause;
import org.mitre.quaerite.core.queries.BooleanQuery;
import org.mitre.quaerite.core.queries.LuceneQuery;
import org.mitre.quaerite.core.queries.MatchAllDocsQuery;
import org.mitre.quaerite.core.queries.MultiMatchQuery;
import org.mitre.quaerite.core.queries.Query;
import org.mitre.quaerite.core.queries.TermQuery;

/**
 * This class needs the tmdb collection up and running.
 *
 * Grab the template from examples and create the collection:
 *
 * curl -X PUT "http://localhost:9200/tmdb"
 *
 * curl -H "Content-Type: application/json" -T tmdb_template.json http://localhost:9200/_template/tmdb
 */


@Disabled("need to have ES tmdb instance running")
public class TestESClient {


    private static Query ALL_DOCS = new MatchAllDocsQuery();
    private static String TMDB_URL = "http://localhost:9200/tmdb";

    @Test
    public void testInit() throws Exception {
        ESClient client = new ESClient("http://localhost:9200/tmdb");
        assertEquals("http://localhost:9200/", client.getESBase());
        assertEquals("http://localhost:9200/tmdb/", client.getUrl());
        assertEquals("tmdb", client.getESCollection());
    }

    @Test
    public void testCopyFields() throws Exception {
        ESClient client = new ESClient("http://localhost:9200/tmdb");
        Collection copyFields = client.getCopyFields();
        Set<String> set = new HashSet<>(copyFields);
        assertTrue(set.contains("people"));
    }


    @Test
    public void testFacets() throws Exception {
        SearchClient client = SearchClientFactory.getClient(TMDB_URL);
        QueryRequest queryRequest = new QueryRequest(ALL_DOCS);
        queryRequest.setFacetField("genres.facet");
        queryRequest.setFacetLimit(10000);
        FacetResult result = client.facet(queryRequest);
        Map<String, Long> counts = result.getFacetCounts();
        assertEquals(21, counts.size());
        assertEquals(13582, counts.get("drama"));
        assertEquals(243, counts.get("tv movie"));


        queryRequest = new QueryRequest(ALL_DOCS);
        queryRequest.setFacetField("production_companies.facet");
        queryRequest.setFacetLimit(100000);
        result = client.facet(queryRequest);
        counts = result.getFacetCounts();
        //TODO: figure out why this # is different from Solr's
        assertEquals(15905, counts.size());
        assertEquals(1, counts.get("haile gerima"));
        assertEquals(90, counts.get("dreamworks skg"));

        queryRequest = new QueryRequest(new TermQuery("title", "red"));
        queryRequest.setFacetLimit(20000);
        queryRequest.setFacetField("production_companies.facet");
        result = client.facet(queryRequest);
        counts = result.getFacetCounts();
        assertEquals(15905, counts.size());
        assertEquals(2, counts.get("summit entertainment"));
        assertEquals(5, counts.get("paramount pictures"));
    }

    @Test
    public void testQuery() throws Exception {
        SearchClient client = SearchClientFactory.getClient(TMDB_URL);
        QueryRequest queryRequest = new QueryRequest(
                new LuceneQuery("title", "psycho"),
                null, client.getDefaultIdField());
        ResultSet result = client.search(queryRequest);
        Set<String> hits = new HashSet<>();
        for (int i = 0; i < result.size(); i++) {
            hits.add(result.get(i));
        }
        assertEquals(8, hits.size());
        assertTrue(hits.contains("539"));
        assertTrue(hits.contains("35683"));

        MultiMatchQuery query = new MultiMatchQuery("psycho");
        query.getQF().add(new WeightableField("title"));
        query.setMultiMatchType(new MultiMatchType("best_fields"));
        queryRequest = new QueryRequest(query,
                null, client.getDefaultIdField());
        result = client.search(queryRequest);
        hits = new HashSet<>();
        for (int i = 0; i < result.size(); i++) {
            hits.add(result.get(i));
        }
        assertEquals(8, hits.size());
        assertTrue(hits.contains("539"));
        assertTrue(hits.contains("35683"));

    }

    @Test
    public void testFilterQuery() throws Exception {
        SearchClient client = SearchClientFactory.getClient(TMDB_URL);
        LuceneQuery q = new LuceneQuery("title", "psycho");
        BooleanQuery bq = new BooleanQuery();
        bq.addClause(new BooleanClause(null, BooleanClause.OCCUR.MUST_NOT,
                new TermQuery("_id", "539")));
        bq.addClause(new BooleanClause(null, BooleanClause.OCCUR.SHOULD,
                q));
        QueryRequest queryRequest = new QueryRequest(bq,
                null, client.getDefaultIdField());

//        queryRequest.addFilterQueries(bq);
        queryRequest.setNumResults(100);
        ResultSet result = client.search(queryRequest);
        Set<String> hits = new HashSet<>();
        for (int i = 0; i < result.size(); i++) {
            hits.add(result.get(i));
        }
        assertEquals(7, hits.size());
        assertTrue(hits.contains("35683"));

    }

    @Test
    public void testBoolean() throws Exception {
        SearchClient client = SearchClientFactory.getClient(TMDB_URL);
        MultiMatchQuery q1 = new MultiMatchQuery("brown fox");
        q1.getQF().add(new WeightableField("title"));
        q1.getQF().add(new WeightableField("overview"));
        q1.setMultiMatchType(new MultiMatchType("best_fields"));
        q1.setTie(new TIE(0.3f));

        MultiMatchQuery q2 = new MultiMatchQuery("elephant");
        q2.getQF().add(new WeightableField("title"));
        q2.getQF().add(new WeightableField("overview"));
        q2.setMultiMatchType(new MultiMatchType("best_fields"));
        q2.setTie(new TIE(0.3f));

        BooleanQuery bq = new BooleanQuery();
        bq.addClause(new BooleanClause(null, BooleanClause.OCCUR.SHOULD, q1));
        bq.addClause(new BooleanClause(null, BooleanClause.OCCUR.SHOULD, q2));
        QueryRequest queryRequest = new QueryRequest(bq,
                null, client.getDefaultIdField());
        queryRequest.addFieldsToRetrieve("_id");
        queryRequest.setNumResults(1000);
        ResultSet result = client.search(queryRequest);
        Set<String> hits = new HashSet<>();
        for (int i = 0; i < result.size(); i++) {
            hits.add(result.get(i));
        }
        assertEquals(176, hits.size());
        assertTrue(hits.contains("81579"));
        assertTrue(hits.contains("42254"));

    }
    @Test
    public void testGetDocs() throws Exception {
        Set<String> ids = new HashSet<>();
        ids.addAll(Arrays.asList("539 11252 1359 10576 12662".split(" ")));
        SearchClient searchClient = SearchClientFactory.getClient(TMDB_URL);
        List<StoredDocument> docs = searchClient.getDocs("_id", ids, Collections.EMPTY_SET, Collections.EMPTY_SET);
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
        List<StoredDocument> docs = searchClient.getDocs("_id", ids, whiteListFields, Collections.EMPTY_SET);
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

    @Test
    public void testIDGrabbing() throws Exception {
        SearchClient searchClient = SearchClientFactory.getClient(TMDB_URL);
        final ArrayBlockingQueue<Set<String>> ids = new ArrayBlockingQueue<>(10);
        IdGrabber grabber = searchClient.getIdGrabber(ids, 1000, 1, Collections.EMPTY_SET);
        Thread producer = new Thread(new FutureTask(grabber));
        producer.start();
        final AtomicInteger idCounter = new AtomicInteger(0);
        Thread consumer = new Thread() {
            @Override
            public void run() {
                while (true) {
                    Set<String> set = null;
                    try {
                        set = ids.poll(1, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {

                    }
                    if (set != null) {
                        idCounter.addAndGet(set.size());
                    }
                    if (set != null && set.size() == 0) {
                        break;
                    }
                }
            }
        };
        consumer.start();
        producer.join();
        consumer.join();

        assertEquals(27846, idCounter.get());
    }

    @Disabled
    public void testDeleteAll() throws Exception {
        SearchClient searchClient = SearchClientFactory.getClient(TMDB_URL);
        searchClient.deleteAll();
    }

    @Disabled("for development")
    @Test
    public void testRaw() throws Exception {
        String json =
                "{\n" +
                        "\n" +
                        "  \"query\": {\n" +
                        "  \"bool\":{\n" +
                        "    \"should\":[\n" +
                        "    {\"multi_match\" : {\n" +
                        "      \"query\":      \"brown fox\",\n" +
                        "      \"type\":       \"best_fields\",\n" +
                        "      \"fields\":     [ \"title\", \"overview\" ],\n" +
                        "      \"tie_breaker\": 0.3\n" +
                        "    }},\n" +
                        "\t{\"multi_match\" : {\n" +
                        "      \"query\":      \"elephant\",\n" +
                        "      \"type\":       \"best_fields\",\n" +
                        "      \"fields\":     [ \"title\", \"overview\" ],\n" +
                        "      \"tie_breaker\": 0.3\n" +
                        "    }}\n" +
                        "\t]\n" +
                        "  }\n" +
                        "  }\n" +
                        "}";
        SearchClient searchClient = SearchClientFactory.getClient(TMDB_URL);
        String url = TMDB_URL+"/_search";
        JsonResponse r = searchClient.postJson(url, json);
        System.out.println(r);
    }
}
