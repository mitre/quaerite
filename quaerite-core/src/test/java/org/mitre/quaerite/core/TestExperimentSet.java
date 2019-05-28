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
 *
 */
package org.mitre.quaerite.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mitre.quaerite.core.features.Feature;
import org.mitre.quaerite.core.features.MultiMatchType;
import org.mitre.quaerite.core.features.QF;
import org.mitre.quaerite.core.features.QueryOperator;
import org.mitre.quaerite.core.features.WeightableField;
import org.mitre.quaerite.core.features.WeightableListFeature;
import org.mitre.quaerite.core.queries.BooleanClause;
import org.mitre.quaerite.core.queries.BooleanQuery;
import org.mitre.quaerite.core.queries.BoostingQuery;
import org.mitre.quaerite.core.queries.EDisMaxQuery;
import org.mitre.quaerite.core.queries.MultiFieldQuery;
import org.mitre.quaerite.core.queries.MultiMatchQuery;
import org.mitre.quaerite.core.queries.Query;
import org.mitre.quaerite.core.queries.TermsQuery;
import org.mitre.quaerite.core.scorers.AbstractJudgmentScorer;
import org.mitre.quaerite.core.scorers.Scorer;
import org.mitre.quaerite.core.scorers.NDCG;

public class TestExperimentSet {

    @Test
    public void testLoadingExperiments() throws Exception {

        ExperimentSet experimentSet = null;
        try (Reader reader =
                     new BufferedReader(new InputStreamReader(
                             getClass().getResourceAsStream("/test-documents/experiments_solr_1.json"),
                             StandardCharsets.UTF_8))) {
            experimentSet = ExperimentSet.fromJson(reader);

        }
        assertEquals(8, experimentSet.getScorers().size());
        Map<String, Experiment> map = experimentSet.getExperiments();
        Experiment peopleTitle = map.get("people_title");
        assertEquals("people_title", peopleTitle.getName());
        EDisMaxQuery query = (EDisMaxQuery) peopleTitle.getQuery();
        Feature qf = query.getQF();
        assertEquals(QF.class, qf.getClass());
        List<WeightableField> fields = ((WeightableListFeature) qf).getWeightableFields();
        assertEquals(2, fields.size());
        assertEquals("people", fields.get(0).getFeature());
        assertEquals("title", fields.get(1).getFeature());

        List<Scorer> scorers = experimentSet.getScorers();
        for (Scorer scorer : scorers) {
            if (scorer instanceof NDCG) {
                assertTrue(((AbstractJudgmentScorer) scorer).getExportPMatrix());
                assertTrue(((AbstractJudgmentScorer) scorer).getUseForTrain());
                assertFalse(((AbstractJudgmentScorer) scorer).getUseForTest());
            } else if (scorer instanceof AbstractJudgmentScorer) {
                assertFalse(((AbstractJudgmentScorer) scorer).getExportPMatrix());
                assertFalse(((AbstractJudgmentScorer) scorer).getUseForTrain());
                assertFalse(((AbstractJudgmentScorer) scorer).getUseForTest());
            } else {
                //do nothing
            }
        }
    }

    @Test
    public void testLoadingExperimentConfig() throws Exception {
        ExperimentSet experimentSet = null;
        try (Reader reader =
                     new BufferedReader(new InputStreamReader(
                             getClass().getResourceAsStream("/test-documents/experiments_solr_2.json"),
                             StandardCharsets.UTF_8))) {
            experimentSet = ExperimentSet.fromJson(reader);
        }
        assertEquals(20, experimentSet.getExperimentConfig().getNumThreads());
        assertEquals("customIdField", experimentSet.getExperimentConfig().getIdField());

        String json = experimentSet.toJson();
        ExperimentSet revivified = ExperimentSet.fromJson(new StringReader(json));
        assertEquals(20, revivified.getExperimentConfig().getNumThreads());
        assertEquals("customIdField", revivified.getExperimentConfig().getIdField());

        Map<String, Experiment> map = revivified.getExperiments();
        Experiment peopleTitle = map.get("people_title");
        assertEquals("people_title", peopleTitle.getName());
        EDisMaxQuery query = (EDisMaxQuery) peopleTitle.getQuery();
        Feature qf = query.getQF();
        assertEquals(QF.class, qf.getClass());
        List<WeightableField> fields = ((WeightableListFeature) qf).getWeightableFields();
        assertEquals(2, fields.size());
        assertEquals("people", fields.get(0).getFeature());
        assertEquals("title", fields.get(1).getFeature());

    }

