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
package org.mitre.quaerite.core.features.factories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.mitre.quaerite.core.ExperimentFactory;
import org.mitre.quaerite.core.features.CustomHandler;
import org.mitre.quaerite.core.features.Feature;
import org.mitre.quaerite.core.features.NegativeBoost;
import org.mitre.quaerite.core.features.QueryOperator;
import org.mitre.quaerite.core.queries.BoostingQuery;
import org.mitre.quaerite.core.queries.EDisMaxQuery;
import org.mitre.quaerite.core.queries.MultiMatchQuery;

public class TestQueryFactory {

    @Test
    public void testDeserialization() throws Exception {
        ExperimentFactory experimentFactory = ExperimentFactory.fromJson(newReader("/test-documents/experiment_features_solr_1.json"));

        CustomHandlerFactory customHandlerFactory =
                (CustomHandlerFactory)experimentFactory.getFeatureFactories().get(CustomHandlerFactory.NAME);

        List<CustomHandler> customHandlers = customHandlerFactory.getCustomHandlers();
        assertEquals("custom1", customHandlers.get(0).getHandler());
        assertEquals(CustomHandlerFactory.DEFAULT_QUERY_KEY, customHandlers.get(0).getCustomQueryKey());

        assertEquals("custom2", customHandlers.get(1).getHandler());
        assertEquals("qq", customHandlers.get(1).getCustomQueryKey());

        QueryFactory<EDisMaxQuery> qf = (QueryFactory<EDisMaxQuery>)experimentFactory.getFeatureFactories().get(QueryFactory.NAME);
        FloatFeatureFactory tie = null;
        for (FeatureFactory f : qf.factories) {

            if (((AbstractFeatureFactory)f).getName().equals("TIE")) {
                tie = (FloatFeatureFactory)f;
            }
        }
        assertNotNull(tie);
        assertEquals(0.0, (float)tie.getFloats().get(0), 0.001);
        assertEquals(0.1, (float)tie.getFloats().get(1), 0.001);
        assertEquals(0.2, (float)tie.getFloats().get(2), 0.001);
    }
    @Test
    public void testQFDepthSerialization() throws Exception {
        ExperimentFactory experimentFactory = ExperimentFactory.fromJson(
                newReader("/test-documents/experiment_features_solr_3.json")
        );

        FeatureFactories featureFactories = experimentFactory.getFeatureFactories();
        QueryFactory<EDisMaxQuery> qf = (QueryFactory<EDisMaxQuery>)experimentFactory.getFeatureFactories().get(QueryFactory.NAME);

        for (FeatureFactory f : qf.factories) {
            if (((AbstractFeatureFactory)f).getName().equals("qf")) {
                List<Feature> features = f.permute(1000);
                assertEquals(80, features.size());
            }
            if (((AbstractFeatureFactory)f).getName().equals("pf")) {
                List<Feature> features = f.permute(1000);
                assertEquals(64, features.size());
            }
            if (((AbstractFeatureFactory)f).getName().equals("pf2")) {
                List<Feature> features = f.permute(1000);
                assertEquals(32, features.size());
            }
            if (((AbstractFeatureFactory)f).getName().equals("pf3")) {
                List<Feature> features = f.permute(1000);
                assertEquals(8, features.size());
            }
        }
        List<EDisMaxQuery> queries = qf.permute(50000);
        assertEquals(50000, queries.size());
    }

