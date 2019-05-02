package org.mitre.quaerite.solrtools;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mitre.quaerite.connectors.QueryRequest;
import org.mitre.quaerite.connectors.SearchClient;
import org.mitre.quaerite.connectors.SearchClientFactory;
import org.mitre.quaerite.core.ResultSet;

public class ElevateQueryComparer {
    static Logger LOG = Logger.getLogger(ElevateQueryComparer.class);

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
                Option.builder("q")
                        .longOpt("queries")
                        .hasArg(true)
                        .required(true)
                        .desc("queries (with optional counts)").build()
        );
        OPTIONS.addOption(
                Option.builder("d")
                        .longOpt("outputDirectory")
                        .hasArg(true)
                        .required(false)
                        .desc("directory to which to write reports").build()
        );

        //if you are analyzing e.g. top 10k of GoogleAnalytics
        //you'll need to supply the actual total number of queries
        OPTIONS.addOption(
                Option.builder("t")
                        .longOpt("totalQueries")
                        .hasArg(true)
                        .required(false)
                        .desc("denominator for total number of queries -- " +
                                "sum of queries if used if this is not specified").build()
        );
        //let's say you have a single solr index that hosts
        //several logical indices: "general", "catlovers", "doglovers",
        //and there ids include the logical index, e.g. general-1; catlovers-1
        //you may only want to focus on ids that match this regex:
        //~/(?i)general-\\d+/
        OPTIONS.addOption(
                Option.builder("r")
                        .longOpt("regex")
                        .hasArg(true)
                        .required(false)
                        .desc("regex to subset ids (in case of multiple logical indices stored in a single Solr index)").build()
        );
    }

    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(
                    "java -jar org.mitre.quaerite.solrtools.ElevateQueryComparer",
                    OPTIONS);
            return;
        }
        Matcher idMatcher = null;
        if (commandLine.hasOption("r")) {
            idMatcher = Pattern.compile(commandLine.getOptionValue("r")).matcher("");
        }
        Path reportsRoot = Paths.get(".");
        if (commandLine.hasOption("d")) {
            reportsRoot = Paths.get(commandLine.getOptionValue("d"));
        }

        DecimalFormat df = new DecimalFormat("##.###%");
        //TODO: lowercase queries or run them through an analyzer from a specific field?
        QuerySet queries = loadQueries(Paths.get(commandLine.getOptionValue("q")));
        Map<String, Elevate> elevateMap = ElevateScraper.scrape(Paths.get(commandLine.getOptionValue("e")),
                idMatcher);

        int totalQueries = queries.total;
        if (commandLine.hasOption("t")) {
            totalQueries = Integer.parseInt(commandLine.getOptionValue("t"));
        }

        List<Query> sorted = new ArrayList<>(queries.queries.values());
        Collections.sort(sorted);
        dumpAllElevated(elevateMap, queries, totalQueries, df, reportsRoot);
        dumpElevatedQueries(sorted, totalQueries, elevateMap, df, reportsRoot);
        dumpElevatedButNoQueries(elevateMap.keySet(), queries.queries.keySet(), reportsRoot);

        if (commandLine.hasOption("s")) {
            dumpElevateVsIndex(commandLine.getOptionValue("s"), sorted, elevateMap, df, totalQueries, reportsRoot);
        }

    }

    private static void dumpAllElevated(Map<String, Elevate> elevateMap,
                                        QuerySet queries, int totalCount,
                                        NumberFormat df,
                                        Path reportsRoot) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(
                reportsRoot.resolve("elevated.csv"), StandardCharsets.UTF_8
        )) {
            writer.write(StringUtils.joinWith(",", "Elevated",
                    "QueryCount", "QueryPercentage", "\n"));
            for (String elevated : elevateMap.keySet()) {
                if (elevateMap.get(elevated).ids.size() == 0) {
                    LOG.warn("no ids for this elevated item >"+elevated+"<");
                    continue;
                }
                int cnt = 0;
                Query q  = queries.queries.get(elevated);
                if (q != null) {
                    cnt = q.getCount();
                }
                writer.write(
                        StringUtils.joinWith(",",
                                clean(elevated),
                                cnt,
                                df.format(((double) cnt / (double) totalCount))
                        ) + "\n"
                );

            }
        }
    }

    private static void dumpElevateVsIndex(String searchServer,
                                           List<Query> sorted,
                                           Map<String, Elevate> elevateMap,
                                           DecimalFormat df, int totalCount,
                                           Path reportsRoot) throws Exception {
        SearchClient searchClient = SearchClientFactory.getClient(searchServer);

        Set<String> indexContains = new HashSet<>();
        Set<String> indexMissing = new HashSet<>();
        try (BufferedWriter writer = Files.newBufferedWriter(
                reportsRoot.resolve("elevated_vs_index.csv"), StandardCharsets.UTF_8
        )) {
            writer.write(StringUtils.joinWith(",", "Query", "Id",
                    "IndexContainsId",
                    "QueryCount", "QueryPercentage", "\n"));
            for (Query q : sorted) {
                if (elevateMap.containsKey(q.q)) {
                    Elevate e = elevateMap.get(q.q);
                    for (String id : e.getIds()) {
                        if (!indexContains.contains(id) && !indexMissing.contains(id)) {
                            boolean contains = indexContains(id, searchClient);
                            if (contains) {
                                indexContains.add(id);
                            } else {
                                indexMissing.add(id);
                            }
                        }
                        String contains = "index contains";
                        if (indexMissing.contains(id)) {
                            contains = "index missing";
                        }

                        writer.write(
                                StringUtils.joinWith(",",
                                        clean(q.getQ()),
                                        clean(id),
                                        contains,
                                        q.getCount(),
                                        df.format(((double) q.getCount() / (double) totalCount))
                                ) + "\n"
                        );

                    }
                }
            }
        }

    }

    private static void dumpElevatedButNoQueries(Set<String> elevated, Set<String> queries, Path reportsRoot) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                reportsRoot.resolve("elevated_zero_queries.csv"),
                StandardCharsets.UTF_8)) {
            writer.write("ElevatedQueryNotInQueryLog\n");
            List<String> sorted = new ArrayList<>(elevated);
            Collections.sort(sorted);
            for (String q : sorted) {
                if (!queries.contains(q)) {
                    writer.write(clean(q) + "\n");
                }
            }
        }

    }

    private static void dumpElevatedQueries(List<Query> sorted, int totalQueries,
                                            Map<String, Elevate> elevateMap,
                                            DecimalFormat df, Path reportsRoot) throws Exception {

        try (Writer writer = Files.newBufferedWriter(
                reportsRoot.resolve("queries_elevated_or_not.csv"),
                StandardCharsets.UTF_8)) {
            //header
            writer.write(
                    StringUtils.joinWith(",", "Query", "ElevatedOrNot",
                            "QueryCount", "QueryPercentage", "\n")
            );
            for (Query q : sorted) {
                String elevated = "not_elevated";
                if (elevateMap.containsKey(q.q)) {
                    elevated = "elevated";
                }
                writer.write(StringUtils.joinWith(",",
                        clean(q.getQ()),
                        elevated,
                        q.getCount(),
                        clean(df.format(((double) q.getCount() / (double) totalQueries)))
                ));
                writer.write("\n");
            }
        }
    }

    private static boolean indexContains(String id, SearchClient searchClient) throws Exception {
        QueryRequest qr = new QueryRequest("id:\"" + id + "\"");
        ResultSet rs = searchClient.search(qr);
        return rs.getIds().size() > 0;
    }

    private static String clean(String s) {
        if (s == null) {
            return StringUtils.EMPTY;
        }
        if (s.contains(",") || s.contains("\n") || s.contains("\r") || s.contains("\"")) {
            s = "\"" + s.replaceAll("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static QuerySet loadQueries(Path file) throws Exception {
        QuerySet querySet = new QuerySet();
        Matcher uc = Pattern.compile("[A-Z]").matcher("");
        try (InputStream is = Files.newInputStream(file)) {
            try (Reader reader = new InputStreamReader(new BOMInputStream(is), "UTF-8")) {
                Iterable<CSVRecord> records = CSVFormat.EXCEL
                        .withFirstRecordAsHeader().parse(reader);
                for (CSVRecord record : records) {
                    String q = record.get("query");
                    Integer c = Integer.parseInt(record.get("count"));
                    if (querySet.queries.containsKey(q)) {
                        LOG.warn("duplicate queries?! >"+q+"<");
                    }

                    querySet.set(q, c);
                }
            }
        }
        LOG.info("loaded "+querySet.queries.size()+" queries");
        return querySet;
    }

    private static class ElevateSet {

        Map<String, List<String>> queryToIds = new HashMap<>();

        public void add(String query, String id) {
            List<String> ids = queryToIds.get(query);
            if (ids == null) {
                ids = new ArrayList<>();
                queryToIds.put(query, ids);
            }
            ids.add(id);
        }

        @Override
        public String toString() {
            return "ElevateSet{" +
                    "queryToIds=" + queryToIds +
                    '}';
        }
    }

    private static class QuerySet {
        int total;
        Map<String, Query> queries = new HashMap<>();

        public void set(String query, int count) {
            if (!query.equals("(other)")) {
                queries.put(query, new Query(query, count));
            }
            total += count;
        }

        @Override
        public String toString() {
            return "QuerySet{" +
                    "total=" + total +
                    ", queries=" + queries +
                    '}';
        }
    }

    private static class Query implements Comparable<Query> {
        String q;
        int count = -1;

        public Query(String q, int count) {
            this.q = q;
            this.count = count;
        }

        public String getQ() {
            return q;
        }

        public int getCount() {
            return count;
        }

        @Override
        public String toString() {
            return "Query{" +
                    "q='" + q + '\'' +
                    ", count=" + count +
                    '}';
        }

        @Override
        public int compareTo(Query other) {
            if (other.getCount() == count) {
                return q.compareTo(other.q);
            }
            return Integer.compare(other.count, count);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Query)) return false;
            Query query = (Query) o;
            return count == query.count &&
                    q.equals(query.q);
        }

        @Override
        public int hashCode() {
            return Objects.hash(q, count);
        }
    }


}
