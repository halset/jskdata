package no.jskdata.data;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.logging.Logger;

public class URLFileInfo {

    private String fileName;
    private String url;

    private Number contentLength;
    private Date lastModified;

    private File tempLocalFile;

    private static final Logger log = Logger.getLogger(URLFileInfo.class.getName());

    public URLFileInfo(String fileName, String url) {
        this.fileName = fileName;
        this.url = url;
    }

    public String getFileName() {
        return fileName;
    }

    public String getUrl() {
        return url;
    }

    private void doHEAD() throws IOException {

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("HEAD");

        if (conn.getResponseCode() == 405) {
            // GeoNorge return 302 Moved Temporarily for GET, but 405 Method Not Allowed for
            // HEAD. Do GET first without redirect, then do HEAD for redirected URL.
            log.info("got 405 for " + url);
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(false);
        }

        while (conn.getResponseCode() == 302 || conn.getResponseCode() == 301) {
            // GeoNorge first return 302 to one http URL, then 301 to a https URL..
            log.info("got " + conn.getResponseCode() + " for " + url);
            url = conn.getHeaderField("Location");
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("HEAD");
        }

        if (conn.getResponseCode() == 200) {
            log.info("got 200 for " + url);
            contentLength = conn.getContentLengthLong();
            lastModified = new Date(conn.getHeaderFieldDate("Last-Modified", 0));
        } else {
            System.out.println("got " + conn.getResponseCode() + " for " + url);
        }
    }

    public Number getContentLength() {
        if (contentLength == null) {
            try {
                doHEAD();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return contentLength;
    }

    public Date getLastModified() {
        if (lastModified == null) {
            try {
                doHEAD();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return lastModified;
    }

    public byte[] read(long offset, int length) throws IOException {

        if (tempLocalFile != null && tempLocalFile.canRead()) {
            try (FileInputStream fis = new FileInputStream(tempLocalFile)) {
                return read(fis, offset, length);
            }
        }

        log.info("start read " + offset + "-" + length + " from " + url);
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);

        if (offset > 0 || length > 0) {
            conn.setRequestProperty("Range", "bytes=" + offset + "-" + length);
        }

        if (conn.getResponseCode() == HttpURLConnection.HTTP_PARTIAL) {
            log.info("got partial response :)");
            InputStream in = conn.getInputStream();
            return readFully(in);
        } else if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
            if (offset > 0) {
                log.info("got full response for partial request :( read all and hope for the best :)");
            }

            tempLocalFile = File.createTempFile("jskdata", "");
            tempLocalFile.deleteOnExit(); // it might not be deleted on a hard crash..

            InputStream in = conn.getInputStream();
            try (FileOutputStream out = new FileOutputStream(tempLocalFile)) {
                byte[] buff = new byte[1024];

                int len = 0;
                while ((len = in.read(buff)) >= 0) {
                    out.write(buff, 0, len);
                }
                out.flush();
            }

            try (FileInputStream fis = new FileInputStream(tempLocalFile)) {
                return read(fis, offset, length);
            }

        } else {
            throw new IOException("got status " + conn.getResponseCode() + " from " + url);
        }
    }

    private byte[] read(InputStream in, long offset, int length) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buff = new byte[1024];

        if (offset > 0) {
            long offsetRest = offset;
            while (offsetRest > 0) {
                offsetRest = offsetRest - in.skip(offsetRest);
            }
        }
        int readBytes = 0;
        int len = 0;
        while ((len = in.read(buff)) >= 0) {
            if (readBytes + len > length) {
                len = length - readBytes;
            }
            baos.write(buff, 0, len);
            readBytes = readBytes + len;
            if (readBytes >= length) {
                break;
            }
        }

        return baos.toByteArray();
    }

    private byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buff = new byte[1024];

        int len = 0;
        while ((len = in.read(buff)) >= 0) {
            baos.write(buff, 0, len);
        }

        return baos.toByteArray();
    }

}
