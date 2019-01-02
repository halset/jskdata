package no.jskdata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.Cookie;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import no.jskdata.data.geonorge.Area;
import no.jskdata.data.geonorge.Capabilities;
import no.jskdata.data.geonorge.File;
import no.jskdata.data.geonorge.Order;
import no.jskdata.data.geonorge.OrderLine;
import no.jskdata.data.geonorge.OrderReceipt;

/**
 * A way to access
 * https://www.geonorge.no/for-utviklere/APIer-og-grensesnitt/nedlastingsapiet/
 * from java.
 */
public class GeoNorgeDownloadAPI extends Downloader {

    private final Set<String> datasetIds = new LinkedHashSet<>();

    private final Gson gson = new Gson();

    private final String downloadUrlPrefix;
    private final String directoryUrlPrefix;
    private final String username;
    private final String password;

    public GeoNorgeDownloadAPI() {
        this.downloadUrlPrefix = "https://nedlasting.geonorge.no/";
        this.directoryUrlPrefix = "https://kartkatalog.geonorge.no/";
        this.username = null;
        this.password = null;
    }

    public GeoNorgeDownloadAPI(String username, String password) {
        this.downloadUrlPrefix = "https://nedlasting.geonorge.no/";
        this.directoryUrlPrefix = "https://kartkatalog.geonorge.no/";
        this.username = username;
        this.password = password;
    }

    public GeoNorgeDownloadAPI(String downloadUrlPrefix, String directoryUrlPrefix, String username, String password) {
        this.downloadUrlPrefix = downloadUrlPrefix;
        this.directoryUrlPrefix = directoryUrlPrefix;
        this.username = username;
        this.password = password;
    }

    @Override
    public void dataset(String datasetId) {
        datasetIds.add(datasetId);
    }

    private DatasetInfo datasetInfo(String datasetId) throws IOException {

        String capabilitiesUrl = downloadUrlPrefix + "api/capabilities/" + datasetId;
        Capabilities capabilities = fetchAndParse(capabilitiesUrl, Capabilities.class);
        if (capabilities == null) {
            throw new IllegalArgumentException("Invalid dataset: " + datasetId);
        }

        String orderUrl = capabilities.getOrderUrl();

        @SuppressWarnings("serial")
        List<Area> areas = fetchAndParse(capabilities.getAreaUrl(), new TypeToken<ArrayList<Area>>() {
        }.getType());
        if (areas == null || areas.isEmpty()) {
            throw new IllegalArgumentException("Dataset does not have any areas: " + datasetId);
        }

        DatasetInfo datasetInfo = new DatasetInfo();
        datasetInfo.orderUrl = orderUrl;
        datasetInfo.areas = areas;

        return datasetInfo;
    }

    @Override
    public void download(Receiver receiver) throws IOException {

        Map<String, Order> orderByOrderUrl = new HashMap<>();

        for (String datasetId : datasetIds) {
            DatasetInfo info = datasetInfo(datasetId);

            Order order = orderByOrderUrl.get(info.orderUrl);
            if (order == null) {
                order = new Order();
                orderByOrderUrl.put(info.orderUrl, order);
            }

            for (Area area : info.areasForCountry(formatNameFilter)) {
                OrderLine orderLine = order.getOrCreateOrderLine(datasetId, area.getProjection(),
                        area.formats(formatNameFilter));
                orderLine.addArea(area.asOrderArea());
            }

        }

        if (orderByOrderUrl.isEmpty()) {
            return;
        }

        // SAML authentication.
        Map<String, String> cookies = new HashMap<>();
        if (username != null && password != null) {
            String u = directoryUrlPrefix + "AuthServices/SignIn?ReturnUrl=" + ue(directoryUrlPrefix + "search");
            WebClient client = null;
            try {
                client = new WebClient();
                client.getOptions().setCssEnabled(false);

                HtmlPage page = client.getPage(u);
                HtmlForm form = page.getForms().get(0);
                form.getInputByName("username").setValueAttribute(username);
                form.getInputByName("password").setValueAttribute(password);
                page = page.getElementById("regularsubmit").click();

                for (String metadataUUID : datasetIds) {
                    // an extra request is needed..
                    page = client.getPage(directoryUrlPrefix + "AuthServices/SignIn?ReturnUrl="
                            + ue(downloadUrlPrefix + "AuthServices/SignIn?ReturnUrl=" + ue(directoryUrlPrefix
                                    + "search?text=" + metadataUUID + "&addtocart_event_id=" + metadataUUID)));
                }

                for (Cookie cookie : client.getCookieManager().getCookies()) {
                    cookies.put(cookie.getName(), cookie.getValue());
                }
            } finally {
                if (client != null) {
                    client.close();
                }
            }
        }

        // SAML cookies only
        cookies.keySet().retainAll(Arrays.asList("FedAuth", "FedAuth1"));
        StringBuilder cookiesValue = new StringBuilder();
        if (!cookies.isEmpty()) {
            for (Iterator<Map.Entry<String, String>> it = cookies.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, String> cookie = it.next();
                cookiesValue.append(cookie.getKey()).append('=').append(cookie.getValue());
                if (it.hasNext()) {
                    cookiesValue.append("; ");
                }
            }
        }

        for (Map.Entry<String, Order> e : orderByOrderUrl.entrySet()) {
            String orderUrl = e.getKey();
            Order order = e.getValue();

            orderUrl = downloadUrlPrefix + "api/v2/order";

            InputStream orderIn = openConnectionWithRetry(orderUrl, cookiesValue.toString(), gson.toJson(order));
            Reader reader = new InputStreamReader(orderIn);
            OrderReceipt reciept = gson.fromJson(reader, OrderReceipt.class);

            for (File file : reciept.getFiles()) {
                if (!fileNameFilter.test(file.name)) {
                    continue;
                }

                currentDownloadUrl = file.downloadUrl;

                InputStream in = openConnectionWithRetry(currentDownloadUrl, cookiesValue.toString(), null);
                receiver.receive(file.name, in);

                currentDownloadUrl = null;
            }
        }

    }

