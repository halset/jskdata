package no.jskdata;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WFS2Downloader extends Downloader {

    private final String urlPrefix;
    private final Set<String> typeNames = new LinkedHashSet<>();
    private final String version = "2.0.0";
    private final int count = 999;

    public WFS2Downloader(String urlPrefix) {
        this.urlPrefix = urlPrefix;
    }

    @Override
    public void dataset(String typeName) {
        typeNames.add(typeName);
    }

    @Override
    public void download(Receiver receiver) throws IOException {

        for (String typeName : typeNames) {
            int startIndex = 0;
            List<Integer> contentLengths = new ArrayList<>();
            while (true) {

                if (receiver.shouldStop()) {
                    return;
                }

                StringBuilder url = new StringBuilder(urlPrefix);
                url.append("?");
                url.append("SERVICE=WFS&");
                url.append("VERSION=").append(version).append('&');
                url.append("REQUEST=GetFeature&");
                url.append("TypeName=").append(typeName).append('&');
                url.append("startIndex=").append(startIndex).append('&');
                url.append("count=").append(count);

                HttpURLConnection conn = (HttpURLConnection) new URL(url.toString()).openConnection();
                if (conn.getResponseCode() != 200) {
                    getLogger().info("got " + conn.getResponseCode() + " " + conn.getResponseMessage());
                    break;
                }

                int contentLength = conn.getContentLength();
                if (contentLength > 0 && contentLength < 1024 && contentLengths.size() > 3
                        && contentLength == contentLengths.get(contentLengths.size() - 1)
                        && contentLength == contentLengths.get(contentLengths.size() - 2)) {
                    // several equally small segments after each other. probably nothing.
                    break;
                }
                contentLengths.add(contentLength);

                // read start of file to figure out if last batch
                byte[] startBytes = new byte[1024];
                BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
                in.mark(startBytes.length);
                int len = in.read(startBytes);
                String start = new String(startBytes, 0, len, "UTF-8");
                
                // https://portal.opengeospatial.org/files?artifact_id=43925
                if (start.contains("numberReturned=\"0\"")
                        && !start.contains("NOTE: numberReturned attribute should be 'unknown' as well")) {
                    break;
                }

                // rewind before sending to receiver.
                in.reset();

                StringBuilder fileName = new StringBuilder();
                fileName.append(typeName).append('_');
                fileName.append(startIndex);
                fileName.append(".gml");

                receiver.receive(fileName.toString(), in);

                startIndex = startIndex + count;
            }
        }

    }

    @Override
    public void clear() {
    }

}
