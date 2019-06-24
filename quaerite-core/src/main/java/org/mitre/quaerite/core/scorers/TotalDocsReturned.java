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


import org.mitre.quaerite.core.QueryInfo;
import org.mitre.quaerite.core.SearchResultSet;

public class TotalDocsReturned extends SummingScoreAggregator
        implements SearchResultSetScorer {

    public TotalDocsReturned(int atN) {
        super("TotalDocsReturned", atN);
    }

    @Override
    public double score(QueryInfo queryInfo, SearchResultSet searchResultSet) {
        double hits =  searchResultSet.getTotalHits();
        addScore(queryInfo, hits);
        return hits;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TotalDocsReturned)) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
