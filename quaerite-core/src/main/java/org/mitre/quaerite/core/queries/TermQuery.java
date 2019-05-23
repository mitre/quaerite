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

public class TermQuery extends SingleStringQuery {

    final String field;

    public TermQuery(String field, String term) {
        super(term);
        this.field = field;
    }

    @Override
    public String getName() {
        return "term";
    }

    @Override
    public TermQuery deepCopy() {
        TermQuery tq = new TermQuery(field, getQueryString());
        tq.setQueryStringName(getQueryStringName());
        return tq;
    }

    public String getField() {
        return field;
    }

    public String getTerm() {
        return  getQueryString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TermQuery)) return false;
        TermQuery termQuery = (TermQuery) o;
        return Objects.equals(field, termQuery.field) &&
                Objects.equals(getQueryString(), termQuery.getTerm());
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, getTerm());
    }
}
