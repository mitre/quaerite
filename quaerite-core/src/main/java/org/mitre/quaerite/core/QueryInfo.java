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

package org.mitre.quaerite.core;

import java.util.Objects;

public class QueryInfo {

    public static final String DEFAULT_QUERY_SET = "";
    private final String queryId;
    private final String querySet;
    private final QueryStrings queryStrings;
    private final int queryCount;

    public QueryInfo(String queryId, String querySet, QueryStrings queryStrings, int queryCount) {
        this.queryId = queryId;
        this.querySet = querySet;
        this.queryStrings = queryStrings;
        this.queryCount = queryCount;
    }

    public String getQuerySet() {
        return querySet;
    }

    public QueryStrings getQueryStrings() {
        return queryStrings;
    }

    public int getQueryCount() {
        return queryCount;
    }

    public String getQueryId() {
        return queryId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueryInfo)) return false;
        QueryInfo queryInfo = (QueryInfo) o;
        return queryCount == queryInfo.queryCount &&
                Objects.equals(queryId, queryInfo.queryId) &&
                Objects.equals(querySet, queryInfo.querySet) &&
                Objects.equals(queryStrings, queryInfo.queryStrings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryId, querySet, queryStrings, queryCount);
    }

    @Override
    public String toString() {
        return "QueryInfo{" +
                "queryId='" + queryId + '\'' +
                ", querySet='" + querySet + '\'' +
                ", queryStrings=" + queryStrings +
                ", queryCount=" + queryCount +
                '}';
    }
}
