package no.jskdata;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.io.ByteStreams;

public class GeoNorgeDownloadAPITest extends DownloaderTestCase {

    public void testNRLSOSI() throws IOException {
        Downloader downloader = new GeoNorgeDownloadAPI();
        downloader.setFormatNameFilter(n -> n.contains("SOSI"));
        downloader.dataset("28c896d0-8a0d-4209-bf31-4931033b1082");
        Set<String> fileNames = new HashSet<>();
        Map<String, Long> lengthByFileName = new HashMap<>();
        downloader.download(new DefaultReceiver() {

            @Override
            public void receive(String fileName, InputStream in) throws IOException {
                fileNames.add(fileName);
                long l = ByteStreams.exhaust(in);
                lengthByFileName.put(fileName, Long.valueOf(l));
            }
        });
        assertEquals(1, fileNames.size());
        Long length = lengthByFileName.get(fileNames.iterator().next());
        assertNotNull(length);
        assertTrue(length.longValue() > 0l);
        assertTrue(length.longValue() > (1024l * 1024l));
    }
    
    public void testNRLGML() throws IOException {
        Downloader downloader = new GeoNorgeDownloadAPI();
        downloader.setFormatNameFilter(n -> n.contains("GML"));
        downloader.setProjectionNameFilter(n -> n.contains("3035"));
        downloader.dataset("28c896d0-8a0d-4209-bf31-4931033b1082");
        Set<String> fileNames = new HashSet<>();
        Map<String, Long> lengthByFileName = new HashMap<>();
        downloader.download(new DefaultReceiver() {

            @Override
            public void receive(String fileName, InputStream in) throws IOException {
                fileNames.add(fileName);
                long l = ByteStreams.exhaust(in);
                lengthByFileName.put(fileName, Long.valueOf(l));
            }
        });
        assertEquals(1, fileNames.size());
        assertTrue(fileNames.iterator().next().contains("3035"));
        Long length = lengthByFileName.get(fileNames.iterator().next());
        assertNotNull(length);
        assertTrue(length.longValue() > 0l);
        assertTrue(length.longValue() > (1024l * 1024l));
    }

    public void testFKBLedning() throws IOException {
        
        String username = getProperty(USERNAME_KEY);
        String password = getProperty(PASSWORD_KEY);
        
        Downloader downloader = new GeoNorgeDownloadAPI(username, password);
        downloader.setFormatNameFilter(n -> n.contains("SOSI"));
        downloader.setFileNameFilter(n -> n.contains("Alvdal"));
        downloader.dataset("6e05aefb-f90e-4c7d-9fb9-299574d0bbf6");
        Set<String> fileNames = new HashSet<>();
        Map<String, Long> lengthByFileName = new HashMap<>();
        downloader.download(new DefaultReceiver() {

            @Override
            public void receive(String fileName, InputStream in) throws IOException {
                fileNames.add(fileName);
                long l = ByteStreams.exhaust(in);
                lengthByFileName.put(fileName, Long.valueOf(l));
            }
        });
        assertEquals(1, fileNames.size());
        Long length = lengthByFileName.get(fileNames.iterator().next());
        assertNotNull(length);
        assertTrue(length.longValue() > 0l);
        assertTrue(length.longValue() > (1024l * 32));
        assertTrue(fileNames.iterator().next().contains("SOSI"));
    }
    
}
