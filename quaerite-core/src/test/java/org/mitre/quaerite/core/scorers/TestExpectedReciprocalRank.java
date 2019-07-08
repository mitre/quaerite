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
package org.mitre.quaerite.core.scorers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mitre.quaerite.core.Judgments;
import org.mitre.quaerite.core.QueryInfo;
import org.mitre.quaerite.core.QueryStrings;
import org.mitre.quaerite.core.SearchResultSet;

/**
 * These tests were copied from Max Irwin's tests in
 * RRE: https://github.com/SeaseLtd/rated-ranking-evaluator/blob/master/
 * rre-core/src/test/java/io.sease.rre.core.domain.metrics.impl/
 * ExpectedReciprocalRankTestCase.java
 */
public class TestExpectedReciprocalRank {

    private static SearchResultSet RESULTS;
    private static Map<String, String> DEFAULT_PARAMS;

    static {
        List<String> ids = Arrays.asList(
                ("1 2 3 4 5 6 7 8 9 10 11 12 13 14 15").split("\\s+"));
        RESULTS = new SearchResultSet(100, 100, 100, ids);
        DEFAULT_PARAMS = new HashMap<>();
        //These values follow RRE.  Take care and make sure that they meet
        //your use case before you select these in practice.
        DEFAULT_PARAMS.put(ExpectedReciprocalRank.MAX_SCORE, "3");
        DEFAULT_PARAMS.put(ExpectedReciprocalRank.NO_JUDGMENT, "2");
    }

    @Test
    public void testTop10() {
        //test score when top 10 all have score of 3
        ExpectedReciprocalRank err = new ExpectedReciprocalRank(10, DEFAULT_PARAMS);
        Judgments judgments = newJudgments();
        for (int i = 0; i < 10; i++) {
            judgments.addJudgment(RESULTS.get(i), 3);
        }
        assertEquals(0.935, err.score(judgments, RESULTS), 0.01);
    }

    @Test
    public void testBottom5In10() {
        //test score when bottom 5 of 10 all have score of 3
        ExpectedReciprocalRank err = new ExpectedReciprocalRank(10, DEFAULT_PARAMS);
        Judgments judgments = newJudgments();
        for (int i = 5; i < 15; i++) {
            judgments.addJudgment(RESULTS.get(i), 3);
        }
        assertEquals(0.591, err.score(judgments, RESULTS), 0.01);
    }

    @Test
    public void test9thIsRelevant() {
        //test score when bottom 5 of 10 all have score of 3
        ExpectedReciprocalRank err = new ExpectedReciprocalRank(10, DEFAULT_PARAMS);
        Judgments judgments = newJudgments();
        for (int i = 9; i < 15; i++) {
            judgments.addJudgment(RESULTS.get(i), 3);
        }
        assertEquals(0.589, err.score(judgments, RESULTS), 0.01);
    }

    @Test
    public void testJudgmentBeyondMax() {
        Map<String, String> tmpParams = new HashMap<>();
        tmpParams.put(ExpectedReciprocalRank.MAX_SCORE, "2");
        ExpectedReciprocalRank err = new ExpectedReciprocalRank(10, tmpParams);

        Judgments judgments = newJudgments();
        for (int i = 0; i < 10; i++) {
            judgments.addJudgment(RESULTS.get(i), 3);
        }
        assertThrows(IllegalArgumentException.class,
                () -> err.score(judgments, RESULTS));
    }

    @Test
    public void testMissingBeyondMax() {
        Map<String, String> tmpParams = new HashMap<>();
        tmpParams.put(ExpectedReciprocalRank.MAX_SCORE, "4");
        tmpParams.put(ExpectedReciprocalRank.NO_JUDGMENT, "5");

        assertThrows(IllegalArgumentException.class,
                () -> new ExpectedReciprocalRank(10, tmpParams));
    }

    private static Judgments newJudgments() {
        return new Judgments(
                new QueryInfo("1",
                        QueryInfo.DEFAULT_QUERY_SET, new QueryStrings(), -1));
    }
}
