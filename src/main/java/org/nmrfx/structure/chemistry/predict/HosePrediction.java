package org.nmrfx.structure.chemistry.predict;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.Objects;
import java.util.TreeSet;
import org.apache.commons.collections.bag.HashBag;
import java.nio.charset.Charset;
import java.util.List;

public class HosePrediction {

    static Charset charset = Charset.forName("US-ASCII");
    int nShellGroups = 3;
    byte[] buffer = null;
    int index = 0;
    ArrayList<Integer> hoseList = new ArrayList<>();
    List<Integer>[] shellIndices = new ArrayList[nShellGroups];
    List<Integer> codeStarts = new ArrayList<>();
    static HosePrediction defaultPredictor = null;
    static HosePrediction defaultPredictorN = null;
    boolean stereoMode = false;
    static int maxShells = 5;

    public class HOSEComparator implements Comparator {

        @Override
        public int compare(Object o1, Object o2) {
            String code1;
            String code2;
            if (o1 instanceof HOSEPPM) {
                HOSEPPM hosePPM1 = (HOSEPPM) o1;
                code1 = hosePPM1.code;
            } else if (o1 instanceof Integer) {
                Integer i1 = (Integer) o1;
                HOSEPPM hosePPM1 = getHose(i1);
                if (hosePPM1 == null) {
                    return -1;
                }
                code1 = hosePPM1.code;
            } else {
                code1 = (String) o1;
            }

            if (o2 instanceof HOSEPPM) {
                HOSEPPM hosePPM2 = (HOSEPPM) o2;
                code2 = hosePPM2.code;
            } else if (o2 instanceof Integer) {
                Integer i2 = (Integer) o2;
                HOSEPPM hosePPM2 = getHose(i2);
                if (hosePPM2 == null) {
                    return 1;
                }
                code2 = hosePPM2.code;
            } else {
                code2 = (String) o2;
            }
            return code1.compareTo(code2);
        }
//        public boolean equals(Object i1) {
        //            return false;
        //      }
    }

    public static class HOSEPPM {

        final String[] shells;
        final String code;
        final Double ppmH;
        final Double ppmC;
        ArrayList<Integer> upDownList = null;

        HOSEPPM(String code) {
            this.code = code;
            this.shells = stringToShells(code);
            this.ppmH = null;
            this.ppmC = null;
            upDownList = new ArrayList<>();
//             for (String shell:shells) {
            //                System.out.println("shell " + shell);
            //           }
        }

        HOSEPPM(String code, ArrayList<Integer> upDownList) {
            this.code = code;
            this.shells = stringToShells(code);
            this.ppmH = null;
            this.ppmC = null;
            this.upDownList = upDownList;
//             for (String shell:shells) {
            //                System.out.println("shell " + shell);
            //           }
        }

        HOSEPPM(String code, String[] shells, Double ppmC, Double ppmH, ArrayList<Integer> upDownList) {
            this.code = code;
            this.shells = shells;
            this.ppmC = ppmC;
            this.ppmH = ppmH;
            this.upDownList = upDownList;
        }

        public String getCode() {
            return code;
        }

        final static public String[] stringToShells(String s) {
            String[] sShells = s.split("/");
            if (sShells.length != maxShells) {
                String[] nShells = new String[maxShells];
                int i = 0;
                for (String shell : sShells) {
                    nShells[i++] = shell;
                }
                for (i = sShells.length; i < maxShells; i++) {
                    nShells[i] = "";
                }
                sShells = nShells;
            }
            return sShells;
        }

        public boolean stereoEquals(HOSEPPM hosePPM) {
            boolean equals = true;
            int thisSize = upDownList.size();
            int thatSize = hosePPM.upDownList.size();
            if ((thisSize != 0) && (thatSize != 0)) {
                if (thisSize != thatSize) {
                    equals = false;
                } else {
                    boolean sameEquals = true;
                    boolean revEquals = true;
                    for (int i = 0; i < thisSize; i++) {
                        if (!Objects.equals(upDownList.get(i), hosePPM.upDownList.get(i))) {
                            sameEquals = false;
                        }
                        if (upDownList.get(i) != -hosePPM.upDownList.get(i)) {
                            revEquals = false;
                        }
                        if (!sameEquals && !revEquals) {
                            equals = false;
                            break;
                        }
                    }
                }
            }
            return equals;
        }

