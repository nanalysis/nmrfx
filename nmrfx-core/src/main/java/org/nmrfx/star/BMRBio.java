package org.nmrfx.star;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.FileBody;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.entity.mime.StringBody;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class BMRBio {

    private BMRBio() {

    }

    public static CompletableFuture<HttpResponse<String>> fetchEntryASync(int entryID) throws URISyntaxException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("https://api.bmrb.io/v2/entry/" + entryID + "?format=rawnmrstar"))
                .setHeader("Application", "NMRFx")
                .GET()
                .build();
        HttpClient client = HttpClient.newHttpClient();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    public static CompletableFuture<String> depositEntry(boolean productionMode, String email, String projectName, StringWriter starString) throws IOException {
        File tmpFile = File.createTempFile("star", ".str");
        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(tmpFile, true))) {
            bufferedWriter.write(starString.toString());
        }

        String url = productionMode ? "https://deposit.bmrb.io/deposition/new" : "https://dev-deposit.bmrb.io/deposition/new";
        HttpPost httpPost = new HttpPost(url);

        HttpEntity httpEntity = MultipartEntityBuilder.create()
                .addPart("email",
                        new StringBody(email,
                                ContentType.APPLICATION_FORM_URLENCODED))
                .addPart("deposition_nickname",
                        new StringBody(projectName,
                                ContentType.APPLICATION_FORM_URLENCODED))
                .addPart("deposition_type",
                        new StringBody("macromolecule",
                                ContentType.APPLICATION_FORM_URLENCODED))
                .addPart("nmrstar_file", new FileBody(tmpFile))
                .build();

        httpPost.setEntity(httpEntity);

        BasicHttpClientResponseHandler responseHandler = new BasicHttpClientResponseHandler();

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                return HttpClients.createDefault().execute(httpPost, responseHandler);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        tmpFile.deleteOnExit();

        return future;
    }

    public static CompletableFuture<HttpResponse<String>> fetchSearchResults(String searchString) throws URISyntaxException {
        searchString = searchString.replace(" ", "+");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("https://api.bmrb.io/v2/instant?term=" + searchString))
                .setHeader("Application", "NMRFx")
                .GET()
                .build();
        HttpClient client = HttpClient.newHttpClient();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }
}
