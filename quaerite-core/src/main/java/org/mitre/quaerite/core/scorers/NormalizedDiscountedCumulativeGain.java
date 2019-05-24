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

import java.util.ArrayList;
import java.util.List;

import org.mitre.quaerite.core.Judgments;
import org.mitre.quaerite.core.SearchResultSet;


public class NormalizedDiscountedCumulativeGain extends AbstractRankScorer {

    DiscountedCumulativeGain dcg;

    public NormalizedDiscountedCumulativeGain(int atN) {
        super(atN);
        dcg = new DiscountedCumulativeGain2002(atN);
    }

    @Override
    public double score(Judgments judgments, SearchResultSet searchResultSet) {

        if (dcg == null) {
            dcg = new DiscountedCumulativeGain2002(atN);
        }

        final double idealDCG = calculateIdeal(dcg, judgments,
                Math.min(atN, searchResultSet.size()), searchResultSet.getTotalHits(),
                searchResultSet.getQueryTime(),
                searchResultSet.getElapsedTime());
        if (idealDCG == 0) {
            return 0.0;
        }
        return dcg.score(judgments, searchResultSet)/idealDCG;
    }

    @Override
    String _getName() {
        return "ndcg";
    }

    private double calculateIdeal(DiscountedCumulativeGain dcg, Judgments judgments, int size, long totalHits,
                                  long queryTime, long elapsedTime) {
        List<String> bestResults = new ArrayList<>();
        for (String id : judgments.getSortedJudgments().keySet()) {
            bestResults.add(id);
            if (bestResults.size() >= size) {
                break;
            }
        }
        return dcg.score(judgments, new SearchResultSet(totalHits, queryTime,
                elapsedTime, bestResults));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NormalizedDiscountedCumulativeGain)) return false;
        return super.equals(o);
    }
}