        public HashBag getShellBag(final int iShell) {
            HashBag bag = new HashBag();
            if (iShell >= shells.length) {
                return bag;
            }
            String shell = shells[iShell];
            int shellLength = shell.length();

            for (int i = 0; i < shellLength; i++) {
                char shellChar = shell.charAt(i);
                if ((shellChar >= 'A') && (shellChar <= 'Z')) {
                    String element = "" + shellChar;
                    if (i < (shellLength - 1)) {
                        char shellChar2 = shell.charAt(i + 1);
                        if ((shellChar2 >= 'a') && (shellChar2 <= 'z')) {
                            element = element + shellChar2;
                        }
                    }
                    if (i > 0) {
                        int bondIndex = "=%*".indexOf(shell.charAt(i - 1)) + 2;
                        element = element + bondIndex;
                    }
                    bag.add(element);
                }
            }
            return bag;
        }

        public int shellEquals(HOSEPPM hosePPM) {
            int nEqual = 0;
            for (int i = 0; i < shells.length; i++) {
                //System.out.println(shells[i] + " " + hosePPM.shells[i]);
                if (!shells[i].equals(hosePPM.shells[i])) {
                    break;
                }
                nEqual++;
            }
            return nEqual;
        }

        @Override
        public String toString() {
            return code + "  " + ppmC + " " + ppmH;
        }
    }

    public double compareBags(HashBag bag1, HashBag bag2) {
        if ((bag1.isEmpty()) && (bag2.isEmpty())) {
            return 1.0;
        }
        TreeSet unionSet = new TreeSet();
        unionSet.addAll(bag1.uniqueSet());
        unionSet.addAll(bag2.uniqueSet());

        double denom = 0.0;
        double numer = 0.0;
        for (Object obj : unionSet) {
            int count1 = bag1.getCount(obj);
            int count2 = bag2.getCount(obj);
            numer += Math.min(count1, count2);
            denom += Math.max(count1, count2);
        }
        double distance = 0.0;
        if (denom != 0.0) {
            distance = numer / denom;
        }
        return distance;
    }

    static ArrayList<Integer> compressStereo(String hoseStereo) {
        int shell4Limit = hoseStereo.indexOf(")");
        ArrayList<Integer> stereoList = new ArrayList<>();
        int j = 0;
        for (int i = 0; i < shell4Limit; i++) {
            switch (hoseStereo.charAt(i)) {
                case '1':
                    stereoList.add(j);
                    j++;
                    break;
                case '2':
                    stereoList.add(-j);
                    j++;
                    break;
                case '0':
                    j++;
                    break;
                default:
                    break;
            }
        }
        return stereoList;
    }

    public boolean getStereoMode() {
        return stereoMode;
    }

    public void setStereoMode(final boolean stereoMode) {
        this.stereoMode = stereoMode;
    }

    void start() {
        index = 0;
    }

    public void genIndex() {
        hoseList.clear();
        codeStarts.clear();
        int len = buffer.length;
        int start = 0;
        int iShell = -1;
        int j = 0;
        for (int i = 0; i < len; i++) {
            if (buffer[i] == '\n') {
                int strLen = i - start;
                String s = new String(buffer, start, strLen, charset);
                if (s.startsWith("shell")) {
                    iShell = Integer.parseInt(s.substring(5));
                    shellIndices[iShell] = new ArrayList<>();
                } else if (s.equals("codes")) {
                    iShell = -1;
                } else {
                    if (iShell >= 0) {
                        shellIndices[iShell].add(start);
                    } else {
                        codeStarts.add(start);
                        hoseList.add(j++);
                    }
                }
                start = i + 1;
            }
        }
        System.out.println("hosesize " + hoseList.size());
//        Collections.sort(hoseList, (Comparator) new HOSEComparator());
    }

