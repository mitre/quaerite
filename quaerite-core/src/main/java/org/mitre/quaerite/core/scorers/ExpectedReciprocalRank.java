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
 * This is an implementation of Olivier Chappelle's
 * <a href="http://olivier.chapelle.cc/pub/err.pdf">err.pdf</a>.
 *
 * I'd like to thank Max Irwin for his implementation, which I referenced
 * in addition to the original paper when implementing this.
 *
 * If a document has no judgment or judgment of 0, this will not grant
 * any weight to that document in computing ERR.
 * See also {@link RREExpectedReciprocalRank} for an alternative used by
 * <a href="https://github.com/SeaseLtd/rated-ranking-evaluator">rre</a>
 */
public class ExpectedReciprocalRank extends AbstractJudgmentScorer {

    Double _maxScore;

    public ExpectedReciprocalRank(int atN, double maxScore) {
        super("ERR", atN);
        _maxScore = maxScore;
    }

    ExpectedReciprocalRank(String name, int atN, double maxScore) {
        super(name, atN);
        _maxScore = maxScore;
    }

    public ExpectedReciprocalRank(int atN) {
        super("ERR", atN);
    }

    ExpectedReciprocalRank(String name, int atN) {
        super(name, atN);
    }
    @Override
    public double score(Judgments judgments, SearchResultSet searchResultSet) {
        double max = _maxScore != null ? _maxScore : getMax(judgments);
        
        if (max < 0) {
            throw new IllegalArgumentException("maximum relevance grade must be > 0");
        }
        double twoToTheMax = FastMath.pow(2, max);
        double p = 1.0;
        double err = 0.0;
        for (int i = 0; i < getAtN() && i < searchResultSet.size(); i++) {
            String id = searchResultSet.get(i);
            double grade = getGrade(judgments, id, max);
            if (grade <= 0.0) {
                continue;
            }
            int rank = i + 1;
            double mappedRelevance = mapRelevanceScore(
                    grade, twoToTheMax);
            err = err + (p * mappedRelevance / rank);
            p = p * (1.0 - mappedRelevance);

        }

        addScore(judgments.getQueryInfo(), err);
        return err;
    }

    double getGrade(Judgments judgments, String id, double max) {
        if (!judgments.containsJudgment(id)) {
            return Judgments.NO_JUDGMENT;
        }
        return judgments.getJudgment(id);
    }

    private double mapRelevanceScore(double relevanceScore, double twoToTheMax) {
        return (FastMath.pow(2, relevanceScore) - 1.0) / twoToTheMax;

    }

    private double getMax(Judgments judgments) {
        for (Double d : judgments.getSortedJudgments().values()) {
            return d;
        }
        return -1.0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpectedReciprocalRank)) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
