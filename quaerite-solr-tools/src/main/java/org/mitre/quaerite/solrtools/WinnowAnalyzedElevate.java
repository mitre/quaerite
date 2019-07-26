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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.mitre.quaerite.connectors.SearchClientException;
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

        /**
        If you have multiple logical indices in one index, and your
        elevate file looks like:
            index1-id10
            index2-id10
            index3-id10
            index1-id11
            index2-id11
            index3-id11

            it might be useful to sort the docs by logical index,
            while maintaining the document order:
            index1-id10
            index1-id11
            index2-id10
            index2-id11
            index3-id10
            index3-id11

            the command for this would be: -i index1,index2,index3
         */
        OPTIONS.addOption(
                Option.builder("i")
                        .longOpt("idSort")
                        .hasArg(true)
                        .required(false)
                        .desc("sort ids, comma-delimited list").build()
        );

        OPTIONS.addOption(
                Option.builder("c")
                        .longOpt("comment")
                        .hasArg(false)
                        .required(false)
                        .desc("include removed entries in xml comments").build()
        );
    }

    private final DocSorter docSorter;
    private final boolean commentWinnowed;
    WinnowAnalyzedElevate(DocSorter sorter, boolean commentWinnowed) {
        this.docSorter = sorter;
        this.commentWinnowed = commentWinnowed;

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

        SearchClient client = SearchClientFactory.getClient(commandLine.getOptionValue("s"));
        String analysisField = commandLine.getOptionValue("f");
        DocSorter docSorter = new DocSorter(commandLine.getOptionValue("i"));
        boolean commentWinnowed = (commandLine.hasOption("c") ? true : false);
        WinnowAnalyzedElevate winnowAnalyzedElevate = new WinnowAnalyzedElevate(docSorter, commentWinnowed);
        winnowAnalyzedElevate.execute(inputElevate, winnowElevate, client, analysisField);
    }

    private void execute(Path inputElevate, Path winnowedElevate,
                         SearchClient client, String analysisField)
            throws Exception {
        Map<String, Elevate> elevateMap = ElevateScraper.scrape(inputElevate, null);
        Map<String, List<Elevate>> winnowed = winnow(elevateMap, client, analysisField);
        dumpElevates(winnowedElevate, winnowed);
    }

    public static Map<String, List<Elevate>> analyze (
            Map<String, Elevate> elevateMap,
            SearchClient client, String analysisField) throws IOException, SearchClientException {
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
        return analyzed;
    }

    public static Map<String, List<Elevate>> winnow(Map<String, Elevate> elevateMap,
                                                    SearchClient client,
                                                    String analysisField)
            throws IOException, SearchClientException {

        Map<String, List<Elevate>> analyzed = analyze(elevateMap, client, analysisField);

        Map<String, List<Elevate>> winnowed = new TreeMap<>();
        List<Elevate> extras = new ArrayList<>();
        for (Map.Entry<String, List<Elevate>> e : analyzed.entrySet()) {
            if (e.getValue().size() == 0) {
                continue;
            }
            Collections.sort(e.getValue(), Elevate.SORT_BY_SIZE_DECREASING);

            winnowed.put(e.getValue().get(0).getQuery(), e.getValue());
            for (int i = 1; i < e.getValue().size(); i++) {
                extras.add(e.getValue().get(i));
            }
        }
        return winnowed;
    }

    private void dumpElevates(Path elevateFile, Map<String, List<Elevate>> elevates) throws Exception {
        try (OutputStream os = Files.newOutputStream(elevateFile)) {
            XMLStreamWriter out = XMLOutputFactory.newInstance()
                    .createXMLStreamWriter(
                            new OutputStreamWriter(os, StandardCharsets.UTF_8));
            out.writeStartDocument("UTF-8", "1.0");
            out.writeCharacters("\n");
            out.writeStartElement("elevate");

            for (Map.Entry<String, List<Elevate>> e : elevates.entrySet()) {
                if (e.getValue().size() > 0) {
                    writeElevate(out, e.getValue().get(0));
                }
                if (commentWinnowed) {
                    for (int i = 1; i < e.getValue().size(); i++) {
                        commentElevate(out, e.getValue().get(i));
                    }
                }
            }
            out.writeCharacters("\n");
            out.writeEndElement();//elevate
            out.writeEndDocument();
            out.flush();
            out.close();
        }
    }

    private void commentElevate(XMLStreamWriter out, Elevate elevate) throws XMLStreamException {
        StringWriter writer = new StringWriter();
        XMLStreamWriter comment = XMLOutputFactory.newInstance()
                .createXMLStreamWriter(writer);
        writeElevate(comment, elevate);
        comment.writeCharacters("\n");
        writer.flush();
        out.writeCharacters("\n");
        out.writeComment(writer.toString());
    }

    private void writeElevate(XMLStreamWriter out, Elevate elevate) throws XMLStreamException {
        out.writeCharacters("\n");
        out.writeStartElement("query");
        out.writeAttribute("text", elevate.getQuery());
        out.writeCharacters("\n");

        List<String> sorted = docSorter.sort(elevate.getIds());
        if (sorted.size() != elevate.getIds().size()) {
            throw new IllegalArgumentException(
                    String.format(Locale.US, "something went wrong in sorting:" +
                    "I see %s but there should be %s", sorted.size(),
                    elevate.getIds().size()));
        }
        for (String id : sorted) {
            out.writeCharacters("\t");
            out.writeEmptyElement("doc");
            out.writeAttribute("id", id);
            out.writeCharacters("\n");
        }
        out.writeEndElement();
    }

    private static class DocSorter {
        final List<Matcher> matchers;
        private DocSorter(String commaDelimitedFields) {
            if (commaDelimitedFields == null) {
                matchers = Collections.emptyList();
            } else {
                matchers = new ArrayList<>();
                for (String s : commaDelimitedFields.split(",")) {
                    matchers.add(Pattern.compile(s).matcher(""));
                }
            }
        }

        private List<String> sort(List<String> list) {
            if (matchers.size() == 0) {
                return list;
            }
            List<List<String>> bins = new ArrayList<>();
            //add one at the end for no match/default
            for (int i = 0; i <= matchers.size(); i++) {
                bins.add(new ArrayList<>());
            }
            for (String id : list) {
                boolean inserted = false;
                for (int i = 0; i < matchers.size() && inserted == false; i++) {
                    if (matchers.get(i).reset(id).find()) {
                        List<String> bin = bins.get(i);
                        bin.add(id);
                        inserted = true;
                        break;
                    }
                }
                if (!inserted) {
                    bins.get(bins.size() - 1).add(id);
                }
            }
            List<String> ret = new ArrayList<>();
            for (List<String> bin : bins) {
                for (String id : bin) {
                    ret.add(id);
                }
            }
            return ret;
        }
    }
}