    @Test
    public void testESBooleanAndBoosting() throws Exception {
        ExperimentSet experimentSet = null;
        try (Reader reader =
                     new BufferedReader(new InputStreamReader(
                             getClass().getResourceAsStream("/test-documents/experiments_es_1.json"),
                             StandardCharsets.UTF_8))) {
            experimentSet = ExperimentSet.fromJson(reader);
        }
        //test deserialization before modifying experiment set
        String json = experimentSet.toJson();
        ExperimentSet revivified = ExperimentSet.fromJson(new StringReader(json));
        assertEquals(experimentSet, revivified);

        Query q = experimentSet.getExperiments().get("title").getQuery();
        assertEquals(BooleanQuery.class, q.getClass());

        BooleanQuery bq = (BooleanQuery) q;
        QueryStrings queryStrings = new QueryStrings();
        queryStrings.addQueryString("should_1", "should1query");

        Set<String> used = bq.setQueryStrings(queryStrings);
        if (used.size() != bq.getClauses().size()) {
            //must_not_1 is not set
            fail("should have thrown exception");
        }
        boolean ex = false;
        //test duplicate key
        try {
            queryStrings.addQueryString("should_1", "should1query");
            fail("can't add duplicate key");
        } catch (IllegalArgumentException e) {
            ex = true;
        }
        assertTrue(ex);
        //test bad query string
        queryStrings.addQueryString("unknown", "unknown");
        used = bq.setQueryStrings(queryStrings);
        if (used.contains("unknown")) {
            fail("can't add unknown query string");
        }
        //now do it correctly
        queryStrings = new QueryStrings();
        queryStrings.addQueryString("should_1", "should1query");
        queryStrings.addQueryString("must_not_1", "mustnot1query");
        bq.setQueryStrings(queryStrings);

        List<BooleanClause> shoulds = bq.get(BooleanClause.OCCUR.SHOULD);
        assertEquals(1, shoulds.size());
        Query should = shoulds.get(0).getQuery();
        assertEquals(MultiMatchQuery.class, should.getClass());
        MultiMatchQuery mm = (MultiMatchQuery) should;
        assertTrue(new MultiMatchType("best_fields").equals(mm.getMultiMatchType()));
        assertEquals("should1query", mm.getQueryString());

        List<BooleanClause> mustNots = bq.get(BooleanClause.OCCUR.MUST_NOT);
        assertEquals(1, mustNots.size());
        Query mustNot = mustNots.get(0).getQuery();
        assertEquals(TermsQuery.class, mustNot.getClass());


        Query boosting = experimentSet.getExperiments().get("boostingExperiment").getQuery();
        assertEquals(BoostingQuery.class, boosting.getClass());

        assertEquals((float) 0.001, ((BoostingQuery) boosting).getNegativeBoost().getValue(), 0.1);
    }

