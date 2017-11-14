/*
 * NMRFx Processor : A Program for Processing NMR Data
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
package org.nmrfx.processor.utilities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class WebConnect {

    static String webAddress = "https://nmrfx.org/downloads/processor/version.txt";

    public static String getVersion() {
        WebConnect webConn = new WebConnect();
        String version;
        try {
            version = webConn.fetchContent(webAddress);
        } catch (Exception ex) {
            version = "";
        }
        return version;
    }

    public WebConnect() {
    }

    private String fetchContent(String urlStr)
            throws Exception {
        URL url;
        BufferedReader bufferedReader = null;
        StringBuilder stringBuilder;
        String result;

        try {
            url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");
            conn.setReadTimeout(5000);
            conn.connect();

            bufferedReader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            stringBuilder = new StringBuilder();

            String inputLine;
            while ((inputLine = bufferedReader.readLine()) != null) {
                stringBuilder.append(inputLine).append("\n");
            }
            result = stringBuilder.toString().trim();
        } catch (IOException e) {
            result = "";
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException ioe) {

                }
            }
        }
        return result;
    }
}