    private InputStream openConnectionWithRetry(String url, String cookiesValue, String postData) throws IOException {
        int retryNumber = 0;
        while (true) {
            retryNumber++;
            long t = System.currentTimeMillis();
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();

                if (postData != null) {
                    conn.setRequestMethod("POST");
                }

                conn.setInstanceFollowRedirects(false);

                if (cookiesValue != null && cookiesValue.length() > 0) {
                    conn.setRequestProperty("Cookie", cookiesValue);
                }

                if (postData != null) {
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoInput(true);
                    conn.setDoOutput(true);
                    OutputStream out = conn.getOutputStream();
                    out.write(postData.getBytes("UTF-8"));
                    out.flush();
                }

                int code = conn.getResponseCode();
                if (code >= 300 && code <= 399) {
                    String location = conn.getHeaderField("Location");
                    if (location != null) {
                        url = location;
                        continue;
                    }
                }

                if (conn.getResponseCode() >= 401) {
                    getLogger().info("response message: " + conn.getResponseMessage() + " from " + url);
                    getLogger().info(conn.getHeaderFields().toString());
                    InputStream err = conn.getErrorStream();
                    if (err != null) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buf = new byte[1024];
                        int len = 0;
                        while ((len = err.read(buf)) >= 0) {
                            baos.write(buf, 0, len);
                        }
                        getLogger().info("err: " + new String(baos.toByteArray(), "UTF-8"));
                    }
                }

                InputStream in = conn.getInputStream();
                return in;
            } catch (IOException retryException) {

                t = System.currentTimeMillis() - t;

                StringBuilder msg = new StringBuilder();
                msg.append(retryException.getMessage());
                msg.append(". after ").append(t).append("ms. ");

                if (postData != null) {
                    msg.append(". with POST data: ");
                    msg.append(postData);
                }
                if (cookiesValue != null && cookiesValue.length() > 0) {
                    msg.append(". with cookie value: ");
                    msg.append(cookiesValue);
                }
                msg.append(". ").append(url);

                if (retryNumber > 20) {
                    msg.append("tried ");
                    msg.append(retryNumber);
                    msg.append(" times. ");
                    msg.append(". giving up.");
                    getLogger().info(msg.toString());
                    throw retryException;
                }

                long sleepTimeMS = 100 + (retryNumber * 2000);
                msg.append(". will retry, but first sleep ").append(sleepTimeMS).append("ms");
                getLogger().info(msg.toString());

                try {
                    Thread.sleep(sleepTimeMS);
                } catch (InterruptedException e) {
                    getLogger().info("got interrypted sleeping a retry");
                }
                continue;
            }
        }
    }

    private static String ue(String raw) throws IOException {
        return URLEncoder.encode(raw, "UTF-8");
    }

    private <T> T fetchAndParse(String url, Class<T> type) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        if (conn.getResponseCode() == 404) {
            return null;
        }
        Reader reader = new InputStreamReader(conn.getInputStream());
        return gson.fromJson(reader, type);
    }

    private <T> T fetchAndParse(String url, Type type) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        if (conn.getResponseCode() == 404) {
            return null;
        }

        Reader reader = new InputStreamReader(conn.getInputStream());
        return gson.fromJson(reader, type);
    }

    @Override
    public void clear() {
        datasetIds.clear();
    }

    private static class DatasetInfo {

        String orderUrl;
        List<Area> areas;

        public List<Area> areasForCountry(Predicate<String> formatNameFilter) {
            if (areas == null) {
                return Collections.emptyList();
            }

            // look for country wide
            for (Area area : areas) {
                if (area.isCountryWide() && area.hasFormat(formatNameFilter)) {
                    return Collections.singletonList(area);
                }
            }

            List<Area> selectedAreas = new ArrayList<>();

            for (Area area : areas) {
                if (area.isCounty() && area.hasFormat(formatNameFilter)) {
                    selectedAreas.add(area);
                }
            }
            if (!selectedAreas.isEmpty()) {
                return selectedAreas;
            }

            for (Area area : areas) {
                if (area.hasFormat(formatNameFilter)) {
                    selectedAreas.add(area);
                }
            }
            return selectedAreas;
        }

    }

}
