package no.jskdata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.mail.Address;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.URLName;
import javax.mail.search.SearchTerm;

import org.apache.commons.lang3.time.DateUtils;

import com.google.common.net.UrlEscapers;
import com.google.gson.Gson;

/**
 * Downloading from http://nedlasting.nve.no/gis/ . This require polling a mail
 * server for the response.
 * 
 * NB: Do not attempt to run more than one simultaneous {@link NVEDownloader} for
 * the same mail address as there are no way to figure out what downloaded zip
 * archive belonging to what request.
 */
public class NVEDownloader extends Downloader {

    private final String mailAddress;
    private final String mailServer;

    private final Map<String, String> parameters = new LinkedHashMap<>();
    private final Set<String> datasets = new HashSet<>();

    /**
     * 
     * @param mailAddress
     *            a {@link String} with the email address to use for ordering. For
     *            extra reliability, use a new mail address for each reqyest. Some
     *            mail servers support username+someotherstuff@host format that is
     *            ideal for this use.
     * @param mailServer
     *            a {@link String} like imaps://user:pass@imapserv.er/ to check for
     *            mails from NVE
     */
    public NVEDownloader(String mailAddress, String mailServer) {
        this.mailAddress = mailAddress;
        this.mailServer = mailServer;

        if (mailAddress == null || mailAddress.length() == 0) {
            throw new IllegalArgumentException("Missing mailAddress");
        }

        if (mailServer == null || mailServer.length() == 0) {
            throw new IllegalArgumentException("Missing mailServer");
        }
        
        parameters.put("opt_requesteremail", mailAddress);
        parameters.put("EPOST", mailAddress);
        parameters.put("FORMAT", "SHAPE");
        parameters.put("KOORDS", "EPSG:4326");
        parameters.put("FIRMA", "Ikke gitt");
        parameters.put("BRUKER", "Ikke gitt");
        parameters.put("BRUK", "Ikke gitt");
        parameters.put("KOMMENTAR", "NULL");
        parameters.put("UTTREKKSTYPE", "LAND");
        parameters.put("KLIPPETYPE", "SomOverlapper");
        
    }

    /**
     * Add a NVE dataset like "Master" or "Flomsoner 50".
     * 
     * @param dataset
     *            a {@link String} like "Master" or "Flomsoner 50"
     */
    @Override
    public void dataset(String dataset) {
        datasets.add(dataset);
    }
    
    String createDownloadUrl() throws IOException {

        // contact server for list of possible data sets and their names
        String u = "http://nedlasting.nve.no/gis/js/temadata.js";
        HttpURLConnection conn = (HttpURLConnection) new URL(u).openConnection();
        if (conn.getResponseCode() != 200) {
            throw new IOException("could not download " + u);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        Gson gson = new Gson();
        List<DatasetDefinition> datasetDefinitions = new ArrayList<>();
        String line = null;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (!line.startsWith("{")) {
                continue;
            }
            if (line.endsWith(",")) {
                line = line.substring(0, line.length() - 1);
            }

            DatasetDefinition def = gson.fromJson(line, DatasetDefinition.class);
            if (datasets.remove(def.getName1())) {
                datasetDefinitions.add(def);
            }
        }

        // build up parameters for the data sets
        StringBuilder name0s = new StringBuilder();
        StringBuilder name1s = new StringBuilder();
        for (DatasetDefinition def : datasetDefinitions) {
            if (name0s.length() > 0) {
                name0s.append(' ');
            }
            name0s.append(def.getName0());

            if (name1s.length() > 0) {
                name1s.append(' ');
            }
            name1s.append(def.getName1());
        }
        Map<String, String> params = new LinkedHashMap<>();
        
        params.putAll(parameters);
        params.put("KARTLAG", name0s.toString());
       
        // useful to warn user if the data set has not been found
        if (!datasets.isEmpty()) {
            throw new IOException("Could not find all datasets. Missing " + datasets);
        }

        // build up complete url with all the parameters
        StringBuilder url = new StringBuilder();
        url.append("http://nedlasting.nve.no/gis/WebFormFmeNve.aspx?");
        for (Iterator<Map.Entry<String, String>> it = params.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, String> e = it.next();
            url.append(e.getKey());
            url.append("=");
            url.append(UrlEscapers.urlFragmentEscaper().escape(e.getValue()));
            if (it.hasNext()) {
                url.append('&');
            }
        }
        
        return url.toString();
    }
    
