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


import org.mitre.quaerite.core.QueryInfo;
import org.mitre.quaerite.core.SearchResultSet;

/**
 * How many queries had 0 results returned
 */
public class ZeroResults extends SummingScoreAggregator implements SearchResultSetScorer {


    public ZeroResults() {
        super("ZeroResults", 0);
    }

    @Override
    public double score(QueryInfo queryInfo, SearchResultSet searchResultSet) {
        int ret = -1;
        if (searchResultSet.size() == 0) {
            ret = 1;
        } else {
            ret = 0;
        }
        addScore(queryInfo, ret);
        return ret;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ZeroResults)) return false;
        return super.equals(o);
    }
}
