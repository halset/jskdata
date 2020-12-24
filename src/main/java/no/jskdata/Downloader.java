package no.jskdata;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.logging.Logger;

public abstract class Downloader {

    protected Predicate<String> fileNameFilter = (String) -> true;
    
    protected Predicate<String> formatNameFilter = (String) -> true;
    
    protected Predicate<String> projectionNameFilter = (String) -> true;
    
    protected String currentDownloadUrl;

    private Logger log;

    protected Logger getLogger() {
        if (log == null) {
            log = Logger.getLogger(getClass().getName());
        }
        return log;
    }

    public void setFileNameFilter(Predicate<String> fileNameFilter) {
        this.fileNameFilter = fileNameFilter;
    }
    
    public void setFormatNameFilter(Predicate<String> formatNameFilter) {
        this.formatNameFilter = formatNameFilter;
    }
    
    public void setProjectionNameFilter(Predicate<String> projectionNameFilter) {
        this.projectionNameFilter = projectionNameFilter;
    }

    public abstract void dataset(String dataset);

    public abstract void download(Receiver receiver) throws IOException;

    public void download(BiConsumer<String, InputStream> receiver) throws IOException {
        download(new Receiver() {

            @Override
            public boolean shouldStop() {
                return false;
            }

            @Override
            public void receive(String fileName, InputStream in) throws IOException {
                receiver.accept(fileName, in);
            }

        });
    }

    public abstract void clear();

    public static Downloader create(Map<String, String> options) {
        String type = options.get("type");
        if (type == null) {
            return new NoDownloader();
        }
        
        String username = options.get("username");
        String password = options.get("password");
        boolean hasUserAndPass = username != null && password != null;
        
        String url = options.get("url");

        Downloader dl = new NoDownloader();
        if ("url".equals(type)) {
            dl = new URLDownloader();
        } else if (hasUserAndPass && type.equals("GeoNorgeSkdl2")) {
            dl = new GeoNorgeSkdl2(username, password);
        } else if (type.equals("GeoNorgeDownloadAPI")) {
            dl = new GeoNorgeDownloadAPI(username, password);
        } else if (type.equals("hoydedata.no")) {
            dl = new Hoydedata();
        } else if (type.equals("AvinorAIP")) {
            dl = new AvinorAIPDownloader();
        } else if (type.equals("WFS") && url != null) {
            dl = new WFS2Downloader(url);
        }

        String dataset = options.get("dataset");
        if (dataset != null) {
            dl.dataset(dataset);
        }
        
        String fileNameSuffix = options.get("fileNameSuffix");
        if (fileNameSuffix != null) {
            dl.setFileNameFilter(n -> n.endsWith(fileNameSuffix));
        }

        String formatName = options.get("formatName");
        if (formatName != null) {
            dl.setFormatNameFilter(n -> n.contains(formatName));
        }
        
        String projectionName = options.get("projectionName");
        if (projectionName != null) {
            dl.setProjectionNameFilter(n -> n.contains(projectionName));
        }

        return dl;
    }
    
    /**
     * The current download url used by {@link #download(Receiver)} and
     * {@link #download(BiConsumer)}. Can be useful for debugging.
     * 
     * @return
     */
    public String getCurrentDownloadUrl() {
        return currentDownloadUrl;
    }

}
