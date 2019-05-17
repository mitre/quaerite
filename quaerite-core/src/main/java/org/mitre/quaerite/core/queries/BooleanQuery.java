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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.math3.analysis.function.Sin;
import org.mitre.quaerite.core.QueryStrings;


public class BooleanQuery extends MultiStringQuery {

    Map<String, BooleanClause> clauses = new LinkedHashMap<>();


    public void addClause(BooleanClause clause) {
        if (clauses.containsKey(clause.getQueryStringName())) {
            throw new IllegalArgumentException("This BooleanQuery already contains "+
                    " a clause with this queryStringName:'"+clause.getQueryStringName()+"'");
        }
        clauses.put(clause.getQueryStringName(), clause);
    }

    @Override
    public String getName() {
        return "boolean";
    }

    @Override
    public BooleanQuery deepCopy() {
        BooleanQuery bq = new BooleanQuery();
        for (Map.Entry<String, BooleanClause> e : clauses.entrySet()) {
            BooleanClause c = e.getValue();
            if (!e.getKey().equals(c.getQueryStringName())) {
                throw new IllegalArgumentException("Something went horribly wrong: "+
                        "there's a mismatch between key name ('"+e.getKey()+
                        "') and the clause's queryStringName ('"+c.getQueryStringName()+"')");
            }
            bq.addClause(new BooleanClause(e.getKey(),
                    c.getOccur(), c.getQuery()));
        }
        return bq;
    }

    /**
     * This updates each clause with the appropriate query string.
     * <b>Warning:</b> We do not yet support setting query strings
     * in clauses that contain {@link MultiStringQuery}.
     *
     * @param queryStrings
     * @throws IllegalAccessException if the queryString set does
     * not equal the clauses' queryString set or if the clauses contain
     * anything but SingleStringQueries
     */
    @Override
    public void setQueryStrings(QueryStrings queryStrings) {
        if (clauses.size() != queryStrings.size()) {
            throw new IllegalArgumentException("QueryStrings' size ("+
                    queryStrings.size()+") must equal clauses size ("+
                    clauses.size());
        }
        Set<String> queryStringNames = queryStrings.names();
        //test for complete overlap
        for (String name : queryStringNames) {
            if (! clauses.containsKey(name)) {
                throw new IllegalArgumentException("QueryStrings has '"+
                        name+"' but I can't find a clause with that name");
            }
        }

        for (String name : clauses.keySet()) {
            if (!queryStringNames.contains(name)) {
                throw new IllegalArgumentException("I can't find '"+
                        name+"' in queryStrings!");
            }
            Query q = clauses.get(name).getQuery();
            if (! (q instanceof SingleStringQuery)) {
                throw new IllegalArgumentException("Sorry, currently only support setting query strings " +
                        "in clauses containing SingleStringQueries.  " +
                        "We do not yet support full recursion for setting query strings.");
            }
            ((SingleStringQuery)q).setQueryString(queryStrings.getStringByName(name));
        }

    }

    public List<BooleanClause> get(BooleanClause.OCCUR occur) {
        List<BooleanClause> ret = new ArrayList<>();
        for (BooleanClause clause : clauses.values()) {
            if (clause.getOccur().equals(occur)) {
                ret.add(clause.deepCopy());
            }
        }
        return ret;
    }

    public List<BooleanClause> getClauses() {
        return new ArrayList(clauses.values());
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
