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

import org.mitre.quaerite.core.features.Boost;
import org.mitre.quaerite.core.features.Fuzziness;
import org.mitre.quaerite.core.features.MultiMatchType;
import org.mitre.quaerite.core.features.QF;
import org.mitre.quaerite.core.features.TIE;

public class MultiMatchQuery extends MultiFieldQuery {

    private static final MultiMatchType DEFAULT_TYPE = new MultiMatchType("best_fields");

    private MultiMatchType type = DEFAULT_TYPE;
    private Boost boost = new Boost();
    private Fuzziness fuzziness = new Fuzziness();

    public MultiMatchQuery() {
        super(null);
    }

    public MultiMatchQuery(String queryString) {
        super(queryString);
    }


    public MultiMatchType getMultiMatchType() {
        return type;
    }

    public void setMultiMatchType(MultiMatchType type) {
        this.type = type;
    }

    public Boost getBoost() {
        return boost;
    }

    public void setBoost(Boost boost) {
        this.boost = boost;
    }

    public Fuzziness getFuzziness() {
        return fuzziness;
    }

    public void setFuzziness(Fuzziness fuzziness) {
        this.fuzziness = fuzziness;
    }

    @Override
    public String getName() {
        return "multi_match";
    }

    @Override
    public MultiMatchQuery deepCopy() {
        MultiMatchQuery cp = new MultiMatchQuery();
        cp.type = this.type;
        cp.qf = (qf != null) ? (QF)qf.deepCopy() : null;
        cp.tie = (tie != null) ? (TIE)tie.deepCopy() : null;
        cp.boost = this.boost;
        cp.fuzziness = this.fuzziness;
        cp.setQueryString(getQueryString());
        return cp;
    }

    @Override
    public String toString() {
        return "MultiMatchQuery{" +
                "type=" + type +
                ", boost=" + boost +
                ", fuzziness=" + fuzziness +
                ", queryString='" + getQueryString() + '\'' +
                ", qf=" + qf +
                ", tie=" + tie +
                ", qOp=" + qOp +
                '}';
    }
}
