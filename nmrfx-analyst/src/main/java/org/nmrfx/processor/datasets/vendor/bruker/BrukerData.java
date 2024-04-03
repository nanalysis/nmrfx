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

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
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

    private final static int MAXDIM = 10;
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
    private final double[] f1coef[] = new double[MAXDIM][];   // FnMODE,2 MC2,2
    private final String[] f1coefS = new String[MAXDIM];   // FnMODE,2 MC2,2
    private final String fttype[] = new String[MAXDIM];
    private final int tdsize[] = new int[MAXDIM];  // TD,1 TD,2 etc.
    private final int arraysize[] = new int[MAXDIM];  // TD,1 TD,2 etc.
    private final int maxSize[] = new int[MAXDIM];  // TD,1 TD,2 etc.
    private double deltaPh0_2 = 0.0;
    private final Double[] Ref = new Double[MAXDIM];
    private final Double[] Sw = new Double[MAXDIM];
    private final Double[] Sf = new Double[MAXDIM];
    private final AcquisitionType[] symbolicCoefs = new AcquisitionType[MAXDIM];

    private Double zeroFreq = null;
    private String text = null;

    private final String fpath;
    private FileChannel fc = null;
    private HashMap<String, String> parMap = null;
    private static HashMap<String, Double> phaseTable = null;
    private String[] acqOrder;
    private SampleSchedule sampleSchedule = null;
    private final double scale;
    // flag to indicate BrukerData has been opened as an FID
    private boolean isFID;
    boolean hasFID = false;
    boolean hasSpectrum = false;
    List<Double> arrayValues = new ArrayList<>();
    File nusFile = null;
    private final List<DatasetGroupIndex> datasetGroupIndices = new ArrayList<>();

    /**
     * Open Bruker parameter and data files.
     *
     * @param path    full path to the fid directory or file
     * @param nusFile The name of a NUS file to load
     * @throws java.io.IOException
     */
    public BrukerData(String path, File nusFile) throws IOException {
        if (path.endsWith(File.separator)) {
            path = path.substring(0, path.length() - 1);
        }
        this.fpath = path;
        this.nusFile = nusFile;
        isFID = true;
        File file = new File(path);
        openParFile(file);
        openDataFile(path);
        scale = 1.0e6;
    }

    /**
     * Open Bruker parameter and processed data files.
     *
     * @param path full path to the processed data file
     * @throws java.io.IOException
     */
    public BrukerData(String path) throws IOException {
        if (path.endsWith(File.separator)) {
            path = path.substring(0, path.length() - 1);
        }
        File file = new File(path);
        this.fpath = path;
        this.nusFile = null;
        isFID = false;
        openParFile(file.getParentFile().getParentFile().getParentFile());
        scale = 1.0;
    }

    public Dataset toDataset(String datasetName) throws IOException {
        DatasetLayout layout = new DatasetLayout(dim);
        int lastBlockSize = 1;
        int lastSize = 0;
        int xdim = 0;
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
            File file = new File(fpath);
            datasetName = suggestName(file);
        }
        Dataset dataset = new Dataset(fpath, datasetName, layout, false,
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
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(rootName).append("_").append(numFile.getName()).
                append("_").append(pdataNumFile.getName()).append("_").
                append(file.getName());
        return sBuilder.toString();
    }

    public String suggestName() {
        File file = new File(fpath);
        File numFile;
        if (file.getName().equals("fid") || file.getName().equals("ser")) {
            numFile = file.getParentFile();
        } else {
            numFile = file;
        }
        File rootFile = numFile.getParentFile();
        String rootName = rootFile != null ? rootFile.getName() : "";
        rootName = rootName.replace(" ", "_");
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(rootName).append("_").append(numFile.getName());
        return sBuilder.toString();
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
     * @param bpath full path for data
     * @return if data was successfully found or not
     */
    public static boolean findData(StringBuilder bpath) {
        File file = new File(bpath.toString());
        String fileName = file.getName();
        return isProcessedFile(fileName);
    }

    /**
     * Finds FID data, given a path to search for vendor-specific files and
     * directories.
     *
     * @param bpath full path for FID data
     * @return if FID data was successfully found or not
     */
    public static boolean findFID(StringBuilder bpath) {
        boolean found = false;
        if (findFIDFiles(bpath.toString())) {
            // case: select numeric subdirectory, e.g. 'HMQC/4'
            found = true;
        } else {
            // case: select 'ser', 'fid', or 'acqus' file
            File f = new File(bpath.toString());
            String s = f.getParent();
            if (findFIDFiles(s)) {
                int len2 = bpath.toString().length();
                int len1 = s.length();
                bpath = bpath.delete(len1, len2);
                bpath.trimToSize();
                found = true;
            } else {
                // case: select parent dir, e.g. 'HMQC'; look for subdir, e.g. 'HMQC/4'
                if (bpath.toString().endsWith(File.separator)) {
                    bpath.setLength(bpath.length() - 1);
                    bpath.trimToSize();
                }
                Path bdir = Paths.get(bpath.toString());
                if (bdir.toFile().isDirectory()) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(bdir, "[0-9]")) {
                        for (Path entry : stream) {
                            s = entry.toString();
                            if (findFIDFiles(s)) {
                                s = s.substring(bdir.toString().length());
                                bpath.append(s);
                                found = true;
                                break;
                            }
                        }
                    } catch (DirectoryIteratorException | IOException ex) {
                        // I/O error encountered during the iteration, the cause is an IOException
                        log.warn(ex.getMessage(), ex);
                    }
                }
            }
        }
        return found;
    } // findFID

    private static boolean findFIDFiles(String dpath) {
        boolean found = false;
        if ((new File(dpath + File.separator + "acqus")).exists()) {
            if ((new File(dpath + File.separator + "ser")).exists()) {
                found = true;
            } else if ((new File(dpath + File.separator + "fid")).exists()) {
                found = true;
            }
        }
        return found;
    } // findFIDFiles

    private static boolean findDataFiles(String dpath) {
        boolean found = false;
        if ((new File(dpath + File.separator + "acqus")).exists()) {
            if ((new File(dpath + File.separator + "ser")).exists()) {
                found = true;
            } else if ((new File(dpath + File.separator + "fid")).exists()) {
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
        return fpath;
    }

    @Override
    public String getVendor() {
        return "bruker";
    }

    @Override
    public String getUser() {
        String s = "";
        if ((s = getPar("OWNER,1")) == null) {
            s = "";
        } else {
            s = s.trim();
        }
        return s;
    }

    @Override
    public Map getFidFlags() {
        Map<String, Boolean> flags = new HashMap(5);
        flags.put("fixdsp", fixDSP);
        flags.put("shiftdsp", fixByShift);
        flags.put("exchangeXY", exchangeXY);
        flags.put("swapBits", swapBits);
        flags.put("negatePairs", negatePairs);
        return flags;
    }

    @Override
    public String getFilePath() {
        return fpath;
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
        return names.toArray(new String[names.size()]);
    }

    @Override
    public double getSF(int iDim) {
        double sf = 1.0;
        if (Sf[iDim] != null) {
            sf = Sf[iDim];
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
        Sf[iDim] = value;
    }

    @Override
    public void resetSF(int iDim) {
        Sf[iDim] = null;
    }

    @Override
    public double getSW(int iDim) {
        double sw = 1.0;
        if (Sw[iDim] != null) {
            sw = Sw[iDim];
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
        Sw[iDim] = value;
    }

    @Override
    public void resetSW(int iDim) {
        Sw[iDim] = null;
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
        Ref[iDim] = ref;
        String nucleusName = getTN(iDim);
        Nuclei nucleus = Nuclei.findNuclei(nucleusName);

        if (nucleus == Nuclei.H1) {
            double sf0 = getSF(iDim);
            zeroFreq = sf0 / (1.0 + ref * 1.0e-6);
        }
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
        for (int i = 0;i<getNDim();i++) {
            String nucleusName = getTN(i);
            Nuclei nucleus = Nuclei.findNuclei(nucleusName);
            if (nucleus == Nuclei.H1) {
                hDim = i;
            }
        }
        double calcBaseFreq;
        if (hDim != -1) {
            Double baseFreq = getParDouble("BF1," + (hDim +1));
            Double lockPPM = getParDouble("LOCKPPM,1");
            if (lockPPM == null) {
                if (isAcqueous) {
                    lockPPM = 4.717;
                } else {
                    if (solvent.equalsIgnoreCase("cdcl3")) {
                        lockPPM = 7.29;
                    } else  if (solvent.equalsIgnoreCase("cd3od")) {
                        lockPPM = 4.761;
                    } else  if (solvent.equalsIgnoreCase("dmso")) {
                        lockPPM = 2.578;
                    } else  if (solvent.equalsIgnoreCase("acetone")) {
                        lockPPM = 1.892;
                    } else {
                        lockPPM = 4.717;
                    }
                }
            }

            if (actualLockRef == null) {
                actualLockRef = lockPPM;
            }
            calcBaseFreq =  ReferenceCalculator.getCorrectedBaseFreq(baseFreq, lockPPM, actualLockRef);
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
        if ((d = getParDouble("TEMP,1")) == null) {
            if ((d = getParDouble("TE,1")) == null) {
// fixme what should we return if not present, is it ever not present
                d = 298.0;
            }
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
                log.warn("Unable to parse date.", e);
            }
        } else {
            System.out.println("no date");
        }
        return seconds;
    }

    // open and read Bruker text file
    @Override
    public String getText() {
        if (text == null) {
            String textPath = fpath + "/pdata/1/title";
            if ((new File(textPath)).exists()) {
                try {
                    Path path = FileSystems.getDefault().getPath(textPath);
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
    public boolean arePhasesSet(int dim) {
        Double ph0 = getParDouble("PHC0," + (dim + 1));
        Double ph1 = getParDouble("PHC1," + (dim + 1));
        return (ph0 != null && Math.abs(ph0) > 1.0e-9) || (ph1 != null && Math.abs(ph1) > 1.0e-9);
    }

    @Override
    public double getPH0(int iDim) {
        double ph0 = 0.0;
        Double dpar;
        if ((dpar = getParDouble("PHC0," + (iDim + 1))) != null) {
            ph0 = -dpar;
            if (iDim == 1) {
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
    private void openParFile(File parDirFile) throws IOException {
        parMap = new LinkedHashMap<>(200);
        Path pulseSequencePath = parDirFile.toPath().resolve("pulseprogram");
        String dimPar = null;
        if (pulseSequencePath.toFile().exists()) {
            var lines = scanPulseSequence(pulseSequencePath);
            var optLine = lines.stream().
                    map(line -> line.trim()).
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
                acqfile = "acqus";
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
            Double dpar;
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
            nusFile = new File(fpath + File.separator + "nuslist");
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
        int smallDim = getMinDim();
        if (parMap != null) {
            String[] listTypes = {"vd", "vc", "vp", "fq2", "fq3"};

            for (String listType : listTypes) {
                String parValue;
                if ((parValue = parMap.get(listType)) != null) {
                    String[] sValues = parValue.split("\t");
                    if (sValues.length > 0) {
                        List<String> sList = Arrays.asList(sValues);
                        int dimSize = getSize(smallDim);
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
                                double scale = 1.0;
                                if (sValue.endsWith("m")) {
                                    sValue = sValue.substring(0, sValue.length() - 1);
                                    scale = 1.0e-3;
                                } else if (sValue.endsWith("u")) {
                                    sValue = sValue.substring(0, sValue.length() - 1);
                                    scale = 1.0e-6;
                                } else if (sValue.endsWith("s")) {
                                    sValue = sValue.substring(0, sValue.length() - 1);
                                    scale = 1.0;
                                }
                                result.add(Double.parseDouble(sValue) * scale);
                            } catch (NumberFormatException nFE) {
                                System.out.println("bad double " + sValue);
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
        if ((fnmode = getParInt("AQ_mod,1")) != null) {
            if (fnmode == 2) {
                fttype[0] = "rft";
                complexDim[0] = false;
                exchangeXY = false;
                negatePairs = true;
            }
        }
        for (int i = 2; i <= dim; i++) {
            if ((fnmode = getParInt("FnMODE," + i)) != null) { // acqu2s
                complexDim[i - 1] = true;
                fttype[i - 1] = "ft";
                switch (fnmode) {
                    case 2:
                    case 3:
                        complexDim[i - 1] = false;
                        fttype[i - 1] = "rft";
                        f1coefS[i - 1] = AcquisitionType.REAL.getLabel();
                        break;
                    case 4:
                        f1coef[i - 1] = AcquisitionType.HYPER_R.getCoefficients();
                        f1coefS[i - 1] = AcquisitionType.HYPER_R.getLabel();
                        break;
                    case 0:
                        complexDim[i - 1] = getValues(i - 1).isEmpty();
                        if (complexDim[i - 1]) {
                            f1coef[i - 1] = AcquisitionType.HYPER.getCoefficients();
                            f1coefS[i - 1] = AcquisitionType.HYPER.getLabel();
                            complexDim[i - 1] = true;
                            fttype[i - 1] = "negate";
                        } else {
                            f1coefS[i - 1] = AcquisitionType.ARRAY.getLabel();
                        }
                        break;
                    case 5:
                        f1coef[i - 1] = AcquisitionType.HYPER.getCoefficients();
                        f1coefS[i - 1] = AcquisitionType.HYPER.getLabel();
                        complexDim[i - 1] = true;
                        fttype[i - 1] = "negate";
                        break;
                    case 6:
                        f1coef[i - 1] = AcquisitionType.ECHO_ANTIECHO_R.getCoefficients();
                        f1coefS[i - 1] = AcquisitionType.ECHO_ANTIECHO_R.getLabel();
                        deltaPh0_2 = 90.0;
                        break;
                    case 1:
                        complexDim[i - 1] = getValues(i - 1).isEmpty();
                        if (complexDim[i - 1]) {
                            f1coefS[i - 1] = AcquisitionType.SEP.getLabel();
                        } else {
                            f1coefS[i - 1] = AcquisitionType.ARRAY.getLabel();
                        }
                        break;
                    default:
                        f1coef[i - 1] = new double[]{1, 0, 0, 1};
                        f1coefS[i - 1] = AcquisitionType.SEP.getLabel();
                        break;
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
    private void openDataFile(String datapath) {
        if ((new File(datapath + File.separator + "ser")).exists()) {
            datapath += File.separator + "ser";  // nD
        } else if ((new File(datapath + File.separator + "fid")).exists()) {
            datapath += File.separator + "fid";  // 1D
        }
        try {
            fc = FileChannel.open(Paths.get(datapath), StandardOpenOption.READ);
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
            // fixme which is correct (use ceil or not)
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
                if ((rvec != null) && (ivec != null)) {
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
        } catch (EOFException e) {
            log.warn(e.getMessage(), e);
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException ex) {
                    log.warn(ex.getMessage(), ex);
                }
            }
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException ex) {
                    log.warn(ex.getMessage(), ex);
                }
            }
        }
    }  // end readVecBlock

    // read value along dim
    // fixme only works for 2nd dim
    private void readValue(int iDim, int stride, int fileIndex, int vecIndex, int xCol, byte[] dataBuf, int nPer) {
        try {
            int nread = 0;
            int skips = fileIndex * stride + xCol * 4 * nPer;
            ByteBuffer buf = ByteBuffer.wrap(dataBuf, vecIndex * 4 * nPer, 4 * nPer);
            nread = fc.read(buf, skips);
        } catch (EOFException e) {
            log.warn(e.getMessage(), e);
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException ex) {
                    log.warn(ex.getMessage(), ex);
                }
            }
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException ex) {
                    log.warn(ex.getMessage(), ex);
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
                data[j / 2] = new Complex((double) py / scale, (double) px / scale);
            } else {
                data[j / 2] = new Complex((double) px / scale, -(double) py / scale);
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
        double px, py;
        for (int j = 0; j < np; j += 2) {
            px = dBuffer.get(j);
            py = dBuffer.get(j + 1);
            if (exchangeXY) {
                data[j / 2] = new Complex((double) py / scale, (double) px / scale);
            } else {
                data[j / 2] = new Complex((double) px / scale, -(double) py / scale);
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
                rdata[j / 2] = (double) py / scale;
                idata[j / 2] = (double) px / scale;
            } else {
                rdata[j / 2] = (double) px / scale;
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
        double px, py;
        for (int j = 0; j < np; j += 2) {
            px = dBuffer.get(j);
            py = dBuffer.get(j + 1);
            if (exchangeXY) {
                rdata[j / 2] = (double) py / scale;
                idata[j / 2] = (double) px / scale;
            } else {
                rdata[j / 2] = (double) px / scale;
                idata[j / 2] = -(double) py / scale;
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
            data[j] = (double) px / scale;
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
            data[j] = (double) px / scale;
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
            if (elem.substring(0, 1).equals("p")) {
                builder.append(elem.substring(1, 2));
            } else if (elem.substring(0, 1).equals("a")) {
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
            if ((arrayValues != null) && (arrayValues.size() > 0) && ((arrayValues.size() == getSize(iDim)) || (iDim == minDim))) {
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
    }

    // write binary data into text file, using header info
    public void fileoutraw() {
        String dpath = fpath;
        if ((new File(dpath + File.separator + "ser")).exists()) {
            dpath += File.separator + "ser";
        } else if ((new File(dpath + File.separator + "fid")).exists()) {
            dpath += File.separator + "fid";
        }
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get("/tmp/bwraw.txt"), Charset.forName("US-ASCII"),
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             DataInputStream in = new DataInputStream(new FileInputStream(dpath))) {
            bw.write("rawfile header");
            bw.newLine();
            int px;
            int nvectors = getNVectors();
            for (int i = 0; i < nvectors; i++) {
                bw.write("block " + i + " ");
                for (int j = 0; j < np * 2; j++) {  // data
                    if (j % 512 == 0) {
                        bw.write("\n  block " + i + ":" + (j / 512) + " : ");
                    }
                    px = in.readInt();
                    if (swapBits) {
                        px = Integer.reverseBytes(px);
                    }
                    bw.write(px + " ");
                }
                bw.write(": endblock " + i);
                bw.newLine();
                bw.flush();
            }
        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);
        }
    }

    // output file after dspPhase etc.
    private void fileout2() {
        try {
            try (BufferedWriter bw = Files.newBufferedWriter(Paths.get("/tmp/dwraw.txt"), Charset.forName("US-ASCII"),
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                bw.write("rawfile header");
                bw.newLine();
                int nvectors = getNVectors();

                for (int i = 0; i < nvectors; i++) {
                    bw.write("block " + i + " ");
                    Vec vc = new Vec(np, true);
                    Complex[] cdata;
                    readVector(i, vc);
                    cdata = vc.getCvec();
                    for (int j = 0; j < vc.getSize(); j++) {  // data
                        if (j % 512 == 0) {
                            bw.write("\n  block " + i + ":" + (j / 512) + " : ");
                        }
                        bw.write(cdata[j] + " ");
                    }
                    bw.write(": endblock " + i);
                    bw.newLine();
                    bw.flush();
                }
            }
        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);

        }
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

    public static void main(String[] args) {
        try {
            System.out.println("byte:" + Byte.SIZE + " int:" + Integer.SIZE + " float:" + Float.SIZE + " double:" + Double.SIZE);

            String root = "/Users/bayardfetler/NVJ/nmr_2013_06/rna/nmrraw/";
            String fpath = root + "HMQC/4";

            NMRData bruker = NMRDataUtil.getFID(fpath);
            Complex[] cdata = new Complex[bruker.getNPoints()];
            bruker.readVector(0, cdata);  // not useful, can't dspph
            System.out.print("  complex data:");
            for (int i = 0; i < 30; i++) {
                System.out.print(" " + cdata[i]);
            }
            System.out.println(" : " + cdata[bruker.getNPoints() / 2 - 1]);
            System.out.println(" : " + cdata[bruker.getNPoints() - 1]);

            Vec vc = new Vec(bruker.getNPoints(), true);
            bruker.readVector(0, vc);
            cdata = vc.getCvec();
            System.out.println("vec cdata length " + vc.getSize() + " (orig " + cdata.length + ")");
            System.out.print("  vec 0 data:");
            for (int i = 0; i < 4; i++) {
                System.out.print(" " + cdata[i]);
            }
            System.out.println("");
            int pt = bruker.getNPoints() / 2 - 4;
            for (int i = 0; i < 4; i++) {
                System.out.print(" " + cdata[pt + i]);
            }
            System.out.println("");
            pt = bruker.getNPoints() - 70;
            for (int i = 0; i < 20; i++) {
                System.out.print(" " + cdata[pt + i]);
            }
            System.out.println(" : " + cdata[bruker.getNPoints() - 1]);
            pt = vc.getSize() - 20;
            for (int i = 0; i < 20; i++) {
                System.out.print(" " + cdata[pt + i]);
            }
            System.out.println(" : " + cdata[vc.getSize() - 1]);

            vc = new Vec(bruker.getNPoints(), true);
            bruker.readVector(6, vc);
            System.out.print("  vec 6 data:");
            cdata = vc.getCvec();
            for (int i = 0; i < 4; i++) {
                System.out.print(" " + cdata[i]);
            }
            pt = vc.getSize() - 2;
            System.out.print(" :");
            for (int i = 0; i < 2; i++) {
                System.out.print(" " + cdata[pt + i]);
            }
            System.out.println();

            vc = new Vec(bruker.getNPoints(), true);
            bruker.readVector(128, vc);
            System.out.print("  vec 128 data:");
            cdata = vc.getCvec();
            for (int i = 0; i < 4; i++) {
                System.out.print(" " + cdata[i]);
            }
            pt = vc.getSize() - 2;
            System.out.print(" :");
            for (int i = 0; i < 2; i++) {
                System.out.print(" " + cdata[pt + i]);
            }
            System.out.println();

            vc = new Vec(bruker.getNPoints(), true);
            bruker.readVector(255, vc);
            System.out.print("  vec 255 data:");
            cdata = vc.getCvec();
            for (int i = 0; i < 4; i++) {
                System.out.print(" " + cdata[i]);
            }
            pt = vc.getSize() - 2;
            System.out.print(" :");
            for (int i = 0; i < 2; i++) {
                System.out.print(" " + cdata[pt + i]);
            }
            System.out.println();

            Vec vc2 = new Vec(bruker.getNPoints(), false);
            bruker.readVector(0, vc2);
            System.out.print("  vec2 0 data:");
            for (int i = 0; i < 60; i++) {
                System.out.print(" " + vc2.rvec[i]);
            }
            System.out.println(" : " + vc2.rvec[bruker.getNPoints() - 1]);

            ArrayList alist = NMRDataUtil.guessNucleusFromFreq(810.0);
            System.out.println("guess nucleus : " + alist);
            alist = NMRDataUtil.guessNucleusFromFreq(310.0);
            System.out.println("guess nucleus : " + alist);
            alist = NMRDataUtil.guessNucleusFromFreq(75.7);
            System.out.println("guess nucleus : " + alist);
            alist = NMRDataUtil.guessNucleusFromFreq(20.7);
            System.out.println("guess nucleus : " + alist);

            //guess nucleus : [1H, 10.0, 800.0, 90.0]
            //guess nucleus : [31P, 6.394472500000006, 750.0, 13.845895999999982]
            //guess nucleus : [13C, 0.26498800000000244, 300.0, 0.3258725000000027]
            //guess nucleus : [15N, 9.710349, 300.0, 19.847132000000006]
            int dim = bruker.getNDim();
            System.out.println("bruker dim " + dim + ", solvent " + bruker.getSolvent());
            for (int i = 1; i <= dim; i++) {
                System.out.print("  dim=" + i + " tn=" + bruker.getTN(i));
                System.out.print(" sf=" + bruker.getSF(i));
                System.out.print(" sw=" + bruker.getSW(i));
                System.out.print(" ref=" + bruker.getRef(i));
                System.out.println(" tdsize=" + bruker.getSize(i));
            }

            System.out.print("sequence=" + bruker.getSequence() + " solvent=" + bruker.getSolvent());
            System.out.println(" dim=" + bruker.getNDim() + " nvectors=" + bruker.getNVectors()
                    + " npoints=" + bruker.getNPoints());
            System.out.println("  tdsize's: " + bruker.getSize(0) + " " + bruker.getSize(1)
                    + " " + bruker.getSize(2) + " " + bruker.getSize(3));
            System.out.println("  tn's: " + bruker.getTN(0) + " " + bruker.getTN(1)
                    + " " + bruker.getTN(2) + " " + bruker.getTN(3));
            System.out.println("  sfrq's: " + bruker.getSF(0) + " " + bruker.getSF(1)
                    + " " + bruker.getSF(2) + " " + bruker.getSF(3));
            System.out.println("  sw's: " + bruker.getSW(0) + " " + bruker.getSW(1)
                    + " " + bruker.getSW(2) + " " + bruker.getSW(3));
            System.out.println("  ref's: " + bruker.getRef(0) + " " + bruker.getRef(1)
                    + " " + bruker.getRef(2) + " " + bruker.getRef(3));

            System.out.println("find ser");
            bruker = NMRDataUtil.getFID(fpath + "/ser");
            System.out.println(" dim=" + bruker.getNDim() + " nvectors=" + bruker.getNVectors()
                    + " npoints=" + bruker.getNPoints() + " toString " + bruker.toString());
            System.out.println("find HMQC");
            bruker = NMRDataUtil.getFID(root + "HMQC");
            System.out.println(" dim=" + bruker.getNDim() + " nvectors=" + bruker.getNVectors()
                    + " npoints=" + bruker.getNPoints() + " toString " + bruker.toString());
            System.out.println("find HMQC/");
            bruker = NMRDataUtil.getFID(root + "HMQC/");
            System.out.println(" dim=" + bruker.getNDim() + " nvectors=" + bruker.getNVectors()
                    + " npoints=" + bruker.getNPoints() + " toString " + bruker.toString());
            System.out.println(" get f1coef: " + Arrays.toString(bruker.getCoefs(2)) + " scooby: " + bruker.getParDouble("SCOOBY,3"));
            System.out.println("find NOESY");
            bruker = NMRDataUtil.getFID(root + "NOESY");
            System.out.println(" dim=" + bruker.getNDim() + " nvectors=" + bruker.getNVectors()
                    + " npoints=" + bruker.getNPoints() + " f1coef=" + Arrays.toString(bruker.getCoefs(2)) + " toString " + bruker.toString());
            System.out.println(" expd=" + bruker.getExpd(1));
            SinebellWt sb = bruker.getSinebellWt(2);
            System.out.print(" sinebell: exists=" + sb.exists() + " power=" + sb.power() + " sb=" + sb.sb() + " sbs=" + sb.sbs());
            System.out.println(" size=" + sb.size() + " offset=" + sb.offset() + " end=" + sb.end());
            GaussianWt gb = bruker.getGaussianWt(1);
            System.out.println(" gaussian: exists=" + gb.exists() + " gb=" + gb.gf() + " lb=" + gb.lb() + " gfs=" + gb.gfs());
            FPMult fp = bruker.getFPMult(1);
            System.out.println(" fpmult: exists=" + fp.exists() + " fpmult=" + fp.fpmult());
            LPParams lp = bruker.getLPParams(1);
            System.out.print(" lppars: exists=" + lp.exists() + " status=" + lp.status() + " ncoef=" + lp.ncoef());
            System.out.print(" pstart=" + lp.predictstart() + " pend=" + lp.predictend());
            System.out.println(" fstart=" + lp.fitstart() + " fend=" + lp.fitend());

            System.out.println("find NOT");
            bruker = NMRDataUtil.getFID(root + "NOT");
            System.out.println(" dim=" + bruker.getNDim() + " nvectors=" + bruker.getNVectors()
                    + " npoints=" + bruker.getNPoints());
        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);
        }

    }

}
