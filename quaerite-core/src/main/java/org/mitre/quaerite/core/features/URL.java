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

import org.mitre.quaerite.core.util.ConnectionConfig;

public class URL implements Feature {

    private static final String NAME = "url";

    private ConnectionConfig connectionConfig;
    private String url;
    public URL(ConnectionConfig connectionConfig, String url) {
        this.connectionConfig = connectionConfig;
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public ConnectionConfig getConnectionConfig() {
        return connectionConfig;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public URL deepCopy() {
        return new URL(connectionConfig, url);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof URL)) return false;
        URL url1 = (URL) o;
        return Objects.equals(connectionConfig, url1.connectionConfig) &&
                Objects.equals(url, url1.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionConfig, url);
    }
}
