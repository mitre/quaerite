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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mitre.quaerite.core.QueryInfo;

public abstract class SummingScoreAggregator extends Scorer {
    public static String SUM = "sum";

    private static final List<String> STATISTICS =
            Collections.unmodifiableList(Arrays.asList(new String[]{SUM}));

    public SummingScoreAggregator(String name, int atN) {
        super(name, atN);

    }

    @Override
    public Map<String, Double> getSummaryStatistics(String querySet) {
        Set<QueryInfo> queryInfos = getQueryInfos(querySet);
        Map<String, Double> stats = new HashMap<>();
        for (QueryInfo q : queryInfos) {
            Double val = scores.get(q);
            Double sum = stats.get(SUM);
            if (sum == null) {
                sum = val;
            } else {
                sum += val;
            }
            stats.put(SUM, sum);
        }
        return Collections.unmodifiableMap(stats);
    }



    @Override
    public List<String> getStatistics() {
        return STATISTICS;
    }

    @Override
    public String getPrimaryStatisticName() {
        return getName()+"_"+SUM;
    }


}
