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
 *
 */
package org.mitre.quaerite.solrtools;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import org.apache.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ElevateScraper extends DefaultHandler {

    static Logger LOG = Logger.getLogger(ElevateScraper.class);

    private boolean inQuery = false;
    private String currentQuery = null;
    private Set<String> ids = new LinkedHashSet<>();
    private Map<String, Elevate> elevates = new HashMap<>();
    private final Matcher idMatcher;
    public ElevateScraper(Matcher idMatcher) {
        this.idMatcher = idMatcher;
    }

    public static Map<String, Elevate> scrape(Path p, Matcher idMatcher) throws SAXException, IOException, ParserConfigurationException {

        ElevateScraper scraper = new ElevateScraper(idMatcher);
        try (InputStream is = Files.newInputStream(p)) {
            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                SAXParserFactory.newInstance().newSAXParser().parse(
                        new InputSource(reader), scraper);
                return scraper.getElevates();
            }
        }
    }

    private Map<String, Elevate> getElevates() {
        return elevates;
    }


    @Override
    public void startElement(String uri, String localName, String name, Attributes attrs) throws SAXException {
        if ("query".equals(name)) {
            inQuery = true;
            currentQuery = getValue("text", attrs);
            currentQuery = currentQuery.trim();
        } else if ("doc".equals(name)) {
            String id = getValue("id", attrs);
            if (idMatcher != null) {
                if (idMatcher.reset(id).find()) {
                    ids.add(id);
                }
            } else {
                ids.add(id);
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String name) throws SAXException {
        if ("query".equals(name)) {
            Elevate elevate = elevates.get(currentQuery);
            if (elevate != null) {
                LOG.warn("duplicate entry in elevate file(?!): >"+currentQuery+"<");
                appendMissing(elevate, ids);
            } else {
                elevates.put(currentQuery, new Elevate(currentQuery, new ArrayList<>(ids)));
            }
            currentQuery = null;
            ids.clear();
        }
    }

    private static void appendMissing(Elevate elevate, Set<String> newIds) {
        for (String newId : newIds) {
            if (! elevate.ids.contains(newId)) {
                elevate.ids.add(newId);
            }
        }
    }

    public static String getValue(String attrName, Attributes attrs) {
        for (int i = 0; i < attrs.getLength(); i++) {
            if (attrs.getLocalName(i).equalsIgnoreCase(attrName)) {
                return attrs.getValue(i);
            }
        }
        return null;
    }

}