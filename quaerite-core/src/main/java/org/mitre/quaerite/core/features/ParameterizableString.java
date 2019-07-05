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

import java.util.Objects;

public class ParameterizableString<T extends ParameterizableString>
        extends AbstractFeature<T> {


    //If a factory created this, this must be set.
    //for running experiments, this is not needed.
    private final String factoryId;
    private final String string;
    private float[] values;

    public ParameterizableString(String name, String string) {
        this(name, "", string, null);
    }

    public ParameterizableString(String name, String factoryId, String string) {
        this(name, factoryId, string, null);
    }

    public ParameterizableString(String name, String factoryId, String string, float[] values) {
        super(name);
        this.factoryId = factoryId;
        this.string = string;
        this.values = values;

    }

    @Override
    public String toString() {
        return string;
    }

    /**
     *
     * @return whether or not the underlying values have been stored with this string
     */
    public boolean hasValues() {
        return values != null;
    }

    /**
     *
     * @return values array if stored, null otherwise
     */
    public float[] getValues() {
        if (! hasValues()) {
            return null;
        }
        float[] copy = new float[values.length];
        System.arraycopy(values, 0, copy, 0, values.length);
        return copy;
    }

    @Override
    public T deepCopy() {
        float[] copyVals = null;
        if (hasValues()) {
            copyVals = new float[values.length];
            System.arraycopy(values, 0, copyVals, 0, values.length);
        }
        return (T)new ParameterizableString<T>(getName(), getFactoryId(), string, copyVals);
    }

    public String getFactoryId() {
        return factoryId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParameterizableString)) return false;
        ParameterizableString<?> that = (ParameterizableString<?>) o;
        return factoryId.equals(that.factoryId) &&
                string.equals(that.string);

    }

    @Override
    public int hashCode() {
        return Objects.hash(factoryId, string);
    }
}
