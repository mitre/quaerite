package org.mitre.quaerite;

import java.util.List;

public class ResultSet {

    private final long totalHits;
    private final long queryTime;
    private final long elapsedTime;
    private final List<String> ids;

    public ResultSet(long totalHits, long queryTime, long elapsedTime, List<String> ids) {
        this.totalHits = totalHits;
        this.queryTime = queryTime;
        this.elapsedTime = elapsedTime;
        this.ids = ids;
    }

    public long getTotalHits() {
        return totalHits;
    }

    public long getElapsedTime() {
        return elapsedTime;
    }

    public long getQueryTime() {
        return queryTime;
    }
    public int size() {
        return ids.size();
    }

    public String get(int i) {
        return ids.get(i);
    }
}
