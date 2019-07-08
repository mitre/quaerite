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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mitre.quaerite.core.ExperimentSet;
import org.mitre.quaerite.core.serializers.ScorerListSerializer;

public class TestScorerListSerializer {

    @Test
    public void testBasic() {
        Scorer scorer = new AtLeastOneAtN(2);
        String json = ScorerListSerializer.toJson(scorer);
        Scorer revivified = ScorerListSerializer.fromJson(json);
        assertEquals(revivified.getClass().getCanonicalName(), revivified.getClass().getCanonicalName());
        assertEquals(scorer.getAtN(),
                ((Scorer)revivified).getAtN());
    }

    @Test
    public void testERR() throws Exception {
        ExperimentSet experimentSet = null;
        try (Reader reader = new BufferedReader(
                new InputStreamReader(
                        TestScorerListSerializer.class
                                .getResourceAsStream("/test-documents/experiments_solr_err.json"),
                        StandardCharsets.UTF_8))) {
            experimentSet = ExperimentSet.fromJson(reader);
        }
        List<Scorer> scorers = experimentSet.getScorers();
        ExpectedReciprocalRank err = (ExpectedReciprocalRank)scorers.get(0);
        Double max = err.getMaxScore();
        assertEquals(max, 100, 0.1);

        double noJudgment = err.getNoJudgment();
        assertEquals(0.1, noJudgment, 0.01);

        String json = ScorerListSerializer.toJson(scorers);
        List<Scorer> deserialized = ScorerListSerializer.fromJsonList(json);
        System.out.println(deserialized);
        assertEquals(scorers, deserialized);
    }

}
