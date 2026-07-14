package org.nmrfx.analyst.models;

import org.nmrfx.utilities.UnZipper;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

public class ModelFetcher {

    private ModelFetcher() {

    }

    public static void fetch(Path outputDirectory, String modelZipName) throws IOException {
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()) {
            File outputZipFile = outputDirectory.resolve(modelZipName + ".zip").toFile();

            URI uri = URI.create("https://nmrfx.org/downloads/models/" + modelZipName + ".zip");
            HttpRequest request = HttpRequest.newBuilder().uri(uri).build();

            // use the client to send the asynchronous request
            try (InputStream is = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .thenApply(HttpResponse::body).join()) {

                try (FileOutputStream out = new FileOutputStream(outputZipFile)) {
                    is.transferTo(out);
                    UnZipper unZipper = new UnZipper(outputDirectory.toFile(), outputZipFile);
                    unZipper.unzip();
                }
            }
        }
    }

}