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

public class TermsQuery extends Query {

    private final String field;
    private final List<String> terms;
    public TermsQuery(String field, List<String> terms) {
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
}
