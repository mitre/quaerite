package org.mitre.quaerite.core.scorers;

import org.mitre.quaerite.core.QueryInfo;
import org.mitre.quaerite.core.SearchResultSet;


/**
 * This is intended to compare two search result sets,
 * without reference to judgments, e.g. overlap, jaccard, etc.
 *
 * This has not yet been implemented.
 */
public interface SearchResultSetComparer {

    double compare(QueryInfo queryInfo,
                          SearchResultSet setA, SearchResultSet setB);
}
