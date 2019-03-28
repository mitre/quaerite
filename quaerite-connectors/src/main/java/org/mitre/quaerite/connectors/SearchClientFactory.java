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

import org.mitre.quaerite.connectors.es.ESClient;
import org.mitre.quaerite.connectors.solr.SolrClient;

public class SearchClientFactory {

    public static SearchClient getClient(String url) {
        //TODO: remove collection/core name and actually
        //run something like http://yoursolrhost:8983/solr/admin/info/system?wt=json
        if (url.contains("solr")) {
            return new SolrClient(url);
        } else {
            return new ESClient(url);
        }
    }
}
