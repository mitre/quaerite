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

import org.mitre.quaerite.core.features.Feature;
import org.mitre.quaerite.core.features.QF;
import org.mitre.quaerite.core.features.TIE;

public abstract class MultiMatchQuery extends Query {
    protected String queryString;
    protected QF qf = new QF();
    protected TIE tie;

    public MultiMatchQuery() {

    }

    public MultiMatchQuery(String queryString) {
        this.queryString = queryString;
    }
    public QF getQF() {
        return qf;
    }

    public void setQF(QF qf) {
        this.qf = qf;
    }

    public void setTie(TIE tie) {
        this.tie = tie;
    }

    public Feature getTIE() {
        return tie;
    }

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }
}
