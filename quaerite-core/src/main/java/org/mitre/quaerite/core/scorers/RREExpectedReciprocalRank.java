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

/**
 * This is an extension of ERR that follows Max Irwin's
 * implementation for <a href="https://github.com/SeaseLtd/rated-ranking-evaluator">rre</a>.
 * This uses a "fair" grade of <code>Math.round(max/2)</code>
 * for documents that haven't received a judgment.
 */
public class RREExpectedReciprocalRank extends ExpectedReciprocalRank {

    public RREExpectedReciprocalRank(int atN) {
        super("RRE_ERR", atN);
    }
    
    public RREExpectedReciprocalRank(int atN, double maxScore) {
        super("RRE_ERR", atN, maxScore);
    }

    @Override
    double getGrade(Judgments judgments, String id, double max) {
        if (! judgments.containsJudgment(id)) {
            return Math.round(max / 2.0);
        }
        return judgments.getJudgment(id);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RREExpectedReciprocalRank)) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
