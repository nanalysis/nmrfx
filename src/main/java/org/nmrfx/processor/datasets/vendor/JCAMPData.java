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
package org.nmrfx.processor.datasets.vendor;

import org.nmrfx.processor.datasets.parameters.FPMult;
import org.nmrfx.processor.datasets.parameters.GaussianWt;
import org.nmrfx.processor.datasets.parameters.LPParams;
import org.nmrfx.processor.datasets.parameters.SinebellWt;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.SampleSchedule;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.complex.Complex;

/**
 * JCAMPData implements NMRData methods for opening and reading parameters and
 * FID data in a JCAMP file.
 *
 * @author bfetler
 * @see NMRData
 * @see NMRDataUtil
 */
class JCAMPData implements NMRData {

    private int tbytes = 0;             // TD,1
    private int np;                   // TD,1
    private int nvectors;             // NS,1
    private int dim = 0;                // from acqu[n]s files
    private boolean swapBits = false; // BYTORDA,1
    private double dspph = 0.0;         // GRPDLY,1 etc.
    private double groupDelay = 0.0;         // GRPDLY,1 etc.
    private boolean exchangeXY = false;
    private boolean negatePairs = false;
    private boolean fixDSP = true;
    private boolean fixByShift = false;
    private final boolean[] complexDim = new boolean[5];
    private final double[] f1coef[] = new double[5][];   // FnMODE,2 MC2,2
    private final String[] f1coefS = new String[5];   // FnMODE,2 MC2,2
    private final String fttype[] = new String[5];
    private final int tdsize[] = new int[5];  // TD,1 TD,2 etc.
    private final int maxSize[] = new int[5];  // TD,1 TD,2 etc.
    private double deltaPh0_2 = 0.0;
    // fixme dynamically determine size
    private final Double[] Ref = new Double[5];
    private final Double[] Sf = new Double[5];
    private final Double[] Sw = new Double[5];
    private final String[] Tn = new String[5];
    private String text = null;

    private final String fpath;
    private FileChannel fc = null;
    private HashMap<String, String> parMap = null;
    private static final HashMap<String, Double> PHASE_TABLE = null;
    private String[] acqOrder;
    private SampleSchedule sampleSchedule = null;
    final static Logger LOGGER = Logger.getLogger("org.nmrfx.processor.datasets.Dataset");
    final static double SCALE = 1.0;
    boolean hasFID = false;
    boolean hasSpectrum = false;
    ASDFParser rparser = null;
    ASDFParser iparser = null;

    /**
     * Open Bruker parameter and data files.
     *
     * @param path full path to the fid directory or file
     */
    public JCAMPData(String path) {
        if (path.endsWith(File.separator)) {
            path = path.substring(0, path.length() - 1);
        }
        this.fpath = path;
        openParFile(path);
        openDataFile(path);
    }

