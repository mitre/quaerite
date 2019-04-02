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
package org.mitre.quaerite.connectors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QueryRequest {

    private Map<String, List<String>> parameters = new HashMap<>();
    private String facetField = null;
    private List<String> fields = new ArrayList<>();
    private int facetLimit = 10;
    private final String query;
    private final String customHandler;
    private final String idField;
    private int numResults = 10;

    public QueryRequest(String query) {
        this(query, null, null);
    }

    public QueryRequest(String query, String customHandler, String idField) {
        this.query = query;
        this.customHandler = customHandler;
        this.idField = idField;
    }

    public void setNumResults(int numResults) {
        this.numResults = numResults;
    }

    public void addParameter(String param, String value) {
        List<String> values = parameters.get(param);
        if (values == null) {
            values = new ArrayList<>();
        }
        values.add(value);
        parameters.put(param, values);
    }

    public Map<String, List<String>> getParameters() {
        //defensively copy
        Map<String, List<String>> ret = new HashMap<>(parameters);
        return ret;
    }

    public String getQuery() {
        return query;
    }

    public String getIdField() {
        return idField;
    }

    public int getNumResults() {
        return numResults;
    }

    /**
     *
     * @return the custom handler or null if it doesn't exist
     */
    public String getCustomHandler() {
        return customHandler;
    }

    //TODO: make this multivalued at somepoint
    public void setFacetField(String field) {
        facetField = field;
    }

    public void setFacetLimit(int limit) {
        this.facetLimit = limit;
    }

    public String getFacetField() {
        return facetField;
    }

    public int getFacetLimit() {
        return facetLimit;
    }

    @Override
    public String toString() {
        return "QueryRequest{" +
                "parameters=" + parameters +
                ", facetField=" + facetField +
                ", facetLimit=" + facetLimit +
                ", query='" + query + '\'' +
                ", customHandler='" + customHandler + '\'' +
                ", idField='" + idField + '\'' +
                ", numResults=" + numResults +
                '}';
    }

    public void addField(String field) {
        fields.add(field);
    }

    public List<String> getFields() {
        return fields;
    }

    public void addFields(Set<String> fields) {
        this.fields.addAll(fields);
    }
}
