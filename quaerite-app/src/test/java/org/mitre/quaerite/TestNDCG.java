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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mitre.quaerite.scorers.CumulativeGain;
import org.mitre.quaerite.scorers.DiscountedCumulativeGain2002;
import org.mitre.quaerite.scorers.NormalizedDiscountedCumulativeGain;


public class TestNDCG {
    static Judgments judgments = new Judgments(new QueryInfo("", "query", 1));
    static ResultSet results;
    @BeforeAll
    public static void setUp() {

        judgments.addJugment("1", 3);
        judgments.addJugment("2", 2);
        judgments.addJugment("3", 3);
        judgments.addJugment("5", 1);
        judgments.addJugment("6", 2);
        judgments.addJugment("7", 3);
        judgments.addJugment("8", 2);
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            ids.add(Integer.toString(i));
        }
        results = new ResultSet(1000, 10, 100, ids);
    }


    @Test
    public void testCG() {
        CumulativeGain cumulativeGain = new CumulativeGain(10);
        assertEquals(11.0f, cumulativeGain.score(judgments, results), 0.001);
    }

    @Test
    public void testDCG2002() {
        DiscountedCumulativeGain2002 dcg2002 = new DiscountedCumulativeGain2002(10);
        assertEquals(6.861, dcg2002.score(judgments, results), 0.001);
    }

    @Test
    public void testNDCG() {
        NormalizedDiscountedCumulativeGain ndcg =
                new NormalizedDiscountedCumulativeGain(10);
        assertEquals(0.785, ndcg.score(judgments, results), 0.001);
    }
}
