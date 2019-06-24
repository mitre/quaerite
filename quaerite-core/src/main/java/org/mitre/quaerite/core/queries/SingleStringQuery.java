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
package org.mitre.quaerite.core.queries;


import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.mitre.quaerite.core.QueryStrings;

/**
 * This encapsulates basic queries that take a single
 * string from a user.  If the {@link #queryStringName}
 * is not set, this falls back to {@link QueryStrings#DEFAULT_QUERY_NAME}.
 */
public abstract class SingleStringQuery extends Query {

    private String queryStringName;
    private volatile String queryString;

    public SingleStringQuery(String queryString) {
        this.queryString = queryString;
    }

    @Override
    public Set<String> setQueryStrings(QueryStrings queryStrings) {
        Set<String> used = new HashSet<>();
        if (StringUtils.isBlank(queryStringName)) {
            queryString = queryStrings.getStringByName(QueryStrings.DEFAULT_QUERY_NAME);
        } else {
            queryString = queryStrings.getStringByName(queryStringName);
        }
        if (queryString == null) {
            throw new IllegalArgumentException("Couldn't find queryString in " +
                    queryStrings + "; I was looking for: " +
                    (StringUtils.isBlank(queryStringName) ?
                    QueryStrings.DEFAULT_QUERY_NAME : queryStringName));
        }
        used.add(queryString);
        return used;
    }

    protected void setQueryString(String queryString) {
        this.queryString = queryString;
    }
    public String getQueryString() {
        return queryString;
    }

    public void setQueryStringName(String queryStringName) {
        this.queryStringName = queryStringName;
    }

    public String getQueryStringName() {
        return queryStringName;
    }
}
