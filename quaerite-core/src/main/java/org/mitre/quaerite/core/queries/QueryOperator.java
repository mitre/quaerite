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

import java.util.Locale;

public class QueryOperator {


    public enum OPERATOR {
        AND,
        OR
    }

    private final Float mmFloat;
    private final Integer mmInt;
    private final OPERATOR operator;

    public QueryOperator(OPERATOR operator) {
        this(operator, null, null);
    }

    public QueryOperator(OPERATOR operator, int mm) {
        this(operator, null, mm);
    }

    public QueryOperator(OPERATOR operator, float mm) {
        this(operator, mm, null);
    }

    public String getOperatorString() {
        return operator.toString().toLowerCase(Locale.US);
    }

    private QueryOperator(OPERATOR operator, Float mmFloat, Integer mmInt) {
        this.operator = operator;
        this.mmFloat = mmFloat;
        this.mmInt = mmInt;
    }

}
