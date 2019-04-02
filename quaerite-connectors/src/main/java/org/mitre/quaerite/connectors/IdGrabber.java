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

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.mitre.quaerite.core.queries.Query;

/**
 * This grabs ids from the index and puts them on a blocking queue
 * so that multiple consumers can process the docs in parallel.
 * <p>Make sure to call {@link #addPoison()} to add empty sets
 * to the queue to signal to the consumers to end.</p>
 */
public abstract class IdGrabber implements Callable<Integer> {
    static Logger LOG = Logger.getLogger(IdGrabber.class);

    protected final String idField;
    protected final ArrayBlockingQueue<Set<String>> ids;
    protected final int batchSize;
    protected final int copierThreads;
    protected final Collection<Query> filterQueries;

    public IdGrabber(String idField, ArrayBlockingQueue<Set<String>> ids, int batchSize, int
            copierThreads, Collection<Query> filterQueries) {
        this.idField = idField;
        this.ids = ids;
        this.batchSize = batchSize;
        this.copierThreads = copierThreads;
        this.filterQueries = filterQueries;
    }

    @Override
    public abstract Integer call() throws Exception;

    protected int addSet(ArrayBlockingQueue<Set<String>> ids, Set<String> set) throws InterruptedException {
        int sz = set.size();
        boolean added = ids.offer(set, 1, TimeUnit.SECONDS);
        LOG.debug("id grabber: " + added + " " + ids.size());
        while (!added) {
            added = ids.offer(set, 1, TimeUnit.SECONDS);
            LOG.debug("waiting to add");
        }
        return sz;
    }

    //this is the poison that signals to the copiers to stop copying
    protected void addPoison() {
        try {
            for (int i = 0; i < copierThreads; i++) {
                boolean added = false;

                added = ids.offer(Collections.EMPTY_SET,
                        1, TimeUnit.SECONDS);
                while (!added) {
                    added = ids.offer(Collections.EMPTY_SET,
                            1, TimeUnit.SECONDS);
                }
            }
        } catch (InterruptedException e) {
            //swallow
        }
    }


}
