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
 *
 */
package org.mitre.quaerite.solrtools;


import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;
import org.mitre.quaerite.connectors.SearchClient;
import org.mitre.quaerite.connectors.SearchClientFactory;
import org.mitre.quaerite.core.util.StringUtil;

/**
 * Commandline class to remove duplicate elevate entries that
 * become duplicates after analysis via a given field.
 */
public class WinnowAnalyzedElevate {
    static Logger LOG = Logger.getLogger(WinnowAnalyzedElevate.class);

    static Options OPTIONS = new Options();


    static {
        OPTIONS.addOption(
                Option.builder("s")
                        .hasArg().required().desc("solr url").build()
        );

        OPTIONS.addOption(
                Option.builder("e")
                        .longOpt("elevate")
                        .hasArg(true)
                        .required(true)
                        .desc("elevate file (xml)").build()
        );
        OPTIONS.addOption(
                Option.builder("f")
                        .longOpt("field")
                        .hasArg(true)
                        .required(true)
                        .desc("analysis field to use").build()
        );

        OPTIONS.addOption(
                Option.builder("w")
                        .longOpt("winnowed")
                        .hasArg(true)
                        .required(true)
                        .desc("winnowed elevate file (xml)").build()
        );
        OPTIONS.addOption(
                Option.builder("r")
                        .longOpt("removed")
                        .hasArg(true)
                        .required(true)
                        .desc("elevate file for removed elements").build()
        );
    }

    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(
                    "java -jar org.mitre.quaerite.solrtools.WinnowAnalyzedElevate",
                    OPTIONS);
            return;
        }
        Path inputElevate = Paths.get(commandLine.getOptionValue("e"));
        Path winnowElevate = Paths.get(commandLine.getOptionValue("w"));
        Path removedElevate = Paths.get(commandLine.getOptionValue("r"));

        SearchClient client = SearchClientFactory.getClient(commandLine.getOptionValue("s"));
        String analysisField = commandLine.getOptionValue("f");

        WinnowAnalyzedElevate winnowAnalyzedElevate = new WinnowAnalyzedElevate();
        winnowAnalyzedElevate.execute(inputElevate, winnowElevate,
                removedElevate, client, analysisField);
    }

    private void execute(Path inputElevate, Path winnowedElevate,
                         Path removedElevate, SearchClient client, String analysisField)
            throws Exception {
        Map<String, Elevate> elevateMap = ElevateScraper.scrape(inputElevate, null);
        Map<String, List<Elevate>> analyzed = new TreeMap<>();
        for (String q : elevateMap.keySet()) {
            List<String> tokens = client.analyze(analysisField, q);
            String analyzedKey = StringUtil.joinWith("", tokens);
            if (analyzed.containsKey(analyzedKey)) {
                analyzed.get(analyzedKey).add(elevateMap.get(q));
            } else {
                List<Elevate> e = new ArrayList<>();
                e.add(elevateMap.get(q));
                analyzed.put(analyzedKey, e);
            }
        }

        Map<String, Elevate> winnowed = new TreeMap<>();
        List<Elevate> extras = new ArrayList<>();
        for (Map.Entry<String, List<Elevate>> e : analyzed.entrySet()) {
            if (e.getValue().size() == 0) {
                continue;
            }
            Collections.sort(e.getValue(), Elevate.SORT_BY_SIZE_DECREASING);

            winnowed.put(e.getValue().get(0).getQuery(), e.getValue().get(0));
            for (int i = 1; i < e.getValue().size(); i++) {
                extras.add(e.getValue().get(i));
            }
        }

        dumpElevates(winnowedElevate, winnowed.values());
        dumpElevates(removedElevate, extras);
    }

    private void dumpElevates(Path elevateFile, Collection<Elevate> elevates) throws Exception {
        try (OutputStream os = Files.newOutputStream(elevateFile)) {
            XMLStreamWriter out = XMLOutputFactory.newInstance()
                    .createXMLStreamWriter(
                            new OutputStreamWriter(os, StandardCharsets.UTF_8));
            out.writeStartDocument("UTF-8", "1.0");
            out.writeCharacters("\n");
            out.writeStartElement("elevate");

            for (Elevate e : elevates) {
                writeElevate(out, e);
            }
            out.writeCharacters("\n");
            out.writeEndElement();//elevate
            out.writeEndDocument();
            out.flush();
            out.close();
        }

    }

    private void writeElevate(XMLStreamWriter out, Elevate elevate) throws XMLStreamException {
        out.writeCharacters("\n");
        out.writeStartElement("query");
        out.writeAttribute("text", elevate.getQuery());
        out.writeCharacters("\n");
        for (String id : elevate.getIds()) {
            out.writeCharacters("\t");
            out.writeEmptyElement("doc");
            out.writeAttribute("id", id);
            out.writeCharacters("\n");
        }
        out.writeEndElement();
    }
}

