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
import java.util.List;


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
            bq.addClause(new BooleanClause(c.getOccur(), c.getQuery()));
        }
        return bq;
    }

    @Override
    public void setQueryString(String queryString) {
        //TODO -- need to figure this one out.
    }

    public List<Query> get(BooleanClause.OCCUR occur) {
        List<Query> ret = new ArrayList<>();
        for (BooleanClause clause : clauses) {
            if (clause.getOccur().equals(occur)) {
                ret.add(clause.getQuery());
            }
        }
        return ret;
    }

    public List<BooleanClause> getClauses() {
        return clauses;
    }

    @Override
    public String toString() {
        return "BooleanQuery{" +
                "clauses=" + clauses +
                '}';
    }
}
