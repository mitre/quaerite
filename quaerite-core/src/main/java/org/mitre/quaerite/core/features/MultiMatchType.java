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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MultiMatchType extends StringFeature {

    private static final Set<String> ALLOWABLE;

    static {
        Set<String> tmp = new HashSet<>();
        tmp.addAll(Arrays.asList(new String[]{
                "best_fields",
                "most_fields",
                "cross_fields", "phrase"//add query_string or make a new class?
        }));
        ALLOWABLE = Collections.unmodifiableSet(tmp);
    }

    private static final String NAME = "type";


    public MultiMatchType(String feature) {
        super(NAME, feature);
        if (!ALLOWABLE.contains(feature)) {
            throw new IllegalArgumentException("Must have type in: " + ALLOWABLE +
                    ". I see: " + feature);
        }
    }

    @Override
    public MultiMatchType deepCopy() {
        return new MultiMatchType(getFeature());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!MultiMatchType.class.equals(obj.getClass())) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
