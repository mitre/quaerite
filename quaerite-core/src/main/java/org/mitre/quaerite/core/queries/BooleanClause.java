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

public class BooleanClause {

    public enum OCCUR {
        SHOULD,
        MUST,
        MUST_NOT,
        FILTER
    }

    private final OCCUR occur;
    private final Query query;
    private final String queryStringName;

    /**
     * @param queryStringName
     * @param occur
     * @param query
     */
    public BooleanClause(String queryStringName, OCCUR occur, Query query) {
        this.queryStringName = queryStringName;
        this.occur = occur;
        this.query = query;
    }

    public OCCUR getOccur() {
        return occur;
    }

    public Query getQuery() {
        return query;
    }

    /**
     * This is the name of the queryString that should be injected
     * into this clause.  This can be null.
     * @return
     */
    public String getQueryStringName() {
        return queryStringName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BooleanClause)) return false;
        BooleanClause clause = (BooleanClause) o;
        return occur == clause.occur &&
                Objects.equals(query, clause.query) &&
                Objects.equals(queryStringName, clause.queryStringName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(occur, query, queryStringName);
    }

    @Override
    public String toString() {
        return "BooleanClause{" +
                "occur=" + occur +
                ", query=" + query +
                ", queryStringName='" + queryStringName + '\'' +
                '}';
    }

    public BooleanClause deepCopy() {
        return new BooleanClause(queryStringName, getOccur(),
                (SingleStringQuery)query.deepCopy());
    }


}
