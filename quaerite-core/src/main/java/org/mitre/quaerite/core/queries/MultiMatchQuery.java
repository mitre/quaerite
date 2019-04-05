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

import org.mitre.quaerite.core.features.QF;
import org.mitre.quaerite.core.features.TIE;

public class MultiMatchQuery extends MultiFieldQuery {

    public enum TYPE  {
        best_fields,
        cross_fields,
        most_fields,
        phrase
    };

    private static final TYPE DEFAULT_TYPE = TYPE.best_fields;

    private TYPE type = DEFAULT_TYPE;
    private float boost = 1.0f;
    private float fuzziness = 0.0f;

    public MultiMatchQuery() {
        super(null);
    }

    public MultiMatchQuery(String queryString) {
        super(queryString);
    }


    public TYPE getType() {
        return type;
    }

    public void setType(TYPE type) {
        this.type = type;
    }

    public float getBoost() {
        return boost;
    }

    public void setBoost(float boost) {
        this.boost = boost;
    }

    public float getFuzziness() {
        return fuzziness;
    }

    public void setFuzziness(float fuzziness) {
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
        cp.queryString = queryString;
        return cp;
    }
}
