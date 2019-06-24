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

import org.apache.commons.math3.util.FastMath;
import org.mitre.quaerite.core.Judgments;
import org.mitre.quaerite.core.SearchResultSet;


/**
 * Kalervo Järvelin, Jaana Kekäläinen: Cumulated gain-based evaluation of IR techniques.
 * ACM Transactions on Information Systems 20(4), 422–446 (2002)
 */
public class DiscountedCumulativeGain2002 extends AbstractJudgmentScorer {

    public DiscountedCumulativeGain2002(int atN) {
        super("DCG2002", atN);
    }

    protected DiscountedCumulativeGain2002(String name, int atN) {
        super (name, atN);
    }

    @Override
    public double score(Judgments judgments, SearchResultSet searchResultSet) {
        double score = _score(judgments, searchResultSet);
        addScore(judgments.getQueryInfo(), score);
        return score;
    }

    protected double _score(Judgments judgments, SearchResultSet searchResultSet) {
        int rank = 1;
        double sum = 0;
        for (int i = 0; i < getAtN() && i < searchResultSet.size(); i++) {
            String id = searchResultSet.get(i);
            if (judgments.containsJudgment(id)) {
                double rel = judgments.getJudgment(id);
                sum += rel / FastMath.log(2, rank + 1);
            }
            rank++;
        }
        return sum;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiscountedCumulativeGain2002)) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
