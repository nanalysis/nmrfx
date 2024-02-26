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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class WebConnect {
    private static final Logger log = LoggerFactory.getLogger(WebConnect.class);
    private static final String VERSION_FILE_URL = "https://nmrfx.org/downloads/version/version.txt";

    public static String getVersion() {
        try {
            return new WebConnect().fetchContent(VERSION_FILE_URL);
        } catch (Exception ex) {
            log.warn("Unable to fetch version file", ex);
            return "";
        }
    }

    public String fetchContent(String urlStr) {
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
                    log.warn(ioe.getMessage(), ioe);
                }
            }
        }
        return result;
    }
}
