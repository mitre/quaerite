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

import java.util.Arrays;
import java.util.List;

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
public class TestRREExpectedReciprocalRank {

    private static SearchResultSet RESULTS;

    static {
        List<String> ids = Arrays.asList(
                ("1 2 3 4 5 6 7 8 9 10 11 12 13 14 15").split("\\s+"));
        RESULTS = new SearchResultSet(100, 100, 100, ids);
    }

    @Test
    public void testTop10() {
        //test score when top 10 all have score of 3
        RREExpectedReciprocalRank err = new RREExpectedReciprocalRank(10);
        Judgments judgments = newJudgments();
        for (int i = 0; i < 10; i++) {
            judgments.addJudgment(RESULTS.get(i), 3);
        }
        assertEquals(0.935, err.score(judgments, RESULTS), 0.01);
    }

    @Test
    public void testBottom5In10() {
        //test score when bottom 5 of 10 all have score of 3
        RREExpectedReciprocalRank err = new RREExpectedReciprocalRank(10);
        Judgments judgments = newJudgments();
        for (int i = 5; i < 15; i++) {
            judgments.addJudgment(RESULTS.get(i), 3);
        }
        assertEquals(0.591, err.score(judgments, RESULTS), 0.01);
    }

    @Test
    public void test9thIsRelevant() {
        //test score when bottom 5 of 10 all have score of 3
        RREExpectedReciprocalRank err = new RREExpectedReciprocalRank(10);
        Judgments judgments = newJudgments();
        for (int i = 9; i < 15; i++) {
            judgments.addJudgment(RESULTS.get(i), 3);
        }
        assertEquals(0.589, err.score(judgments, RESULTS), 0.01);
    }

    private static Judgments newJudgments() {
        return new Judgments(
                new QueryInfo("1",
                        QueryInfo.DEFAULT_QUERY_SET, new QueryStrings(), -1));
    }
}
