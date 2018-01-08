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
package org.nmrfx.processor.datasets.peaks.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.SpectralDim;

/**
 *
 * @author Bruce Johnson
 */
public class PeakReader {

    Map<Long, List<PeakDim>> resMap = null;
    final boolean linkResonances;

    public PeakReader() {
        this(false);
    }

    public PeakReader(boolean linkResonances) {
        this.linkResonances = linkResonances;
        resMap = new HashMap<>();
    }

    private void addResonance(long resID, PeakDim peakDim) {
        List<PeakDim> peakDims = resMap.get(resID);
        if (peakDims == null) {
            peakDims = new ArrayList<>();
            resMap.put(resID, peakDims);
        }
        peakDims.add(peakDim);
    }

    public void linkResonances() {
        for (Long resID : resMap.keySet()) {
            List<PeakDim> peakDims = resMap.get(resID);
            PeakDim firstPeakDim = peakDims.get(0);
//            System.out.println(resID + " " + firstPeakDim.getName() + " " + peakDims.size());
            if (peakDims.size() > 1) {

                for (PeakDim peakDim : peakDims) {
                    if (peakDim != firstPeakDim) {
//                        System.out.println(peakDim.getName());
                        PeakList.linkPeakDims(firstPeakDim, peakDim);
                    }

                }
            }
        }
    }

