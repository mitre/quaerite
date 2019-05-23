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

    /**
     * @param occur
     * @param query
     */
    public BooleanClause(OCCUR occur, Query query) {
        this.occur = occur;
        this.query = query;
    }

    public OCCUR getOccur() {
        return occur;
    }

    public Query getQuery() {
        return query;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BooleanClause)) return false;
        BooleanClause clause = (BooleanClause) o;
        return occur == clause.occur &&
                Objects.equals(query, clause.query);
    }

    @Override
    public int hashCode() {
        return Objects.hash(occur, query);
    }

    @Override
    public String toString() {
        return "BooleanClause{" +
                "occur=" + occur +
                ", query=" + query +
                '}';
    }

    public BooleanClause deepCopy() {
        return new BooleanClause(getOccur(),
                (SingleStringQuery)query.deepCopy());
    }


}
