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

package org.mitre.quaerite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mitre.quaerite.core.Experiment;
import org.mitre.quaerite.core.ExperimentSet;
import org.mitre.quaerite.core.JudgmentList;
import org.mitre.quaerite.core.Judgments;
import org.mitre.quaerite.core.QueryInfo;
import org.mitre.quaerite.core.features.FQ;
import org.mitre.quaerite.core.features.SimpleStringFeature;
import org.mitre.quaerite.core.features.SimpleStringListFeature;
import org.mitre.quaerite.core.features.WeightableField;
import org.mitre.quaerite.core.features.WeightableListFeature;
import org.mitre.quaerite.core.scoreaggregators.AtLeastOneHitAtKAggregator;
import org.mitre.quaerite.db.ExperimentDB;

public class TestExperimentDB {
    private static Path DB_DIR;

    @BeforeAll
    public static void init() throws Exception {
        DB_DIR = Files.createTempDirectory("exp-");
    }

    @AfterAll
    public static void tearDown() throws Exception {
        FileUtils.deleteDirectory(DB_DIR.toFile());
    }

    @Test
    public void testBasicDB() throws Exception {
        ExperimentDB db = ExperimentDB.open(DB_DIR);
        Experiment experiment = new Experiment("test1", "http://solr");
        WeightableListFeature weightableListFeature = new WeightableListFeature("qf");
        weightableListFeature.add(new WeightableField("f1^2"));
        weightableListFeature.add(new WeightableField("f2^5"));
        weightableListFeature.add(new WeightableField("f3^10"));

        experiment.addParam("qf", weightableListFeature);
        experiment.addParam("fq", new SimpleStringListFeature("fq",
                Arrays.asList("fq1", "fq2"), 0, 2));
        db.addExperiment(experiment);
        db.addScoreAggregator(new AtLeastOneHitAtKAggregator(1));
        db.addScoreAggregator(new AtLeastOneHitAtKAggregator(3));
        db.addScoreAggregator(new AtLeastOneHitAtKAggregator(5));
        db.addScoreAggregator(new AtLeastOneHitAtKAggregator(10));
        db.close();

        db = ExperimentDB.open(DB_DIR);

        ExperimentSet experimentSet = db.getExperiments();

        Experiment revivified = null;
        for (Experiment e : experimentSet.getExperiments().values()) {
            revivified = e;
            break;
        }

        assertEquals("test1", revivified.getName());
        assertEquals("http://solr", revivified.getSearchServerUrl());
        assertEquals(3, ((WeightableListFeature)revivified.getParams("qf")).size());
        assertEquals(2, ((FQ)revivified.getParams("fq")).size());
        db.close();

        db = ExperimentDB.open(DB_DIR);

        Judgments judgments = new Judgments(new QueryInfo("", "q1", 1));

        judgments.addJudgment("id1", 2.0);
        judgments.addJudgment("id2", 4.0);
        judgments.addJudgment("id5", 6.0);

        db.addJudgment(judgments);
        db.close();

        db = ExperimentDB.open(DB_DIR);
        JudgmentList judgmentsList = db.getJudgments();
        assertEquals(1, judgmentsList.getJudgmentsList().size());

        Judgments revivifiedJudgments = judgmentsList.getJudgmentsList().get(0);
        assertEquals("q1", revivifiedJudgments.getQuery());
        assertEquals(4.0, revivifiedJudgments.getJudgment("id2"), 0.01);
        db.close();

    }

}
