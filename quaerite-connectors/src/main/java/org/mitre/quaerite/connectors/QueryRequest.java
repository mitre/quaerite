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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;


import org.mitre.quaerite.core.features.CustomHandler;
import org.mitre.quaerite.core.queries.Query;

public class QueryRequest {

    enum SORT_ORDER {
        ASC,
        DESC
    }
    private String facetField = null;
    private List<String> fieldsToRetrieve = new ArrayList<>();
    private int facetLimit = 10;
    private final Query query;
    private final List<Query> filterQueries = new ArrayList<>();
    private final CustomHandler customHandler;
    private final String idField;
    private int start = 0;
    private int numResults = 10;
    private String sortField;
    private SORT_ORDER sortOrder;

    public QueryRequest(Query query) {
        this(query, null, null);
    }

    public QueryRequest(Query query, CustomHandler customHandler, String idField) {
        this.query = query;
        this.customHandler = customHandler;
        this.idField = idField;
    }

    public void setNumResults(int numResults) {
        this.numResults = numResults;
    }



    public Query getQuery() {
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
    public CustomHandler getCustomHandler() {
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
                ", facetField='" + facetField + '\'' +
                ", fieldsToRetrieve=" + fieldsToRetrieve +
                ", facetLimit=" + facetLimit +
                ", query=" + query +
                ", filterQueries=" + filterQueries +
                ", customHandler='" + customHandler + '\'' +
                ", idField='" + idField + '\'' +
                ", numResults=" + numResults +
                '}';
    }

    public List<String> getFieldsToRetrieve() {
        return fieldsToRetrieve;
    }

    public void addFieldsToRetrieve(String ... fields) {
        addFieldsToRetrieve(Arrays.asList(fields));
    }

    public void addFieldsToRetrieve(Collection<String> fieldsToRetrieve) {
        this.fieldsToRetrieve.addAll(fieldsToRetrieve);
    }

    public void addFilterQueries(Query ... filterQueries) {
        addFilterQueries(Arrays.asList(filterQueries));
    }

    public void addFilterQueries(Collection<Query> filterQueries) {
        this.filterQueries.addAll(filterQueries);
    }


    public String getSortField() {
        return sortField;
    }

    public SORT_ORDER getSortOrder() {
        return sortOrder;
    }

    /**
     *
     * @param start first row to return
     */
    public void setStart(int start) {
        this.start = start;
    }

    public int getStart() {
        return start;
    }

    public void setSort(String sortField, SORT_ORDER sortOrder) {
        this.sortField = sortField;
        this.sortOrder = sortOrder;
    }

    public List<Query> getFilterQueries() {
        return filterQueries;
    }

}
