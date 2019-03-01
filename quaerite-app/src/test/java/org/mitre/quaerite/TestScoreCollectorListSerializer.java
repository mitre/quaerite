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

import org.junit.jupiter.api.Test;
import org.mitre.quaerite.scorecollectors.AbstractScoreCollector;
import org.mitre.quaerite.scorecollectors.HadAtLeastOneHitAtKCollector;
import org.mitre.quaerite.scorecollectors.ScoreCollector;
import org.mitre.quaerite.scorecollectors.ScoreCollectorListSerializer;

public class TestScoreCollectorListSerializer {

    @Test
    public void testBasic() {
        ScoreCollector scoreCollector = new HadAtLeastOneHitAtKCollector(2);
        String json = ScoreCollectorListSerializer.toJson(scoreCollector);
        System.out.println(json);
        ScoreCollector revivified = ScoreCollectorListSerializer.fromJson(json);
        assertEquals(revivified.getClass().getCanonicalName(), revivified.getClass().getCanonicalName());
        assertEquals(scoreCollector.getK(),
                ((AbstractScoreCollector)revivified).getK());
    }

/*    @Test
    public void testList() {
        List<RankScorer> scorers = new ArrayList<>();
        scorers.add(new PrecisionAtK(2));
        scorers.add(new HighestRank(4));
        String json = ScorerSerializer.toJson(scorers);

        List<RankScorer> revivified = ScorerSerializer.fromJsonList(json);
        for (RankScorer r : revivified) {
            System.out.println(r);
        }

    }*/
}
