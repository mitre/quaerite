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

import org.mitre.quaerite.core.QueryStrings;
import org.mitre.quaerite.core.features.NegativeBoost;

/**
 * This represents ES's BoostingQuery.  There is no
 * equivalent in Solr.  Instead, set the boost directly
 * in the queries within the clauses in a {@link BooleanQuery}
 */
public class BoostingQuery extends MultiStringQuery {

    public static final String POSITIVE_QUERY_STRING_NAME = "positive";
    public static final String NEGATIVE_QUERY_STRING_NAME = "negative";

    final SingleStringQuery positiveQuery;
    final SingleStringQuery negativeQuery;
    final NegativeBoost negativeBoost;

    /**
     * For now, this only works with SingleStringQueries...
     * In the future, this may be completely recursive and allow for
     * just {@link Query}
     *
     * @param positiveQuery
     * @param negativeQuery
     * @param negativeBoost
     */
    public BoostingQuery(SingleStringQuery positiveQuery,
                         SingleStringQuery negativeQuery, NegativeBoost negativeBoost) {
        this.positiveQuery = positiveQuery;
        this.negativeQuery = negativeQuery;
        this.negativeBoost = negativeBoost;
    }

    @Override
    public String getName() {
        return "boosting";
    }

    public SingleStringQuery getPositiveQuery() {
        return positiveQuery;
    }

    public SingleStringQuery getNegativeQuery() {
        return negativeQuery;
    }

    public NegativeBoost getNegativeBoost() {
        return negativeBoost;
    }

    @Override
    public BoostingQuery deepCopy() {
        BoostingQuery bq =
                new BoostingQuery((SingleStringQuery)positiveQuery.deepCopy(),
                        (SingleStringQuery)negativeQuery.deepCopy(), negativeBoost);
        return bq;
    }

    /**
     * This updates each clause with the appropriate query string.
     * @param queryStrings
     * @throws IllegalAccessException if there the queryString set does
     * not equal the clauses' queryString set
     */
    @Override
    public void setQueryStrings(QueryStrings queryStrings) {
        if (queryStrings.getStringByName(POSITIVE_QUERY_STRING_NAME) == null) {
            throw new IllegalArgumentException("queryStrings must contain: "+
                    POSITIVE_QUERY_STRING_NAME);
        }
        if (queryStrings.getStringByName(NEGATIVE_QUERY_STRING_NAME) == null) {
            throw new IllegalArgumentException("queryStrings must contain: "+
                    NEGATIVE_QUERY_STRING_NAME);

        }
        this.positiveQuery.setQueryString(queryStrings.getStringByName(POSITIVE_QUERY_STRING_NAME));
        this.negativeQuery.setQueryString(queryStrings.getStringByName(NEGATIVE_QUERY_STRING_NAME));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BoostingQuery)) return false;
        BoostingQuery that = (BoostingQuery) o;
        return Objects.equals(that.negativeBoost, negativeBoost) &&
                Objects.equals(positiveQuery, that.positiveQuery) &&
                Objects.equals(negativeQuery, that.negativeQuery);
    }

    @Override
    public int hashCode() {
        return Objects.hash(positiveQuery, negativeQuery, negativeBoost);
    }

    @Override
    public String toString() {
        return "BoostingQuery{" +
                "positiveQuery=" + positiveQuery +
                ", negativeQuery=" + negativeQuery +
                ", negativeBoost=" + negativeBoost +
                '}';
    }
}