    @Test
    public void testEdismaxRandomization() throws Exception {
        ExperimentFactory experimentFactory = ExperimentFactory.fromJson(
                newReader("/test-documents/experiment_features_solr_3.json")
        );
        QueryFactory<EDisMaxQuery> qf = (QueryFactory<EDisMaxQuery>)experimentFactory.getFeatureFactories().get(QueryFactory.NAME);

        List<EDisMaxQuery> queries = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            queries.add(qf.random());
        }
        assertWithinBounds(queries);
    }

    @Test
    public void testEdismaxMutate() throws Exception {
        ExperimentFactory experimentFactory = ExperimentFactory.fromJson(
                newReader("/test-documents/experiment_features_solr_3.json")
        );
        QueryFactory<EDisMaxQuery> qf = (QueryFactory<EDisMaxQuery>)experimentFactory.getFeatureFactories().get(QueryFactory.NAME);
        List<EDisMaxQuery> queries = new ArrayList<>();
        int numQueries = 1000;
        int equal = 0;
        double probability = 0.8;
        for (int i = 0; i < numQueries; i++) {
            EDisMaxQuery q = qf.random();
            EDisMaxQuery mutated = qf.mutate(q, probability, 0.9);
            if (mutated.equals(q)) {
                equal++;
            }
            assertTrue(q != mutated);
            queries.add(mutated);
        }
        double percentEqual = (double)equal/(double)numQueries;
        double percentMutated = 1.0-percentEqual;
        //this test will fail very, very, very rarely...
        assertEquals(probability, percentMutated, 0.1);
        assertWithinBounds(queries);
    }

    @Test
    public void testEdismaxCrossover() throws Exception {
        ExperimentFactory experimentFactory = ExperimentFactory.fromJson(
                newReader("/test-documents/experiment_features_solr_3.json")
        );
        QueryFactory<EDisMaxQuery> qf = (QueryFactory<EDisMaxQuery>)experimentFactory.getFeatureFactories().get(QueryFactory.NAME);
        List<EDisMaxQuery> queries = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            EDisMaxQuery p1 = qf.random();
            EDisMaxQuery p2 = qf.random();
            Pair<EDisMaxQuery, EDisMaxQuery> children = qf.crossover(p1, p2);

            assertTrue(p1 != children.getLeft());
            assertTrue(p1 != children.getRight());
            assertTrue(p2 != children.getLeft());
            assertTrue(p2 != children.getRight());
            queries.add(children.getLeft());
            queries.add(children.getRight());
        }
        assertWithinBounds(queries);
    }

    @Test
    public void testBoostingQueryRandom() throws Exception {
        ExperimentFactory experimentFactory = ExperimentFactory.fromJson(
                newReader("/test-documents/experiment_features_es_2.json")
        );
        QueryFactory<BoostingQuery> qf = (QueryFactory<BoostingQuery>)experimentFactory.getFeatureFactories().get(QueryFactory.NAME);
        List<BoostingQuery> bqs = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            bqs.add(qf.random());
        }
        assertBoostingWithinBounds(bqs);
    }

    @Test
    public void testBoostingMutate() throws Exception {
        ExperimentFactory experimentFactory = ExperimentFactory.fromJson(
                newReader("/test-documents/experiment_features_es_2.json")
        );
        BoostingQueryFactory qf = (BoostingQueryFactory) experimentFactory.getFeatureFactories().get(QueryFactory.NAME);

        List<BoostingQuery> boostingQueries = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            BoostingQuery boosting = qf.random();
            boostingQueries.add(qf.mutate(boosting, 0.2, 0.9));
        }
        assertBoostingWithinBounds(boostingQueries);
    }

    @Test
    public void testBoostingCrossover() throws Exception {
        ExperimentFactory experimentFactory = ExperimentFactory.fromJson(
                newReader("/test-documents/experiment_features_es_2.json")
        );
        BoostingQueryFactory qf = (BoostingQueryFactory)experimentFactory.getFeatureFactories().get(QueryFactory.NAME);

        List<BoostingQuery> boostingQueries = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            BoostingQuery mA = qf.random();
            BoostingQuery mB = qf.random();
            Pair<BoostingQuery, BoostingQuery> pair = qf.crossover(mA, mB);
            boostingQueries.add(pair.getLeft());
            boostingQueries.add(pair.getRight());
        }
        assertBoostingWithinBounds(boostingQueries);
    }

    @Test
    public void testEdismaxQueryOperators() throws Exception {
        ExperimentFactory experimentFactory = ExperimentFactory.fromJson(
                newReader("/test-documents/experiment_features_solr_queryOp.json")
        );
        QueryFactory<EDisMaxQuery> qf = (QueryFactory<EDisMaxQuery>) experimentFactory.getFeatureFactories().get(QueryFactory.NAME);
        for (EDisMaxQuery q : qf.permute(10000)) {
            System.out.println(q);
        }
        assertEquals(1120, qf.permute(10000).size());

        float minFloat = -0.8f;
        float maxFloat = 0.8f;
        int minInt = -3;
        int maxInt = 4;
        int ands = 0;
        int orNoMM = 0;
        int orFloats = 0;
        int orInts = 0;

        int iterations = 10000;
        for (int i = 0; i < iterations; i++) {
            EDisMaxQuery q = qf.random();
            QueryOperator op = q.getQueryOperator();
            //for generating experiments, we want to ensure that
            //query operators are specified
            assertFalse(op.getOperator() == QueryOperator.OPERATOR.UNSPECIFIED);
            if (op.getOperator() == QueryOperator.OPERATOR.AND) {
                ands++;
                continue;
            }
            switch (op.getMM()) {
                case NONE:
                    orNoMM++;
                    break;
                case INTEGER:
                    orInts++;
                    assertTrue(op.getInt() <= maxInt,
                            "int: "+op.getInt());
                    assertTrue(op.getInt() >= minInt, "int: " + op.getInt());
                    break;
                case FLOAT:
                    assertTrue(op.getMmFloat() <= maxFloat,
                            "float: "+op.getMmFloat());
                    assertTrue(op.getMmFloat() >= minFloat);
                    orFloats++;
                    break;
            }
        }
        //20% are 'and', 80% are 'not'
        assertEquals(0.20, (double)ands/(double)iterations, 0.1);
        //of the remaining 80% 'or', 20% are no mm, 40% are int, 40% are float
        assertEquals((0.20*0.80), (double)orNoMM/(double)iterations, 0.1);
        assertEquals((0.40*0.80), (double)orFloats/(double)iterations, 0.1);
        assertEquals((0.40*0.80), (double)orInts/(double)iterations, 0.1);
    }


    @Test
    public void testBoostingPermute() throws Exception {
        ExperimentFactory experimentFactory = ExperimentFactory.fromJson(
                newReader("/test-documents/experiment_features_es_2.json")
        );
        BoostingQueryFactory qf = (BoostingQueryFactory) experimentFactory.getFeatureFactories().get(QueryFactory.NAME);
        List<BoostingQuery> bqs = qf.permute(100000);
        assertBoostingWithinBounds(bqs);
        assertEquals(100000, bqs.size());
    }
    @Test
    public void testMultiMatchRandom() throws Exception {
        ExperimentFactory experimentFactory = ExperimentFactory.fromJson(
                newReader("/test-documents/experiment_features_es_1.json")
        );
        QueryFactory<MultiMatchQuery> qf = (QueryFactory<MultiMatchQuery>)experimentFactory.getFeatureFactories().get(QueryFactory.NAME);

        List<MultiMatchQuery> multiMatchQueries = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            MultiMatchQuery mmq = qf.random();
            multiMatchQueries.add(mmq);
        }
        assertMultiMatchWithinBounds(multiMatchQueries);
    }

    @Test
    public void testMultiMatchMutate() throws Exception {
        ExperimentFactory experimentFactory = ExperimentFactory.fromJson(
                newReader("/test-documents/experiment_features_es_1.json")
        );
        QueryFactory<MultiMatchQuery> qf = (QueryFactory<MultiMatchQuery>)experimentFactory.getFeatureFactories().get(QueryFactory.NAME);

        List<MultiMatchQuery> multiMatchQueries = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            MultiMatchQuery mmq = qf.random();
            multiMatchQueries.add(qf.mutate(mmq, 0.2, 0.9));
        }

        assertMultiMatchWithinBounds(multiMatchQueries);
    }

    @Test
    public void testMultiMatchCrossover() throws Exception {
        ExperimentFactory experimentFactory = ExperimentFactory.fromJson(
                newReader("/test-documents/experiment_features_es_1.json")
        );
        QueryFactory<MultiMatchQuery> qf = (QueryFactory<MultiMatchQuery>)experimentFactory.getFeatureFactories().get(QueryFactory.NAME);

        List<MultiMatchQuery> multiMatchQueries = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            MultiMatchQuery mA = qf.random();
            MultiMatchQuery mB = qf.random();
            Pair<MultiMatchQuery, MultiMatchQuery> pair = qf.crossover(mA, mB);
            multiMatchQueries.add(pair.getLeft());
            multiMatchQueries.add(pair.getRight());
        }

        assertMultiMatchWithinBounds(multiMatchQueries);
    }

    private void assertBoostingWithinBounds(List<BoostingQuery> bqs) {
        List<MultiMatchQuery> allQueries = new ArrayList<>();
        for (BoostingQuery bq : bqs) {
            allQueries.add((MultiMatchQuery)bq.getPositiveQuery());
            allQueries.add((MultiMatchQuery)bq.getNegativeQuery());
            NegativeBoost nb = bq.getNegativeBoost();
            assertTrue(nb.getValue() <= 1.0f);
            assertTrue(nb.getValue() >= 0.001f);
        }
        assertMultiMatchWithinBounds(allQueries);
    }


    private void assertMultiMatchWithinBounds(List<MultiMatchQuery> queries) {
        Set<String> types = new HashSet<>();
        Set<String> qOps = new HashSet<>();
        int minQf = 100;
        int maxQf = -1;

        for (MultiMatchQuery mmq : queries) {
            types.add(mmq.getMultiMatchType().toString());
            mmq.getQF();
            if (mmq.getQF().size() < minQf) {
                minQf = mmq.getQF().size();
            }
            if (mmq.getQF().size() > maxQf) {
                maxQf = mmq.getQF().size();
            }
            qOps.add(mmq.getQueryOperator().getOperatorString());
        }

        assertEquals(4, types.size());
        assertEquals(1, minQf);
        assertEquals(4, maxQf);
        //TODO
        //assertEquals(2, qOps.size());

    }

    private void assertWithinBounds(List<EDisMaxQuery> queries) {

        int minQf = 100;
        int maxQf = -1;
        int minPf = 100;
        int maxPf = -1;
        int minPf2 = 100;
        int maxPf2 = -1;
        int minPf3 = 100;
        int maxPf3 = -1;
        float minTie = 100;
        float maxTie = -1.0f;

        for (EDisMaxQuery q : queries) {
            if (q.getQF().size() < minQf) {
                minQf = q.getQF().size();
            }
            if (q.getQF().size() > maxQf) {
                maxQf = q.getQF().size();
            }
            if (q.getPF().size() < minPf) {
                minPf = q.getPF().size();
            }
            if (q.getPF().size() > maxPf) {
                maxPf = q.getPF().size();
            }
            if (q.getPF2().size() < minPf2) {
                minPf2 = q.getPF2().size();
            }
            if (q.getPF2().size() > maxPf2) {
                maxPf2 = q.getPF2().size();
            }
            if (q.getPF3().size() < minPf3) {
                minPf3 = q.getPF3().size();
            }
            if (q.getPF3().size() > maxPf3) {
                maxPf3 = q.getPF3().size();
            }
            if (q.getTie().getValue() < minTie) {
                minTie = q.getTie().getValue();
            }
            if (q.getTie().getValue() > maxTie) {
                maxTie = q.getTie().getValue();
            }
        }
        assertEquals(1, minQf);
        assertEquals(4, maxQf);
        assertEquals(0, minPf);
        assertEquals(3, maxPf);
        assertEquals(0, minPf2);
        assertEquals(2, maxPf2);
        assertEquals(0, minPf3);
        assertEquals(1, maxPf3);
        assertEquals(0.0, minTie, 0.01);
        assertEquals(0.2, maxTie, 0.01);

    }


    private Reader newReader(String path) {
        return new BufferedReader(
                new InputStreamReader(
                        this.getClass().getResourceAsStream(path),
                        StandardCharsets.UTF_8
                )
        );
    }
}
