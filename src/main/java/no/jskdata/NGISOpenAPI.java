package no.jskdata;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;

public class NGISOpenAPI extends Downloader {

    private final String url;
    private final String username;
    private final String password;

    private final Set<String> datasetIds = new LinkedHashSet<>();

    public NGISOpenAPI(String url, String username, String password) {
        this.url = url;

        if (username == null || password == null) {
            throw new IllegalArgumentException("missing username and/or password");
        }
        this.username = username;
        this.password = password;

    }

    @Override
    public void dataset(String dataset) {
        datasetIds.add(dataset);
    }

    @Override
    public void download(Receiver receiver) throws IOException {

        // A proper client should allow the client to connect and query url+"datasets"
        // to list possible datasets

        Format gml = new Format("gml", "application/vnd.kartverket.sosi+gml; version=1.0");
        Format json = new Format("json", "application/vnd.kartverket.json+gml; version=1.0");

        Format format = gml;

        for (String datasetId : datasetIds) {

            if (receiver.shouldStop()) {
                break;
            }

            // fetch more meta data for the data set
            // String datasetUrl = url + "datasets/" + datasetId;

            // create a greedy url. this will probably not work for large data sets
            String featuresUrl = url + "datasets/" + datasetId + "/features?references=none";

            HttpURLConnection conn = (HttpURLConnection) new URL(featuresUrl).openConnection();

            if (username != null && password != null) {
                String userpass = username + ":" + password;
                String encoded = Base64.getEncoder().encodeToString(userpass.getBytes("UTF-8"));
                conn.setRequestProperty("Authorization", "Basic " + encoded.trim());
            }

            conn.setRequestProperty("Accept", format.getAcceptHeaderValue());
            conn.setConnectTimeout(1 * 1000);
            conn.setReadTimeout(30 * 1000);

            if (conn.getResponseCode() != 200) {

                InputStream err = conn.getErrorStream();
                if (err != null) {
                    getLogger().info(featuresUrl + " returned status code: " + conn.getResponseCode() + ", error: "
                            + new String(err.readAllBytes()));
                }

                throw new IOException(featuresUrl + " returned status code: " + conn.getResponseCode());
            }

            receiver.receive(datasetId + "." + format.getFileNameSuffix(), conn.getInputStream());
        }

    }

    @Override
    public void clear() {
        datasetIds.clear();
    }

    private static final class Format {

        private final String fileNameSuffix;

        private final String acceptHeaderValue;

        private Format(String fileNameSuffix, String acceptHeaderValue) {
            this.fileNameSuffix = fileNameSuffix;
            this.acceptHeaderValue = acceptHeaderValue;
        }

        public String getFileNameSuffix() {
            return fileNameSuffix;
        }

        public String getAcceptHeaderValue() {
            return acceptHeaderValue;
        }

    }

}