    @Test
    public void testBooleanOperatorAndMM() throws Exception {
        ExperimentSet experimentSet = null;
        try (Reader reader =
                     new BufferedReader(new InputStreamReader(
                             getClass().getResourceAsStream(
                                     "/test-documents/experiments_solr_queryOp.json"),
                             StandardCharsets.UTF_8))) {
            experimentSet = ExperimentSet.fromJson(reader);
        }

        //test deserialization first
        String json = experimentSet.toJson();
        ExperimentSet revivified = ExperimentSet.fromJson(new StringReader(json));
        assertEquals(experimentSet, revivified);


        Experiment ex = experimentSet.getExperiment("unspecified");
        Query q = ex.getQuery();
        assertEquals(QueryOperator.OPERATOR.UNSPECIFIED, ((MultiFieldQuery) q).getQueryOperator().getOperator());


        ex = experimentSet.getExperiment("query_and");
        q = ex.getQuery();
        assertEquals(QueryOperator.OPERATOR.AND, ((MultiFieldQuery) q).getQueryOperator().getOperator());

        ex = experimentSet.getExperiment("query_or_none");
        q = ex.getQuery();
        assertEquals(QueryOperator.OPERATOR.OR, ((MultiFieldQuery) q).getQueryOperator().getOperator());
        assertEquals(QueryOperator.MM.NONE, ((MultiFieldQuery) q).getQueryOperator().getMM());

        ex = experimentSet.getExperiment("query_or_int");
        q = ex.getQuery();
        assertEquals(QueryOperator.OPERATOR.OR, ((MultiFieldQuery) q).getQueryOperator().getOperator());
        assertEquals(QueryOperator.MM.INTEGER, ((MultiFieldQuery) q).getQueryOperator().getMM());
        assertEquals(2, ((MultiFieldQuery) q).getQueryOperator().getInt());

        ex = experimentSet.getExperiment("query_or_int_no_op");
        q = ex.getQuery();
        assertEquals(QueryOperator.OPERATOR.OR, ((MultiFieldQuery) q).getQueryOperator().getOperator());
        assertEquals(QueryOperator.MM.INTEGER, ((MultiFieldQuery) q).getQueryOperator().getMM());
        assertEquals(2, ((MultiFieldQuery) q).getQueryOperator().getInt());

        ex = experimentSet.getExperiment("query_or_percent_neg20");
        q = ex.getQuery();
        assertEquals(QueryOperator.OPERATOR.OR, ((MultiFieldQuery) q).getQueryOperator().getOperator());
        assertEquals(QueryOperator.MM.FLOAT, ((MultiFieldQuery) q).getQueryOperator().getMM());
        assertEquals(-0.20, ((MultiFieldQuery) q).getQueryOperator().getMmFloat(), 0.01);

        ex = experimentSet.getExperiment("query_or_percent_no_op_neg20");
        q = ex.getQuery();
        assertEquals(QueryOperator.OPERATOR.OR, ((MultiFieldQuery) q).getQueryOperator().getOperator());
        assertEquals(QueryOperator.MM.FLOAT, ((MultiFieldQuery) q).getQueryOperator().getMM());
        assertEquals(-0.20, ((MultiFieldQuery) q).getQueryOperator().getMmFloat(), 0.001);

        ex = experimentSet.getExperiment("query_or_percent_18");
        q = ex.getQuery();
        assertEquals(QueryOperator.OPERATOR.OR, ((MultiFieldQuery) q).getQueryOperator().getOperator());
        assertEquals(QueryOperator.MM.FLOAT, ((MultiFieldQuery) q).getQueryOperator().getMM());
        assertEquals(0.18, ((MultiFieldQuery) q).getQueryOperator().getMmFloat(), 0.01);

        ex = experimentSet.getExperiment("query_or_percent_no_op_18");
        q = ex.getQuery();
        assertEquals(QueryOperator.OPERATOR.OR, ((MultiFieldQuery) q).getQueryOperator().getOperator());
        assertEquals(QueryOperator.MM.FLOAT, ((MultiFieldQuery) q).getQueryOperator().getMM());
        assertEquals(0.18, ((MultiFieldQuery) q).getQueryOperator().getMmFloat(), 0.01);

    }

    @Test
    public void testBadAtN() throws Exception {
        //none of the scorers have an "atN" set
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> {
                    try (Reader reader =
                                 new BufferedReader(new InputStreamReader(
                                         getClass().getResourceAsStream(
                                                 "/test-documents/experiments_solr_no_atN.json"),
                                         StandardCharsets.UTF_8))) {
                        ExperimentSet.fromJson(reader);
                    }
                });
    }
}
