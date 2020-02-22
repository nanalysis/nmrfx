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
package org.nmrfx.structure.chemistry.io;

import org.nmrfx.processor.star.ParseException;
import java.io.*;
import java.util.*;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.star.MMCIF;

public class MMcifReader {

    final MMCIF mmcif;
    final File starFile;

    Map entities = new HashMap();
    boolean hasResonances = false;
    Map<Long, List<PeakDim>> resMap = new HashMap<>();

    public MMcifReader(final File starFile, final MMCIF star3) {
        this.mmcif = star3;
        this.starFile = starFile;
//        PeakDim.setResonanceFactory(new AtomResonanceFactory());
    }

    public static void read(String starFileName) throws ParseException {
        File file = new File(starFileName);
        read(file);
    }

    public static void read(File starFile) throws ParseException {
        FileReader fileReader;
        try {
            fileReader = new FileReader(starFile);
        } catch (FileNotFoundException ex) {
            return;
        }
        BufferedReader bfR = new BufferedReader(fileReader);

        MMCIF star = new MMCIF(bfR, "mmcif");

        try {
            star.scanFile();
        } catch (ParseException parseEx) {
            throw new ParseException(parseEx.getMessage() + " " + star.getLastLine());
        }
        MMcifReader reader = new MMcifReader(starFile, star);
        reader.process();

    }

    void process() {


    }

}
