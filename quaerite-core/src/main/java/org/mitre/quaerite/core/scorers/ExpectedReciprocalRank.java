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

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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
 * If a document has no judgment, this will grant a score of {@link #getNoJudgment()}.
 *
 * As of July, 2019, <a href="https://github.com/SeaseLtd/rated-ranking-evaluator">RRE's</a>
 * implementation automatically rounds the value of the mean of 0 and {@link #getMaxScore()}
 *
 * If a "maxScore" is not specified, this will calculate the highest score
 * _per_ {@link Judgments} object for each calculation.
 *
 * If a {@link Judgments} objects has a score that is higher than
 * {@link #maxScore}, this scorer will throw an {@link IllegalArgumentException}.
 */
public class ExpectedReciprocalRank extends AbstractJudgmentScorer {

    public static final String MAX_SCORE = "maxScore";
    public static final String NO_JUDGMENT = "noJudgment";

    public static final double DEFAULT_NO_JUDGMENT = Judgments.NO_JUDGMENT;

    private final Double maxScore;
    private final double noJudgment;

    public ExpectedReciprocalRank(int atN, Map<String, String> params) {
        super("ERR", atN, params);
        if (params.containsKey(MAX_SCORE)) {
            maxScore = Double.parseDouble(params.get(MAX_SCORE));
        } else {
            maxScore = null;
        }
        if (params.containsKey(NO_JUDGMENT)) {
            noJudgment = Double.parseDouble(params.get(NO_JUDGMENT));
        } else {
            noJudgment = DEFAULT_NO_JUDGMENT;
        }
        if (noJudgment != DEFAULT_NO_JUDGMENT && maxScore != null &&
                noJudgment > maxScore) {
            throw new IllegalArgumentException(
                    String.format(Locale.US,
                            "noJudgment (%s) can't be > maxScore (%s)",
                            noJudgment, maxScore));

        }
    }

    @Override
    public double score(Judgments judgments, SearchResultSet searchResultSet) {
        double maxInTheseJudgments = getMax(judgments);
        if (maxScore != null && maxInTheseJudgments > maxScore) {
            throw new IllegalArgumentException(
                    String.format(Locale.US,
                            "Maximum in this jugment set (%s) is > than the max (%s)",
                            maxInTheseJudgments, maxScore));

        }
        double max = maxScore != null ? maxScore : maxInTheseJudgments;
        
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
            return noJudgment;
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

    public Double getMaxScore() {
        return maxScore;
    }

    public double getNoJudgment() {
        return noJudgment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpectedReciprocalRank)) return false;
        if (!super.equals(o)) return false;
        ExpectedReciprocalRank that = (ExpectedReciprocalRank) o;
        return Double.compare(that.noJudgment, noJudgment) == 0 &&
                maxScore.equals(that.maxScore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), maxScore, noJudgment);
    }

    @Override
    public String toString() {
        return "ExpectedReciprocalRank{" +
                "maxScore=" + maxScore +
                ", noJudgment=" + noJudgment +
                ", useForTrain=" + getUseForTrain() +
                ", useForTest=" + getUseForTest() +
                ", exportPMatrix=" + getExportPMatrix() +
                ", params=" + getParams() +
                '}';
    }
}
