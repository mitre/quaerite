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
package org.mitre.quaerite.scorers;

import org.apache.commons.math3.util.FastMath;
import org.mitre.quaerite.Judgments;
import org.mitre.quaerite.ResultSet;

/**
 * Kalervo Järvelin, Jaana Kekäläinen: Cumulated gain-based evaluation of IR techniques.
 * ACM Transactions on Information Systems 20(4), 422–446 (2002)
 */
public class DiscountedCumulativeGain2002 extends DiscountedCumulativeGain {

    public DiscountedCumulativeGain2002(int atN) {
        super(atN);
    }

    @Override
    public double score(Judgments judgments, ResultSet resultSet) {
        int rank = 1;
        double sum = 0;
        for (int i = 0; i < atN && i < resultSet.size(); i++) {
            String id = resultSet.get(i);
            if (judgments.containsJudgment(id)) {
                double rel = judgments.getJudgment(id);
                sum += rel/FastMath.log(2, rank+1);
            }
            rank++;
        }
        return sum;
    }

    @Override
    String _getName() {
        return "dcg2002";
    }
}
