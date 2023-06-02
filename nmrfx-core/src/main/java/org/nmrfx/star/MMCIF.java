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

import org.nmrfx.star.Saveframe.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;

public class MMCIF extends STAR3Base {

    private static final Logger log = LoggerFactory.getLogger(MMCIF.class);

    public MMCIF(String name) {
        super(name);
    }

    public MMCIF(final String fileName, final String name) {
        super(fileName, name);
    }

    public MMCIF(BufferedReader bfR, final String name) {
        super(bfR, name);
    }

    public void scanFile() throws ParseException {
        while (true) {
            String token = getNextToken();
            if (token == null) {
                break;
            } else if (token.startsWith("data_")) {
                processSaveFrame(token);
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

    public void dump(String fileName) throws ParseException, IOException {
        try (FileWriter fileWriter = new FileWriter(fileName)) {
            for (Saveframe saveFrame : getSaveFrames().values()) {
                String name = saveFrame.name;
                for (String catName : getCategories(name)) {
                    Category category = saveFrame.getCategory(catName);
                    if (category.isLoop()) {
                        Loop loop = saveFrame.getLoop(catName);
                        STAR3Base.writeLoopStrings(fileWriter, catName, loop.tags);
                        int nRows = loop.getNRows();
                        for (int i = 0; i < nRows; i++) {
                            String[] values = loop.getRowValues(i);
                            StringBuilder sBuilder = new StringBuilder();
                            for (String value : values) {
                                sBuilder.append(" ");
                                sBuilder.append(STAR3Base.quote(value));
                            }
                            fileWriter.write(sBuilder.toString());
                            fileWriter.write("\n");
                        }
                        fileWriter.write("#\n");

                    } else {
                        StringBuilder sBuilder = new StringBuilder();
                        for (String tag : category.tagMap.keySet()) {
                            sBuilder.setLength(0);
                            sBuilder.append(catName).append(".").
                                    append(tag).append(" ");
                            fileWriter.write(sBuilder.toString());
                            STAR3Base.writeString(fileWriter, category.get(tag), 128);
                        }
                        fileWriter.write("#\n");

                    }

                }
            }
        }
    }

    public static void main(String[] argv) {
        if (argv.length != 1) {
            log.warn("usage: fileName");
        } else {
            MMCIF star3 = new MMCIF(argv[0]);
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
