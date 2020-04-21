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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nmrfx.processor.datasets.peaks.Measures;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.SpectralDim;
import org.python.util.PythonInterpreter;

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

    public PeakList readPeakList(String fileName) throws IOException {
        return readPeakList(fileName, null);
    }

    public PeakList readPeakList(String fileName, Map<String, Object> pMap) throws IOException {
        Path path = Paths.get(fileName);
        PeakFileDetector detector = new PeakFileDetector();
        String type = detector.probeContentType(path);
        switch (type) {
            case "xpk2":
                return readXPK2Peaks(fileName);
            case "xpk":
                return readXPKPeaks(fileName);
            case "sparky_save":
                return readSparkySaveFile(fileName, pMap);
            case "sparky_assign":
                return readSparkyAssignmentFile(fileName);
            default:
                throw new IllegalArgumentException("Invalid file type " + fileName);
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
                        for (String headerLabel : map.keySet()) {
                            if (headerLabel.startsWith("prop:")) {
                                String propName = headerLabel.substring(5);
                                String propValue = data[map.get(headerLabel)];
                                peakList.setProperty(propName, propValue);
                            }
                        }
                    } else {
                        throw new IOException("Reading .xpk2 file: no ndim field.");
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
                            processLine(peakList, dataHeader, dataMap, data);
                        }
                    }
                }
            }
        }
        return peakList;
    }

    public void processLine(PeakList peakList, String[] dataHeader, Map<String, Integer> dataMap, String[] data) {
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
                        case "M":
                            // fixme
                            break;
                        case "m":
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
                    String value = null;
                    try {
                        value = data[dataIndex];
                        int flagNum = 0;
                        if (field.startsWith("flag") && (field.length() > 4) && Character.isDigit(field.charAt(4))) {
                            flagNum = Integer.parseInt(field.substring(4));
                            field = "flag";
                        }
                        switch (field) {
                            case "id":
                                peak.setIdNum(Integer.valueOf(value));
                                break;
                            case "int":
                            case "intensity":
                                peak.setIntensity(Float.valueOf(value));
                                break;
                            case "intensity_err":
                                peak.setIntensityErr(Float.valueOf(value));
                                break;
                            case "vol":
                            case "volume":
                                peak.setVolume1(Float.valueOf(value));
                                break;
                            case "volume_err":
                                peak.setVolume1Err(Float.valueOf(value));
                                break;
                            case "status":
                                peak.setStatus(Integer.valueOf(value));
                                break;
                            case "stat":
                                peak.setStatus(Integer.valueOf(value));
                                break;
                            case "type":
                                peak.setType(Integer.valueOf(value));
                                break;
                            case "comment":
                                peak.setComment(value);
                                break;
                            case "flags":
                                peak.setFlag2(value);
                                break;
                            case "flag":
                                peak.setFlag2(flagNum, value);
                                break;
                            case "color":
                                peak.setColor(value);
                                break;
                            default:
                                throw new IllegalArgumentException("Unknown field " + field);
                        }

                    } catch (NumberFormatException nfE) {
                        throw new IllegalArgumentException("Can't parse number: " + value + " for field " + field);
                    }
                }
            }
        }

    }

    public void readMPK2(PeakList peakList, String fileName) throws IOException {
        Path path = Paths.get(fileName);
        boolean gotHeader = false;
        boolean hasErrors = false;
        int valStart = -1;
        int nValues = -1;
        int nDim = peakList.nDim;
        double[] xValues;
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
                    valStart = nDim + 1;
                    if ((data.length > (valStart + 1)) && (data[valStart + 1].equals("err"))) {
                        hasErrors = true;
                    }
                    nValues = data.length - (nDim + 1);
                    if (hasErrors) {
                        nValues /= 2;
                    }
                    xValues = new double[nValues];
                    boolean ok = true;
                    for (int i = valStart, j = 0; i < data.length; i++) {
                        try {
                            xValues[j++] = Double.parseDouble(data[i]);
                            if (hasErrors) {
                                i++;
                            }
                        } catch (NumberFormatException nfE) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) {
                        Measures measure = new Measures(xValues);
                        peakList.setMeasures(measure);
                    }
                } else {
                    int peakId = Integer.parseInt(data[0]);
                    Peak peak = peakList.getPeakByID(peakId);
                    if (peak != null) {
                        double[][] values = new double[2][nValues];
                        for (int i = valStart, j = 0; i < data.length; i++) {
                            values[0][j] = Double.parseDouble(data[i]);
                            if (hasErrors) {
                                values[1][j] = Double.parseDouble(data[i + 1]);
                                i++;
                            }
                            j++;
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

    /*

    if {[gets $fileid fields] == -1} {
        error "Can't read first line"
    }

    foreach item $fields {
        if {[gets $fileid s] == -1} {
            error "Can't read line"
        }
        set $item $s
    }


    if {![info exists label]} {
        error "Can't find line for peak labels"
    }

    if {[lsearch $lists $lst]< 0} {
        eval nv_peak addlist $lst $label
        eval nv_peak dataset $lst $dataset
        eval nv_peak sf $lst $sf
        eval nv_peak sw $lst $sw
        if {[info exists condition]} {
            eval nv_peak sample condition $lst $condition
        }
    }

    if {[gets $fileid fields] == -1} {
        error "Can't read peak fields line"
    }

    set i 0
    set idnums [list]
    while {[gets $fileid s] != -1} {
        if {$i>=[nv_peak n $lst]} {
            set i [nv_peak add $lst]
        }
        set j 1
        set idnum [lindex $s 0]
        foreach field $fields {
            set value [lindex $s $j]
            nv_peak elem $field $lst.$i $value
            incr j
        }
        lappend idnums $idnum $i
        incr i
    }
    foreach "idnum i" $idnums {
        nv_peak idnum $lst.$i $idnum
    }

     */
    public PeakList readXPKPeaks(String fileName) throws IOException {
        Path path = Paths.get(fileName);
        String fileTail = path.getFileName().toString();
        fileTail = fileTail.substring(0, fileTail.lastIndexOf('.'));
        String listName = fileTail;
        boolean gotHeader = false;
        String[] dataHeader = null;
        Map<String, Integer> dataMap = null;
        String units = "ppm";
        try (final BufferedReader fileReader = Files.newBufferedReader(path)) {
            String line = fileReader.readLine();
            List<String> listFields = parseXPKLine(line);
            Map<String, List<String>> listMap = new HashMap<>();
            for (String field : listFields) {
                line = fileReader.readLine();
                List<String> values = parseXPKLine(line);
                listMap.put(field, values);
            }
            int nDim = listMap.get("label").size();
            PeakList peakList = new PeakList(listName, nDim);
            for (String field : listFields) {
                if (!field.equals("dataset") && !field.equals("condition")) {
                    for (int iDim = 0; iDim < nDim; iDim++) {
                        SpectralDim sDim = peakList.getSpectralDim(iDim);
                        String value = listMap.get(field).get(iDim);
                        switch (field) {
                            case "label":
                                sDim.setDimName(value);
                                break;
                            case "sf":
                                sDim.setSf(Double.valueOf(value));
                                break;
                            case "sw":
                                sDim.setSw(Double.valueOf(value));
                                break;
                        }
                    }
                }
            }
            if (listMap.containsKey("dataset")) {
                peakList.setDatasetName(listMap.get("dataset").get(0));
            }
            if (listMap.containsKey("condition")) {
                peakList.setSampleConditionLabel(listMap.get("condition").get(0));
            }
            String[] data = null;
            while (true) {
                line = fileReader.readLine();
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
                List<String> fields = parseXPKLine(line);
                try {

                    if (dataHeader == null) {
                        List<String> headerList = parseXPKLine(line);
                        headerList.add(0, "id");
                        dataHeader = new String[headerList.size()];
                        headerList.toArray(dataHeader);
                        data = new String[headerList.size()];
                    } else {
                        if (dataMap == null) {
                            dataMap = headerMap(dataHeader);
                        }
                        List<String> lineList = parseXPKLine(line);
                        lineList.toArray(data);
                        processLine(peakList, dataHeader, dataMap, data);
                    }
                } catch (Exception exc) {
                    throw new IOException("Can't read line: " + line + " \n" + exc.getMessage());
                }
            }

            return peakList;
        }

    }

    public static List<String> parseXPKLine(String line) {
        List<String> store = new ArrayList<>();
        StringBuilder curVal = new StringBuilder();
        boolean inquotes = false;
        boolean started = false;
        char quoteChar = '\'';
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inquotes) {
                started = true;
                if (ch == quoteChar) {
                    inquotes = false;
                    store.add(curVal.toString().trim());
                    curVal = new StringBuilder();
                    started = false;
                } else {
                    curVal.append((char) ch);
                }
            } else if ((ch == '\"') || (ch == '\'') || (ch == '{')) {
                inquotes = true;
                quoteChar = ch == '{' ? '}' : ch;

                if (started) {
                    // if this is the second quote in a value, add a quote
                    // this is for the double quote in the middle of a value
                    curVal.append(quoteChar);
                }
            } else if ((ch == ' ') || (ch == '\t')) {
                if (curVal.length() != 0) {
                    store.add(curVal.toString().trim());
                    curVal = new StringBuilder();
                    started = false;
                }
            } else {
                curVal.append((char) ch);
            }
        }
        store.add(curVal.toString().trim());
        return store;
    }

    public static PeakList readSparkySaveFile(String fileName, Map<String, Object> pMap) {
        Path path = Paths.get(fileName);
        String fileTail = path.getFileName().toString();
        fileTail = fileTail.substring(0, fileTail.lastIndexOf('.'));
        String listName = fileTail;
        PythonInterpreter interpreter = new PythonInterpreter();
        interpreter.exec("import sparky");
        String rdString;
        interpreter.set("pMap", pMap);
        interpreter.exec("sparky.pMap=pMap");
        rdString = String.format("sparky.loadSaveFile('%s','%s')", fileName, listName);
        interpreter.exec(rdString);
        PeakList peakList = PeakList.get(listName);
        return peakList;
    }

    public static PeakList readSparkyAssignmentFile(String fileName) throws IOException {
        Path path = Paths.get(fileName);
        String fileTail = path.getFileName().toString();
        fileTail = fileTail.substring(0, fileTail.indexOf('.'));
        String listName = fileTail;
        PeakList peakList;
        Pattern pattern = Pattern.compile("([A-Za-z]+)([0-9]+)(.*)");
        try (final BufferedReader fileReader = Files.newBufferedReader(path)) {
            String line = fileReader.readLine();
            line = line.trim();
            String[] fields = line.split("\\s+");
            int nDim = fields.length - 1;
            peakList = new PeakList(listName, nDim);
            while (true) {
                line = fileReader.readLine();
                if (line == null) {
                    break;
                }
                line = line.trim();
                fields = line.split("\\s+");
                if (fields.length == (nDim + 1)) {
                    Peak peak = peakList.getNewPeak();
                    Matcher matcher = pattern.matcher(fields[0]);
                    String resName = "";
                    String[] atoms = null;
                    if (matcher.matches()) {
                        resName = matcher.group(1) + matcher.group(2);
                        atoms = matcher.group(3).split("-");
                    }
                    peak.setIntensity(1.0f);

                    for (int i = 0; i < nDim; i++) {
                        float ppm = Float.parseFloat(fields[i + 1]);
                        PeakDim peakDim = peak.getPeakDim(i);
                        peakDim.setChemShift(ppm);
                        float widthHz = 20.0f;
                        double sf = 600.0;
                        double sw = 2000.0;

                        if ((atoms != null) && (atoms.length == nDim)) {
                            String label = resName + "." + atoms[i];
                            peakDim.setLabel(label);
                            if (atoms[i].startsWith("H")) {
                                widthHz = 15.0f;
                                sf = 600.0;
                                sw = 5000.0;

                                peakList.getSpectralDim(i).setDimName("1H");
                            } else if (atoms[i].startsWith("N")) {
                                sf = 60.0;
                                sw = 2000.0;
                                widthHz = 30.0f;
                                peakList.getSpectralDim(i).setDimName("15N");
                            } else if (atoms[i].startsWith("C")) {
                                widthHz = 30.0f;
                                sf = 150.0;
                                sw = 4000.0;
                                peakList.getSpectralDim(i).setDimName("13C");
                            }
                        }
                        peakList.getSpectralDim(i).setSf(sf);
                        peakList.getSpectralDim(i).setSw(sw);
                        peakDim.setLineWidthHz(widthHz);
                        peakDim.setBoundsHz(widthHz * 3.0f);
                    }
                }
            }
        }
        return peakList;
    }
}
