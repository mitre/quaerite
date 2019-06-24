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
import org.mitre.quaerite.core.SearchResultSet;

/**
 * Returns 1 if there was any hit in the results; 0 otherwise.
 */
public class AtLeastOneAtN extends SummingScoreAggregator implements JudgmentScorer {

    public AtLeastOneAtN(int atN) {
        super("AtLeastOneAtN", atN);
    }

    @Override
    public double score(Judgments judgments, SearchResultSet searchResultSet) {
        int val = 0;
        for (int i = 0; i < getAtN() && i < searchResultSet.size(); i++) {
            if (judgments.containsJudgment(searchResultSet.get(i))) {
                val = 1;
                break;
            }
        }
        addScore(judgments.getQueryInfo(), val);
        return val;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AtLeastOneAtN)) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
