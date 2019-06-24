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

import java.util.Objects;

import org.mitre.quaerite.core.features.QueryOperator;

public class LuceneQuery extends SingleStringQuery {

    public final static QueryOperator.OPERATOR
            DEFAULT_QUERY_OPERATOR = QueryOperator.OPERATOR.AND;

    private String defaultField;
    private QueryOperator.OPERATOR qop;

    public LuceneQuery(String defaultField, String queryString) {
        this(defaultField, queryString, DEFAULT_QUERY_OPERATOR);
    }
    public LuceneQuery(String defaultField, String queryString, QueryOperator.OPERATOR qop) {
        super(queryString);
        this.defaultField = defaultField;
        this.qop = qop;

    }

    public String getDefaultField() {
        return defaultField;
    }

    public QueryOperator.OPERATOR getQueryOperator() {
        return qop;
    }

    @Override
    public String getName() {
        return "lucene";
    }

    @Override
    public LuceneQuery deepCopy() {
        LuceneQuery lq = new LuceneQuery(defaultField, getQueryString(), qop);
        lq.setQueryStringName(getQueryStringName());
        return lq;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LuceneQuery that = (LuceneQuery) o;
        return Objects.equals(defaultField, that.defaultField) &&
                Objects.equals(getQueryString(), that.getQueryString()) &&
                qop == that.qop;
    }

    @Override
    public int hashCode() {
        return Objects.hash(defaultField, getQueryString(), qop);
    }
}
