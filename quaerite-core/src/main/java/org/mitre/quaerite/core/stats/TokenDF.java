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
package org.mitre.quaerite.core.stats;

import java.util.Objects;

public class TokenDF {

    private final String token;
    private final long df;

    public TokenDF(String token, long df) {
        this.token = token;
        this.df = df;
    }

    public String getToken() {
        return token;
    }

    public long getDf() {
        return df;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenDF tokenDF = (TokenDF) o;
        return df == tokenDF.df &&
                Objects.equals(token, tokenDF.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token, df);
    }

    @Override
    public String toString() {
        return "TokenDF{" +
                "token='" + token + '\'' +
                ", df=" + df +
                '}';
    }


}
