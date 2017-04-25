/*
 * NMRFx Structure : A Program for Calculating Structures 
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.nmrfx.structure.lambda;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.util.StringInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


import java.io.UnsupportedEncodingException;
import java.util.List;

public class LambdaIO {

    private static String bucketName = "structures";
    private static String key = "1ema.pdb";
    static AmazonS3 s3Client = null;

    public static void main(String[] args) throws IOException {
        getClient();
        listBuckets(s3Client);
        listObjects(s3Client, bucketName);
        try {

            System.out.println("Downloading an object");
            S3Object s3object = s3Client.getObject(new GetObjectRequest(
                    bucketName, key));
            System.out.println("Content-Type: "
                    + s3object.getObjectMetadata().getContentType());
            displayTextInputStream(s3object.getObjectContent());

            // Get a range of bytes from an object.
            GetObjectRequest rangeObjectRequest = new GetObjectRequest(
                    bucketName, key);
            rangeObjectRequest.setRange(0, 10);
            S3Object objectPortion = s3Client.getObject(rangeObjectRequest);

            System.out.println("Printing bytes retrieved.");
            displayTextInputStream(objectPortion.getObjectContent());

        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which"
                    + " means your request made it "
                    + "to Amazon S3, but was rejected with an error response"
                    + " for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means"
                    + " the client encountered "
                    + "an internal error while trying to "
                    + "communicate with S3, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
    }

    private static void listBuckets(AmazonS3 s3Client) {
        List<Bucket> buckets = s3Client.listBuckets();
        System.out.println("Your Amazon S3 buckets:");
        for (Bucket b : buckets) {
            System.out.println("* " + b.getName());
        }
    }

    private static void listObjects(AmazonS3 s3Client, String bucketName) {
        List<Bucket> buckets = s3Client.listBuckets();
        System.out.println("Your Amazon S3 objects:");
        ObjectListing ol = s3Client.listObjects(bucketName);
        List<S3ObjectSummary> objects = ol.getObjectSummaries();
        for (S3ObjectSummary os : objects) {
            System.out.println("* " + os.getKey());
        }
    }

    private static void displayTextInputStream(InputStream input)
            throws IOException {
        // Read one text line at a time and display.
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }

            System.out.println("    " + line);
        }
        System.out.println();
    }

    public static void putObject(String bucketName, String key, String data) throws UnsupportedEncodingException {
        InputStream input = new StringInputStream(data);
        putObject(bucketName, key, input, data.length());
    }

    public static void putObject(String bucketName, String key, InputStream input, int nChars) {
        getClient();
        ObjectMetadata objData = new ObjectMetadata();
        if (nChars != 0) {
            objData.setContentLength(nChars);
        }
        PutObjectRequest putObjectReq = new PutObjectRequest(bucketName, key, input, objData);
        s3Client.putObject(putObjectReq);
    }

    public static void listObjects(String bucketName) {
        getClient();
        listBuckets(s3Client);
        listObjects(s3Client, bucketName);
    }

    private static AmazonS3 getClient() {
        AWSCredentialsProvider creds = new AWSCredentialsProviderChain(
                new ProfileCredentialsProvider(),
                new InstanceProfileCredentialsProvider(),
                new EnvironmentVariableCredentialsProvider(),
                new SystemPropertiesCredentialsProvider()
        );

        //provider = new ProfileCredentialsProvider();
        if (s3Client == null) {
            s3Client = new AmazonS3Client(creds);
        }
        return s3Client;
    }

    public static InputStream getInputStream(String bucketName, String key) {
        getClient();
        try {

            System.out.println("Downloading an object");
            S3Object s3object = s3Client.getObject(new GetObjectRequest(
                    bucketName, key));
            System.out.println("Content-Type: "
                    + s3object.getObjectMetadata().getContentType());
            return s3object.getObjectContent();

        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which"
                    + " means your request made it "
                    + "to Amazon S3, but was rejected with an error response"
                    + " for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means"
                    + " the client encountered "
                    + "an internal error while trying to "
                    + "communicate with S3, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
        return null;
    }

}
