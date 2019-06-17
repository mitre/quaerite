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
package org.mitre.quaerite.core.scorers;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.util.DoubleArray;
import org.apache.commons.math3.util.ResizableDoubleArray;
import org.mitre.quaerite.core.QueryInfo;

public abstract class DistributionalScoreAggregator extends Scorer {

    public static String MEAN = "mean";
    public static String MEDIAN = "median";
    public static String STDEV = "stdev";

    private static final List<String> STATISTICS =
            Collections.unmodifiableList(Arrays.asList(new String[]{MEAN, MEDIAN, STDEV}));

    private NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);


    public DistributionalScoreAggregator(String name, int atN) {
        super(name, atN);
        numberFormat.setMinimumFractionDigits(2);
        numberFormat.setMaximumFractionDigits(2);
        numberFormat.setRoundingMode(RoundingMode.HALF_DOWN);
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

    @Override
    public String getPrimaryStatisticName() {
        return getName()+"_"+MEAN;
    }

    @Override
    public String format(String statName, Map<String, Double> values) {
        if (! values.containsKey(statName)) {
            throw new IllegalArgumentException("can't find stat name: "+statName
                    + "in "+values);
        }

        return numberFormat.format(values.get(statName));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DistributionalScoreAggregator)) return false;
        return super.equals(o);
    }
}
