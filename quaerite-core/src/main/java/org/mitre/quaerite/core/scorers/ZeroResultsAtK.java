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


import org.mitre.quaerite.core.Judgments;
import org.mitre.quaerite.core.ResultSet;

/**
 * How many queries had 0 results returned
 */
public class ZeroResultsAtK extends AbstractRankScorer {


    public ZeroResultsAtK(int atK) {
        super(atK);
    }

    @Override
    String _getName() {
        return "r";
    }

    @Override
    public double score(Judgments judgments, ResultSet resultSet) {
        int hits = 0;
        for (int i = 0; i < atN && i < resultSet.size(); i++) {
            if (judgments.containsJudgment(resultSet.get(i))) {
                hits++;
            }
        }
        return (hits == 0) ? 1 : 0;
    }
}
