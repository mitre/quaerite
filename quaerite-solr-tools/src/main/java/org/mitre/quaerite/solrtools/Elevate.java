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
 *
 *
 */
package org.mitre.quaerite.solrtools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Elevate {
    public static final Comparator<? super Elevate> SORT_BY_SIZE_DECREASING =
            new SortBySizeDecreasing();
    final String query;
    final List<String> ids;

    public Elevate(String query, List<String> ids) {
        this.query = query;
        this.ids = new ArrayList<>();
        this.ids.addAll(ids);
    }

    public String getQuery() {
        return query;
    }

    public List<String> getIds() {
        return ids;
    }

    @Override
    public String toString() {
        return "Elevate{" +
                "query='" + query + '\'' +
                ", ids=" + ids +
                '}';
    }

    private static class SortBySizeDecreasing implements Comparator<Elevate> {
        @Override
        public int compare(Elevate o1, Elevate o2) {
            return Integer.compare(o2.getIds().size(), o1.getIds().size());
        }
    }
}
