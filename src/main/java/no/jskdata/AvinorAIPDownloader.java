package no.jskdata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.common.io.ByteStreams;
import com.google.gson.Gson;

import no.jskdata.geojson.Feature;
import no.jskdata.geojson.FeatureCollection;
import no.jskdata.geojson.Point;

public class AvinorAIPDownloader extends Downloader {

    private Integer maxCount = null;

    void setMaxCount(Integer maxCount) {
        this.maxCount = maxCount;
    }

    @Override
    public void dataset(String dataset) {
    }

    @Override
    public void download(Receiver receiver) throws IOException {

        // find latest AIP version number by navigating..
        String u = "https://ais.avinor.no/no/AIP/";
        Connection.Response r = Jsoup.connect(u).execute();
        Document d = r.parse();
        Elements elements = d.select("a.frontpage_language_link[href=main_en.html]");
        u = elements.get(0).absUrl("href");
        r = Jsoup.connect(u).execute();
        d = r.parse();

        // find all ICAOs
        FeatureCollection features = new FeatureCollection();
        for (Element element : d.select("option[value]")) {
            u = element.absUrl("value");

            String icao = element.text().substring(0, 4);
            Feature feature = new Feature();
            feature.setProperty("name", element.text());
            feature.setProperty("icao", icao);

            // find PDFs for ICAO
            List<Attachment> attachments = new ArrayList<>();
            r = Jsoup.connect(u).execute();
            d = r.parse();
            for (Element pdfElement : d.select("a[href]")) {
                String href = pdfElement.attr("href");
                // https://ais.avinor.no/no/AIP/View/25/aip/ad/enno/enno_en.html
                // had a backslash to one of the PDFs
                href = href.replace('\\', '/');
                String url = StringUtil.resolve(pdfElement.baseUri(), href);
                // did not get select on href=.pdf to work..
                if (!url.toLowerCase().endsWith(".pdf")) {
                    continue;
                }
                if (!url.toLowerCase().contains(icao.toLowerCase())) {
                    continue;
                }
                attachments.add(new Attachment(pdfElement.text().trim(), url));
            }
            feature.setProperty("attachments", attachments);

            // parse first pdf and look for coordinate
            if (!attachments.isEmpty()) {
                String mainPdfUrl = attachments.get(0).getUrl();
                HttpURLConnection conn = (HttpURLConnection) new URL(mainPdfUrl).openConnection();
                if (conn.getResponseCode() != 200) {
                    throw new IOException("could not get " + mainPdfUrl);
                }
                byte[] data = ByteStreams.toByteArray(conn.getInputStream());
                PDDocument document = PDDocument.load(data);
                PDFTextStripper pdfTextStripper = new PDFTextStripper();
                String text = pdfTextStripper.getText(document);

                // search for, extract and parse coordinate
                Matcher matcher = matcher(text);
                if (matcher.find()) {
                    double y = parseCoordinatePart(matcher.group(1), 2);
                    double x = parseCoordinatePart(matcher.group(2), 3);
                    feature.setGeometry(new Point(x, y));
                }

                document.close();
            }

            features.add(feature);

            if (maxCount != null && maxCount.intValue() <= features.size()) {
                break;
            }
        }

        byte[] data = new Gson().toJson(features).getBytes("UTF-8");
        receiver.receive("avinor_aip.geojson", new ByteArrayInputStream(data));
    }
    
    static Matcher matcher(String text) {
        Pattern pattern = Pattern.compile("([0-9.]{6,9}[N]{1}) ([0-9.]{7,10}[E]{1})");
        return pattern.matcher(text);
    }

    static double parseCoordinatePart(String encoded, int degreeDigits) {
        if (!((encoded.length() == (degreeDigits + 5)) || (encoded.length() == (degreeDigits + 8)))) {
            throw new IllegalArgumentException("wrong coordinate part: " + encoded);
        }

        double v = Double.parseDouble(encoded.substring(0, degreeDigits));
        double minutes = Double.parseDouble(encoded.substring(degreeDigits, degreeDigits + 2));
        double seconds = Double.parseDouble(encoded.substring(degreeDigits + 2, encoded.length() - 1));
        minutes = minutes + (seconds / 60.0);
        v = v + (minutes / 60.0);
        return v;
    }

    @Override
    public void clear() {
    }

    private static class Attachment {

        private String name;
        private String url;

        public Attachment(String name, String url) {
            this.name = name;
            this.url = url;
        }

        @SuppressWarnings("unused")
        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }

    }

}
