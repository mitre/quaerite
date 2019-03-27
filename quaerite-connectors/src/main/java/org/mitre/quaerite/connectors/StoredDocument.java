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
package org.mitre.quaerite.connectors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

public class StoredDocument {
    Map<String, Object> fields = new LinkedHashMap<>();

    public void addNonBlankField(String field, List<String> values) {
        if (values == null) {
            return;
        }
        for (String v : values) {
            addNonBlankField(field, v);
        }
    }

    public void addNonBlankField(String field, String value) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        Object values;
        if (fields.containsKey(field)) {
            values = fields.get(field);
            if (values instanceof String) {
                List<String> tmp = new ArrayList<>();
                tmp.add((String)values);
                fields.put(field, tmp);
            } else {
                ((List)values).add(value);
            }
        } else {
            fields.put(field, value);
        }
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    @Override
    public String toString() {
        return "StoredDocument{" +
                "fields=" + fields +
                '}';
    }
}
