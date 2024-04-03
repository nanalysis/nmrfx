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
package org.nmrfx.peaks.io;

import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.peaks.*;
import org.python.util.PythonInterpreter;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author Bruce Johnson
 */
@PythonAPI("pscript")
public class PeakReader {

    Map<Long, List<PeakDim>> resMap;
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
            if (peakDims.size() > 1) {

                for (PeakDim peakDim : peakDims) {
                    if (peakDim != firstPeakDim) {
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
        return switch (type) {
            case "xpk2" -> readXPK2Peaks(fileName);
            case "xpk" -> readXPKPeaks(fileName);
            case "sparky_save" -> readSparkySaveFile(fileName, pMap);
            case "sparky_assign" -> readSparkyAssignmentFile(fileName);
            case "nmrpipe" -> readNMRPipePeaks(fileName);
            default -> throw new IllegalArgumentException("Invalid file type " + fileName);
        };
    }

    public PeakList readXPK2Peaks(String fileName) throws IOException {
        Path path = Paths.get(fileName);
        String fileTail = path.getFileName().toString();
        fileTail = fileTail.substring(0, fileTail.lastIndexOf('.'));
        boolean gotHeader = false;
        String[] dataHeader = null;
        Map<String, Integer> dataMap = null;
        PeakList peakList = null;
        try (final BufferedReader fileReader = Files.newBufferedReader(path)) {
            while (true) {
                String line = fileReader.readLine();
                if (line == null) {
                    break;
                }
                String sline = line.trim();
                if (sline.isEmpty()) {
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
                        int nDim = Integer.parseInt(data[map.get("ndim")]);
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
                        for (var entry : map.entrySet()) {
                            String headerLabel = entry.getKey();
                            if (headerLabel.startsWith("prop:")) {
                                String propName = headerLabel.substring(5);
                                String propValue = data[entry.getValue()];
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
                                    case "label" -> sDim.setDimName(value);
                                    case "code" -> sDim.setNucleus(value);
                                    case "sf" -> sDim.setSf(Double.parseDouble(value));
                                    case "sw" -> sDim.setSw(Double.parseDouble(value));
                                    case "fp" -> sDim.setRef(Double.parseDouble(value));
                                    case "idtol" -> sDim.setIdTol(Double.parseDouble(value));
                                    case "pattern" -> sDim.setPattern(value);
                                    case "bonded" -> sDim.setRelation(value);
                                    case "spatial" -> sDim.setSpatialRelation(value);
                                    case "acqdim" -> sDim.setAcqDim(Boolean.parseBoolean(value));
                                    case "abspos" -> sDim.setAbsPosition(Boolean.parseBoolean(value));
                                    case "folding" -> sDim.setNEFAliasing(value);
                                    case "units" -> {
                                    }
                                    default -> throw new IllegalArgumentException("Unknown field " + field);
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
                        case "L" -> {
                            List<String> labelList = Arrays.asList(value.split(" "));
                            peakDim.setLabel(labelList);
                        }
                        case "P" -> peakDim.setChemShiftValue(Float.parseFloat(value));
                        case "W" -> peakDim.setLineWidthValue(Float.parseFloat(value));
                        case "WH" ->
                                peakDim.setLineWidthValue(Float.parseFloat(value) / (float) peakDim.getSpectralDimObj().getSf());
                        case "B" -> peakDim.setBoundsValue(Float.parseFloat(value));
                        case "BH" ->
                                peakDim.setBoundsValue(Float.parseFloat(value) / (float) peakDim.getSpectralDimObj().getSf());
                        case "J" -> {
                        }
                        // fixme
                        case "M" -> {
                        }
                        // fixme
                        case "m" -> {
                        }
                        // fixme
                        case "E" -> peakDim.setError(value);
                        case "F" -> peakDim.setFrozen(!value.equals("0"));
                        case "U" -> peakDim.setUser(value);
                        case "r" -> {
                            long resNum = Long.parseLong(value);
                            if (linkResonances) {
                                addResonance(resNum, peakDim);
                            }
                        }
                        default -> throw new IllegalArgumentException("Unknown field " + field);
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
                            case "id" -> peak.setIdNum(Integer.parseInt(value));
                            case "int", "intensity" -> peak.setIntensity(Float.parseFloat(value));
                            case "intensity_err" -> peak.setIntensityErr(Float.parseFloat(value));
                            case "vol", "volume" -> peak.setVolume1(Float.parseFloat(value));
                            case "volume_err" -> peak.setVolume1Err(Float.parseFloat(value));
                            case "status", "stat" -> peak.setStatus(Integer.parseInt(value));
                            case "type" -> peak.setType(Integer.parseInt(value));
                            case "comment" -> peak.setComment(value);
                            case "flags" -> peak.setFlag2(value);
                            case "flag" -> peak.setFlag2(flagNum, value);
                            case "color" -> peak.setColor(value);
                            default -> throw new IllegalArgumentException("Unknown field " + field);
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
                if (sline.isEmpty()) {
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
        String[] dataHeader = null;
        Map<String, Integer> dataMap = null;
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
                            case "label" -> sDim.setDimName(value);
                            case "sf" -> sDim.setSf(Double.parseDouble(value));
                            case "sw" -> sDim.setSw(Double.parseDouble(value));
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
                if (sline.isEmpty()) {
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

    /**
     * Converts a String into a list of Strings based on white space or tab characters.
     * Quotes are removed from the string. If there is an inner quote, that will be kept. Example {123.h5'} -> 123.h5'
     * and {123.h5''} -> 123.h5''
     * In this method, quote characters are any of the following four characters: ", ', {, }, Empty quotes are returned
     * as an empty string in the list.
     *
     * @param line The String to parse.
     * @return The parsed String as a list.
     */
    public static List<String> parseXPKLine(String line) {
        List<String> store = new ArrayList<>();
        StringBuilder curVal = new StringBuilder();
        boolean inquotes = false;
        char quoteChar = '\'';
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inquotes) {
                if (ch == quoteChar) {
                    inquotes = false;
                    store.add(curVal.toString().trim());
                    curVal = new StringBuilder();
                } else {
                    curVal.append(ch);
                }
            } else if ((ch == '\"') || (ch == '\'') || (ch == '{')) {
                inquotes = true;
                quoteChar = ch == '{' ? '}' : ch;

            } else if ((ch == ' ') || (ch == '\t')) {
                if (!curVal.isEmpty()) {
                    store.add(curVal.toString().trim());
                    curVal = new StringBuilder();
                }
            } else {
                curVal.append(ch);
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
        try (PythonInterpreter interpreter = new PythonInterpreter()) {
            interpreter.exec("import sparky");
            interpreter.set("pMap", pMap);
            interpreter.exec("sparky.pMap=pMap");
            interpreter.set("sparkyFileName", fileName);
            interpreter.set("sparkyListName", listName);
            interpreter.exec("sparky.loadSaveFile(sparkyFileName, sparkyListName)");
        }
        return PeakList.get(listName);
    }

    public static boolean hasSparkyDataHeight(String[] fields) {
        int nFields = fields.length;
        return (nFields > 2) && fields[nFields - 2].equals("Data") && fields[nFields - 1].equals("Height");
    }

    public static int countSparkyDims(String[] fields) {
        int nDim = 0;
        for (String field : fields) {
            if (field.trim().startsWith("w")) {
                nDim++;
            }
        }
        return nDim;
    }

    public static PeakList readSparkyAssignmentFile(String fileName) throws IOException {
        Path path = Paths.get(fileName);
        String fileTail = path.getFileName().toString();
        fileTail = fileTail.substring(0, fileTail.indexOf('.'));
        String listName = fileTail;
        PeakList peakList;
        Pattern pattern = Pattern.compile("([A-Za-z]*)([0-9]+)([A-Za-z].*)");
        try (final BufferedReader fileReader = Files.newBufferedReader(path)) {
            String line = fileReader.readLine();
            line = line.trim();
            String[] fields = line.split("\\s+");
            int nDim = countSparkyDims(fields);
            boolean hasHeight = hasSparkyDataHeight(fields);
            peakList = new PeakList(listName, nDim);
            int expectedFields = 1 + nDim;
            if (hasHeight) {
                expectedFields++;
            }
            while (true) {
                line = fileReader.readLine();
                if (line == null) {
                    break;
                }
                line = line.trim();
                fields = line.split("\\s+");
                if (fields.length >= expectedFields) {
                    Peak peak = peakList.getNewPeak();
                    String[] assignFields = fields[0].split("-");
                    if (hasHeight) {
                        float height = Float.parseFloat(fields[fields.length - 1]);
                        peak.setIntensity(height);
                    } else {
                        peak.setIntensity(1.0f);
                    }

                    for (int i = 0; i < nDim; i++) {
                        float ppm = Float.parseFloat(fields[i + 1]);
                        PeakDim peakDim = peak.getPeakDim(i);
                        peakDim.setChemShift(ppm);
                        float widthHz = 20.0f;
                        double sf = 600.0;
                        double sw = 2000.0;
                        String resName = "";
                        String atomName = "";
                        if (assignFields.length == nDim) {
                            String assignField = assignFields[i];
                            if (!assignField.equals("?")) {
                                Matcher matcher = pattern.matcher(assignField);
                                if (matcher.matches()) {
                                    if (matcher.group(1) == null) {
                                        resName = matcher.group(2);
                                    } else {
                                        resName = matcher.group(1) + matcher.group(2);
                                    }
                                    atomName = matcher.group(3);
                                }
                            }
                        }

                        if (!atomName.isBlank()) {
                            String label = resName + "." + atomName;
                            peakDim.setLabel(label);
                            if (atomName.startsWith("H")) {
                                widthHz = 15.0f;
                                sf = 600.0;
                                sw = 5000.0;

                                peakList.getSpectralDim(i).setDimName("1H");
                            } else if (atomName.startsWith("N")) {
                                sf = 60.0;
                                sw = 2000.0;
                                widthHz = 30.0f;
                                peakList.getSpectralDim(i).setDimName("15N");
                            } else if (atomName.startsWith("C")) {
                                widthHz = 30.0f;
                                sf = 150.0;
                                sw = 4000.0;
                                peakList.getSpectralDim(i).setDimName("13C");
                            }
                            peakList.getSpectralDim(i).setSf(sf);
                            peakList.getSpectralDim(i).setSw(sw);
                        }
                        peakDim.setLineWidthHz(widthHz);
                        peakDim.setBoundsHz(widthHz * 3.0f);
                    }
                }
            }
        }
        return peakList;
    }

    public PeakList readNMRPipePeaks(String fileName) throws IOException {
        Path path = Paths.get(fileName);
        String fileTail = path.getFileName().toString();
        fileTail = fileTail.substring(0, fileTail.lastIndexOf('.'));
        boolean gotHeader = false;
        boolean swSfSet = false;
        Map<String, Integer> dataMap = new HashMap<>();
        PeakList peakList = null;
        List<String> dimNames = new ArrayList<>();
        List<double[]> ppmStarts = new ArrayList<>();
        String[] nucTypes = {"H", "N", "P", "C"};
        try (final BufferedReader fileReader = Files.newBufferedReader(path)) {
            while (true) {
                String line = fileReader.readLine();
                if (line == null) {
                    break;
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.charAt(0) == '#') {
                    continue;
                }
                if (!gotHeader) {
                    //DATA  X_AXIS HN           1   659   10.297ppm    5.798ppm
                    if (line.startsWith("DATA")) {
                        String[] fields = line.split(" +", -1);
                        dimNames.add(fields[2]);
                        String ppmStartField = fields[5];
                        String ppmEndField = fields[6];
                        if (ppmStartField.endsWith("ppm")) {
                            double ppmStart = Double.parseDouble(ppmStartField.substring(0,ppmStartField.indexOf("p")));
                            double ppmEnd = Double.parseDouble(ppmEndField.substring(0,ppmEndField.indexOf("p")));
                            double[] ppms = {ppmStart, ppmEnd};
                            ppmStarts.add(ppms);
                        }
                    } else if (line.startsWith("FORMAT")) {
                        gotHeader = true;
                    } else if (line.startsWith("VARS")) {
                        //VARS   INDEX X_AXIS Y_AXIS Z_AXIS DX DY DZ X_PPM Y_PPM Z_PPM X_HZ Y_HZ Z_HZ XW YW ZW XW_HZ YW_HZ ZW_HZ X1 X3 Y1 Y3 Z1 Z3 HEIGHT DHEIGHT VOL PCHI2 TYPE ASS CLUSTID MEMCNT
                        String[] fields = line.split(" +", -1);
                        int nDim = 0;
                        for (int i = 1; i < fields.length; i++) {
                            dataMap.put(fields[i], i - 1);
                            if (fields[i].endsWith("_AXIS")) {
                                nDim++;
                            }
                        }
                        peakList = new PeakList(fileTail, nDim);
                        for (int i = 0; i < dimNames.size(); i++) {
                            String dimName = dimNames.get(i);
                            SpectralDim sDim = peakList.getSpectralDim(i);
                            sDim.setDimName(dimNames.get(i));
                            for (String nucType:nucTypes) {
                                if (dimName.contains(nucType)) {
                                    sDim.setNucleus(nucType);
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    String[] data = line.split(" +", -1);
                    if (peakList != null) {
                        processNMRPipeLine(peakList, dataMap, data);
                        if (!swSfSet) {
                            setSfSw(peakList, dataMap, data, ppmStarts);
                            swSfSet = true;
                        }
                    }
                }
            }
        }
        return peakList;
    }

    private Double getPipeValue(Map<String, Integer> dataMap, String[]data, String varName) {
        Integer index = dataMap.get(varName);
        Double result = null;
        if (index != null) {
            String field = data[index];
            result = Double.parseDouble(field);
        }
        return result;
    }

    private void setSfSw(PeakList peakList, Map<String, Integer> dataMap, String[] data, List<double[]> ppmStarts) {

        int nDim = peakList.getNDim();
        String[] labels = {"X", "Y", "Z", "A", "B", "C"};

        for (int iDim = 0; iDim < nDim; iDim++) {
            String axis = labels[iDim];
            Double shift = getPipeValue(dataMap, data, axis + "_PPM");
            Double shiftHz = getPipeValue(dataMap, data, axis + "_HZ");
            if ((shift != null)  && (shiftHz != null)) {
                double[] ppms = ppmStarts.get(iDim);
                double sf = Math.abs(shiftHz / (shift - ppms[0]));
                peakList.getSpectralDim(iDim).setSf(sf);
                double sw = (ppms[0] - ppms[1]) * sf;
                sw = Math.round(sw * 100.0) / 100.0;
                peakList.getSpectralDim(iDim).setSw(sw);
            }

        }
    }

    public void processNMRPipeLine(PeakList peakList, Map<String, Integer> dataMap, String[]
            data) {
        Peak peak = peakList.getNewPeak();
        Double intensity =  getPipeValue(dataMap, data, "HEIGHT");
        if (intensity != null) {
            peak.setIntensity(intensity.floatValue());
        }
        Double volume =  getPipeValue(dataMap, data, "VOL");
        if (volume != null) {
            peak.setVolume1(volume.floatValue());
        }
        int nDim = peakList.getNDim();
        String[] labels = {"X", "Y", "Z", "A", "B", "C"};

        for (int iDim=0;iDim<nDim;iDim++) {
            PeakDim peakDim = peak.getPeakDim(iDim);
            String axis = labels[iDim];
            Double shift = getPipeValue(dataMap, data, axis + "_PPM");
            if (shift != null) {
                peakDim.setChemShiftValue(shift.floatValue());
            }
            Double shiftHz = getPipeValue(dataMap, data, axis + "_HZ");
            if (shift != null) {
                peakDim.setChemShiftValue(shift.floatValue());
            }

            Double wHz = getPipeValue(dataMap, data, axis + "W_HZ");
            Double w = getPipeValue(dataMap, data, axis + "W");
            if (wHz != null) {
                peakDim.setLineWidthHz(wHz.floatValue());
            }
            Double bound1 = getPipeValue(dataMap, data, axis + "1");
            Double bound3 = getPipeValue(dataMap, data, axis + "3");
            if ((bound1 != null)  && (bound3 != null) && (wHz != null) && (w != null)) {
                float bounds = bound3.floatValue() - bound1.floatValue() + 1.0f;
                float boundsHz = (float) (bounds * wHz / w);
                peakDim.setBoundsHz(boundsHz);
            }
        }
    }

}
