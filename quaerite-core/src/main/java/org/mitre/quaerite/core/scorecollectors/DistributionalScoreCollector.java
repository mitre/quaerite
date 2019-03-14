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
 *
 */
package org.mitre.quaerite.core.scorecollectors;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.util.DoubleArray;
import org.apache.commons.math3.util.ResizableDoubleArray;
import org.mitre.quaerite.core.QueryInfo;
import org.mitre.quaerite.core.scorers.AbstractRankScorer;

public class DistributionalScoreCollector extends AbstractScoreCollector {

    public static String MEAN = "mean";
    public static String MEDIAN = "median";
    public static String STDEV = "stdev";

    private static final List<String> STATISTICS =
            Collections.unmodifiableList(Arrays.asList(new String[]{MEAN, MEDIAN, STDEV}));

    public DistributionalScoreCollector(AbstractRankScorer scorer) {
        super(scorer);
    }


    @Override
    public Map<String, Double> getSummaryStatistics(String querySet) {
        StatSummarizer statSummarizer = new StatSummarizer();
        for (Map.Entry<QueryInfo, Double> scoreEntry : scores.entrySet()) {
            if (scoreEntry.getKey().getQuerySet().equals(querySet)) {
                statSummarizer.addValue(scoreEntry.getValue());
            }
        }

        Map<String, Double> stats = new LinkedHashMap<>();
        stats.put(MEAN, statSummarizer.getMean());
        stats.put(MEDIAN, statSummarizer.getMedian());
        stats.put(STDEV, statSummarizer.getStandardDeviation());

        return Collections.unmodifiableMap(stats);
    }

    @Override
    public List<String> getStatistics() {
        return STATISTICS;
    }

    private class StatSummarizer {
        SummaryStatistics summaryStatistics = new SummaryStatistics();
        DoubleArray doubleArray = new ResizableDoubleArray();

        private void addValue(double d) {
            summaryStatistics.addValue(d);
            doubleArray.addElement(d);
        }

        public double getMean() {
            return summaryStatistics.getMean();
        }

        public double getMedian() {
            Median median = new Median();
            return median.evaluate(doubleArray.getElements());
        }

        public double getStandardDeviation() {
            return summaryStatistics.getStandardDeviation();
        }
    }
}
