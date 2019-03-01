package org.mitre.quaerite.connectors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryRequest {

    private Map<String, List<String>> parameters = new HashMap<>();
    private List<String> facetFields = new ArrayList<>();
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
        return parameters;
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

    public void addFacetField(String field) {
        facetFields.add(field);
    }

    public void setFacetLimit(int limit) {
        this.facetLimit = limit;
    }

    public List<String> getFacetFields() {
        return facetFields;
    }

    public int getFacetLimit() {
        return facetLimit;
    }
}
