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
 * This ignores quaerite scores and answers the question: of
 * the documents that had a quaerite score >= 0, what's
 * the best rank?
 */
public class PrecisionAtN extends AbstractJudgmentScorer {

    public PrecisionAtN(int atN) {
        super("precision", atN);
    }

    @Override
    public double score(Judgments judgments, SearchResultSet searchResultSet) {
        if (searchResultSet.size() == 0) {
            return 0.0;
        }
        int hits = 0;
        for (int i = 0; i < getAtN() && i < searchResultSet.size(); i++) {
            if (judgments.containsJudgment(searchResultSet.get(i))) {
                hits++;
            }
        }
        double val = (double)hits/(double) searchResultSet.size();
        addScore(judgments.getQueryInfo(), val);
        return val;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PrecisionAtN)) return false;
        return super.equals(o);
    }
}