    @Override
    public void close() {
        try {
            fc.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e.getMessage());
        }
    }

    /**
     * Finds data, given a path to search for vendor-specific files and
     * directories.
     *
     * @param bpath full path for data
     * @return if data was successfully found or not
     */
    protected static boolean findData(StringBuilder bpath) {
        boolean found = false;
        if (bpath.toString().toUpperCase().endsWith(".JDX")) {
            found = true;
        } else if (bpath.toString().toUpperCase().endsWith(".DX")) {
            found = true;
        }
        return found;
    }

    /**
     * Finds FID data, given a path to search for vendor-specific files and
     * directories.
     *
     * @param bpath full path for FID data
     * @return if FID data was successfully found or not
     */
    protected static boolean findFID(StringBuilder bpath) {
        boolean found = false;
        if (bpath.toString().toUpperCase().endsWith(".JDX")) {
            found = true;
        } else if (bpath.toString().toUpperCase().endsWith(".DX")) {
            found = true;
        }
        return found;
    }

    @Override
    public String toString() {
        return fpath;
    }

    @Override
    public String getVendor() {
        return "bruker";
    }

    @Override
    public Map getFidFlags() {
        Map<String, Boolean> flags = new HashMap(5);
        return flags;
    }

    @Override
    public String getFilePath() {
        return fpath;
    }

    @Override
    public String getPar(String parname) {
        if (parMap == null) {
            return null;
        } else {
            return parMap.get(parname);
        }
    }

    @Override
    public Double getParDouble(String parname) {
        if ((parMap == null) || (parMap.get(parname) == null)) {
            return null;
//            throw new NullPointerException();
        } else {
            return Double.parseDouble(parMap.get(parname));
        }
    }

    @Override
    public Integer getParInt(String parname) {
        if ((parMap == null) || (parMap.get(parname) == null)) {
            return null;
        } else {
            return Integer.parseInt(parMap.get(parname));
        }
    }

    @Override
    public int getNVectors() {  // number of vectors
        return nvectors;
    }

    @Override
    public int getNPoints() {  // points per vector
        return np / 2;
    }

    @Override
    public int getNDim() {
        return dim;
    }

    @Override
    public boolean isComplex(int iDim) {
        return complexDim[iDim];
    }

    // used only for indirect dimensions; direct dimension done when FID read 
    @Override
    public boolean getNegatePairs(int iDim) {
        return (fttype[iDim].equals("negate"));
    }

    @Override
    public boolean getNegateImag(int iDim) {
        return iDim > 0;
    }

    @Override
    public double[] getCoefs(int iDim) {
        return f1coef[iDim];
    }

    @Override
    public String getSymbolicCoefs(int iDim) {
        return f1coefS[iDim];
    }

    @Override
    public String[] getSFNames() {
        int nDim = getNDim();
        String[] names = new String[nDim];
        for (int i = 0; i < nDim; i++) {
            names[i] = ".OBSERVEFREQUENCY," + (i + 1);
        }
        return names;
    }

    @Override
    public String[] getSWNames() {
        int nDim = getNDim();
        String[] names = new String[nDim];
        for (int i = 0; i < nDim; i++) {
            names[i] = "";
        }
        return names;
    }

    @Override
    public String[] getLabelNames() {
        int nDim = getNDim();
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < nDim; i++) {
            String name = getTN(i);
            if (names.contains(name)) {
                name = name + "_" + (i + 1);
            }
            names.add(name);
        }
        return names.toArray(new String[names.size()]);
    }

    @Override
    public double getSF(int iDim) {
        return Sf[iDim];
    }

    @Override
    public void setSF(int iDim, double value) {
        Sf[iDim] = value;
    }

    @Override
    public void resetSF(int iDim) {
    }

    @Override
    public double getSW(int iDim) {
        return Sw[iDim];
    }

    @Override
    public void setSW(int iDim, double value) {
        Sw[iDim] = value;
    }

    @Override
    public void resetSW(int iDim) {
    }

    @Override
    public int getSize(int iDim) {
        return tdsize[iDim];
    }

    @Override
    public int getMaxSize(int iDim) {
        return maxSize[iDim];
    }

    @Override
    public void setSize(int iDim, int size) {
        if (size > maxSize[iDim]) {
            size = maxSize[iDim];
        }
        tdsize[iDim] = size;
    }

    @Override
    public void setRef(int iDim, double ref) {
        Ref[iDim] = ref;
    }

    @Override
    public void resetRef(int iDim) {
        Ref[iDim] = null;
    }

    @Override
    public double getRef(int iDim) {
        double ref = 0.0;
        if (Ref[iDim] != null) {
            ref = Ref[iDim];
        } else {
            Double dpar;
            if ((dpar = getParDouble("TRANSMITPOS," + (iDim + 1))) != null) {
                ref = dpar;
            }
            setRef(iDim, ref);
        }
        return ref;
    }

    @Override
    public double getRefPoint(int dim) {
        return 1.0;
    }

    @Override
    public String getTN(int iDim) {
        String tn;
        if (Tn[iDim] != null) {
            tn = Tn[iDim];
        } else {
            tn = getPar(".OBSERVENUCLEUS," + (iDim + 1));
            if (tn == null) {
                double sf = getSF(iDim);
                tn = NMRDataUtil.guessNucleusFromFreq(sf).get(0).toString();
            } else if ((tn.length() > 1) && (tn.charAt(0) == '^')) {
                tn = tn.substring(1);
            }
            if (tn == null) {
                tn = "";
            }
            Tn[iDim] = tn;
        }
        return tn;
    }

    @Override
    public String getSolvent() {
        String s;
        if ((s = getPar("SOLVENTNAME,1")) == null) {
            s = "";
        }
        return s;
    }

    @Override
    public double getTempK() {
        Double d;
        if ((d = getParDouble("TEMPERATURE,1")) == null) {
// fixme what should we return if not present, is it ever not present
            d = 298.0;
        }
        return d;
    }

    @Override
    public String getSequence() {
        String s;
        if ((s = getPar("PULPROG,1")) == null) {
            s = "";
        }
        return s;
    }

    @Override
    public long getDate() {
        String s;
        long seconds = 0;
        if ((s = getPar("DATE,1")) != null) {
            try {
                System.out.println("sec " + s);
                seconds = Long.parseLong(s);
            } catch (NumberFormatException e) {
            }
        } else {
            System.out.println("no date");
        }
        return seconds;
    }

    // open and read Bruker text file
    public String getText() {
        if (text == null) {
            String textPath = "";
            text = "";
        }
        return text;
    }

    @Override
    public double getPH0(int iDim) {
        double ph0 = 0.0;
        Double dpar;
        if ((dpar = getParDouble("PHC0," + (iDim + 1))) != null) {
            ph0 = -dpar;
            if (iDim == 0) {
                ph0 += 90.0;
            } else if (iDim == 1) {
                ph0 += deltaPh0_2;
            }
        }
        return ph0;
    }

    @Override
    public double getPH1(int iDim) {
        double ph1 = 0.0;
        Double dpar;
        if ((dpar = getParDouble("PHC1," + (iDim + 1))) != null) {
            ph1 = -dpar;
        }
        return ph1;
    }

    @Override
    public int getLeftShift(int iDim) {
        int shift = 0;
        Integer ipar;
        if ((ipar = getParInt("LS," + (iDim + 1))) != null) {
            shift = -ipar;
        }
        return shift;
    }

    @Override
    public double getExpd(int iDim) {
        double expd = 0.0;
        Integer wdw = getParInt("WDW," + (iDim + 1));
        String spar;
        if (wdw != null && wdw == 1) {
            if ((spar = getPar("LB," + (iDim + 1))) != null) {
                if (!spar.equals("n")) {
                    expd = Double.parseDouble(spar);
                }
            }
        }
        return expd;
    }

    @Override
    public SinebellWt getSinebellWt(int iDim) {
        return new BrukerSinebellWt(iDim);
    }

    @Override
    public GaussianWt getGaussianWt(int iDim) {
        return new BrukerGaussianWt(iDim);
    }

    @Override
    public FPMult getFPMult(int iDim) {
        return new FPMult(); // does not exist in Bruker params
    }

    @Override
    public LPParams getLPParams(int iDim) {
        return new LPParams(); // does not exist in Bruker params
    }

    // open Bruker parameter file(s)
    private void openParFile(String parpath) {
        parMap = new LinkedHashMap<>(200);
        // process proc files if they exist
        String path = parpath;
        try {
            BrukerPar.processBrukerParFile(parMap, path, 1, true);
        } catch (NMRParException ex) {
            LOGGER.log(Level.WARNING, ex.getMessage());
        }
        // process acqu files if they exist
        int acqdim = 1;
        this.dim = acqdim;
        setPars();
        setArrayPars(acqdim);
    }

    private void setPars() {
        String dataClass = "";
        String spar;
        int iDim = 0;
        if ((spar = getPar("DATACLASS," + (iDim + 1))) != null) {
            dataClass = spar;
        }
        double sw = 1000.0;
        double firstX = 0;
        double lastX = 0;
        int nPoints = 0;
        double xFactor;
        double yFactor;
        double rFactor;
        double iFactor;
        String units = "";
        if (dataClass.equals("XYDATA")) {
            Sf[0] = getParDouble(".OBSERVEFREQUENCY," + (iDim + 1));
            System.out.println("sf " + Sf[0] + " " + iDim);
            Tn[0] = getPar(".OBSERVENUCLEUS," + (iDim + 1));
            firstX = getParDouble("FIRSTX," + (iDim + 1));
            System.out.println("firstx " + firstX + " " + iDim);
            lastX = getParDouble("LASTX," + (iDim + 1));
            System.out.println("lastx " + lastX + " " + iDim);
            units = getPar("XUNITS," + (iDim + 1));
            nPoints = getParInt("NPOINTS," + (iDim + 1));
            tdsize[0] = nPoints;
            System.out.println("tdsize " + tdsize[0] + " " + iDim);
            xFactor = getParDouble("XFACTOR," + (iDim + 1));
            yFactor = getParDouble("YFACTOR," + (iDim + 1));

            String xyData = getPar("XYDATA," + (iDim + 1));
            int firstLF = xyData.indexOf('\n');
            firstLF++;
            fromASDF(xyData.substring(firstLF), firstX, lastX, xFactor, yFactor, nPoints);

        } else if (dataClass.equals("NTUPLES")) {
            Sf[0] = getParDouble(".OBSERVEFREQUENCY," + (iDim + 1));
            Tn[0] = getPar(".OBSERVENUCLEUS," + (iDim + 1));
            String values = getPar("FIRST," + (iDim + 1));
            String[] valueArray = values.split(",");
            firstX = Double.parseDouble(valueArray[0].trim());

            values = getPar("LAST," + (iDim + 1));
            valueArray = values.split(",");
            lastX = Double.parseDouble(valueArray[0].trim());

            values = getPar("UNITS," + (iDim + 1));
            valueArray = values.split(",");
            units = valueArray[0].trim();

            values = getPar("VARDIM," + (iDim + 1));
            valueArray = values.split(",");
            nPoints = Integer.parseInt(valueArray[0].trim());
            tdsize[0] = nPoints;

            values = getPar("FACTOR," + (iDim + 1));
            valueArray = values.split(",");
            xFactor = Double.parseDouble(valueArray[0]);
            rFactor = Double.parseDouble(valueArray[1]);
            iFactor = Double.parseDouble(valueArray[2]);

            String xyDataR = getPar("DATATABLE1," + (iDim + 1));
            int firstLFR = xyDataR.indexOf('\n');
            firstLFR++;
            String xyDataI = getPar("DATATABLE2," + (iDim + 1));
            int firstLFI = xyDataI.indexOf('\n');
            firstLFI++;

            fromASDF(xyDataR.substring(firstLFR), xyDataI.substring(firstLFI), firstX, lastX, xFactor, rFactor, iFactor, nPoints);

        }
        Tn[0] = Tn[0].replace("^", "");
        double sf = Sf[0];
        Sw[0] = 1000.0;
        switch (units) {
            case "HZ": {
                Sw[0] = Math.abs(lastX - firstX);
                double swP = (Sw[0] / sf);
                Ref[0] = firstX / Sf[0];
                break;
            }
            case "PPM": {
                double swP = Math.abs(lastX - firstX);
                Sw[0] = sf / swP;
                Ref[0] = firstX;
                break;
            }
            case "SECONDS": {
                double dwell = Math.abs(lastX - firstX) / nPoints;
                Sw[0] = 1.0 / dwell;
                double swP = (Sw[0] / sf);
                Ref[0] = 0.0;
                break;
            }
            default:
                break;
        }
        maxSize[iDim] = tdsize[iDim];

    }

    private void setArrayPars(int nDim) {
        // set nvectors, see tcl lines 405, 418
        // fixme need to figure out if complex or not
        int num = 1;
        for (int i = 1; i < nDim; i++) {
            num *= getSize(i) * 2;
        }
        nvectors = num;
    }

    private void setFTpars() {
        // see bruker.tcl line 781-820
        Integer fnmode;
        complexDim[0] = true;  // same as exchange really
        exchangeXY = false;
        negatePairs = false;
        fttype[0] = "ft";
    }

    /**
     * Set flags before FID data is read using readVector. Flags are only active
     * on BrukerData. Allowable flags are 'fixdsp', 'exchange', 'swapbits',
     * 'negatepairs', with values of True or False. For example, in python:
     * <p>
     * f = FID(serDir) f.flags = {'fixdsp':True,
     * 'shiftdsp':True,'exchange':True, 'swapbits':True, 'negatepairs':False}
     * CREATE(serDir+'hmqc.nv', dSizes, f)
     * </p>
     *
     * @param flags a Map of String / boolean key value pairs
     */
    @Override
    public void setFidFlags(Map flags) {

    }

    private void setSwapBitsOff() {
        swapBits = false;
    }

    private void setSwapBitsOn() {
        swapBits = true;
    }

    private void setExchangeOn() {
        exchangeXY = true;
    }

    private void setExchangeOff() {
        exchangeXY = false;
    }

    private void setNegatePairsOn() {
        negatePairs = true;
    }

    private void setNegatePairsOff() {
        negatePairs = false;
    }

    private void setFixDSPOn() {
        fixDSP = true;
    }

    private void setFixDSPOff() {
        fixDSP = false;
    }

    private void setDSPShiftOn() {
        fixByShift = true;
    }

    private void setDSPShiftOff() {
        fixByShift = false;
    }

    private void setDspph() {
    }

    // open Bruker file, read fid data
    private void openDataFile(String datapath) {
        if ((new File(datapath)).exists()) {
        }
        System.out.println("open data file " + datapath);
        try {
            fc = FileChannel.open(Paths.get(datapath), StandardOpenOption.READ);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, ex.getMessage());
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, e.getMessage());
                }
            }
        }
    }

    public void fromASDF(String asdfString, double xFirst, double xLast, double xFactor, double yFactor, int nPoints) {
        rparser = new ASDFParser(xFirst, xLast, xFactor, yFactor, nPoints);
        rparser.fromASDF(asdfString);
    }

    public void fromASDF(String rString, String iString, double xFirst, double xLast, double xFactor, double rFactor, double iFactor, int nPoints) {
        rparser = new ASDFParser(xFirst, xLast, xFactor, rFactor, nPoints);
        iparser = new ASDFParser(xFirst, xLast, xFactor, iFactor, nPoints);

        rparser.fromASDF(rString);
        iparser.fromASDF(iString);
    }

    @Override
    public void readVector(int iVec, Vec dvec) {
        dvec.setGroupDelay(groupDelay);
        ArrayList<Double> rValues = rparser.getYValues();
        int n = rValues.size();

        if (iparser == null) {
            dvec.resize(n, false);
            for (int i = 0; i < n; i++) {
                dvec.set(i, rValues.get(i));
            }
        } else {
            ArrayList<Double> iValues = iparser.getYValues();
            dvec.resize(n, true);
            for (int i = 0; i < n; i++) {
                dvec.set(i, iValues.get(i), rValues.get(i));
            }

        }

        dvec.dwellTime = 1.0 / getSW(0);
        dvec.centerFreq = getSF(0);

        double delRef = (dvec.getSize() / 2 - 0) * (1.0 / dvec.dwellTime) / dvec.centerFreq / dvec.getSize();
        dvec.refValue = getRef(0) + delRef;
        //System.out.println("zeroref " + dvec.refValue);
        //dvec.setPh0(getPH0(1));
        //dvec.setPh1(getPH1(1));
    }

    @Override
    public void readVector(int iDim, int iVec, Vec dvec) {
        int shiftAmount = 0;
        if (groupDelay > 0) {
            // fixme which is correct (use ceil or not)
            //shiftAmount = (int)Math.round(Math.ceil(groupDelay));
            shiftAmount = (int) Math.round(groupDelay);
            System.out.println(iVec + " " + groupDelay + " " + shiftAmount);
        }
        if (dvec.isComplex()) {
            if (dvec.useApache()) {
                readVector(iDim, iVec + shiftAmount, dvec.getCvec());
            } else {
// fixme
                readVector(iVec + shiftAmount, dvec.rvec, dvec.ivec);
            }
        } else {
// fixme
            readVector(iVec, dvec.rvec);
        }
        dvec.dwellTime = 1.0 / getSW(iDim);
        dvec.centerFreq = getSF(iDim);
        dvec.refValue = getRef(iDim);
        dvec.setPh0(getPH0(iDim));
        dvec.setPh1(getPH1(iDim));
        if (iDim == 0) {
            dvec.setGroupDelay(groupDelay);
        } else {
            dvec.setGroupDelay(0.0);
        }
    }

    @Override
    public void readVector(int iVec, Complex[] cdata) {
        byte[] dataBuf = new byte[tbytes];
        readVecBlock(iVec, dataBuf);
        copyVecData(dataBuf, cdata);
    }

    @Override
    public void readVector(int iVec, double[] rdata, double[] idata) {
        byte[] dataBuf = new byte[tbytes];
        readVecBlock(iVec, dataBuf);
        copyVecData(dataBuf, rdata, idata);
    }

    @Override
    public void readVector(int iVec, double[] data) {
        byte[] dataBuf = new byte[tbytes];
        readVecBlock(iVec, dataBuf);
        copyVecData(dataBuf, data);
    }

    public void readVector(int iDim, int iVec, Complex[] cdata) {
        int size = getSize(iDim);
        int nPer = 1;
        if (isComplex(iDim)) {
            nPer = 2;
        }
        int nPoints = size * nPer;
        byte[] dataBuf = new byte[nPoints * 4 * 2];
        IntBuffer ibuf = ByteBuffer.wrap(dataBuf).asIntBuffer();
        for (int j = 0; j < (nPoints * 2); j++) {
            ibuf.put(j, 0);
        }
        for (int i = 0; i < (nPoints); i++) {
            if (sampleSchedule != null) {
                int[] point = {i / 2};
                int index = sampleSchedule.getIndex(point);
                if (index != -1) {
                    index = index * 2 + (i % 2);
                    readValue(iDim, index, i, iVec, dataBuf);
                }
            } else {
                readValue(iDim, i, i, iVec, dataBuf);
            }
        }
        for (int j = 0; j < (nPoints * 2); j += 2) {
            int px = ibuf.get(j);
            int py = ibuf.get(j + 1);
            if (swapBits) {
                px = Integer.reverseBytes(px);
                py = Integer.reverseBytes(py);
            }
            cdata[j / 2] = new Complex((double) px / SCALE, (double) py / SCALE);
        }
    }

    // read i'th data block
    private void readVecBlock(int i, byte[] dataBuf) {
        try {
            int skips = i * tbytes;
            ByteBuffer buf = ByteBuffer.wrap(dataBuf);
            int nread = fc.read(buf, skips);
            if (nread < tbytes) // nread < tbytes, nread < np
            {
                throw new ArrayIndexOutOfBoundsException("file index " + i + " out of bounds");
            }
            //System.out.println("readVecBlock read "+nread+" bytes");
        } catch (EOFException e) {
            LOGGER.log(Level.WARNING, e.getMessage());
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, ex.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e.getMessage());
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, ex.getMessage());
                }
            }
        }
    }  // end readVecBlock

    // read value along dim
    // fixme only works for 2nd dim
    private void readValue(int iDim, int fileIndex, int vecIndex, int xCol, byte[] dataBuf) {
        try {
            int nread = 0;
            int skips = fileIndex * tbytes + xCol * 4 * 2;
            ByteBuffer buf = ByteBuffer.wrap(dataBuf, vecIndex * 4 * 2, 4 * 2);
            nread = fc.read(buf, skips);
        } catch (EOFException e) {
            LOGGER.log(Level.WARNING, e.getMessage());
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, ex.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e.getMessage());
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, ex.getMessage());
                }
            }
        }
    }

    // copy read data into Complex array
    private void copyVecData(byte[] dataBuf, Complex[] data) {
        IntBuffer ibuf = ByteBuffer.wrap(dataBuf).asIntBuffer();
        int px, py;
        for (int j = 0; j < np; j += 2) {
            px = ibuf.get(j);
            py = ibuf.get(j + 1);
            if (swapBits) {
                px = Integer.reverseBytes(px);
                py = Integer.reverseBytes(py);
            }
            if (exchangeXY) {
                data[j / 2] = new Complex((double) py / SCALE, (double) px / SCALE);
            } else {
                data[j / 2] = new Complex((double) px / SCALE, -(double) py / SCALE);
            }
        }
        if (negatePairs) {
            Vec.negatePairs(data);
        }
    }  // end copyVecData

    // copy read data into double arrays of real, imaginary
    private void copyVecData(byte[] dataBuf, double[] rdata, double[] idata) {
        IntBuffer ibuf = ByteBuffer.wrap(dataBuf).asIntBuffer();
        int px, py;
        for (int j = 0; j < np; j += 2) {
            px = ibuf.get(j);
            py = ibuf.get(j + 1);
            if (swapBits) {
                px = Integer.reverseBytes(px);
                py = Integer.reverseBytes(py);
            }
            if (exchangeXY) {
                rdata[j / 2] = (double) py / SCALE;
                idata[j / 2] = (double) px / SCALE;
            } else {
                rdata[j / 2] = (double) px / SCALE;
                idata[j / 2] = -(double) py / SCALE;
            }
        }
        if (negatePairs) {
            Vec.negatePairs(rdata, idata);
        }
    }

    // copy read data into double array
    private void copyVecData(byte[] dataBuf, double[] data) {
        IntBuffer ibuf = ByteBuffer.wrap(dataBuf).asIntBuffer();
        for (int j = 0; j < np; j++) {
            int px = ibuf.get(j);
            if (swapBits) {
                px = Integer.reverseBytes(px);
            }
            data[j] = (double) px / SCALE;
        }
        // cannot exchange XY, only real data
        if (negatePairs) {
            Vec.negatePairs(data);
        }
    }

    private void fixDSP(Vec dvec) {
        if (fixDSP) {
            if (fixByShift) {
                dspPhase(dvec);
            } else {
//                dvec.fixWithPhasedHFT(45.0);
                dvec.fixWithPhasedHFT();
            }
        }
    }

    private void dspPhase(Vec vec) {
        if (dspph != 0.0) {  // check DMX flag?
            System.out.println("  BrukerData dspPhase=" + dspph);
            vec.checkPowerOf2(); // resize
            vec.fft();
            vec.phase(0.0, dspph, false, false);
            vec.ifft();
            vec.resize(tdsize[0], true);
            vec.setGroupDelay(0.0);
        }
    }

    @Override
    public void resetAcqOrder() {
        acqOrder = null;
    }

    @Override
    public String[] getAcqOrder() {
        if (acqOrder == null) {
            int nDim = getNDim() - 1;
            acqOrder = new String[nDim * 2];
            // p1,d1,p2,d2
            for (int i = 0; i < nDim; i++) {
                acqOrder[i * 2] = "p" + (i + 1);
                acqOrder[i * 2 + 1] = "d" + (i + 1);
            }
        }
        return acqOrder;
    }

    @Override
    public void setAcqOrder(String[] acqOrder) {
        this.acqOrder = acqOrder;
    }

    @Override
    public SampleSchedule getSampleSchedule() {
        return sampleSchedule;
    }

    @Override
    public void setSampleSchedule(SampleSchedule sampleSchedule) {
        this.sampleSchedule = sampleSchedule;
    }

    class BrukerSinebellWt extends SinebellWt {

        BrukerSinebellWt(int iDim) {
            Integer wdw = getParInt("WDW," + (iDim + 1));
            String spar;
            if (wdw != null && (wdw == 3 || wdw == 4)) {
                if ((spar = getPar("SSB," + (iDim + 1))) != null) {
                    if (!spar.equals("n")) {
                        if (wdw == 4) {
                            power = 2;
                        } else {
                            power = 1;
                        }
                        sb = 1.0;
                        sbs = Double.parseDouble(spar);
//                      size = -1; // size = "";
                        if (sbs < 2.0) {
                            offset = 0.0;
                        } else {
                            offset = 1.0 / sbs;
                        }
                        end = 1.0;
                    }
                }
            }
        }

    }

    class BrukerGaussianWt extends GaussianWt {

        BrukerGaussianWt(int iDim) {
            Integer wdw = getParInt("WDW," + (iDim + 1));
            String spar;
            if (wdw != null && wdw == 2) {
                if ((spar = getPar("GB," + (iDim + 1))) != null) {
                    if (!spar.equals("n")) {
                        gf = Double.parseDouble(spar);
                        if ((spar = getPar("LB," + (iDim + 1))) != null) {
                            if (!spar.equals("n")) {
                                lb = Double.parseDouble(spar);
                            }
                        }
                    }
                }
            }
        }

    }

    public static void main(String[] args) {

    }

}
