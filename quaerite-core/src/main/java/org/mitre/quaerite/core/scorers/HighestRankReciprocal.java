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
    Reciprocal of the highest single hit; {@link HighestRank#NOT_FOUND} otherwise.
 */
public class HighestRankReciprocal extends HighestRank {

    public HighestRankReciprocal(int atN) {
        super("highestRankReciprocal", atN);
    }

    @Override
    public double score(Judgments judgments, SearchResultSet searchResultSet) {
        int rank = super._score(judgments, searchResultSet);
        if (rank == NOT_FOUND) {
            return NOT_FOUND;
        } else {
            double ret = (double)1 / rank;
            addScore(judgments.getQueryInfo(), ret);
            return ret;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HighestRankReciprocal)) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
