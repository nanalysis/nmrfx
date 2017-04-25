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

//import com.amazonaws.services.lambda.runtime.RequestHandler;
//import com.amazonaws.services.lambda.runtime.Context;
//import com.amazonaws.services.lambda.runtime.RequestHandler;
//import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.event.S3EventNotification.S3EventNotificationRecord;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.event.S3EventNotification;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.python.util.PythonInterpreter;

/**
 *
 * @author Bruce Johnson
 */
public class AnnealLambda {

    public void handlerTest(String s) {
        System.out.println("ran lambda " + s);
    }

    public void handler(String key) {
        System.setProperty("python.cachedir.skip", "true");
        System.setProperty("python.import.site", "false");
        String bucketName = "nmrfx-scripts";
        System.out.println("key is " + key);
        InputStream input = LambdaIO.getInputStream(bucketName, key);
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        StringBuilder sBuilder = new StringBuilder();
        try {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                sBuilder.append(line);
                sBuilder.append('\n');

            }
        } catch (IOException ioE) {
            System.err.println(ioE.getMessage());
            return;
        }
        String script = sBuilder.toString();
        System.out.println(script);
        PythonInterpreter interp = new PythonInterpreter();
        interp.exec(script);

    }

    public String handleRequest(S3EventNotification sEventNot) {
        try {
            System.out.println("handleRequest");
            S3EventNotificationRecord record = sEventNot.getRecords().get(0);
            String srcBucket = record.getS3().getBucket().getName();
            // Object key may have spaces or unicode non-ASCII characters.
            String srcKey = record.getS3().getObject().getKey()
                    .replace('+', ' ');
            srcKey = URLDecoder.decode(srcKey, "UTF-8");
            System.out.println("bucket and key " + srcBucket + " " + srcKey);
            handler(srcKey);

//            String dstBucket = srcBucket + "resized";
//            String dstKey = "resized-" + srcKey;
//
//            // Sanity check: validate that source and destination are different
//            // buckets.
//            if (srcBucket.equals(dstBucket)) {
//                System.out
//                        .println("Destination bucket must not match source bucket.");
//                return "";
//            }
//
//            // Infer the image type.
//            Matcher matcher = Pattern.compile(".*\\.([^\\.]*)").matcher(srcKey);
//            if (!matcher.matches()) {
//                System.out.println("Unable to infer image type for key "
//                        + srcKey);
//                return "";
//            }
//
//            // Download the image from S3 into a stream
//            AmazonS3 s3Client = new AmazonS3Client();
//            S3Object s3Object = s3Client.getObject(new GetObjectRequest(
//                    srcBucket, srcKey));
//            InputStream objectData = s3Object.getObjectContent();
//
//            ObjectMetadata meta = new ObjectMetadata();
//
//            // Uploading to S3 destination bucket
//            System.out.println("Writing to: " + dstBucket + "/" + dstKey);
////            s3Client.putObject(dstBucket, dstKey, is, meta);
//            System.out.println("Successfully resized " + srcBucket + "/"
//                    + srcKey + " and uploaded to " + dstBucket + "/" + dstKey);
            return "Ok";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
