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
package org.mitre.quaerite.core;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

public class ExperimentConfig {

    public static final int DEFAULT_NUM_THREADS = 6;

    private int numThreads = DEFAULT_NUM_THREADS;
    private String idField = StringUtils.EMPTY;

    public int getNumThreads() {
        return numThreads;
    }

    //returns id field if customized in experiment config
    //or empty string if nothing was specified
    public String getIdField() {
        return idField;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExperimentConfig)) return false;
        ExperimentConfig that = (ExperimentConfig) o;
        return numThreads == that.numThreads &&
                Objects.equals(idField, that.idField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numThreads, idField);
    }
}
