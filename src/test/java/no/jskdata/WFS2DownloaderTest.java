package no.jskdata;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.google.common.io.ByteStreams;

public class WFS2DownloaderTest extends DownloaderTestCase {

    public void testWFS() throws IOException {
        WFS2Downloader d = new WFS2Downloader("http://wfs.geonorge.no/skwms1/wfs.stedsnavn50");
        d.dataset("app:Sted");
        List<String> fileNames = new ArrayList<>();
        d.download(new Receiver() {

            @Override
            public boolean shouldStop() {
                return fileNames.size() >= 2;
            }

            @Override
            public void receive(String fileName, InputStream in) throws IOException {
                fileNames.add(fileName);
                System.out.println(fileName);
                ByteStreams.exhaust(in);
            }
        });
        assertEquals(2, fileNames.size());
    }

}
