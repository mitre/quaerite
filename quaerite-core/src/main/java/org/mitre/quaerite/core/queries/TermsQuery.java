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
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

public class TermsQuery extends SingleStringQuery {

    private final String field;
    private volatile List<String> terms;
    public TermsQuery(String field, List<String> terms) {
        super(StringUtils.joinWith(",", terms));
        this.field = field;
        //defensive copy
        this.terms = new ArrayList<>(terms);
    }
    
    public List<String> getTerms() {
        return terms;
    }

    public String getField() {
        return field;
    }

    /**
     * This currently splits the query string on ','
     * @param queryString
     */
    @Override
    public void setQueryString(String queryString) {
        super.setQueryString(queryString);
        terms.clear();
        terms.addAll(Arrays.asList(queryString.split(",")));
    }

    @Override
    public String getName() {
        return "terms";
    }

    @Override
    public Object deepCopy() {
        return new TermsQuery(field, terms);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TermsQuery)) return false;
        TermsQuery that = (TermsQuery) o;
        return Objects.equals(field, that.field) &&
                Objects.equals(terms, that.terms);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, terms);
    }
}
