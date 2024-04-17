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
package org.nmrfx.processor.datasets.vendor.bruker;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.Precision;
import org.nmrfx.datasets.DatasetLayout;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.processor.datasets.*;
import org.nmrfx.processor.datasets.parameters.FPMult;
import org.nmrfx.processor.datasets.parameters.GaussianWt;
import org.nmrfx.processor.datasets.parameters.LPParams;
import org.nmrfx.processor.datasets.parameters.SinebellWt;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.datasets.vendor.NMRParException;
import org.nmrfx.processor.datasets.vendor.VendorPar;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.SampleSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.Map.Entry;

/**
 * BrukerData implements NMRData methods for opening and reading parameters and
 * FID data acquired using a Bruker instrument.
 *
 * @author bfetler
 * @see NMRData
 * @see NMRDataUtil
 */
public class BrukerData implements NMRData {
    private static final Logger log = LoggerFactory.getLogger(BrukerData.class);
    private static final String ACQUS = "acqus";
    private static final String SER = "ser";
    private static final String FID = "fid";

    private static final int MAXDIM = 10;
    private int tbytes = 0;             // TD,1
    private int np;                   // TD,1
    private int dim = 0;                // from acqu[n]s files
    private int dType = 0;
    private boolean swapBits = false; // BYTORDA,1
    private double dspph = 0.0;         // GRPDLY,1 etc.
    private double groupDelay = 0.0;         // GRPDLY,1 etc.
    private boolean exchangeXY = false;
    private boolean negatePairs = false;
    private boolean fixDSP = true;
    private boolean fixByShift = false;
    private DatasetType preferredDatasetType = DatasetType.NMRFX;
    private final boolean[] complexDim = new boolean[MAXDIM];
    private final double[][] f1coef = new double[MAXDIM][];   // FnMODE,2 MC2,2
    private final String[] f1coefS = new String[MAXDIM];   // FnMODE,2 MC2,2
    private final String[] fttype = new String[MAXDIM];
    private final int[] tdsize = new int[MAXDIM];  // TD,1 TD,2 etc.
    private final int[] arraysize = new int[MAXDIM];  // TD,1 TD,2 etc.
    private final int[] maxSize = new int[MAXDIM];  // TD,1 TD,2 etc.
    private double deltaPh02 = 0.0;
    private final Double[] refValue = new Double[MAXDIM];
    private final Double[] sweepWidth = new Double[MAXDIM];
    private final Double[] specFreq = new Double[MAXDIM];
    private final AcquisitionType[] symbolicCoefs = new AcquisitionType[MAXDIM];

    private Double zeroFreq = null;
    private String text = null;

    private final File dirFile;

    private final File dataFile;
    private FileChannel fc = null;
    private HashMap<String, String> parMap = null;
    private static HashMap<String, Double> phaseTable = null;
    private String[] acqOrder;
    private SampleSchedule sampleSchedule = null;
    private final double scale;
    // flag to indicate BrukerData has been opened as an FID
    private final boolean isFID;
    List<Double> arrayValues = new ArrayList<>();
    File nusFile;
    private final List<DatasetGroupIndex> datasetGroupIndices = new ArrayList<>();

    /**
     * Open Bruker parameter and data files.
     *
     * @param file    The file to open
     * @param nusFile The file containing the NUS schedule
     * @throws java.io.IOException if file can't be read
     */
    public BrukerData(File file, File nusFile) throws IOException {
        if (file.isDirectory()) {
            dirFile = file;
        } else {
            dirFile = file.getParentFile();
        }
        File fidFile = dirFile.toPath().resolve(FID).toFile();
        File serFile = dirFile.toPath().resolve(SER).toFile();
        if (fidFile.exists()) {
            dataFile = fidFile;
        } else if (serFile.exists()) {
            dataFile = serFile;
        } else {
            dataFile = null;
        }

        this.nusFile = nusFile;
        isFID = true;
        openParFile(dirFile);
        openDataFile(dirFile);
        scale = 1.0e6;
    }

    /**
     * Open Bruker parameter and processed data files.
     *
     * @param file full The file to open
     * @throws java.io.IOException if file can't be read
     */
    public BrukerData(File file) throws IOException {
        dataFile = file;
        dirFile = dataFile.getParentFile().getParentFile().getParentFile();
        this.nusFile = null;
        isFID = false;
        openParFile(dirFile);
        scale = 1.0;
    }

    public Dataset toDataset(String datasetName) throws IOException {
        DatasetLayout layout = new DatasetLayout(dim);
        int lastBlockSize = 1;
        int lastSize = 0;
        int xdim;
        for (int i = 0; i < dim; i++) {
            Integer thisBlockSize = getParInt("XWIN," + (i + 1));
            if ((thisBlockSize == null) || (thisBlockSize == 0)) {
                thisBlockSize = getParInt("SI," + (i + 1));
            }
            int thisSize = getParInt("SI," + (i + 1));
            layout.setSize(i, thisSize);
            if ((lastSize == lastBlockSize) && (thisSize == thisBlockSize)) {
                xdim = 131072 / lastSize;
                if (xdim > thisSize) {
                    xdim = thisSize;
                }
                if ((thisSize % xdim) != 0) {
                    xdim = thisSize;
                }

            } else {
                xdim = thisBlockSize;
            }
            if (xdim > thisSize) {
                xdim = thisSize;
            }
            layout.setBlockSize(i, xdim);
            lastBlockSize = xdim;
            lastSize = thisSize;

        }
        layout.dimDataset();
        if (datasetName == null) {
            datasetName = suggestName(dataFile);
        }
        Dataset dataset = new Dataset(dataFile.toString(), datasetName, layout, false,
                ByteOrder.LITTLE_ENDIAN, 1);
        dataset.newHeader();
        for (int i = 0; i < dim; i++) {
            dataset.setSf(i, getSF(i));
            Double sW = getParDouble("SW_p," + (i + 1));
            Double offset = getParDouble("OFFSET," + (i + 1));
            if (sW != null) {
                dataset.setSw(i, sW);
            } else {
                dataset.setSw(i, getSW(i));
            }
            if (offset != null) {
                dataset.setRefValue(i, offset);
                dataset.setRefPt(i, 0);

            } else {
                dataset.setRefValue(i, getRef(i));
                dataset.setRefPt(i, dataset.getSizeTotal(i) / 2.0);
            }
            dataset.setComplex(i, false);
            dataset.setFreqDomain(i, true);
            String nucLabel = getTN(i);
            dataset.setNucleus(i, nucLabel);
            dataset.setLabel(i, nucLabel + (i + 1));
            dataset.syncPars(i);
        }
        dataset.setNFreqDims(dataset.getNDim());
        Integer ncProc = getParInt("NC_proc,1");
        if (ncProc == null) {
            ncProc = 0;
        }
        dataset.setScale(1.0e6 / Math.pow(2, ncProc));
        dataset.setDataType(1);
        return dataset;
    }