    public PeakList readXPK2Peaks(String fileName) throws IOException {
        Path path = Paths.get(fileName);
        String fileTail = path.getFileName().toString();
        fileTail = fileTail.substring(0, fileTail.lastIndexOf('.'));
        boolean gotHeader = false;
        String[] dataHeader = null;
        Map<String, Integer> dataMap = null;
        PeakList peakList = null;
        String units = "ppm";
        try (final BufferedReader fileReader = Files.newBufferedReader(path)) {
            while (true) {
                String line = fileReader.readLine();
                if (line == null) {
                    break;
                }
                String sline = line.trim();
                if (sline.length() == 0) {
                    continue;
                }
                if (sline.charAt(0) == '#') {
                    continue;
                }
                if (peakList == null) {
                    if (line.contains("ndim")) {
                        String[] header = line.split("\t", -1);
                        Map<String, Integer> map = headerMap(header);
                        String lineData = fileReader.readLine();
                        String[] data = lineData.split("\t", -1);
                        int nDim = Integer.valueOf(data[map.get("ndim")]);
                        String listName = fileTail;
                        if (map.get("peaklist") != null) {
                            listName = data[map.get("peaklist")];
                        }
                        peakList = new PeakList(listName, nDim);
                        if (map.get("dataset") != null) {
                            peakList.setDatasetName(data[map.get("dataset")]);
                        }
                        if (map.get("condition") != null) {
                            peakList.setSampleConditionLabel(data[map.get("condition")]);
                        }
                    }
                } else {
                    if (!gotHeader) {
                        String[] header = line.split("\t", -1);
                        Map<String, Integer> map = headerMap(header);
                        for (int i = 0; i < peakList.nDim; i++) {
                            String lineData = fileReader.readLine();
                            String[] data = lineData.split("\t", -1);
                            SpectralDim sDim = peakList.getSpectralDim(i);
                            for (String field : header) {
                                String value = data[map.get(field)];
                                switch (field) {
                                    case "label":
                                        sDim.setDimName(value);
                                        break;
                                    case "code":
                                        sDim.setNucleus(value);
                                        break;
                                    case "sf":
                                        sDim.setSf(Double.valueOf(value));
                                        break;
                                    case "sw":
                                        sDim.setSw(Double.valueOf(value));
                                        break;
                                    case "fp":
                                        sDim.setRef(Double.valueOf(value));
                                        break;
                                    case "idtol":
                                        sDim.setIdTol(Double.valueOf(value));
                                        break;
                                    case "pattern":
                                        sDim.setPattern(value);
                                        break;
                                    case "bonded":
                                        sDim.setRelation(value);
                                        break;
                                    case "spatial":
                                        sDim.setSpatialRelation(value);
                                        break;
                                    case "acqdim":
                                        sDim.setAcqDim(Boolean.valueOf(value));
                                        break;
                                    case "abspos":
                                        sDim.setAbsPosition(Boolean.valueOf(value));
                                        break;
                                    case "folding":
                                        sDim.setNEFAliasing(value);
                                        break;
                                    case "units":
                                        units = value;
                                        break;
                                    default:
                                        throw new IllegalArgumentException("Unknown field " + field);
                                }
                            }
                        }
                        gotHeader = true;
                    } else {
                        if (dataHeader == null) {
                            dataHeader = line.split("\t", -1);
                        } else {
                            if (dataMap == null) {
                                dataMap = headerMap(dataHeader);
                            }
                            String[] data = line.split("\t", -1);
                            Peak peak = peakList.getNewPeak();
                            for (String field : dataHeader) {
                                int dotIndex = field.indexOf('.');
                                if (dotIndex != -1) {
                                    Integer dataIndex = dataMap.get(field);
                                    String dimLabel = field.substring(0, dotIndex);
                                    field = field.substring(dotIndex + 1);
                                    PeakDim peakDim = peak.getPeakDim(dimLabel);
                                    if (dataIndex != null) {
                                        String value = data[dataIndex];
                                        switch (field) {
                                            case "L":
                                                List<String> labelList = Arrays.asList(value.split(" "));
                                                peakDim.setLabel(labelList);
                                                break;
                                            case "P":
                                                peakDim.setChemShiftValue(Float.valueOf(value));
                                                break;
                                            case "W":
                                                peakDim.setLineWidthValue(Float.valueOf(value));
                                                break;
                                            case "WH":
                                                peakDim.setLineWidthValue(Float.valueOf(value) / (float) peakDim.getSpectralDimObj().getSf());
                                                break;
                                            case "B":
                                                peakDim.setBoundsValue(Float.valueOf(value));
                                                break;
                                            case "BH":
                                                peakDim.setBoundsValue(Float.valueOf(value) / (float) peakDim.getSpectralDimObj().getSf());
                                                break;
                                            case "J":
                                                // fixme
                                                break;
                                            case "E":
                                                peakDim.setError(value);
                                                break;
                                            case "F":
                                                peakDim.setFrozen(!value.equals("0"));
                                                break;
                                            case "U":
                                                peakDim.setUser(value);
                                                break;
                                            case "r":
                                                long resNum = Long.valueOf(value);
                                                if (linkResonances) {
                                                    addResonance(resNum, peakDim);
                                                }
                                                break;
                                            default:
                                                throw new IllegalArgumentException("Unknown field " + field);
                                        }
                                    }
                                } else {
                                    Integer dataIndex = dataMap.get(field);
                                    //   id      HN.L    HN.P    HN.WH   HN.B    HN.E    HN.J    HN.U
                                    // N.L     N.P     N.WH    N.B     N.E     N.J     N.U
                                    // volume  intensity       status  comment flags
                                    if (dataIndex != null) {
                                        String value = data[dataIndex];
                                        switch (field) {
                                            case "id":
                                                peak.setIdNum(Integer.valueOf(value));
                                                break;
                                            case "intensity":
                                                peak.setIntensity(Float.valueOf(value));
                                                break;
                                            case "volume":
                                                peak.setVolume1(Float.valueOf(value));
                                                break;
                                            case "status":
                                                peak.setStatus(Integer.valueOf(value));
                                                break;
                                            case "comment":
                                                peak.setComment(value);
                                                break;
                                            case "flags":
                                                peak.setFlag(value);
                                                break;
                                            case "color":
                                                peak.setColor(value);
                                                break;
                                            default:
                                                throw new IllegalArgumentException("Unknown field " + field);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return peakList;
    }

    public void readMPK2(PeakList peakList, String fileName) throws IOException {
        Path path = Paths.get(fileName);
        boolean gotHeader = false;
        int valStart = -1;
        int nValues = -1;
        try (final BufferedReader fileReader = Files.newBufferedReader(path)) {
            while (true) {
                String line = fileReader.readLine();
                if (line == null) {
                    break;
                }
                String sline = line.trim();
                if (sline.length() == 0) {
                    continue;
                }
                if (sline.charAt(0) == '#') {
                    continue;
                }
                String[] data = line.split("\t", -1);
                if (!gotHeader) {
                    gotHeader = true;
                    int i = 0;
                    for (String s : data) {
                        if (s.startsWith("val")) {
                            valStart = i;
                            nValues = data.length - valStart;
                            break;
                        }
                        i++;
                    }
                } else {
                    int peakId = Integer.parseInt(data[0]);
                    Peak peak = peakList.getPeakByID(peakId);
                    if (peak != null) {
                        double[] values = new double[nValues];
                        for (int i = valStart, j = 0; i < data.length; i++) {
                            values[j++] = Double.parseDouble(data[i]);
                        }
                        peak.setMeasures(values);
                    }

                }
            }
        }
    }

    /*
    dataset ndim
    C_nhsqcsegr_b.nv        2
    id      label   units   sf      sw      fp      idtol   pattern relation        folding abspos  acqdim
    1       HN"ppm  499.83770751953125      2617.1875       0.0     0.007815036643363612                    0.0     circular        true    true
    2       N"ppm   50.653602600097656      2000.0  0.0     0.15423384712985722                     0.0     circular        true    false
    index   id      HN.L    HN.P    HN.WH   HN.B    HN.E    HN.J    HN.U    N.L     N.P     N.WH    N.B     N.E     N.J     N.U     volume  intensity       status  comment flags
    0       0               8.94238 0.03142 0.03220 ++                              132.96933       0.39033 0.40230 ++                      0.0     1.5329096       0               0
     */
    public static Map<String, Integer> headerMap(String[] header) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            map.put(header[i], i);
        }
        return map;
    }
}
