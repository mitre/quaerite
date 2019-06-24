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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.mitre.quaerite.core.QueryStrings;


public class BooleanQuery extends Query {

    List<BooleanClause> clauses = new ArrayList<>();


    public void addClause(BooleanClause clause) {
        clauses.add(clause);
    }

    @Override
    public String getName() {
        return "boolean";
    }

    @Override
    public BooleanQuery deepCopy() {
        BooleanQuery bq = new BooleanQuery();
        for (BooleanClause c : clauses) {
            bq.addClause(c.deepCopy());
        }
        return bq;
    }

    /**
     * This updates each clause with the appropriate query string.
     *
     * @param queryStrings
     * @throws IllegalAccessException if the queryString set does
     * not equal the clauses' queryString set or if the clauses contain
     * anything but SingleStringQueries
     */
    @Override
    public Set<String> setQueryStrings(QueryStrings queryStrings) {
        Set<String> used = new HashSet<>();
        for (BooleanClause clause : clauses) {
            used.addAll(clause.getQuery().setQueryStrings(queryStrings));
        }
        return used;
    }

    public List<BooleanClause> get(BooleanClause.OCCUR occur) {
        List<BooleanClause> ret = new ArrayList<>();
        for (BooleanClause clause : clauses) {
            if (clause.getOccur().equals(occur)) {
                ret.add(clause.deepCopy());
            }
        }
        return ret;
    }

    public List<BooleanClause> getClauses() {
        //defensive copy?
        return clauses;
    }

    @Override
    public String toString() {
        return "BooleanQuery{" +
                "clauses=" + clauses +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BooleanQuery)) return false;
        BooleanQuery that = (BooleanQuery) o;
        return Objects.equals(clauses, that.clauses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clauses);
    }
}