    @Override
    public DatasetType getPreferredDatasetType() {
        return preferredDatasetType;
    }

    @Override
    public void setPreferredDatasetType(DatasetType datasetType) {
        this.preferredDatasetType = datasetType;
    }

    public String suggestName(File file) {
        File pdataNumFile = file.getParentFile();
        File numFile = pdataNumFile.getParentFile().getParentFile();
        File rootFile = numFile.getParentFile();
        String rootName = rootFile != null ? rootFile.getName() : "";
        rootName = rootName.replace(" ", "_");
        return rootName + "_" + numFile.getName() +
                "_" + pdataNumFile.getName() + "_" +
                file.getName();
    }

    public String suggestName() {
        File numFile = dirFile;
        File rootFile = numFile.getParentFile();
        String rootName = rootFile != null ? rootFile.getName() : "";
        rootName = rootName.replace(" ", "_");
        return rootName + "_" + numFile.getName();
    }

    @Override
    public void close() {
        try {
            fc.close();
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
    }

    /**
     * Finds data, given a path to search for vendor-specific files and
     * directories.
     *
     * @param file full path for data
     * @return if data was successfully found or not
     */
    public static boolean findData(File file) {
        String fileName = file.getName();
        return isProcessedFile(fileName);
    }

    /**
     * Finds FID data, given a path to search for vendor-specific files and
     * directories.
     *
     * @param file full path for FID data
     * @return if FID data was successfully found or not
     */
    public static Optional<File> findFID(File file) {
        Optional<File> result = findFIDFiles(file);
        if (result.isEmpty()) {
            if (file.isDirectory()) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(file.toPath(), "[0-9]")) {
                    for (Path entry : stream) {
                        result = findFIDFiles(entry.toFile());
                        if (result.isPresent()) {
                            break;
                        }
                    }
                } catch (DirectoryIteratorException | IOException ex) {
                    // I/O error encountered during the iteration, the cause is an IOException
                    log.warn(ex.getMessage(), ex);
                }
            }
        }

