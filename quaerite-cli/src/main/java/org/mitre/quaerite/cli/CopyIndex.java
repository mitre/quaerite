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
package org.mitre.quaerite.cli;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mitre.quaerite.connectors.SearchClient;
import org.mitre.quaerite.connectors.SearchClientException;
import org.mitre.quaerite.connectors.SearchClientFactory;
import org.mitre.quaerite.connectors.StoredDocument;

public class CopyIndex extends AbstractCLI {

    static Logger LOG = Logger.getLogger(CopyIndex.class);

    private static final int NUM_THREADS = 10;
    private static final int BATCH_SIZE = 100;

    static Options OPTIONS = new Options();

    static {
        OPTIONS.addOption(
                Option.builder("src")
                        .hasArg().required().desc("source searcher url").build()
        );

        OPTIONS.addOption(
                Option.builder("targ")
                        .hasArg()
                        .required()
                        .desc("target searcher url").build()
        );
        OPTIONS.addOption(
                Option.builder("whiteListFields")
                        .hasArg()
                        .required(false)
                        .desc("copy only these fields; comma-delimited list").build()
        );
        OPTIONS.addOption(
                Option.builder("fq")
                        .longOpt("filterQueries")
                        .hasArg()
                        .required(false)
                        .desc("filter queries; comma-delimited list").build()
        );
        OPTIONS.addOption(
                Option.builder("blackListFields")
                        .hasArg()
                        .required(false)
                        .desc("do not copy these fields; comma-delimited list").build()
        );
        OPTIONS.addOption(
                Option.builder("clean")
                        .hasArg(false)
                        .required(false)
                        .desc("clean (DELETE) the target index before copying").build()
        );
        OPTIONS.addOption(
                Option.builder("b")
                        .longOpt("batchSize")
                        .hasArg(true)
                        .required(false)
                        .desc("batch size; default: "+BATCH_SIZE).build()
        );
        OPTIONS.addOption(
                Option.builder("n")
                        .longOpt("numThreads")
                        .hasArg(true)
                        .required(false)
                        .desc("num copier threads; default: "+NUM_THREADS).build()
        );
    }

    private int numThreads = NUM_THREADS;
    private int batchSize = BATCH_SIZE;

    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(
                    "java -jar org.mitre.quaerite.cli.DumpResults",
                    OPTIONS);
            return;
        }
        SearchClient srcClient = SearchClientFactory.getClient(commandLine.getOptionValue("src"));
        SearchClient targClient = SearchClientFactory.getClient(commandLine.getOptionValue("targ"));
        Set<String> whiteListFields = splitComma(
                getString(commandLine, "whiteListFields", StringUtils.EMPTY));
        Set<String> blackListFields = splitComma(
                getString(commandLine, "blackListFields", StringUtils.EMPTY));
        Set<String> filterQueries = splitComma(
                getString(commandLine, "fq", StringUtils.EMPTY));

        blackListFields = updateBlackList(srcClient, blackListFields);

        LOG.debug("whiteListFields:" + whiteListFields);
        LOG.debug("blackListFields:" + blackListFields);
        LOG.debug("filterQueries:"+filterQueries);
        CopyIndex copyIndex = new CopyIndex();
        if (commandLine.hasOption("clean")) {
            targClient.deleteAll();
        }
        copyIndex.setNumThreads(getInt(commandLine, "numThreads", NUM_THREADS));
        copyIndex.setBatchSize(getInt(commandLine, "b", BATCH_SIZE));

        copyIndex.execute(srcClient, targClient, filterQueries, whiteListFields, blackListFields);
    }

    private static Set<String> updateBlackList(SearchClient srcClient, Set<String> blackListFields) throws IOException, SearchClientException {
        Set<String> tmp = new HashSet<>();
        tmp.addAll(blackListFields);
        tmp.addAll(srcClient.getCopyFields());
        //stinky solr-specific
        tmp.add("_version_");
        return tmp;
    }

    private void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }

    private void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    private static Set<String> splitComma(String s) {
        if (StringUtils.isBlank(s)) {
            return Collections.EMPTY_SET;
        }
        Set<String> ret = new HashSet<>();
        for (String c : s.split(",")) {
            ret.add(c);
        }
        return ret;
    }

    private void execute(SearchClient srcClient, SearchClient targClient, Set<String> filterQueries, Set<String> whiteListFields, Set<String> blackListFields) throws IOException, SearchClientException {
        ArrayBlockingQueue<Set<String>> ids = new ArrayBlockingQueue<>(100);
        srcClient.startLoadingIds(ids, batchSize, numThreads, filterQueries);

        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        ExecutorCompletionService<Integer> executorCompletionService = new ExecutorCompletionService<>(executorService);
        String idField = srcClient.getIdField();
        for (int i = 0; i < numThreads; i++) {
            executorCompletionService.submit(new Copier(idField,
                    ids, srcClient, targClient, whiteListFields, blackListFields));
        }
        int finished = 0;
        try {
            while (finished < numThreads) {

                Future<Integer> future = executorCompletionService.poll(1, TimeUnit.SECONDS);
                if (future != null) {
                    Integer done = future.get();
                    LOG.debug("finished: " + finished + " : "+ done);
                    finished++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        executorService.shutdownNow();
    }

    private static class Copier implements Callable<Integer> {
        private final String idField;
        private final ArrayBlockingQueue<Set<String>> ids;
        private final SearchClient src;
        private final SearchClient targ;
        private final Set<String> whiteListFields;
        private final Set<String> blackListFields;
        private int totalDocs = 0;

        private Copier(String idField, ArrayBlockingQueue<Set<String>> ids,
                       SearchClient src, SearchClient targ,
                       Set<String> whiteListFields, Set<String> blackListFields) {
            this.idField = idField;
            this.ids = ids;
            this.src = src;
            this.targ = targ;
            this.whiteListFields = whiteListFields;
            this.blackListFields = blackListFields;
        }

        @Override
        public Integer call() throws Exception {
            while (true) {
                //block on more ids
                Set<String> myIds = ids.take();
                LOG.debug("ids size: "+ids.size() + " : " + myIds.size());
                if (myIds.size() == 0) {
                    return totalDocs;
                }
                List<StoredDocument> docs = src.getDocs(idField, myIds, whiteListFields, blackListFields);
                targ.addDocuments(docs);
                LOG.debug("inserted : "+totalDocs);
                totalDocs += docs.size();
            }

        }
    }
}
