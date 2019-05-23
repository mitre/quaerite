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


import java.util.Objects;

import org.mitre.quaerite.core.features.QF;
import org.mitre.quaerite.core.features.QueryOperator;
import org.mitre.quaerite.core.features.TIE;

public abstract class MultiFieldQuery extends SingleStringQuery {
    protected QF qf = new QF();
    protected TIE tie = new TIE(0.0f);
    protected QueryOperator qOp = new QueryOperator(QueryOperator.OPERATOR.UNSPECIFIED);

    public MultiFieldQuery() {
        super(null);
    }

    public MultiFieldQuery(String queryString) {
        super(queryString);
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

    public TIE getTie() {
        return tie;
    }

    public QueryOperator getQueryOperator() {
        return qOp;
    }

    public void setQueryOperator(QueryOperator qOp) {
        this.qOp = qOp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MultiFieldQuery)) return false;
        if (! super.equals(o));
        MultiFieldQuery that = (MultiFieldQuery) o;
        return  Objects.equals(qf, that.qf) &&
                Objects.equals(tie, that.tie) &&
                Objects.equals(qOp, that.qOp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getQueryString(), qf, tie, qOp);
    }
}