    public int count() {
        int n = hoseList.size();
        int nMatched = 0;
        ArrayList<Double> cPPMs = new ArrayList<>();
        ArrayList<Double> hPPMs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            cPPMs.clear();
            hPPMs.clear();
            HOSEPPM hosePPM = getHose(i);
            int nMatch = 0;
            if (hosePPM.ppmC != null) {
                cPPMs.add(hosePPM.ppmC);
            }
            if (hosePPM.ppmH != null) {
                hPPMs.add(hosePPM.ppmH);
            }
            for (int j = (i + 1); j < n; j++) {
                HOSEPPM testHOSE = getHose(j);
                if (hosePPM.shellEquals(testHOSE) != maxShells) {
                    break;
                }
                if (testHOSE.ppmC != null) {
                    cPPMs.add(testHOSE.ppmC);
                }
                if (testHOSE.ppmH != null) {
                    hPPMs.add(testHOSE.ppmH);
                }
                nMatch++;
            }
            if (nMatch > 0) {
                if (cPPMs.size() > 1) {
                    HOSEStat cStat = new HOSEStat(cPPMs);
                    if (cStat.range > 30.0) {
                        System.out.println(cPPMs);
                        System.out.println("C " + cStat.range + " " + hosePPM.code);
                    }
                }
                if (hPPMs.size() > 1) {
                    HOSEStat hStat = new HOSEStat(hPPMs);
                    if (hStat.range > 1.0) {
                        System.out.println(hPPMs);
                        System.out.println("H " + hStat.range + " " + hosePPM.code);
                    }
                }
                nMatched++;
            }
            i += nMatch;
        }
        System.out.println(nMatched);
        return 0;
    }

    public int find(String s) {
        int pos = Collections.binarySearch(hoseList, s, (Comparator) new HOSEComparator());
        int hosePos = -1;
        //System.out.println("find at " + pos);

        if (pos >= 0) {
            hosePos = hoseList.get(pos);
            //System.out.println(getHose(hosePos));
        } else {
            pos = -(pos + 1);
            if ((pos > 0) && (pos < hoseList.size())) {
                hosePos = hoseList.get(pos);
                HOSEPPM thisHose = getHose(hosePos);
                String thisString = thisHose.code;
                int nMatchThis = 0;
                for (int i = 0; (i < s.length()) && (i < thisString.length()); i++) {
                    if (s.charAt(i) != thisString.charAt(i)) {
                        break;
                    }
                    nMatchThis++;
                }

                int prevPos = hosePos - 1;
                HOSEPPM prevHose = getHose(prevPos);
                String prevString = prevHose.code;
                int nMatchPrev = 0;
                for (int i = 0; (i < s.length()) && (i < prevString.length()); i++) {
                    if (s.charAt(i) != prevString.charAt(i)) {
                        break;
                    }
                    nMatchPrev++;
                }
                if (nMatchPrev > nMatchThis) {
                    hosePos = prevPos;
                }
            }
        }
        return hosePos;
    }

    public int find(HOSEPPM hosePPM) {
        return find(hosePPM.code);
    }

    public PredictResult[] predictHC(String hoseString, String stereoString) {
        HOSEPPM hosePPM;
        if (!stereoString.equals("")) {
            ArrayList<Integer> hoseStereo = compressStereo(stereoString);
            hosePPM = new HOSEPPM(hoseString, hoseStereo);
        } else {
            hosePPM = new HOSEPPM(hoseString);
        }
        int iShell = maxShells;
        PredictResult[] pResults = new PredictResult[2];
        for (; iShell > 0; iShell--) {
            PredictResult pResult = predict(hosePPM, iShell);
            if (pResults[0] == null) {
                HOSEStat stat = pResult.getStat("1H");
                if ((stat != null) && (stat.nValues >= 1)) {
                    pResults[0] = pResult;
                }
            }
            if (pResults[1] == null) {
                HOSEStat stat = pResult.getStat("13C");
                if ((stat != null) && (stat.nValues >= 1)) {
                    pResults[1] = pResult;
                }
            }
            if ((pResults[0] != null) && (pResults[1] != null)) {
                break;
            }
        }
        return pResults;
    }

    public PredictResult[] predictHN(String hoseString, String stereoString) {
        HOSEPPM hosePPM;
        if (!stereoString.equals("")) {
            ArrayList<Integer> hoseStereo = compressStereo(stereoString);
            hosePPM = new HOSEPPM(hoseString, hoseStereo);
        } else {
            hosePPM = new HOSEPPM(hoseString);
        }
        int iShell = maxShells;
        PredictResult[] pResults = new PredictResult[2];
        for (; iShell > 0; iShell--) {
            PredictResult pResult = predict(hosePPM, iShell);
            if (pResults[0] == null) {
                HOSEStat stat = pResult.getStat("1H");
                if ((stat != null) && (stat.nValues >= 1)) {
                    pResults[0] = pResult;
                }
            }
            if (pResults[1] == null) {
                HOSEStat stat = pResult.getStat("15N");
                if ((stat != null) && (stat.nValues >= 1)) {
                    pResults[1] = pResult;
                }
            }
            if ((pResults[0] != null) && (pResults[1] != null)) {
                break;
            }
        }
        return pResults;
    }

    public PredictResult predict(HOSEPPM hosePPM, String elemType) {
        int iShell = maxShells;
        PredictResult pResult = null;
        for (; iShell > 0; iShell--) {
            pResult = predict(hosePPM, iShell);
            if (pResult != null) {
                HOSEStat stat = pResult.getStat(elemType);
//System.out.println(stat.nValues);
                if ((stat != null) && (stat.nValues >= 1)) {
                    break;
                }
            }
        }
        return pResult;
    }

    public PredictResult predict(HOSEPPM hosePPM, int nShells) {
        ArrayList<Double> cPPMs = new ArrayList<>();
        ArrayList<Double> hPPMs = new ArrayList<>();
        ArrayList<Double> cDistances = new ArrayList<>();
        ArrayList<Double> hDistances = new ArrayList<>();
        int pos = find(hosePPM);
//System.out.println("pred pos " + pos + " with shell " + nShells);
        HashBag hoseBag = hosePPM.getShellBag(nShells + 1);
        int searchPos = pos;
        while ((searchPos >= 0)) {
            HOSEPPM testHOSE = getHose(searchPos);
            int nEqual = testHOSE.shellEquals(hosePPM);
//System.out.println("AnEqual " + searchPos + " " +  nEqual + " " + testHOSE.ppmC);
            if (nEqual < nShells) {
                break;
            }
            if (nEqual >= 4) {
                if (stereoMode && !testHOSE.stereoEquals(hosePPM)) {
                    searchPos--;
                    continue;
                }
            }
            HashBag testBag = testHOSE.getShellBag(nShells + 1);
            double distance = compareBags(hoseBag, testBag);
            if (testHOSE.ppmC != null) {
                cPPMs.add(testHOSE.ppmC);
                cDistances.add(distance);
            }
            if (testHOSE.ppmH != null) {
                hPPMs.add(testHOSE.ppmH);
                hDistances.add(distance);
            }
            searchPos--;
        }
        searchPos = pos + 1;
        while ((searchPos >= 0) && (searchPos < hoseList.size())) {
            HOSEPPM testHOSE = getHose(searchPos);
            int nEqual = testHOSE.shellEquals(hosePPM);
//System.out.println("BnEqual " + searchPos + " " +  nEqual + " " + testHOSE.ppmC);
            if (nEqual < nShells) {
                break;
            }
            if (nEqual >= 4) {
                if (stereoMode && !testHOSE.stereoEquals(hosePPM)) {
                    searchPos++;
                    continue;
                }
            }
            HashBag testBag = testHOSE.getShellBag(nShells + 1);
            double distance = compareBags(hoseBag, testBag);
            if (testHOSE.ppmC != null) {
                cPPMs.add(testHOSE.ppmC);
                cDistances.add(distance);
            }
            if (testHOSE.ppmH != null) {
                hPPMs.add(testHOSE.ppmH);
                hDistances.add(distance);
            }
            searchPos++;
        }
        HOSEStat hStat = null;
        if (hPPMs.size() > 0) {
            hStat = new HOSEStat(hPPMs, hDistances);
        }
        HOSEStat cStat = new HOSEStat(cPPMs, cDistances);
        PredictResult predResult = new PredictResult(cStat, hStat, nShells);
        return predResult;
    }

    void validate(String validate) {
        ArrayList<Double> cPPMs = new ArrayList<>();
        ArrayList<Double> hPPMs = new ArrayList<>();
        double[] deltaSums = new double[2];
        int[] nValues = new int[2];
        int[] nNulls = new int[2];
        int[] nViols = new int[2];
        try {
            FileReader reader = new FileReader(validate);
            LineNumberReader lineReader = new LineNumberReader(reader);
            while (true) {
                cPPMs.clear();
                hPPMs.clear();
                String string = lineReader.readLine();
                if (string == null) {
                    break;
                }
                String[] values = string.split("\t");
                String[] ppmValues = values[5].split(" ");
                String[] hoseString = values[4].split(" ");
                ArrayList<Integer> upDownList = compressStereo(hoseString[1]);

                String smile = values[1];
                String molNum = values[2];
                for (int i = 0; i < ppmValues.length; i += 2) {
                    if (ppmValues[i].equals("13C")) {
                        cPPMs.add(Double.parseDouble(ppmValues[i + 1]));
                    }
                    if (ppmValues[i].equals("15N")) {
                        cPPMs.add(Double.parseDouble(ppmValues[i + 1]));
                    }
                    if (ppmValues[i].equals("1H")) {
                        hPPMs.add(Double.parseDouble(ppmValues[i + 1]));
                    }
                }
//System.out.println(cPPMs.size() + " " + hPPMs.size() + " " + hoseString[0]);
                String[] statNames = {"13C", "1H"};
                double[] extra = {2.0, 0.4};
                for (int i = 0; i < 2; i++) {
                    double ppm;
                    if (i == 0) {
                        if (cPPMs.isEmpty()) {
                            continue;
                        } else {
                            HOSEStat cStat = new HOSEStat(cPPMs);
                            ppm = cStat.dStat.getPercentile(50.0);
                        }
                    } else {
                        if (hPPMs.isEmpty()) {
                            continue;
                        } else {
                            HOSEStat hStat = new HOSEStat(hPPMs);
                            ppm = hStat.dStat.getPercentile(50.0);
                        }
                    }
                    PredictResult pResult;
                    HOSEStat stat = null;
                    int iShell = maxShells;
                    for (; iShell > 0; iShell--) {
                        HOSEPPM hosePPM = new HOSEPPM(hoseString[0], upDownList);
                        pResult = predict(hosePPM, iShell);
                        stat = pResult.getStat(statNames[i]);
                        if (stat != null) {
                            if (stat.nValues >= 1) {
                                break;
                            }
                        }
                    }
                    if ((stat == null) || (stat.nValues < 1)) {
                        nNulls[i]++;
                    } else {
                        nValues[i]++;
                        //double predPPM = stat.dStat.getPercentile(50.0);
                        double predPPM = stat.wmean;
                        deltaSums[i] += (ppm - predPPM) * (ppm - predPPM);
                        System.out.printf("%s\t%8.2f\t%8.2f\t%8.2f\t%d\t%d\t%s\t%s\t%s\n", statNames[i], ppm, predPPM, Math.abs(ppm - predPPM), iShell, stat.nValues, hoseString[0], smile, molNum);
                    }
                    if ((stat == null) || (stat.nValues < 4)) {
                        if (stat == null) {
                            //System.err.println(lineReader.getLineNumber() + " " + statNames[i] + " " + hoseString[0] + " null " + iShell + " " +  ppm);
                        } else {
                            //System.err.println(lineReader.getLineNumber() + " " + statNames[i] + " " + hoseString[0] + " insufficient " + stat.nValues + " " +  iShell + " " + ppm);
                        }
                    } else {
                        double p0 = stat.dStat.getPercentile(1.);
                        double p1 = stat.dStat.getPercentile(99.);
                        double extraValue = extra[i] * (maxShells - iShell);
                        if ((ppm < (p0 - extraValue)) || (ppm > (p1 + extraValue))) {
                            //System.err.println(lineReader.getLineNumber() + " " + statNames[i] + " " + hoseString[0] + " " + (p0-extraValue) + " " + ppm + " " + (p1+extraValue) + " " + stat.nValues + " " + iShell);
                            nViols[i]++;
                        }
                    }
                }
            }
            reader.close();
        } catch (IOException ioE) {
        }
        for (int i = 0; i < 2; i++) {
            System.err.println(nValues[i] + " " + deltaSums[i] + " " + Math.sqrt(deltaSums[i] / nValues[i]) + " " + nViols[i] + " " + nNulls[i]);
        }
    }

    public HOSEPPM getHose(int i) {
        int pos = codeStarts.get(i);
        return getHoseAtPosition(pos);
    }

    HOSEPPM getNextHose() {
        return getHoseAtPosition(index);
    }

    HOSEPPM getHoseAtPosition(int position) {
        StringBuilder hoseCode = new StringBuilder();
        if (position >= buffer.length) {
            return null;
        }
        int start = position;
        for (int i = 0; i < nShellGroups; i++) {
            String s = new String(buffer, start, 4, charset);
            int index = Integer.parseUnsignedInt(s, 16);
            String frag;
            if (index < 128) {
                frag = new String(buffer, start + 4, index, charset);
                start += 4 + index;
            } else {
                index -= 128 + 1;
                int fragStart = shellIndices[i].get(index);
                int fragEnd = fragStart;
                for (int j = fragStart + 1; j < buffer.length; j++) {
                    if (buffer[j] == '\n') {
                        fragEnd = j;
                        break;
                    }
                }
                frag = new String(buffer, fragStart, fragEnd - fragStart, charset);
                start += 4;
            }
            hoseCode.append(frag);
            if (i < (nShellGroups - 1)) {
                hoseCode.append("/");
            }
        }
        for (int i = hoseCode.length() - 1; i >= 0; i--) {
            if (hoseCode.charAt(i) != '/') {
                break;
            }
            hoseCode.setLength(i);
        }
        String[] hoseShells = HOSEPPM.stringToShells(hoseCode.toString());
        String s = new String(buffer, start, 4, charset);
        Double ppmC = null;
        Double ppmH = null;
        int c13Int = Integer.parseUnsignedInt(s, 16);
        if (c13Int != 0) {
            ppmC = ((double) c13Int) / 32768 * 300.0 - 50.0;
        }
        start += 4;
        s = new String(buffer, start, 4, charset);
        int h1Int = Integer.parseUnsignedInt(s, 16);
        if (h1Int != 0) {
            ppmH = ((double) h1Int) / 32768 * 30.0 - 10.0;
        }

        ArrayList<Integer> upDownList = new ArrayList<>();
        HOSEPPM hosePPM = new HOSEPPM(hoseCode.toString(), hoseShells, ppmC, ppmH, upDownList);
        return hosePPM;
    }

    public void openData(String fileName, boolean resourceMode) {
        try {
            InputStream iStream;
            if (resourceMode) {
                System.out.println("open " + fileName);
                iStream = ClassLoader.getSystemResourceAsStream(fileName);
                System.out.println("got " + iStream);
                ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[16384];
                while ((nRead = iStream.read(data, 0, data.length)) != -1) {
                    byteBuffer.write(data, 0, nRead);
                }
                byteBuffer.flush();
                buffer = byteBuffer.toByteArray();
            } else {
                iStream = new FileInputStream(fileName);
                int size = iStream.available();
                buffer = new byte[size];
                iStream.read(buffer);
            }
            iStream.close();
        } catch (IOException ioE) {
            System.out.println("error " + ioE.getMessage());
        }
    }

    public static HosePrediction getPredictor() {
        System.out.println("getP");
        HosePrediction hosePredictor = new HosePrediction();
        System.out.println("gotP");
        hosePredictor.openData("data/hosecodes.txt", true);
        System.out.println("gotD");
        System.out.println("gotI");
        hosePredictor.genIndex();
        System.out.println("gend");
        return hosePredictor;
    }

    public static HosePrediction getPredictorN() {
        System.out.println("getPN");
        HosePrediction hosePredictor = new HosePrediction();
        System.out.println("gotPN");
        hosePredictor.openData("data/hosecodesN.txt", true);
        System.out.println("gotDN");
        System.out.println("gotIN");
        hosePredictor.genIndex();
        System.out.println("gendN");
        return hosePredictor;
    }

    public static HosePrediction getDefaultPredictor() {
        if (defaultPredictor == null) {
            defaultPredictor = getPredictor();
        }
        return defaultPredictor;
    }

    public static HosePrediction getDefaultPredictorN() {
        if (defaultPredictorN == null) {
            defaultPredictorN = getPredictorN();
        }
        return defaultPredictorN;
    }

    public void dump() {
        int i = 0;
        for (Integer j : hoseList) {
            System.out.println(i + " " + j + " " + codeStarts.get(j) + " " + getHose(j));
            i++;
        }
    }

    public static void main(String[] args) {
        //HosePrediction hosePredictor = new HosePrediction();
        //hosePredictor.openData("hose.bin", false);
        //hosePredictor.readFragmentFiles("hoseidx", false);
        //hosePredictor.genIndex();
        //hosePredictor.count();
        if (args.length < 1) {
            System.out.println("No args");
        } else {
            if (args[0].equals("predict")) {
                String hoseString = args[1];
                HosePrediction hosePredictor;
                if (hoseString.charAt(0) == 'C') {
                    hosePredictor = getPredictor();
                } else {
                    hosePredictor = getPredictorN();
                }
                HOSEPPM hosePPM;
                if (args.length > 2) {
                    ArrayList<Integer> hoseStereo = compressStereo(args[2]);
                    System.out.println("gog stereo " + hoseStereo);
                    hosePPM = new HOSEPPM(hoseString, hoseStereo);
                } else {
                    hosePPM = new HOSEPPM(hoseString);
                }
                int pos = hosePredictor.find(hosePPM);
                System.out.println("position (hose) " + pos);
                pos = hosePredictor.find(hoseString);
                System.out.println("position (string)   " + pos);
                HOSEPPM closestHOSE = hosePredictor.getHose(pos);
                System.out.println("input arg " + hoseString);
                System.out.println("hose ppm  " + hosePPM);
                if (pos > 0) {
                    System.out.println("closest-1 " + hosePredictor.getHose(pos - 1));
                }
                System.out.println("closest   " + closestHOSE);
                if (pos < hosePredictor.hoseList.size() - 1) {
                    System.out.println("closest+1 " + hosePredictor.getHose(pos + 1));
                }
                System.out.println("shell " + hosePPM.shellEquals(closestHOSE));

                System.out.println("predict");
                PredictResult pResult = hosePredictor.predict(hosePPM, "13C");
                System.out.println(pResult.cStat.nValues + " " + pResult.cStat.range + " " + pResult.cStat.dStat.getPercentile(50.0));
            } else if (args[0].equals("validate")) {
                System.out.println("validate file");
                HosePrediction hosePredictor;
                if (args[1].charAt(0) == 'C') {
                    hosePredictor = getPredictor();
                } else {
                    hosePredictor = getPredictorN();
                }
                String hoseFile = args[2];
                hosePredictor.validate(hoseFile);
            } else if (args[0].equals("dump")) {
                HosePrediction hosePredictor;
                if (args[1].charAt(0) == 'C') {
                    hosePredictor = getPredictor();
                } else {
                    hosePredictor = getPredictorN();
                }
                hosePredictor.dump();
            } else {
                System.out.println("Invalid arg " + args[0]);
            }
        }
    }
}
