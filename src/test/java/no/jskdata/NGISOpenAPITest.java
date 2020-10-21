package no.jskdata;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class NGISOpenAPITest extends DownloaderTestCase {

    public void testDownload() throws IOException {

        String username = getRequiredProperty(USERNAME_KEY);
        String password = getRequiredProperty(PASSWORD_KEY);

        NGISOpenAPI d = new NGISOpenAPI("https://openapi-test.kartverket.no/v1/", username, password);
        d.dataset("61f41e79-923c-4ac7-b27b-2ced5d31631f");
        d.download((fileName, in) -> {
            File file = new File("target/ngis-openapi-out-" + fileName);
            try {
                Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

}
