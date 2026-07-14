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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.datasets;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Bruce Johnson
 */
public class DatasetParameterFile {

    final DatasetBase dataset;
    final DatasetLayout layout;
    String relativeFIDPath = "";
    String absoluteFIDPath = "";
    final static Pattern DLABEL_PAT = Pattern.compile("dlabel +[0-9]+ (.*)");

    public DatasetParameterFile(DatasetBase dataset, DatasetLayout layout) {
        this.dataset = dataset;
        this.layout = layout;
    }

    public String getParameterFileName() {
        String fileName = dataset.getCanonicalFile();
        return getParameterFileName(fileName);
    }

    public static String getParameterFileName(String fileName) {
        Pattern pattern = Pattern.compile("\\.(nv|ucsf|nvlnk)$");
        Matcher matcher = pattern.matcher(fileName);
        int endIndex = matcher.find() ? matcher.start() : fileName.length();
        return fileName.substring(0, endIndex) + ".par";
    }

    public final boolean remove() {
        String fileName = getParameterFileName();
        File file = new File(fileName);
        boolean removed;
        try {
            removed = Files.deleteIfExists(file.toPath());
        } catch (IOException ioE) {
            removed = false;
        }
        return removed;
    }

    public final void writeFile() {
        String parFileName = getParameterFileName();
        writeFile(parFileName);

    }

    public final void writeFile(String parFileName) {
        File parFile = new File(parFileName);
        try (PrintStream pStream = new PrintStream(parFile)) {
            int nDim = dataset.getNDim();
            if (!dataset.isMemoryFile()) {
                pStream.printf("dim %d", nDim);
                for (int i = 0; i < nDim; i++) {
                    pStream.printf(" %d", dataset.getSizeTotal(i));
                }
                for (int i = 0; i < nDim; i++) {
                    pStream.printf(" %d", layout.getBlockSize(i));
                }
                pStream.print("\n");
            }
            for (int i = 0; i < nDim; i++) {
                pStream.printf("sw %d %.2f\n", (i + 1), dataset.getSw(i));
                pStream.printf("sf %d %.8f\n", (i + 1), dataset.getSf(i));
                pStream.printf("label %d %s\n", (i + 1), dataset.getLabel(i));
                pStream.printf("dlabel %d %s\n", (i + 1), dataset.getDlabel(i));
                pStream.printf("nucleus %d %s\n", (i + 1), dataset.getNucleus(i).getNameNumber());
                pStream.printf("complex %d %d\n", (i + 1), dataset.getComplex(i) ? 1 : 0);
                pStream.printf("fdomain %d %d\n", (i + 1), dataset.getFreqDomain(i) ? 1 : 0);
                double[] values = dataset.getValues(i);
                if (values != null) {
                    pStream.printf("values %d", (i + 1));
                    for (double value : values) {
                        pStream.printf(" %.4f", value);
                    }
                    pStream.print('\n');
                }
            }
            pStream.printf("posneg %d\n", dataset.getPosneg());
            pStream.printf("lvl %f\n", dataset.getLvl());
            pStream.printf("scale %g\n", dataset.getScale());
            pStream.printf("norm %g\n", dataset.getNorm());
            if (dataset.getNoiseLevel() != null) {
                pStream.printf("noise %g\n", dataset.getNoiseLevel());
            }
            pStream.printf("rdims %d\n", dataset.getNFreqDims());
            pStream.printf("datatype %d\n", dataset.getDataType());
            pStream.printf("poscolor %s\n", dataset.getPosColor());
            pStream.printf("negcolor %s\n", dataset.getNegColor());
            for (int i = 0; i < nDim; i++) {
                pStream.printf("ref %d %.4f %.1f\n", (i + 1), dataset.getRefValue(i), (dataset.getRefPt(i) + 1));
            }
            Map map = dataset.getPropertyList();
            for (Object obj : map.entrySet()) {
                Entry entry = (Entry) obj;
                pStream.printf("%s %s %s\n", "property", entry.getKey(), entry.getValue());
            }
            Path dFile = dataset.getFile().getCanonicalFile().toPath();
            dataset.sourceFID().ifPresent(fidFile -> {
                try {
                    Path fPath = fidFile.getCanonicalFile().toPath();
                    Path rPath = dFile.relativize(fPath);
                    pStream.printf("%s %s\n", "fid_rel", rPath);
                    pStream.printf("%s %s\n", "fid_abs", fPath);
                } catch (IOException ioE1) {

                }
            });
        } catch (IOException ioE) {
            System.out.println("error " + ioE.getMessage());

        }
    }

