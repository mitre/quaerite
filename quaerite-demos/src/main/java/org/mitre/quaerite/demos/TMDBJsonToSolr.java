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
package org.mitre.quaerite.demos;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.apache.commons.lang3.StringUtils;
import org.mitre.quaerite.connectors.SearchClient;
import org.mitre.quaerite.connectors.SearchClientFactory;
import org.mitre.quaerite.connectors.StoredDocument;

public class TMDBJsonToSolr {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("java -jar o.m.q.demos.TMDBToSolr tmdb.json http://localhost:8983/solr/tmdb");
            System.exit(0);
        }
        Path p = Paths.get(args[0]);
        SearchClient searchClient = SearchClientFactory.getClient(args[1]);
        int cnt = 0;
        long start = System.currentTimeMillis();
        List<Movie> movies = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.beginObject();
            while (jsonReader.hasNext()) {
                Movie movie = nextMovie(jsonReader);
                movies.add(movie);
                if (movies.size() >= 1000) {
                    searchClient.addDocuments(buildDocuments(movies));
                    movies.clear();
                    System.out.println("indexed "+cnt + " in "+
                            (System.currentTimeMillis()-start) + " ms");
                }
                cnt++;
            }
            jsonReader.endObject();
        }
        searchClient.addDocuments(buildDocuments(movies));
        System.out.println("added "+cnt + " in "+
                (System.currentTimeMillis()-start) + " ms");
    }

    private static List<StoredDocument> buildDocuments(List<Movie> movies) {
        List<StoredDocument> docs = new ArrayList<>();
        for (Movie movie : movies) {
            docs.add(buildDocument(movie));
        }
        return docs;
    }

    private static StoredDocument buildDocument(Movie movie) {
        StoredDocument storedDocument = new StoredDocument();
        storedDocument.addNonBlankField("id", movie.id);
        storedDocument.addNonBlankField("original_language", movie.originalLanguage);
        storedDocument.addNonBlankField("original_title", movie.originalTitle);
        storedDocument.addNonBlankField("title", movie.title);
        storedDocument.addNonBlankField("overview", movie.overview);
        storedDocument.addNonBlankField("budget", doubleToString(movie.budget));
        storedDocument.addNonBlankField("revenue", doubleToString(movie.revenue));
        storedDocument.addNonBlankField("popularity", doubleToString(movie.popularity));
        storedDocument.addNonBlankField("vote_average", doubleToString(movie.voteAverage));
        storedDocument.addNonBlankField("vote_count", integerToString(movie.voteCount));


        storedDocument.addNonBlankField("genres", movie.genres);
        storedDocument.addNonBlankField("production_companies", movie.productionCompanies);
        storedDocument.addNonBlankField("cast", movie.cast);
        storedDocument.addNonBlankField("directors", movie.directors);
        storedDocument.addNonBlankField("release_date", movie.getReleaseDateString());

        return storedDocument;
    }

    private static String integerToString(int voteCount) {
        if (voteCount < 0) {
            return StringUtils.EMPTY;
        }
        return Integer.toString(voteCount);
    }


    private static String doubleToString(double value) {
        if (value < 0) {
            return StringUtils.EMPTY;
        }
        return String.format("%.2f", value);
    }

    private static Movie nextMovie(JsonReader jsonReader) throws IOException {
        String id = jsonReader.nextName();
        jsonReader.beginObject();
        Movie movie = new Movie();
        movie.id = id;
        while (jsonReader.hasNext()) {
            String name = getName(jsonReader, "");
            if ("adult".equals(name)) {
                movie.adult = getBoolean(jsonReader, false);
            } else if ("genres".equals(name)){
                movie.setGenres(extractNamesFromArrayOfObjections(jsonReader));
            } else if ("original_language".equals(name)) {
                movie.originalLanguage = getString(jsonReader, StringUtils.EMPTY);
            } else if ("original_title".equals(name)) {
                movie.originalTitle = getString(jsonReader, StringUtils.EMPTY);
            } else if ("overview".equals(name)) {
                movie.overview = getString(jsonReader, StringUtils.EMPTY);
            } else if ("title".equals(name)) {
                movie.title = getString(jsonReader, StringUtils.EMPTY);
            } else if ("popularity".equals(name)) {
                movie.popularity = getDouble(jsonReader, -1.0);
            } else if ("vote_count".equals(name)) {
                movie.voteCount = getInt(jsonReader, -1);
            } else if ("vote_average".equals(name)) {
                movie.voteAverage = getDouble(jsonReader, -1.0f);
            } else if ("cast".equals(name)) {
                movie.setCast(extractNamesFromArrayOfObjections(jsonReader));
            } else if ("directors".equals(name)) {
                movie.setDirectors(extractNamesFromArrayOfObjections(jsonReader));
            } else if ("release_date".equals(name)) {
                movie.setReleaseDate(getString(jsonReader, ""));
            } else if ("production_companies".equals(name)) {
                movie.setProductionCompanies(extractNamesFromArrayOfObjections(jsonReader));
            } else if ("budget".equals(name)) {
                movie.budget = getDouble(jsonReader, -1.0);
            } else if ("revenue".equals(name)) {
                movie.revenue = getDouble(jsonReader, -1.0);
            } else {
                jsonReader.skipValue();
            }
        }
        jsonReader.endObject();
        return movie;
    }

    private static String getName(JsonReader jsonReader, String nullValue) throws IOException {
        if (jsonReader.peek().equals(JsonToken.NULL)) {
            return nullValue;
        }
        return jsonReader.nextName();
    }

    private static String getString(JsonReader jsonReader, String nullValue) throws IOException {
        if (jsonReader.peek().equals(JsonToken.NULL)) {
            return nullValue;
        }
        return jsonReader.nextString();
    }

    private static double getDouble(JsonReader jsonReader, double nullValue) throws IOException {
        if (jsonReader.peek().equals(JsonToken.NULL)) {
            return nullValue;
        }
        return jsonReader.nextDouble();
    }

    private static int getInt(JsonReader jsonReader, int nullValue) throws IOException {
        if (jsonReader.peek().equals(JsonToken.NULL)) {
            return nullValue;
        }
        return jsonReader.nextInt();
    }

    private static boolean getBoolean(JsonReader jsonReader, boolean nullValue) throws IOException {
        if (jsonReader.peek().equals(JsonToken.NULL)) {
            return nullValue;
        }
        return jsonReader.nextBoolean();
    }

    private static List<String> extractNamesFromArrayOfObjections(JsonReader jsonReader) throws IOException {
        List<String> names = new ArrayList<>();
        jsonReader.beginArray();
        if (jsonReader.peek().equals(JsonToken.END_ARRAY)) {
            jsonReader.endArray();
            return names;
        }
        jsonReader.beginObject();
        while (jsonReader.hasNext()) {
            String name = jsonReader.nextName();
            if ("name".equals(name)) {
                String s = jsonReader.nextString();
                if (! StringUtils.isBlank(s)) {
                    names.add(s);
                }
            } else {
                jsonReader.skipValue();
            }
            if (jsonReader.peek().equals(JsonToken.END_OBJECT)) {
                jsonReader.endObject();
                if (jsonReader.peek().equals(JsonToken.END_ARRAY)) {
                    break;
                } else {
                    jsonReader.beginObject();
                }
            } else if (jsonReader.peek().equals(JsonToken.BEGIN_OBJECT)) {
                jsonReader.beginObject();
            }
        }
        jsonReader.endArray();
        return names;
    }

    private static class Movie {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        DateFormat UTCFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

        String id;
        boolean adult;
        String originalLanguage;
        String originalTitle;
        String title;
        String overview;
        double popularity = -1;
        int voteCount = -1;
        double voteAverage = -1;
        Date releaseDate;
        double budget = -1;
        double revenue = -1;
        List<String> genres;
        List<String> cast;
        List<String> directors;
        List<String> productionCompanies;


        public void setDirectors(List<String> names) {
            this.directors = names;
        }

        public void setGenres(List<String> names) {
            this.genres = names;
        }

        public void setCast(List<String> names) {
            this.cast = names;
        }

        public void setProductionCompanies(List<String> names) {
            this.productionCompanies = names;
        }

        @Override
        public String toString() {
            return "Movie{" +
                    "adult=" + adult +
                    ", originalLanguage='" + originalLanguage + '\'' +
                    ", originalTitle='" + originalTitle + '\'' +
                    ", title='" + title + '\'' +
                    ", overview='" + overview + '\'' +
                    ", popularity=" + popularity +
                    ", voteCount=" + voteCount +
                    ", voteAverage=" + voteAverage +
                    ", genres=" + genres +
                    ", cast=" + cast +
                    ", directors=" + directors +
                    '}';
        }

        public void setReleaseDate(String string) {
            if (StringUtils.isBlank(string)) {
                return;
            }
            try {
                releaseDate = dateFormat.parse(string);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        public String getReleaseDateString() {
            if (releaseDate == null) {
                return StringUtils.EMPTY;
            }
            return UTCFormat.format(releaseDate);
        }
    }
}
