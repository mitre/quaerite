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
package org.mitre.quaerite.core.features;

import java.util.Locale;
import java.util.Objects;

/**
 * this currently only supports the basics
 * and, or and mm, where mm is a positive/negative integer or float.
 * This does not yet support the more interesting syntax
 * options:3&lt;90% or multiple combinations
 */
public class QueryOperator implements Feature {

    public enum OPERATOR {
        AND,
        OR,
        UNSPECIFIED
    }

    //is there a minshouldmatch, and which type
    public enum MM {
        FLOAT,
        INTEGER,
        NONE
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

    public MM getMM() {
        if (mmFloat == null && mmInt == null) {
            return MM.NONE;
        } else if (mmFloat != null) {
            return MM.FLOAT;
        } else if (mmInt != null) {
            return MM.INTEGER;
        }
        throw new IllegalArgumentException("must be one of the above");
    }

    public Integer getInt() {
        return mmInt;
    }

    public Float getMmFloat() {
        return mmFloat;
    }

    public QueryOperator.OPERATOR getOperator() {
        return operator;
    }


    private QueryOperator(OPERATOR operator, Float mmFloat, Integer mmInt) {
        this.operator = operator;
        this.mmFloat = mmFloat;
        this.mmInt = mmInt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueryOperator)) return false;
        QueryOperator that = (QueryOperator) o;
        return Objects.equals(mmFloat, that.mmFloat) &&
                Objects.equals(mmInt, that.mmInt) &&
                operator == that.operator;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mmFloat, mmInt, operator);
    }

    @Override
    public String toString() {
        return "QueryOperator{" +
                "mmFloat=" + mmFloat +
                ", mmInt=" + mmInt +
                ", operator=" + operator +
                '}';
    }

    @Override
    public String getName() {
        return "q.op";
    }

    @Override
    public Object deepCopy() {
        return new QueryOperator(operator, mmFloat, mmInt);
    }
}
