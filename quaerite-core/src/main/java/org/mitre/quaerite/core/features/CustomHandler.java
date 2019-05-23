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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.mitre.quaerite.core.features.factories.CustomHandlerFactory;

/**
 * For Solr...users can specify a custom handler
 * that modifies the query server side. (see Hossman's Hidden Gems).
 * https://home.apache.org/~hossman/rev2016/
 *
 * These custom handlers might use a different key for the query, e.g. "qq".
 */
public class CustomHandler implements Feature<CustomHandler> {

    public static final CustomHandler DEFAULT_HANDLER =
            new CustomHandler("select", "q");

    private static final String NAME = "customHandler";

    private final String handler;
    private String customQueryKey;
    public CustomHandler(String handler, String customQueryKey) {
        this.handler = handler;
        this.customQueryKey = StringUtils.isBlank(customQueryKey) ?
                CustomHandlerFactory.DEFAULT_QUERY_KEY : customQueryKey;
    }

    public String getHandler() {
        return handler;
    }

    public String getCustomQueryKey() {
        //have to do this because deserialization
        //may set this to null. :(
        if (StringUtils.isBlank(customQueryKey)) {
            customQueryKey = CustomHandlerFactory.DEFAULT_QUERY_KEY;
        }
        return customQueryKey;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public CustomHandler deepCopy() {
        return new CustomHandler(getHandler(), getCustomQueryKey());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomHandler)) return false;
        return super.equals(o);
    }
}
