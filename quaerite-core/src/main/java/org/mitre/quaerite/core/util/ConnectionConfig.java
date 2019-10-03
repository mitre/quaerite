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
package org.mitre.quaerite.core.util;

import java.util.Objects;

public class ConnectionConfig {

    public static final int UNDEFINED_PROXY_PORT = -1;
    public static final ConnectionConfig DEFAULT_CONNECTION_CONFIG =
            new ConnectionConfig(null, null,
                    null, UNDEFINED_PROXY_PORT);

    private final String user;
    private final String password;
    private final String proxyHost;
    private final int proxyPort;

    public ConnectionConfig(String user, String password, String proxyHost, int proxyPort) {
        this.user = user;
        this.password = password;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
    }

    public ConnectionConfig(String user, String password) {
        this(user, password, null, UNDEFINED_PROXY_PORT);
    }

    public ConnectionConfig(String proxyHost, int proxyPort) {
        this(null, null, proxyHost, proxyPort);
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConnectionConfig)) return false;
        ConnectionConfig that = (ConnectionConfig) o;
        return proxyPort == that.proxyPort &&
                Objects.equals(user, that.user) &&
                Objects.equals(password, that.password) &&
                Objects.equals(proxyHost, that.proxyHost);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, password, proxyHost, proxyPort);
    }
}
