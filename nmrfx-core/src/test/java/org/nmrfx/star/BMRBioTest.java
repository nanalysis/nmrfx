package org.nmrfx.star;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.FileBody;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.entity.mime.StringBody;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpResponse;
import org.junit.Test;
import org.nmrfx.chemistry.io.NMRStarReader;
import org.nmrfx.chemistry.io.NMRStarWriter;

import java.io.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class BMRBioTest {
    @Test
    public void depositEntry() throws ParseException, IOException {
        String email = "ekoag@gc.cuny.edu";
        String projectName = "nmrfx test";
        String filePath = "/Users/ekoag/Downloads/test_ubiq.str";
        NMRStarReader.read(filePath);
        StringWriter starString = NMRStarWriter.writeToString(null);

        File tmpFile = File.createTempFile("star",".str");
        FileWriter fileWriter = new FileWriter(tmpFile, true);
        BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
        bufferedWriter.write(starString.toString());
        bufferedWriter.close();

        String dev_url = "https://dev-deposit.bmrb.io/deposition/new";
        HttpPost httpPost = new HttpPost(dev_url);

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
        //BasicHttpClientResponseHandler responseHandler = new BasicHttpClientResponseHandler();
        CompletableFuture<CloseableHttpResponse> future = CompletableFuture.supplyAsync(() -> {
            try {
                return HttpClients.createDefault().execute(httpPost);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        future.thenAccept(r -> System.out.println(r.toString()));
//        HttpResponse result = HttpClients.createDefault().execute(httpPost);
//        result.getCode();
        tmpFile.deleteOnExit();
    }

}