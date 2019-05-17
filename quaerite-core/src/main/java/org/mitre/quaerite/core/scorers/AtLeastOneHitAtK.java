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
 * Returns 1 if there was any hit in the results; 0 otherwise.
 */
public class AtLeastOneHitAtK extends AbstractRankScorer {

    public AtLeastOneHitAtK(int atN) {
        super(atN);
    }

    @Override
    public double score(Judgments judgments, ResultSet resultSet) {

        for (int i = 0; i < atN && i < resultSet.size(); i++) {
            if (judgments.containsJudgment(resultSet.get(i))) {
                return 1;
            }
        }
        return 0.0;
    }

    @Override
    String _getName() {
        return "AtLeastOneHitAtK";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AtLeastOneHitAtK)) return false;
        return super.equals(o);
    }

}
