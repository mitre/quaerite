package org.mitre.quaerite.connectors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;


import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.mitre.quaerite.FacetResult;
import org.mitre.quaerite.ResultSet;
import org.mitre.quaerite.tools.FindFeatures;

public abstract class SearchServer {

    public abstract ResultSet search(QueryRequest query) throws SearchServerException, IOException;
    public abstract FacetResult facet(QueryRequest query) throws SearchServerException, IOException;


    protected byte[] get(String url) throws SearchServerException {
        //overly simplistic...need to add proxy, etc., but good enough for now
        URI uri = null;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpHost target = new HttpHost(uri.getHost(), uri.getPort());
        HttpGet httpGet = null;
        try {
            String get = uri.getPath();
            if (!StringUtils.isBlank(uri.getQuery())) {
                get += "?"+uri.getQuery();
            }
            httpGet = new HttpGet(get);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        HttpResponse httpResponse = null;
        try {
            httpResponse = httpClient.execute(target, httpGet);
        } catch (IOException e) {
            throw new SearchServerException(e);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (httpResponse.getStatusLine().getStatusCode() != 200) {
            throw new SearchServerException("Bad status code: "+httpResponse.getStatusLine().getStatusCode());
        }
        try {
            IOUtils.copy(httpResponse.getEntity().getContent(), bos);
        } catch (IOException e) {
            throw new SearchServerException(e);
        }
        return bos.toByteArray();
    }

    protected static String encode(String s) throws IllegalArgumentException {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
