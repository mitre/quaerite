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

import java.util.HashSet;
import java.util.Set;

public class Fuzziness extends StringFeature {

    public static final String DEFAULT_FUZZINESS = "AUTO";
    private static final String NAME = "fuzziness";
    private static final Set<String> VALID = new HashSet<>();

    static {
        VALID.add("0");
        VALID.add("1");
        VALID.add("2");
        VALID.add("AUTO");
    }
    public Fuzziness() {
        super(NAME, DEFAULT_FUZZINESS);
    }
    public Fuzziness(String value) {
        super(NAME, checkValid(value));
    }

    private static String checkValid(String value) {
        if (VALID.contains(value)) {
            return value;
        }
        throw new IllegalArgumentException("Fuzziness must be in " +
                VALID);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Fuzziness)) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public Fuzziness deepCopy() {
        return new Fuzziness(getFeature());
    }
}
