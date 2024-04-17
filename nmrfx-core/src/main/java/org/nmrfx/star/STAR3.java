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
package org.nmrfx.star;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;

public class STAR3 extends STAR3Base {

    private static final Logger log = LoggerFactory.getLogger(STAR3.class);

    public STAR3(String name) {
        super(name);
    }

    public STAR3(final String fileName, final String name) {
        super(fileName, name);
    }

    public STAR3(BufferedReader bfR, final String name) {
        super(bfR, name);
    }

    public void scanFile() throws ParseException {
        while (true) {
            String token = getNextToken();
            if (token == null) {
                break;
            } else if (token.startsWith("save_")) {
                processSaveFrame(token);
            }
        }
    }

    public void scanFile(String saveName) throws ParseException {
        while (true) {
            String token = getNextToken();
            if (token == null) {
                break;
            } else if (token.startsWith(saveName)) {
                processSaveFrame(token);
                break;
            }
        }
    }

    public void scanMMcif() throws ParseException {
        while (true) {
            String token = getNextToken();
            if (token == null) {
                break;
            } else if (token.startsWith("data_")) {
                processSaveFrame(token);
            } else {
                log.info(token);
            }
        }
    }

    public static void main(String[] argv) {
        if (argv.length != 1) {
            log.warn("usage: fileName");
        } else {
            STAR3 star3 = new STAR3(argv[0]);
            while (true) {
                String token = star3.getNextToken();
                if (token == null) {
                    break;
                } else {
                    log.warn(token);
                }
            }
        }
    }
}
