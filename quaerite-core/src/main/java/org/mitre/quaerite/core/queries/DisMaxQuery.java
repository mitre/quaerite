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


import org.mitre.quaerite.core.features.BF;
import org.mitre.quaerite.core.features.BQ;
import org.mitre.quaerite.core.features.Feature;
import org.mitre.quaerite.core.features.PF;
import org.mitre.quaerite.core.features.PS;

public class DisMaxQuery extends MultiFieldQuery {


    protected BF bf;
    protected BQ bq;
    protected PF pf;
    protected PS ps;


    public DisMaxQuery() {
       super();
    }

    public DisMaxQuery(String queryString) {
        super(queryString);
    }

    @Override
    public String getName() {
        return "dismax";
    }

    public BF getBF() {
        return bf;
    }

    public void setBF(BF bf) {
        this.bf = bf;
    }

    public BQ getBQ() {
        return bq;
    }

    public void setBQ(BQ bq) {
        this.bq = bq;
    }

    public PF getPF() {
        return pf;
    }

    public void setPF(PF pf) {
        this.pf = pf;
    }

    public PS getPS() {
        return ps;
    }

    public void setPS(PS ps) {
        this.ps = ps;
    }

    @Override
    public DisMaxQuery deepCopy() {
        DisMaxQuery cp = new DisMaxQuery();
        cp.bq = (bq == null) ? null : bq.deepCopy();
        cp.bf = (bf == null) ? null : bf.deepCopy();
        cp.pf = (pf == null) ? null : pf.deepCopy();
        cp.ps = (ps == null) ? null : ps.deepCopy();
        cp.qf = (qf == null) ? null : qf.deepCopy();
        cp.tie = (tie == null) ? null : tie.deepCopy();
        cp.setQueryString(getQueryString());
        cp.setQueryStringName(getQueryStringName());
        cp.setQueryOperator(getQueryOperator());
        return cp;
    }
}