        return result;
    }

    private static Optional<File> findFIDFiles(File file) {
        Optional<File> result = Optional.empty();

        if (file.exists()) {
            File dirFile;
            String name = file.getName();
            if (file.isFile() && (name.equals(FID) || name.equals(SER) || name.equals(ACQUS))) {
                dirFile = file.getParentFile();
            } else {
                dirFile = file;
            }
            if (dirFile.isDirectory()) {
                Path path = dirFile.toPath();
                File fidFile = path.resolve(FID).toFile();
                File serFile = path.resolve(SER).toFile();
                File acqusFile = path.resolve(ACQUS).toFile();
                if (acqusFile.exists() && (fidFile.exists()) || serFile.exists()) {
                    result = Optional.of(dirFile);
                }
            }
        }
        return result;
    }

    private static boolean findDataFiles(String dpath) {
        boolean found = false;
        if ((new File(dpath + File.separator + ACQUS)).exists()) {
            if ((new File(dpath + File.separator + SER)).exists()) {
                found = true;
            } else if ((new File(dpath + File.separator + FID)).exists()) {
                found = true;
            }
        }
        return found;
    } // findFIDFiles

    public static boolean isProcessedFile(String name) {
        boolean result = false;
        if (!name.isBlank() && (name.length() > 1)
                && Character.isDigit(name.charAt(0))) {
            int nDim = Integer.parseInt(name.substring(0, 1));
            if ((nDim > 0) && (nDim == (name.length() - 1))) {
                result = true;
                for (int i = 1; i < name.length(); i++) {
                    if (name.charAt(i) != 'r') {
                        result = false;
                        break;
                    }
                }
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return dirFile.toString();
    }

    @Override
    public String getVendor() {
        return "bruker";
    }

    @Override
    public String getUser() {
        String s;
        if ((s = getPar("OWNER,1")) == null) {
            s = "";
        } else {
            s = s.trim();
        }
        return s;
    }

    @Override
    public Map<String, Boolean> getFidFlags() {
        Map<String, Boolean> flags = new HashMap<>(5);
        flags.put("fixdsp", fixDSP);
        flags.put("shiftdsp", fixByShift);
        flags.put("exchangeXY", exchangeXY);
        flags.put("swapBits", swapBits);
        flags.put("negatePairs", negatePairs);
        return flags;
    }

    @Override
    public String getFilePath() {
        return dirFile.toString();
    }

    @Override
    public List<VendorPar> getPars() {
        List<VendorPar> vendorPars = new ArrayList<>();
        for (Entry<String, String> par : parMap.entrySet()) {
            vendorPars.add(new VendorPar(par.getKey(), par.getValue()));
        }
        return vendorPars;
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

    public List<Double> getDoubleListPar(String parName) {
        List<Double> result = new ArrayList<>();
        if ((parMap != null) && (parMap.get(parName) != null)) {
            String[] sValues = parMap.get(parName).split(" ");
            for (String sValue : sValues) {
                try {
                    result.add(Double.parseDouble(sValue));
                } catch (NumberFormatException nFE) {
                    result = null;
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public int getNVectors() {  // number of vectors
        int num = 1;
        for (int i = 1; i < dim; i++) {
            num *= getSize(i) * (isComplex(i) ? 2 : 1);
        }
        return num;
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

    @Override
    public int getGroupSize(int dim) {
        return isComplex(dim) ? 2 : 1;
    }

    @Override
    public String getFTType(int iDim) {
        return fttype[iDim];
    }

    // used only for indirect dimensions; direct dimension done when FID read 
    @Override
    public boolean getNegatePairs(int iDim) {
        return (fttype[iDim].equals("negate"));
    }

    @Override
    public boolean getNegateImag(int iDim) {
        if (iDim > 0) {
            return !f1coefS[iDim].equals(AcquisitionType.SEP.getLabel());
        } else {
            return false;
        }
    }

    @Override
    public double[] getCoefs(int iDim) {
        return f1coef[iDim];
    }

    @Override
    public String getSymbolicCoefs(int iDim) {
        return f1coefS[iDim];
    }

    public void setUserSymbolicCoefs(int iDim, AcquisitionType coefs) {
        symbolicCoefs[iDim] = coefs;
    }

    public AcquisitionType getUserSymbolicCoefs(int iDim) {
        return symbolicCoefs[iDim];
    }

    @Override
    public String[] getSFNames() {
        int nDim = getNDim();
        String[] names = new String[nDim];
        for (int i = 0; i < nDim; i++) {
            names[i] = "SFO1," + (i + 1);
        }
        return names;
    }

    @Override
    public String[] getSWNames() {
        int nDim = getNDim();
        String[] names = new String[nDim];
        for (int i = 0; i < nDim; i++) {
            names[i] = "SW_h," + (i + 1);
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
        return names.toArray(new String[0]);
    }

    @Override
    public double getSF(int iDim) {
        double sf = 1.0;
        if (specFreq[iDim] != null) {
            sf = specFreq[iDim];
        } else {
            Double dpar;
            if ((dpar = getParDouble("SFO1," + (iDim + 1))) != null) {
                sf = dpar;
            }
        }
        return sf;
    }

    @Override
    public void setSF(int iDim, double value) {
        specFreq[iDim] = value;
    }

    @Override
    public void resetSF(int iDim) {
        specFreq[iDim] = null;
    }

    @Override
    public double getSW(int iDim) {
        double sw = 1.0;
        if (sweepWidth[iDim] != null) {
            sw = sweepWidth[iDim];
        } else {
            Double dpar;
            if ((dpar = getParDouble("SW_h," + (iDim + 1))) != null) {
                sw = dpar;
            } else if ((dpar = getParDouble("SW," + (iDim + 1))) != null) {
                double sf = getSF(iDim);
                sw = dpar * sf;
            }
        }
        return sw;
    }

    @Override
    public void setSW(int iDim, double value) {
        sweepWidth[iDim] = value;
    }

    @Override
    public void resetSW(int iDim) {
        sweepWidth[iDim] = null;
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
    public int getArraySize(int iDim) {
        return arraysize[iDim];
    }

    @Override
    public void setArraySize(int iDim, int size) {
        arraysize[iDim] = size;
    }

    @Override
    public void setRef(int iDim, double ref) {
        refValue[iDim] = ref;
        String nucleusName = getTN(iDim);
        Nuclei nucleus = Nuclei.findNuclei(nucleusName);

        if (nucleus == Nuclei.H1) {
            double sf0 = getSF(iDim);
            zeroFreq = sf0 / (1.0 + ref * 1.0e-6);
        }
    }

    @Override
    public void resetRef(int iDim) {
        refValue[iDim] = null;
    }

    @Override
    public double getRef(int iDim) {
        double ref = 0.0;
        if (refValue[iDim] != null) {
            ref = refValue[iDim];
        } else {
            Double dpar;
            var refOpt = getRefAtCenter(iDim);
            if (refOpt.isPresent()) {
                ref = refOpt.get();
                // OFFSET from proc[n]s
            } else if ((dpar = getParDouble("OFFSET," + (iDim + 1))) != null) {
                double sw = getSW(iDim);
                double sf = getSF(iDim);
                ref = dpar - (sw / 2.0) / sf;
                ref = Precision.round(ref, 5);
            } else if ((dpar = getParDouble("O1," + (iDim + 1))) != null) {
                double o1 = dpar;
                if ((dpar = getParDouble("BF1," + (iDim + 1))) != null) {
                    double sf = dpar;
                    ref = o1 / sf;
                }
            }
            setRef(iDim, ref);
        }
        return ref;
    }

    Optional<Double> getRefAtCenter(int iDim) {
        String nucleusName = getTN(iDim);
        Nuclei nucleus = Nuclei.findNuclei(nucleusName);
        double zf = getZeroFreq();
        double ref = ReferenceCalculator.refByRatio(zf, getSF(iDim), nucleus, getSolvent());
        return Optional.of(ref);
    }

    double getCorrectedBaseFreq() {
        String solvent = getSolvent();
        boolean isAcqueous = ReferenceCalculator.isAcqueous(solvent);
        Double actualLockRef = null;
        if (isAcqueous) {
            actualLockRef = ReferenceCalculator.getH2ORefPPM(getTempK());
        }
        int hDim = -1;
        for (int i = 0; i < getNDim(); i++) {
            String nucleusName = getTN(i);
            Nuclei nucleus = Nuclei.findNuclei(nucleusName);
            if (nucleus == Nuclei.H1) {
                hDim = i;
            }
        }
        double calcBaseFreq;
        if (hDim != -1) {
            Double baseFreq = getParDouble("BF1," + (hDim + 1));
            Double lockPPM = getParDouble("LOCKPPM,1");
            if (lockPPM == null) {
                if (isAcqueous) {
                    lockPPM = 4.717;
                } else {
                    if (solvent.equalsIgnoreCase("cdcl3")) {
                        lockPPM = 7.29;
                    } else if (solvent.equalsIgnoreCase("cd3od")) {
                        lockPPM = 4.761;
                    } else if (solvent.equalsIgnoreCase("dmso")) {
                        lockPPM = 2.578;
                    } else if (solvent.equalsIgnoreCase("acetone")) {
                        lockPPM = 1.892;
                    } else {
                        lockPPM = 4.717;
                    }
                }
            }

            if (actualLockRef == null) {
                actualLockRef = lockPPM;
            }
            calcBaseFreq = ReferenceCalculator.getCorrectedBaseFreq(baseFreq, lockPPM, actualLockRef);
        } else {
            String nucleusName = getTN(0);
            Nuclei nucleus = Nuclei.findNuclei(nucleusName);
            Double baseFreq = getParDouble("BF1,1");
            double xRatio = nucleus.getRatio();
            calcBaseFreq = baseFreq / (xRatio / 100.0);
        }
        return calcBaseFreq;
    }

    @Override
    public void setZeroFreq(Double value) {
        zeroFreq = value;
    }

    @Override
    public double getZeroFreq() {
        if (zeroFreq == null) {
            zeroFreq = getCorrectedBaseFreq();
        }
        return zeroFreq;
    }

    @Override
    public double getRefPoint(int dim) {
        return (double) getSize(dim) / 2;
    }

    @Override
    public String getTN(int iDim) {
        String s = getPar("NUC1," + (iDim + 1));
        if (s == null || s.equals("off")) {
            s = getPar("NUCLEUS," + (iDim + 1));
            if (s == null) {
                double sf = getSF(iDim);
                s = NMRDataUtil.guessNucleusFromFreq(sf).get(0).toString();
            }
        }
        if (s == null) {
            s = "";
        }
        return s;
    }

    @Override
    public String getSolvent() {
        String s;
        if ((s = getPar("SOLVENT,1")) == null) {
            s = "";
        }
        return s;
    }

    @Override
    public double getTempK() {
        Double d;
        if (((d = getParDouble("TEMP,1")) == null) && ((d = getParDouble("TE,1")) == null)) {
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
                seconds = Long.parseLong(s);
            } catch (NumberFormatException e) {
                log.warn("Unable to parse date in file " + dirFile, e);
            }
        } else {
            log.warn("no date in file {}", dirFile);
        }
        return seconds;
    }

    // open and read Bruker text file
    @Override
    public String getText() {
        if (text == null) {
            File textFile = dirFile.toPath().resolve("pdata/1/title").toFile();
            if (textFile.exists()) {
                try {
                    Path path = textFile.toPath();
                    text = new String(Files.readAllBytes(path));
                } catch (IOException ex) {
                    text = "";
                }
            } else {
                text = "";
            }
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
                ph0 += deltaPh02;
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
    private void openParFile(File parDirFile) throws IOException {
        parMap = new LinkedHashMap<>(200);
        Path pulseSequencePath = parDirFile.toPath().resolve("pulseprogram");
        String dimPar = null;
        if (pulseSequencePath.toFile().exists()) {
            var lines = scanPulseSequence(pulseSequencePath);
            var optLine = lines.stream().
                    map(String::trim).
                    filter(line -> line.startsWith(";$DIM=")).findFirst();  // ;$DIM=2D
            if (optLine.isPresent()) {
                String[] aqSeqParts = optLine.get().split("=");
                if ((aqSeqParts.length == 2) && (aqSeqParts[1].endsWith("D") && Character.isDigit(aqSeqParts[1].charAt(0)))) {
                    dimPar = aqSeqParts[1].substring(0, 1);
                }
            }
        }
        int maxDim = dimPar != null ? Integer.parseInt(dimPar) : MAXDIM;

        // process proc files if they exist
        File pdataFile = parDirFile.toPath().resolve("pdata").toFile();
        if (pdataFile.exists()) {
            Path bdir = pdataFile.toPath();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(bdir, "[0-9]")) {
                for (Path entry : stream) {
                    String s = entry.toString();
                    if (new File(s + File.separator + "procs").exists()) {
                        for (int i = 0; i < maxDim; i++) {
                            String procFileName;
                            if (i == 0) {
                                procFileName = "procs";
                            } else {
                                procFileName = "proc" + (i + 1) + "s";
                            }
                            File procFile = entry.resolve(procFileName).toFile();
                            if (procFile.exists()) {
                                BrukerPar.processBrukerParFile(parMap, procFile.toString(), i + 1, false);
                            }
                        }
                        break;
                    }
                }
            } catch (DirectoryIteratorException | IOException ex) {
                log.warn(ex.getMessage(), ex);
            }
        }
        // process acqu files if they exist
        int acqdim = 0;
        for (int i = 0; i < maxDim; i++) {
            String acqfile;
            if (i == 0) {
                acqfile = ACQUS;
            } else {
                acqfile = "acqu" + (i + 1) + "s";
            }
            File acquFile = parDirFile.toPath().resolve(acqfile).toFile();

            try {
                if (acquFile.exists()) {
                    BrukerPar.processBrukerParFile(parMap, acquFile.toString(), i + 1, false);
                    if (getParInt("TD," + (i + 1)) != null) {
                        acqdim++;
                    }
                } else {
                    break;
                }
            } catch (NMRParException ex) {
                log.warn(ex.getMessage(), ex);
            }
        }
        String[] listTypes = {"vd", "vc", "vp", "fq2", "fq3"};
        for (String listType : listTypes) {
            Path listPath = parDirFile.toPath().resolve(listType + "list");
            if (Files.exists(listPath)) {
                List<String> lines = Files.readAllLines(listPath);
                BrukerPar.storeParameter(parMap, listType, lines, "\t");
            }
        }
        this.dim = acqdim;
        setPars();
    }

    private void setPars() throws IOException {
        // need to get (or calculate)  groupDelay before calculating shiftAmount below
        setDspph();
        Integer ipar;
        int bytesPerWord = 4;
        if ((ipar = getParInt("DTYPA,1")) != null) {
            dType = ipar;
            if (dType == 2) {
                bytesPerWord = 8;
            }
        }
        if ((ipar = getParInt("TD,1")) != null) {
            np = ipar;
            if (dType == 0) {
                int pad = np % 256;
                if (pad > 0) {
                    tbytes = (np + 256 - pad) * bytesPerWord;
                } else {
                    tbytes = np * bytesPerWord;
                }
            } else if (dType == 2) {
                int pad = np % 128;
                if (pad > 0) {
                    tbytes = (np + 128 - pad) * bytesPerWord;
                } else {
                    tbytes = np * bytesPerWord;
                }
            } else {
                tbytes = np * bytesPerWord;
            }
            int shiftAmount = 0;
            if (groupDelay > 0) {
                shiftAmount = (int) Math.round(Math.ceil(groupDelay));
            }
            tdsize[0] = np / 2 - shiftAmount; // tcl line 348, lines 448-459
        }
        boolean nusMode = false;
        if ((ipar = getParInt("FnTYPE,1")) != null) {
            nusMode = ipar == 2;
        }
        boolean gotSchedule = false;
        if (nusFile == null) {
            nusFile = dirFile.toPath().resolve("nuslist").toFile();
        }
        if (nusMode && nusFile.exists()) {
            readSampleSchedule(nusFile.getPath(), false, false);
            if (sampleSchedule.getTotalSamples() == 0) {
                throw new IOException("nuslist file exists, but is empty");
            } else {
                gotSchedule = true;
            }
        }
        arrayValues = getArrayValues();
        getTDSizes(gotSchedule);
        setFTpars();
        if (!gotSchedule) {
            adjustTDForComplex();
        }
        if ((arrayValues != null) && !arrayValues.isEmpty()) {
            arrayValues = fixArraySize(arrayValues, getMinDim());
        }

        if ((ipar = getParInt("BYTORDA,1")) != null) {
            if (ipar == 0) {
                setSwapBitsOn();
            }
        }
    }

    private void getTDSizes(boolean gotSchedule) {
        String tdpar = "TD,";
        Integer ipar;
        if (gotSchedule) {
            int[] dims = sampleSchedule.getDims();
            for (int i = 0; i < dims.length; i++) {
                tdsize[i + 1] = dims[i];
                dim = i + 2;
            }
        } else {
            for (int i = 2; i <= dim; i++) {
                tdsize[i - 1] = 1;
                if ((ipar = getParInt(tdpar + i)) != null) {
                    tdsize[i - 1] = ipar / (isComplex(i - 1) ? 2 : 1);
                }
            }
        }
        for (int j = 0; j < tdsize.length; j++) {
            if (tdsize[j] == 0) {
                tdsize[j] = 1;
                complexDim[j] = false;
            }
            maxSize[j] = tdsize[j];
        }
    }

    private void adjustTDForComplex() {
        for (int j = 1; j < tdsize.length; j++) {
            if (isComplex(j) && (tdsize[j] > 1)) {
                tdsize[j] /= 2;
            }
            maxSize[j] = tdsize[j];
        }
    }

    private List<Double> getArrayValues() {
        List<Double> result = new ArrayList<>();
        // kluge  find smallest dimension.  This is the most likely one to use an array of values
        if (parMap != null) {
            String[] listTypes = {"vd", "vc", "vp", "fq2", "fq3"};

            for (String listType : listTypes) {
                String parValue;
                if ((parValue = parMap.get(listType)) != null) {
                    String[] sValues = parValue.split("\t");
                    if (sValues.length > 0) {
                        List<String> sList = Arrays.asList(sValues);
                        if (listType.startsWith("fq")) {
                            if (getSequence().contains("cest")) {
                                sList.set(0, "0.0");
                            }
                        }

                        for (String sValue : sList) {
                            // first line of fqlist can start with value like "bf ppm", so remove that line
                            if (sValue.startsWith("bf")) {
                                continue;
                            }
                            try {
                                double valueScale;
                                if (sValue.endsWith("m")) {
                                    sValue = sValue.substring(0, sValue.length() - 1);
                                    valueScale = 1.0e-3;
                                } else if (sValue.endsWith("u")) {
                                    sValue = sValue.substring(0, sValue.length() - 1);
                                    valueScale = 1.0e-6;
                                } else if (sValue.endsWith("s")) {
                                    sValue = sValue.substring(0, sValue.length() - 1);
                                    valueScale = 1.0;
                                } else {
                                    valueScale = 1.0;
                                }
                                result.add(Double.parseDouble(sValue) * valueScale);
                            } catch (NumberFormatException nFE) {
                                log.warn("bad double in list file {}", sValue);
                                result = null;
                                break;
                            }
                        }
                        break;
                    }
                }
            }
        }
        return result;
    }

    private List<Double> fixArraySize(List<Double> values, int iDim) {
        int dimSize = getSize(iDim);
        if (values.size() * 2 == dimSize) {
            List<Double> newValues = new ArrayList<>();
            for (int i = 0; i < dimSize; i++) {
                newValues.add(values.get(i / 2));
            }
            values.clear();
            values.addAll(newValues);
        } else if (values.size() < dimSize) {
            int n = values.size();
            if (n != 0) {
                for (int i = n; i < dimSize; i++) {
                    int j = i % n;
                    values.add(values.get(j));
                }
            }
        }
        return values;
    }

    private void setFTpars() {
        // see bruker.tcl line 781-820
        Integer fnmode;
        complexDim[0] = true;  // same as exchange really
        exchangeXY = false;
        negatePairs = false;
        fttype[0] = "ft";
        if (((fnmode = getParInt("AQ_mod,1")) != null) && (fnmode == 2)) {
            fttype[0] = "rft";
            complexDim[0] = false;
            exchangeXY = false;
            negatePairs = true;
        }
        for (int i = 2; i <= dim; i++) {
            if ((fnmode = getParInt("FnMODE," + i)) != null) { // acqu2s
                complexDim[i - 1] = true;
                fttype[i - 1] = "ft";
                switch (fnmode) {
                    case 2, 3 -> {
                        complexDim[i - 1] = false;
                        fttype[i - 1] = "rft";
                        f1coefS[i - 1] = AcquisitionType.REAL.getLabel();
                    }
                    case 4 -> {
                        f1coef[i - 1] = AcquisitionType.HYPER_R.getCoefficients();
                        f1coefS[i - 1] = AcquisitionType.HYPER_R.getLabel();
                    }
                    case 0 -> {
                        complexDim[i - 1] = getValues(i - 1).isEmpty();
                        if (complexDim[i - 1]) {
                            f1coef[i - 1] = AcquisitionType.HYPER.getCoefficients();
                            f1coefS[i - 1] = AcquisitionType.HYPER.getLabel();
                            complexDim[i - 1] = true;
                            fttype[i - 1] = "negate";
                        } else {
                            f1coefS[i - 1] = AcquisitionType.ARRAY.getLabel();
                        }
                    }
                    case 5 -> {
                        f1coef[i - 1] = AcquisitionType.HYPER.getCoefficients();
                        f1coefS[i - 1] = AcquisitionType.HYPER.getLabel();
                        complexDim[i - 1] = true;
                        fttype[i - 1] = "negate";
                    }
                    case 6 -> {
                        f1coef[i - 1] = AcquisitionType.ECHO_ANTIECHO_R.getCoefficients();
                        f1coefS[i - 1] = AcquisitionType.ECHO_ANTIECHO_R.getLabel();
                        deltaPh02 = 90.0;
                    }
                    case 1 -> {
                        complexDim[i - 1] = getValues(i - 1).isEmpty();
                        if (complexDim[i - 1]) {
                            f1coefS[i - 1] = AcquisitionType.SEP.getLabel();
                        } else {
                            f1coefS[i - 1] = AcquisitionType.ARRAY.getLabel();
                        }
                    }
                    default -> {
                        f1coef[i - 1] = new double[]{1, 0, 0, 1};
                        f1coefS[i - 1] = AcquisitionType.SEP.getLabel();
                    }
                }
                if (tdsize[i - 1] < 2) {
                    complexDim[i - 1] = false;
                }
            }
        }
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
        for (Object key : flags.keySet()) {
            boolean value = (boolean) flags.get(key);
            switch (key.toString()) {
                case "fixdsp" -> {
                    if (value) {
                        setFixDSPOn();
                    } else {
                        setFixDSPOff();
                    }
                }
                case "shiftdsp" -> {
                    if (value) {
                        setDSPShiftOn();
                    } else {
                        setDSPShiftOff();
                    }
                }
                case "exchangeXY" -> {
                    if (value) {
                        setExchangeOn();
                    } else {
                        setExchangeOff();
                    }
                }
                case "negatePairs" -> {
                    if (value) {
                        setNegatePairsOn();
                    } else {
                        setNegatePairsOff();
                    }
                }
                case "swapBits" -> {
                    if (value) {
                        setSwapBitsOn();
                    } else {
                        setSwapBitsOff();
                    }
                }
            }
        }
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

    @Override
    public void setFixDSP(boolean value) {
        fixDSP = value;
    }

    @Override
    public boolean getFixDSP() {
        return fixDSP;
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
        String s;
        groupDelay = -1.0;
        if ((s = getPar("GRPDLY,1")) != null) {
            double d = Double.parseDouble(s);
            if (d >= 0.0) {
                dspph = -d * 360.0;
                groupDelay = d;
            }
        }
        if (groupDelay < 0.0) {
            String t;
            if (((s = getPar("DECIM,1")) != null) && ((t = getPar("DSPFVS,1")) != null)) {
                initPhaseTable();
                Double dd;
                if ((dd = phaseTable.get(s + "," + t)) != null) {
                    dspph = -dd * 360.0;
                    groupDelay = dd;
                }
            }
        }
    }

    private static void initPhaseTable() {
// see nspinit.tcl for phaseTable details 
        if (phaseTable == null) {
            phaseTable = new HashMap<>();

            phaseTable.put("1,0", 0.0);
            phaseTable.put("2,10", 44.75);
            phaseTable.put("2,11", 46.0);
            phaseTable.put("2,12", 46.311);
            phaseTable.put("3,10", 33.5);
            phaseTable.put("3,11", 36.5);
            phaseTable.put("3,12", 36.53);
            phaseTable.put("4,10", 66.625);
            phaseTable.put("4,11", 48.0);
            phaseTable.put("4,12", 47.87);
            phaseTable.put("6,10", 59.0833);
            phaseTable.put("6,11", 50.1667);
            phaseTable.put("6,12", 50.229);
            phaseTable.put("8,10", 68.5625);
            phaseTable.put("8,11", 53.25);
            phaseTable.put("8,12", 53.289);
            phaseTable.put("12,10", 60.375);
            phaseTable.put("12,11", 69.5);
            phaseTable.put("12,12", 69.551);
            phaseTable.put("16,10", 69.5313);
            phaseTable.put("16,11", 72.25);
            phaseTable.put("16,12", 71.6);
            phaseTable.put("24,10", 61.0208);
            phaseTable.put("24,11", 72.1667);
            phaseTable.put("24,12", 70.184);
            phaseTable.put("32,10", 70.0156);
            phaseTable.put("32,11", 72.75);
            phaseTable.put("32,12", 72.138);
            phaseTable.put("48,10", 61.3438);
            phaseTable.put("48,11", 70.5);
            phaseTable.put("48,12", 70.528);
            phaseTable.put("64,10", 70.2578);
            phaseTable.put("64,11", 73.0);
            phaseTable.put("64,12", 72.348);
            phaseTable.put("96,10", 61.5052);
            phaseTable.put("96,11", 70.6667);
            phaseTable.put("96,12", 70.7);
            phaseTable.put("128,10", 70.3789);
            phaseTable.put("128,11", 72.5);
            phaseTable.put("128,12", 72.524);
            phaseTable.put("192,10", 61.5859);
            phaseTable.put("192,11", 71.3333);
            phaseTable.put("256,10", 70.4395);
            phaseTable.put("256,11", 72.25);
            phaseTable.put("384,10", 61.6263);
            phaseTable.put("384,11", 71.6667);
            phaseTable.put("512,10", 70.4697);
            phaseTable.put("512,11", 72.125);
            phaseTable.put("768,10", 61.6465);
            phaseTable.put("768,11", 71.8333);
            phaseTable.put("1024,10", 70.4849);
            phaseTable.put("1024,11", 72.0625);
            phaseTable.put("1536,10", 61.6566);
            phaseTable.put("1536,11", 71.9167);
            phaseTable.put("2048,10", 70.4924);
            phaseTable.put("2048,11", 72.0313);
        }
    }

    // open Bruker file, read fid data
    private void openDataFile(File file) {
        Path fidPath = file.toPath().resolve(FID);
        Path serPath = file.toPath().resolve(SER);
        Path filePath;
        if (fidPath.toFile().exists()) {
            filePath = fidPath;
        } else if (serPath.toFile().exists()) {
            filePath = serPath;
        } else {
            return;
        }
        try {
            fc = FileChannel.open(filePath, StandardOpenOption.READ);
        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException e) {
                    log.warn(ex.getMessage(), ex);
                }
            }
        }
    }

    @Override
    public void readVector(int iVec, Vec dvec) {
        dvec.setGroupDelay(groupDelay);
        if (dvec.isComplex()) {
            if (dvec.useApache()) {
                readVector(iVec, dvec.getCvec());
                fixDSP(dvec);
            } else {
                readVector(iVec, dvec.rvec, dvec.ivec);
                fixDSP(dvec);
            }
        } else {
            readVector(iVec, dvec.rvec);
            // cannot dspPhase
        }
        dvec.dwellTime = 1.0 / getSW(0);
        dvec.centerFreq = getSF(0);

        dvec.setRefValue(getRef(0));
    }

    @Override
    public void readVector(int iDim, int iVec, Vec dvec) {
        int shiftAmount = 0;
        if (groupDelay > 0) {
            shiftAmount = (int) Math.round(groupDelay);
        }
        if (dvec.isComplex()) {
            if (dvec.useApache()) {
                readVector(iDim, iVec + shiftAmount, dvec.getCvec());
            } else {
                readVector(iDim, iVec + shiftAmount, null, dvec.rvec, dvec.ivec);
            }
        } else {
            readVector(iDim, iVec, null, dvec.rvec, null);
        }
        dvec.dwellTime = 1.0 / getSW(iDim);
        dvec.centerFreq = getSF(iDim);
        dvec.setRefValue(getRef(iDim));
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
        if (dType == 0) {
            copyVecData(dataBuf, cdata);
        } else {
            copyDoubleVecData(dataBuf, cdata);
        }
    }

    @Override
    public void readVector(int iVec, double[] rdata, double[] idata) {
        byte[] dataBuf = new byte[tbytes];
        readVecBlock(iVec, dataBuf);
        if (dType == 0) {
            copyVecData(dataBuf, rdata, idata);
        } else {
            copyDoubleVecData(dataBuf, rdata, idata);
        }
    }

    @Override
    public void readVector(int iVec, double[] data) {
        byte[] dataBuf = new byte[tbytes];
        readVecBlock(iVec, dataBuf);
        if (dType == 0) {
            copyVecData(dataBuf, data);
        } else {
            copyDoubleVecData(dataBuf, data);
        }
    }

    public void readVector(int iDim, int iVec, Complex[] cdata) {
        readVector(iDim, iVec, cdata, null, null);
    }

    public void readVector(int iDim, int iVec, Complex[] cdata, double[] rvec, double[] ivec) {
        int size = getSize(iDim);
        int nPer = getGroupSize(iDim);
        int nPoints = size * nPer;
        byte[] dataBuf = new byte[nPoints * 4 * nPer];
        IntBuffer ibuf = ByteBuffer.wrap(dataBuf).asIntBuffer();
        for (int j = 0; j < (nPoints * nPer); j++) {
            ibuf.put(j, 0);
        }
        int stride = tbytes;
        for (int i = 1; i < iDim; i++) {
            stride *= getSize(i) * 2;
        }

        for (int i = 0; i < (nPoints); i++) {
            if (sampleSchedule != null) {
                int[] point = {i / 2};
                int index = sampleSchedule.getIndex(point);
                if (index != -1) {
                    index = index * 2 + (i % 2);
                    readValue(iDim, stride, index, i, iVec, dataBuf, nPer);
                }
            } else {
                readValue(iDim, stride, i, i, iVec, dataBuf, nPer);
            }
        }
        if ((rvec != null) && (ivec == null)) {
            for (int j = 0; j < nPoints; j++) {
                int px = ibuf.get(j);
                if (swapBits) {
                    px = Integer.reverseBytes(px);
                }
                rvec[j] = px / scale;
            }

        } else {
            for (int j = 0; j < (nPoints * 2); j += 2) {
                int px = ibuf.get(j);
                int py = ibuf.get(j + 1);
                if (swapBits) {
                    px = Integer.reverseBytes(px);
                    py = Integer.reverseBytes(py);
                }
                if (rvec != null) {
                    rvec[j / 2] = px / scale;
                    ivec[j / 2] = py / scale;
                } else {
                    cdata[j / 2] = new Complex(px / scale, py / scale);
                }
            }
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
                throw new ArrayIndexOutOfBoundsException("file index " + i + " out of bounds " + nread + " " + tbytes);
            }
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
            try {
                fc.close();
            } catch (IOException ex) {
                log.warn(ex.getMessage(), ex);
            }
        }
    }  // end readVecBlock

    // read value along dim
    // fixme only works for 2nd dim
    private void readValue(int iDim, int stride, int fileIndex, int vecIndex, int xCol, byte[] dataBuf, int nPer) {
        try {
            int skips = fileIndex * stride + xCol * 4 * nPer;
            ByteBuffer buf = ByteBuffer.wrap(dataBuf, vecIndex * 4 * nPer, 4 * nPer);
            fc.read(buf, skips);
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
            try {
                fc.close();
            } catch (IOException ex) {
                log.warn(ex.getMessage(), ex);
            }
        }
    }

    // copy read data into Complex array
    private void copyVecData(byte[] dataBuf, Complex[] data) {
        IntBuffer ibuf = ByteBuffer.wrap(dataBuf).asIntBuffer();
        for (int j = 0; j < np; j += 2) {
            int px = ibuf.get(j);
            int py = ibuf.get(j + 1);
            if (swapBits) {
                px = Integer.reverseBytes(px);
                py = Integer.reverseBytes(py);
            }
            if (exchangeXY) {
                data[j / 2] = new Complex(py / scale, px / scale);
            } else {
                data[j / 2] = new Complex(px / scale, -(double) py / scale);
            }
        }
        if (negatePairs) {
            Vec.negatePairs(data);
        }
    }  // end copyVecData

    // copy read data into Complex array
    private void copyDoubleVecData(byte[] dataBuf, Complex[] data) {
        ByteOrder byteOrder = swapBits ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        DoubleBuffer dBuffer = ByteBuffer.wrap(dataBuf).order(byteOrder).asDoubleBuffer();
        for (int j = 0; j < np; j += 2) {
            double px = dBuffer.get(j);
            double py = dBuffer.get(j + 1);
            if (exchangeXY) {
                data[j / 2] = new Complex(py / scale, px / scale);
            } else {
                data[j / 2] = new Complex(px / scale, -py / scale);
            }
        }
        if (negatePairs) {
            Vec.negatePairs(data);
        }
    }  // end copyVecData

    // copy read data into double arrays of real, imaginary
    private void copyVecData(byte[] dataBuf, double[] rdata, double[] idata) {
        IntBuffer ibuf = ByteBuffer.wrap(dataBuf).asIntBuffer();
        for (int j = 0; j < np; j += 2) {
            int px = ibuf.get(j);
            int py = ibuf.get(j + 1);
            if (swapBits) {
                px = Integer.reverseBytes(px);
                py = Integer.reverseBytes(py);
            }
            if (exchangeXY) {
                rdata[j / 2] = py / scale;
                idata[j / 2] = px / scale;
            } else {
                rdata[j / 2] = px / scale;
                idata[j / 2] = -(double) py / scale;
            }
        }
        if (negatePairs) {
            Vec.negatePairs(rdata, idata);
        }
    }

    // copy read data into double arrays of real, imaginary
    private void copyDoubleVecData(byte[] dataBuf, double[] rdata, double[] idata) {
        ByteOrder byteOrder = swapBits ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        DoubleBuffer dBuffer = ByteBuffer.wrap(dataBuf).order(byteOrder).asDoubleBuffer();
        for (int j = 0; j < np; j += 2) {
            double px = dBuffer.get(j);
            double py = dBuffer.get(j + 1);
            if (exchangeXY) {
                rdata[j / 2] = py / scale;
                idata[j / 2] = px / scale;
            } else {
                rdata[j / 2] = px / scale;
                idata[j / 2] = -py / scale;
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
            data[j] = px / scale;
        }
        // cannot exchange XY, only real data
        if (negatePairs) {
            Vec.negatePairs(data);
        }
    }

    // copy read data into double array
    private void copyDoubleVecData(byte[] dataBuf, double[] data) {
        ByteOrder byteOrder = swapBits ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        DoubleBuffer dBuffer = ByteBuffer.wrap(dataBuf).order(byteOrder).asDoubleBuffer();
        for (int j = 0; j < np; j++) {
            double px = dBuffer.get(j);
            data[j] = px / scale;
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
                dvec.fixWithPhasedHFT();
            }
        }
    }

    private void dspPhase(Vec vec) {
        if (dspph != 0.0) {  // check DMX flag?
            vec.checkPowerOf2(); // resize
            vec.fft();
            vec.phase(0.0, dspph, false, false);
            vec.ifft();
            vec.resize(tdsize[0], true);
            vec.setGroupDelay(0.0);
        }
    }

    @Override
    public String[] getAcqOrder() {
        if (acqOrder == null) {
            int nDim = getNDim() - 1;
            acqOrder = new String[nDim * 2];
            // p1,d1,p2,d2
            Integer ipar;
            int aqSeq = 0;
            if ((ipar = getParInt("AQSEQ,1")) != null) {
                aqSeq = ipar;
            }

            if ((nDim == 2) && (aqSeq == 1)) {
                for (int i = 0; i < nDim; i++) {
                    int j = 1 - i;
                    acqOrder[i * 2] = "p" + (j + 1);
                    acqOrder[i * 2 + 1] = "d" + (j + 1);
                }
            } else {
                if ((sampleSchedule != null) && !sampleSchedule.isDemo()) {
                    for (int i = 0; i < nDim; i++) {
                        acqOrder[i] = "p" + (i + 1);
                    }
                    for (int i = 0; i < nDim; i++) {
                        acqOrder[i + nDim] = "d" + (i + 1);
                    }
                } else {
                    for (int i = 0; i < nDim; i++) {
                        acqOrder[i * 2] = "p" + (i + 1);
                        acqOrder[i * 2 + 1] = "d" + (i + 1);
                    }
                }
            }
        }
        return acqOrder;
    }

    @Override
    public void resetAcqOrder() {
        acqOrder = null;
    }

    @Override
    public void setAcqOrder(String[] newOrder) {
        if (newOrder.length == 1) {
            String s = newOrder[0];
            final int len = s.length();
            int nDim = getNDim();
            int nIDim = nDim - 1;
            if ((len == nDim) || (len == nIDim)) {
                acqOrder = new String[nIDim * 2];
                int j = 0;
                if ((sampleSchedule != null) && !sampleSchedule.isDemo()) {
                    for (int i = (len - 1); i >= 0; i--) {
                        String dimStr = s.substring(i, i + 1);
                        if (!dimStr.equals(nDim + "")) {
                            acqOrder[j++] = "p" + dimStr;
                        }
                    }
                    for (int i = (len - 1); i >= 0; i--) {
                        String dimStr = s.substring(i, i + 1);
                        if (!dimStr.equals(nDim + "")) {
                            acqOrder[j++] = "d" + dimStr;
                        }
                    }
                } else {
                    for (int i = (len - 1); i >= 0; i--) {
                        String dimStr = s.substring(i, i + 1);
                        if (!dimStr.equals(nDim + "")) {
                            acqOrder[j++] = "p" + dimStr;
                            acqOrder[j++] = "d" + dimStr;
                        }
                    }
                }
            } else if (len > nDim) {
                acqOrder = new String[(len - 1) * 2];
                int j = 0;
                if ((sampleSchedule != null) && !sampleSchedule.isDemo()) {
                    for (int i = (len - 1); i >= 0; i--) {
                        String dimStr = s.substring(i, i + 1);
                        if (!dimStr.equals((nDim + 1) + "")) {
                            acqOrder[j++] = "p" + dimStr;
                        }
                    }
                    for (int i = (len - 1); i >= 0; i--) {
                        String dimStr = s.substring(i, i + 1);
                        if (!dimStr.equals((nDim + 1) + "")) {
                            acqOrder[j++] = "d" + dimStr;
                        }
                    }
                } else {
                    for (int i = (len - 1); i >= 0; i--) {
                        String dimStr = s.substring(i, i + 1);
                        if (!dimStr.equals((nDim + 1) + "")) {
                            acqOrder[j++] = "p" + dimStr;
                            acqOrder[j++] = "d" + dimStr;
                        }
                    }
                }
            }
        } else {
            this.acqOrder = new String[newOrder.length];
            System.arraycopy(newOrder, 0, this.acqOrder, 0, newOrder.length);
        }
    }

    @Override
    public String getAcqOrderShort() {
        String[] acqOrderArray = getAcqOrder();
        StringBuilder builder = new StringBuilder();
        int nDim = getNDim();
        if (acqOrderArray.length / 2 == nDim) {
            builder.append(acqOrderArray.length / 2 + 1);
        } else {
            builder.append(nDim);
        }
        for (int i = acqOrderArray.length - 1; i >= 0; i--) {
            String elem = acqOrderArray[i];
            if (elem.charAt(0) == 'p') {
                builder.append(elem.charAt(1));
            } else if (elem.charAt(0) == 'a') {
                return "";
            }
        }
        return builder.toString();
    }

    @Override
    public List<Double> getValues(int dim) {
        List<Double> result;
        if (dim == getMinDim()) {
            result = arrayValues;
        } else {
            result = new ArrayList<>();
        }
        return result;
    }

    int getMinDim() {
        int minSize = getSize(0);
        int minDim = 0;
        for (int i = 1; i < getNDim(); i++) {
            if (getSize(i) < minSize) {
                minSize = getSize(i);
                minDim = i;
            }
        }
        return minDim;
    }

    @Override
    public boolean isFrequencyDim(int iDim) {
        boolean result = true;
        int minDim = getMinDim();
        if (!isComplex(iDim)) {
            // second test is because sometimes the vclist/vdlist can be smaller than the td for the dimension
            // so we assume we assume if there are arrayed values the smallest dim is the one that is arrayed
            if ((arrayValues != null) && (!arrayValues.isEmpty()) && ((arrayValues.size() == getSize(iDim)) || (iDim == minDim))) {
                result = false;
            }
        }
        return result;
    }

    @Override
    public boolean isFID() {
        return isFID;
    }

    @Override
    public SampleSchedule getSampleSchedule() {
        return sampleSchedule;
    }

    @Override
    public void setSampleSchedule(SampleSchedule sampleSchedule) {
        this.sampleSchedule = sampleSchedule;
    }

    @Override
    public List<DatasetGroupIndex> getSkipGroups() {
        return datasetGroupIndices;
    }

    private List<String> scanPulseSequence(Path path) throws IOException {
        return Files.readAllLines(path, StandardCharsets.ISO_8859_1);
    } // end fileout2

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
}
