package org.mitre.quaerite.core.scorers;

import org.mitre.quaerite.core.QueryInfo;
import org.mitre.quaerite.core.SearchResultSet;

/**
 * This is designed to capture stats about the search result set,
 * such as query time, raw number of results, etc.
 */
public interface SearchResultSetScorer {

    double score(QueryInfo queryInfo, SearchResultSet searchResultSet);
}
