package org.nmrfx.star;

import org.nmrfx.chemistry.io.NMRStarReader;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class BMRBFetch {

    private BMRBFetch() {

    }

    public static CompletableFuture<HttpResponse<String>> fetchEntryASync(int entryID) throws URISyntaxException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://api.bmrb.io/v2/entry/" + entryID + "?format=rawnmrstar"))
                .setHeader("Application", "NMRFx")
                .GET()
                .build();
        HttpClient client = HttpClient.newHttpClient();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    public static String fetchEntrySync(int entryID) throws IOException, InterruptedException, URISyntaxException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://api.bmrb.io/v2/entry/" + entryID + "?format=rawnmrstar"))
                .GET()
                .build();
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    public static CompletableFuture<HttpResponse<String>> postEntry(String body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://dev-deposit.bmrb.io/deposition/new")) //dev url
                .setHeader("Application", "NMRFx")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpClient client = HttpClient.newHttpClient();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    public static CompletableFuture<HttpResponse<String>> fetchSearch(String searchString) throws URISyntaxException{
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://api.bmrb.io/v2/instant/" + searchString))
                .setHeader("Application", "NMRFx")
                .GET()
                .build();
        HttpClient client = HttpClient.newHttpClient();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }
}
