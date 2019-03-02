package org.mitre.quaerite;

import java.util.Map;

public class FacetResult {
        private final long totalDocs;
        private final Map<String, Long> facetCounts;

        public FacetResult(long totalDocs, Map<String, Long> facetCounts) {
            this.totalDocs = totalDocs;
            this.facetCounts = facetCounts;
        }

        public long getTotalDocs() {
            return totalDocs;
        }

        public Map<String, Long> getFacetCounts() {
            return facetCounts;
        }

    @Override
    public String toString() {
        return "FacetResult{" +
                "totalDocs=" + totalDocs +
                ", facetCounts=" + facetCounts +
                '}';
    }
}