    @Override
    public void download(Receiver receiver) throws IOException {

        if (datasets.isEmpty()) {
            return;
        }

        // Find urls before. Both to check that mail is configured properly and works
        // *and* to be able to calculate url delta.
        Set<String> urlsBefore = urlsFromMails();

        String url = createDownloadUrl();

        // the main request that should start the server process
        HttpURLConnection formSubmitConn = (HttpURLConnection) new URL(url.toString()).openConnection();
        if (formSubmitConn.getResponseCode() != 200) {
            throw new IOException("unexpected response from " + formSubmitConn.getResponseCode() + " "
                    + formSubmitConn.getResponseMessage());
        }
        
        Gson gson = new Gson();
        Response response = gson.fromJson(new InputStreamReader(formSubmitConn.getInputStream(), "UTF-8"),
                Response.class);
        if (response == null || response.getValue() != 1) {
            throw new IOException("unexpected response from " + url.toString() + ". value=" + response.getValue());
        }

        getLogger().info("started a download by calling " + url.toString());

        // start polling for email with a download link
        long singleWaitMS = TimeUnit.SECONDS.toMillis(30);
        long maxTotalTimeMS = TimeUnit.MINUTES.toMillis(45);
        long maxTries = maxTotalTimeMS / singleWaitMS;
        for (long tryN = 0; tryN < maxTries; tryN++) {

            // not checking too often
            try {
                Thread.sleep(singleWaitMS);
            } catch (InterruptedException e) {
                throw new IOException(e);
            }

            getLogger().info("checking mail..");

            // check number of urls. +1 is good.
            Set<String> urlsAfter = urlsFromMails();
            if (urlsAfter.size() < urlsBefore.size()) {
                throw new IOException("oups. number of urls reduced");
            } else if (urlsAfter.size() == urlsBefore.size()) {
                continue;
            } else if (urlsAfter.size() > (urlsBefore.size() + 1)) {
                throw new IOException("oups. too many new urls. not able to decide what to use");
            }

            // pick out the single url to download
            Set<String> urls = new HashSet<>(urlsAfter);
            urls.removeAll(urlsBefore);
            String theUrl = urls.iterator().next();

            // actual download of the real data
            getLogger().info("downloading " + theUrl);
            HttpURLConnection theConn = (HttpURLConnection) new URL(theUrl).openConnection();
            if (theConn.getResponseCode() != 200) {
                throw new IOException("not able to download from " + theUrl);
            }
            receiver.receive("nve.zip", theConn.getInputStream());
            return;

        }

        throw new IOException("not able to download");

    }

    private Set<String> urlsFromMails() throws IOException {

        // A term to filter out the relevant mails. For IMAP only, there are more
        // efficient ways to query large mail boxes.
        Logger log = getLogger();
        SearchTerm term = new SearchTerm() {

            private static final long serialVersionUID = 1L;

            @Override
            public boolean match(Message message) {

                try {
                    if (message.getReceivedDate().before(DateUtils.addDays(new Date(), -1))) {
                        return false;
                    }

                    if (!"Kartdata fra NVEs nedlastingstjeneste er klare til nedlasting".equals(message.getSubject())) {
                        log.info("wrong subject: " + message.getSubject());
                        return false;
                    }

                    if (!toStrings(message.getFrom()).contains("nedlasting@nve.no")) {
                        log.info("wrong from: " + toStrings(message.getFrom()));
                        return false;
                    }

                    if (!toStrings(message.getRecipients(RecipientType.TO)).contains(mailAddress)) {
                        log.info("wrong to: " + toStrings(message.getRecipients(RecipientType.TO)));
                        return false;
                    }

                    return true;
                } catch (MessagingException e) {
                    return false;
                }
            }
        };

        Set<String> urls = new HashSet<>();

        URLName urlName = new URLName(mailServer);
        String folderName = "INBOX";

        Properties props = new Properties();
        props.put("mail.pop3.connectiontimeout", "60000");
        props.put("mail.imap.connectiontimeout", "60000");
        props.put("mail.pop3.timeout", "60000");
        props.put("mail.imap.timeout", "60000");
        Session session = Session.getInstance(props);

        Store store = null;
        Folder folder = null;
        try {
            store = session.getStore(urlName);
            store.connect();

            folder = store.getFolder(folderName);
            folder.open(Folder.READ_ONLY);

            Message[] messages = folder.search(term);
            for (Message message : messages) {

                Object content = message.getContent();
                if (content instanceof Multipart) {
                    Multipart mp = (Multipart) content;
                    for (int i = 0; i < mp.getCount(); i++) {
                        findDownloadUrls(mp.getBodyPart(i).getInputStream(), urls);
                    }
                } else {
                    findDownloadUrls(message.getInputStream(), urls);
                }

            }

            return Collections.unmodifiableSet(urls);

        } catch (MessagingException e) {
            throw new IOException(e);
        } finally {
            if (folder != null) {
                try {
                    folder.close(false);
                } catch (MessagingException e) {
                    throw new IOException("could not close folder", e);
                }
            }
            if (store != null) {
                try {
                    store.close();
                } catch (MessagingException e) {
                    throw new IOException("could not close store", e);
                }
            }
        }

    }

    private static Set<String> toStrings(Address[] addresses) {
        Set<String> ss = new HashSet<>(addresses.length);
        for (Address address : addresses) {
            ss.add(address.toString());
        }
        return Collections.unmodifiableSet(ss);
    }

    static void findDownloadUrls(InputStream in, Set<String> urls) throws IOException {
        findDownloadUrls(new BufferedReader(new InputStreamReader(in, "UTF-8")), urls);
    }

    static void findDownloadUrls(String content, Set<String> urls) throws IOException {
        findDownloadUrls(new BufferedReader(new StringReader(content)), urls);
    }

    static void findDownloadUrls(BufferedReader reader, Set<String> urls) throws IOException {

        StringBuilder completeBuffer = new StringBuilder();

        String line = null;
        while ((line = reader.readLine()) != null) {

            if (line.endsWith("_=")) {
                line = line.substring(0, line.length() - 2);
            }

            completeBuffer.append(line);
        }

        String complete = completeBuffer.toString();
        int p = complete.indexOf("http");
        int p2 = complete.indexOf(".zip", p);

        if (p > 0 && p2 > p) {
            String url = complete.substring(p, p2 + ".zip".length());
            urls.add(url);
        }

    }

    @Override
    public void clear() {
        datasets.clear();
    }

    private final class DatasetDefinition {

        private String name0;
        private String name1;

        public String getName0() {
            return name0;
        }

        public String getName1() {
            return name1;
        }

        @Override
        public String toString() {
            return super.toString() + "{name0: " + name0 + ", name1: " + name1 + "}";
        }

    }

    private final class Response {
        private int value;

        public int getValue() {
            return value;
        }
    }

}
