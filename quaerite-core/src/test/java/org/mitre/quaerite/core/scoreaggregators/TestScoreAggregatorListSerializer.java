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
package org.mitre.quaerite.core.scoreaggregators;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class TestScoreAggregatorListSerializer {

    @Test
    public void testBasic() {
        ScoreAggregator scoreAggregator = new HadAtLeastOneHitAtKAggregator(2);
        String json = ScoreAggregatorListSerializer.toJson(scoreAggregator);
        ScoreAggregator revivified = ScoreAggregatorListSerializer.fromJson(json);
        assertEquals(revivified.getClass().getCanonicalName(), revivified.getClass().getCanonicalName());
        assertEquals(scoreAggregator.getK(),
                ((AbstractScoreAggregator)revivified).getK());
    }

}