    public final void readFile() {
        String parFileName = getParameterFileName();
        File parFile = new File(parFileName);
        if (parFile.exists()) {
            try (BufferedReader br = Files.newBufferedReader(Paths.get(parFileName))) {
                if (br.ready()) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) {
                            continue;
                        }
                        if (line.charAt(0) == '#') {
                            continue;
                        }
                        try {
                            parseLine(line);
                        } catch (Exception e) {
                            System.out.println(e.getMessage());
                        }
                    }
                }
            } catch (IOException ioE) {
                System.out.println(" error in par file " + ioE.getMessage());
            }
            if (!absoluteFIDPath.isBlank()) {
                File testFile = new File(absoluteFIDPath);
                if (testFile.exists()) {
                    dataset.sourceFID(testFile);
                }
            }
            if (dataset.sourceFID().isEmpty()) {
                if (!relativeFIDPath.isBlank()) {
                    File testFile = dataset.getFile().toPath().resolve(Path.of(relativeFIDPath)).toFile();
                    if (testFile.exists()) {
                        dataset.sourceFID(testFile);
                    }
                }
            }
        }
    }

    void parseLine(String line) {
        String[] fields = line.split(" +");
        if (fields.length < 2) {
            return;
        }
        switch (fields[0]) {
            case "complex" -> {
                int iDim = Integer.parseInt(fields[1]) - 1;
                int value = Integer.parseInt(fields[2]);
                dataset.setComplex(iDim, value == 1);
            }
            case "fdomain" -> {
                int iDim = Integer.parseInt(fields[1]) - 1;
                int value = Integer.parseInt(fields[2]);
                dataset.setFreqDomain(iDim, value == 1);
            }
            case "nucleus" -> {
                int iDim = Integer.parseInt(fields[1]) - 1;
                dataset.setNucleus(iDim, fields[2]);
            }
            case "label" -> {
                int iDim = Integer.parseInt(fields[1]) - 1;
                dataset.setLabel(iDim, fields[2]);
            }
            case "dlabel" -> {
                int iDim = Integer.parseInt(fields[1]) - 1;
                Matcher matcher = DLABEL_PAT.matcher(line);
                if (matcher.matches()) {
                    String dLabel = matcher.group(1).trim();
                    dataset.setDlabel(iDim, dLabel);
                }
            }
            case "sw" -> {
                int iDim = Integer.parseInt(fields[1]) - 1;
                double value = Double.parseDouble(fields[2]);
                dataset.setSw(iDim, value);
            }
            case "sf" -> {
                int iDim = Integer.parseInt(fields[1]) - 1;
                double value = Double.parseDouble(fields[2]);
                dataset.setSf(iDim, value);
            }
            case "lvl" -> {
                double value = Double.parseDouble(fields[1]);
                dataset.setLvl(value);
            }
            case "scale" -> {
                double value = Double.parseDouble(fields[1]);
                dataset.setScale(value);
            }
            case "norm" -> {
                double value = Double.parseDouble(fields[1]);
                dataset.setNorm(value);
            }
            case "noise" -> {
                double value = Double.parseDouble(fields[1]);
                dataset.setNoiseLevel(value);
            }
            case "rdims" -> {
                int value = Integer.parseInt(fields[1]);
                dataset.setNFreqDims(value);
            }
            case "datatype" -> {
                int value = Integer.parseInt(fields[1]);
                dataset.setDataType(value);
            }
            case "byteorder" -> {
                int value = Integer.parseInt(fields[1]);
                if (value == 0) {
                    dataset.setLittleEndian();
                } else {
                    dataset.setBigEndian();
                }
            }
            case "poscolor" -> {
                dataset.setPosColor(fields[1]);
            }
            case "negcolor" -> {
                dataset.setNegColor(fields[1]);
            }
            case "posneg" -> {
                int value = Integer.parseInt(fields[1]);
                if (value == 0) {
                    value = 1;
                }
                dataset.setPosneg(value);
            }
            case "ref" -> {
                int iDim = Integer.parseInt(fields[1]) - 1;
                double value = Double.parseDouble(fields[2]);
                double pt = Double.parseDouble(fields[3]);
                dataset.setRefPt(iDim, pt - 1);
                dataset.setRefValue(iDim, value);
                dataset.setRefPt_r(iDim, pt - 1);
                dataset.setRefValue_r(iDim, value);
            }
            case "dim" -> {
                int nDim = Integer.parseInt(fields[1]);
                if (fields.length != (nDim * 2 + 2)) {
                    throw new IllegalArgumentException("invalid par line " + line);
                }
                boolean sameSize = true;
                if (nDim != dataset.getNDim()) {
                    sameSize = false;
                } else {
                    for (int i = 0; i < nDim; i++) {
                        int size = Integer.parseInt(fields[2 + i]);
                        int blockSize = Integer.parseInt(fields[2 + i + nDim]);
                        if ((size != dataset.getSizeTotal(i)) || (blockSize != layout.getBlockSize(i))) {
                            sameSize = false;
                            break;
                        }
                    }
                }
                if (!sameSize) {
                    dataset.setNDim(nDim);
                    layout.resize(nDim);
                    int fSize = layout.getFileHeaderSize();
                    int bSize = layout.getBlockHeaderSize();
                    dataset.newHeader();
                    layout.setFileHeaderSize(fSize);
                    layout.setBlockHeaderSize(bSize);

                    for (int i = 0; i < nDim; i++) {
                        int size = Integer.parseInt(fields[2 + i]);
                        int blockSize = Integer.parseInt(fields[2 + i + nDim]);
                        layout.setSize(i, size);
                        layout.setBlockSize(i, blockSize);
                    }
                    layout.dimDataset();
                }
            }
            case "property" -> {
                String propName = fields[1];
                if (fields.length == 2) {
                    dataset.addProperty(propName, "");
                } else {
                    int index = line.indexOf(propName) + propName.length();
                    String propValue = line.substring(index).trim();
                    dataset.addProperty(propName, propValue);
                }
            }
            case "fid_rel" -> {
                int spacePos = line.indexOf(" ");
                if (spacePos != -1) {
                    relativeFIDPath = line.substring(spacePos).trim();
                }
            }
            case "fid_abs" -> {
                int spacePos = line.indexOf(" ");
                if (spacePos != -1) {
                    absoluteFIDPath = line.substring(spacePos).trim();
                }
            }
            case "values" -> {
                int iDim = Integer.parseInt(fields[1]) - 1;
                double[] values = new double[fields.length - 2];
                for (int i = 2; i < fields.length; i++) {
                    double value = Double.parseDouble(fields[i]);
                    values[i - 2] = value;
                }
                dataset.setValues(iDim, values);
            }
        }

    }
}
