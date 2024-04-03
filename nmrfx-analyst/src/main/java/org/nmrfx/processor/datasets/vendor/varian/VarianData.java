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
package org.nmrfx.processor.datasets.vendor.varian;

import org.apache.commons.math3.complex.Complex;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.processor.datasets.AcquisitionType;
import org.nmrfx.processor.datasets.DatasetGroupIndex;
import org.nmrfx.processor.datasets.DatasetType;
import org.nmrfx.processor.datasets.ReferenceCalculator;
import org.nmrfx.processor.datasets.parameters.FPMult;
import org.nmrfx.processor.datasets.parameters.GaussianWt;
import org.nmrfx.processor.datasets.parameters.LPParams;
import org.nmrfx.processor.datasets.parameters.SinebellWt;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.datasets.vendor.VendorPar;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.SampleSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * @author bfetler
 * <p>
 * access through NMRDataUtil
 */
public class VarianData implements NMRData {
    private static final Logger log = LoggerFactory.getLogger(VarianData.class);

    DateTimeFormatter vTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");//20050804T233538
    DateTimeFormatter vDateFormatter = DateTimeFormatter.ofPattern("MMM ppd yyyy");// Feb  4 2000
    private final static int MAXDIM = 10;

    private final String fpath;
    private int nblocks = 0;
    private int np;
    private boolean isSpectrum = false;
    private int ebytes = 0, tbytes = 0, nbheaders = 0, ntraces = 0;
    private short status = 0;
    private boolean isFloat = false;
    private boolean isShort = false;
    private FileChannel fc = null;
    private HashMap<String, String> parMap = null;
    private String[] acqOrder;
    // fixme dynamically determine size
    private final int arraysize[] = new int[MAXDIM];  // TD,1 TD,2 etc.
    private final Double[] Ref = new Double[MAXDIM];
    private final Double[] Sw = new Double[MAXDIM];
    private final Double[] Sf = new Double[MAXDIM];

    private Double zeroFreq = null;
    private final AcquisitionType[] symbolicCoefs = new AcquisitionType[MAXDIM];
    private String text = null;
    private SampleSchedule sampleSchedule = null;
    Integer nDimVal = null;
    int[] sizes = null;
    int[] maxSizes = null;
    double scale = 1.0e6;
    private DatasetType preferredDatasetType = DatasetType.NMRFX;
    List<Double> arrayValues = new ArrayList<>();
    private final List<DatasetGroupIndex> datasetGroupIndices = new ArrayList<>();

    static final String PAR_LIST = "acqdim apptype array arraydim axis axisf procdim "
            + "solvent seqfil pslabel sfrq dfrq dfrq2 dfrq3 sw sw1 sw2 sw3 "
            + "tn dn dn2 dn3 np ni ni2 ni3 f1coef f2coef f2coef "
            + "phase phase2 phase3 rfl rfp rfl1 rfp1 rfl2 rfp2 rfl3 rfp3 "
            + "rp lp rp1 lp1 rp2 lp2 rp3 lp3 lsfid lsfid1 lsfid2 lsfid3 "
            + "fpmult fpmult1 fpmult2 fpmult3 gf gfs gf1 gfs1 gf2 gfs2 gf3 gfs3 "
            + "lb lb1 lb2 lb3 sb sbs sb1 sbs1 sb2 sbs2 sb3 sbs3 "
            + "proc lpalg lpopt lpfilt lpnupts lpext strtlp strtext "
            + "proc1 lpalg1 lpopt1 lpfilt1 lpnupts1 lpext1 strtlp1 strtext1 "
            + "proc2 lpalg2 lpopt2 lpfilt2 lpnupts2 lpext2 strtlp2 strtext2 "
            + "proc3 lpalg3 lpopt3 lpfilt3 lpnupts3 lpext3 strtlp3 strtext3 temp";

    /**
     * open Varian parameter and data files
     *
     * @param path : full path to the .fid directory, fid file or spectrum
     */
    public VarianData(String path) {
        if (path.endsWith(File.separator)) {
            path = path.substring(0, path.length() - 1);
        }
        this.fpath = path;
        openParFile(path);
        openDataFile(path);
        // force caching nDim and sizes
        getNDim();
        getSize(0);
        checkAndOpenSampleSchedule(path);
    }

    public String suggestName() {
        File file = new File(fpath);
        String name = file.getName();
        if (name.equals("fid")) {
            name = file.getParentFile().getName();
        }
        if (name.endsWith(".fid")) {
            int len = name.length();
            name = name.substring(0, len-3);
        }
        return name;
    }

    @Override
    public void close() {
        try {
            fc.close();
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
    }

    public static boolean findFID(StringBuilder bpath) {
        boolean found = false;
        if (findFIDFiles(bpath.toString())) {
            // case: select .fid directory
            found = true;
        } else {
            // case: select 'fid' or 'procpar' file
            File f = new File(bpath.toString());
            String s = f.getParent();
            if (findFIDFiles(s)) {
                int len = bpath.toString().length();
                bpath = bpath.delete(s.length(), len);
                bpath.trimToSize();
                found = true;
            }
        }
        return found;
    } // findFID

    private static boolean findFIDFiles(String dpath) {
        boolean found = false;
        if (dpath.endsWith(".fid" + File.separator)
                || dpath.endsWith(".fid")) {
            if ((new File(dpath + File.separator + "fid")).exists()) {
                if ((new File(dpath + File.separator + "procpar")).exists()) {
                    found = true;
                }
            }
        }
        return found;
    } // findFIDFiles

    @Override
    public String toString() {
        return fpath;
    }

    @Override
    public String getVendor() {
        return "varian";
    }

    @Override
    public String getFilePath() {
        return fpath;
    }

    @Override
    public DatasetType getPreferredDatasetType() {
        return preferredDatasetType;
    }

    @Override
    public void setPreferredDatasetType(DatasetType datasetType) {
        this.preferredDatasetType = datasetType;
    }

    @Override
    public List<VendorPar> getPars() {
        List<VendorPar> vendorPars = new ArrayList<>();
        for (Map.Entry<String, String> par : parMap.entrySet()) {
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
        if ((parMap == null) || (parMap.get(parname) == null) || parMap.get(parname).equals("n")) {
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

    @Override
    public int getNVectors() {  // number of vectors
        return nblocks;
    }

    @Override
    public int getNPoints() {  // points per vector
        return np / 2;
    }

    public boolean isSpectrum() {
        return isSpectrum;
    }

    @Override
    public String getFTType(int iDim) {
        // fixme
        return "ft";
    }

    @Override
    public final int getNDim() {
        if (nDimVal == null) {
            int nDim = 1;
            Integer ipar;
            if (isSpectrum) {
                ipar = getParInt("procdim");
                if (ipar != null) {
                    nDim = ipar;
                } else // not really correct, gives acqdim not procdim
                {
                    if (((ipar = getParInt("ni3")) != null) && (ipar > 1)) {
                        nDim = 3; // can't do 4d ft on Varian software
                    } else if (((ipar = getParInt("ni2")) != null) && (ipar > 1)) {
                        nDim = 3;
                    } else if (((ipar = getParInt("ni")) != null) && (ipar > 1)) {
                        nDim = 2;
                    }
                }
            } else {
                ipar = getParInt("acqdim");
                if (ipar != null) {
                    nDim = ipar;
                } else if (((ipar = getParInt("ni3")) != null) && (ipar > 1)) {
                    nDim = 4;
                } else if (((ipar = getParInt("ni2")) != null) && (ipar > 1)) {
                    nDim = 3;
                } else if (((ipar = getParInt("ni")) != null) && (ipar > 1)) {
                    nDim = 2;
                }
            }
            nDimVal = nDim;
        }
        return nDimVal;
    }

    public void setUserSymbolicCoefs(int iDim, AcquisitionType coefs) {
        symbolicCoefs[iDim] = coefs;
    }

    public AcquisitionType getUserSymbolicCoefs(int iDim) {
        return symbolicCoefs[iDim];
    }

    @Override
    public String getSymbolicCoefs(int iDim) {
        String name = "f" + iDim + "coef";
        String s = getPar(name);
        if (s == null || s.isEmpty()) {
            return AcquisitionType.HYPER.getLabel();
        }

        return switch (s) {
            case "1 0 0 0 0 0 -1 0" -> AcquisitionType.HYPER.getLabel();
            case "1 0 0 0 0 0 1 0" -> AcquisitionType.HYPER_R.getLabel();
            case "1 0 -1 0 0 1 0 1" -> AcquisitionType.ECHO_ANTIECHO.getLabel();
            case "1 0 1 0 0 1 0 -1" -> AcquisitionType.ECHO_ANTIECHO_R.getLabel();
            case "1 0 1 0 1 0 1 0" -> AcquisitionType.GE.getLabel();
            case "1 0 0 1" -> AcquisitionType.SEP.getLabel();
            default -> s;
        };
    }

    @Override
    public double[] getCoefs(int iDim) {
        String name = "f" + iDim + "coef";
        double dcoefs[] = {1, 0, 0, 0}; // reasonable for noesy, tocsy
        String s;
        if ((s = getPar(name)) == null) {
            s = "";
        }
        if (!s.equals("")) {
            String[] coefs = s.split(" ");
            dcoefs = new double[coefs.length];
            for (int i = 0; i < coefs.length; i++) {
                dcoefs[i] = Double.valueOf(coefs[i]);
            }
        }
        return dcoefs;
    }
// e.g. hnco3d.fid f1coef="1 0 0 0 0 0 -1 0" f2coef="1 0 1 0 0 1 0 -1"

    private char getAxisChar(int iDim) {
        char achar = 'h';
        String axis = getPar("axis");
        if (axis != null) {
            if (iDim < axis.length()) {
                achar = axis.charAt(iDim);
            } else {
                switch (iDim) {
                    case 1:
                        achar = 'd';
                        break;
                    case 2:
                        achar = '2';
                        break;
                    case 3:
                        achar = '3';
                        break;
                    default:
                        achar = 'h';
                        break;
                }
            }
        }
        return achar;
    }

    private String getAxisFreqName(int iDim) {
        String freqName;
        char achar = getAxisChar(iDim);
        switch (achar) {
            case 'h':
                freqName = "hz";
                break;
            case 'p':
                freqName = "sfrq";
                break;
            case 'd':
                freqName = "dfrq";
                break;
            case '1':
                freqName = "dfrq";
                break;
            case '2':
                freqName = "dfrq2";
                break;
            case '3':
                freqName = "dfrq3";
                break;
            default:
                freqName = "hz";
                break;
        }
        return freqName;
    }

    public String getApptype() {
        // homo2d hetero2d etc. if exists
        return getPar("apptype");
    }

    @Override
    public double getSF(int iDim) {
        double sf = 1.0;
        if (Sf[iDim] != null) {
            sf = Sf[iDim];
        } else {
            Double dpar;
            String name = getSFName(iDim);
            if (((dpar = getParDouble(name)) != null) && (dpar > 0.0)) {
                sf = dpar;
            }
        }
        return sf;
    }

    @Override
    public void setSF(int iDim, double sf) {
        Sf[iDim] = sf;
    }

    @Override
    public void resetSF(int iDim) {
        Sf[iDim] = null;
    }

    public String getSFName(int iDim) {
        String name = "sfrq";
        if (iDim > 0) {
            String app = getApptype();
            if ((iDim == 1) && (app != null) && (app.equals("homo2d"))) {
                name = "sfrq";
            } else if ((iDim == 1) && (app != null) && (app.equals("hetero2d"))) {
                name = "dfrq";
            } else {
                name = getAxisFreqName(iDim);
                if (name.equals("hz")) {
                    if (iDim == 1) {
                        name = "dfrq";
                    } else {
                        name = "dfrq" + iDim;
                    }
                }
            }
        }
        return name;
    }

    double getCorrectedBaseFreq() {
        Double reffrq = null;
        Nuclei nuclei = Nuclei.findNuclei(getTN(0));
        if ((reffrq = getParDouble("reffrq")) == null) {
            double rfl = getParDouble("rfl");
            double rfp = getParDouble("rfp");
            reffrq = getSF(0) * 1e6 - getSW(0) / 2.0 + rfl - rfp;
            reffrq = reffrq / 1.0e6;
        }
        return reffrq / (nuclei.getRatio() / 100.0);

    }

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
    public double getSW(int iDim) {
        double sw = 1.0;
        if (Sw[iDim] != null) {
            sw = Sw[iDim];
        } else {
            Double dpar;
            String name = "sw";
            if (iDim > 0) {
                String app = getApptype();
                if (iDim == 1 && app != null && app.equals("homo2d")) {
                    name = "sw";
                } else {
                    name = "sw" + iDim;
                }
            }
            if ((dpar = getParDouble(name)) != null) {
                sw = dpar;
            }
        }
        return sw;
    }

    @Override
    public void setSW(int iDim, double sw) {
        Sw[iDim] = sw;
    }

    @Override
    public void resetSW(int iDim) {
        Sw[iDim] = null;
    }

    @Override
    public String[] getSFNames() {
        int nDim = getNDim();
        String[] names = new String[nDim];
        for (int i = 0; i < nDim; i++) {
            names[i] = getSFName(i);
        }
        return names;
    }

    @Override
    public String[] getSWNames() {
        int nDim = getNDim();
        String[] names = new String[nDim];

        for (int i = 0; i < nDim; i++) {
            int dim = i;
            String name = "sw";
            if (dim > 0) {
                String app = getApptype();
                if (dim == 1 && app != null && app.equals("homo2d")) {
                    name = "sw";
                } else {
                    name = "sw" + dim;
                }
            }
            names[i] = name;
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

    private String getAxisTNname(int iDim) {
        String name;
        char achar = getAxisChar(iDim);
        switch (achar) {
            case 'h':
                name = "n";
                break;
            case 'p':
                name = "tn";
                break;
            case 'd':
                name = "dn";
                break;
            case '1':
                name = "dn";
                break;
            case '2':
                name = "dn2";
                break;
            case '3':
                name = "dn3";
                break;
            default:
                name = "n";
                break;
        }
        return name;
    }

    @Override
    public String getTN(int iDim) {
        String s;
        String tn = "";
        String name = "tn";
        if (iDim > 0) {
            String app = getApptype();
            if ((iDim == 1) && (app != null) && (app.equals("homo2d"))) {
                name = "tn";
            } else if ((iDim == 1) && (app != null) && (app.equals("hetero2d"))) {
                name = "dn";
            } else {
                name = getAxisTNname(iDim);
                if (name.equals("n")) {
                    if (iDim == 1) {
                        name = "dn";
                    } else {
                        name = "dn" + iDim;
                    }
                }
            }
        }
        if ((s = getPar(name)) != null) {
            tn = s;
        }
        switch (tn) {
            case "H1":
                tn = "1H";
                break;
            case "C13":
                tn = "13C";
                break;
            case "N15":
                tn = "15N";
                break;
            case "P31":
                tn = "31P";
                break;
            default:
                int nChars = tn.length();
                int firstDigit = -1;
                for (int i = 0; i < nChars; i++) {
                    if (Character.isDigit(tn.charAt(i))) {
                        firstDigit = i;
                        break;
                    }
                }
                if (firstDigit > 0) {
                    tn = tn.substring(firstDigit) + tn.substring(0, firstDigit);
                }
                break;
        }
        return tn;
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
            var refOpt = getRefAtCenter(iDim);
            if (refOpt.isPresent()) {
                ref = refOpt.get();
                // OFFSET from proc[n]s
            } else {
                String ext = "";
                if (iDim > 0) {
                    ext += iDim;
                }
                Double rfp;
                if ((rfp = getParDouble("rfl" + ext)) != null) {
                    double rfl = rfp;
                    if ((rfp = getParDouble("rfp" + ext)) != null) {
                        double sf = getSF(iDim);
                        double sw = getSW(iDim);
                        double dppm = sw / sf;
                        double reffrac = (sw - rfl) / sw;
                        ref = rfp / sf + dppm * reffrac - dppm / 2.0;
                        //ref = (sw - rfl + rfp) / sf;
                        // see vnmr.tcl line 805; use reffrq, reffrq1 instead of sfrq, dfrq?
                    }
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
        Optional<Double> result = Optional.of(ref);
        return result;
    }

    @Override
    public double getRefPoint(int iDim) {
        double refpt = getSize(iDim) / 2;
        return refpt;
    }

    @Override
    public final int getSize(int iDim) {
        if (sizes == null) {
            sizes = new int[getNDim()];
            maxSizes = new int[getNDim()];
            for (int i = 0; i < sizes.length; i++) {
                if (i == 0) {
                    sizes[i] = np / 2;
                } else {
                    // see vnmr.tcl lines 773-779, use here or new method getNarray?
                    int td = getNI(i);
                    sizes[i] = td;
                }
                maxSizes[i] = sizes[i];
            }
        }
        return sizes[iDim];
    }

    /**
     * Get the number of increments in the specified dimension
     *
     * @param iDim the dimension (1 is the first indirect dimension)
     * @return the number of increments
     */
    private int getNI(int iDim) {
        Integer ipar;
        int td = 0;
        String name = "ni";
        if (iDim > 1) {
            name = "ni" + iDim;
        }
        if ((ipar = getParInt(name)) != null) {
            td = ipar;
        }
        return td;
    }

    @Override
    public int getMaxSize(int iDim) {
        return maxSizes[iDim];
    }

    @Override
    public void setSize(int iDim, int size) {
        if (sizes == null) {
            // calling getSize will populate sizes with default
            getSize(iDim);
        }
        if (size > maxSizes[iDim]) {
            size = maxSizes[iDim];
        }
        sizes[iDim] = size;
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
    public List<Double> getValues(int dim) {
        List<Double> result;
        if (dim >= getNDim()) {
            result = arrayValues;
        } else {
            if ((dim != 0) && (dim == getMinDim())) {
                result = arrayValues;
            } else {
                result = new ArrayList<>();
            }
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
    public boolean isComplex(int iDim) {
        if (iDim == 0) {
            String s = getPar("proc");
            return !"rft".equals(s);
        } else {
            String ext = String.valueOf(iDim);
            String s = getPar("proc" + ext);
            boolean notRFT = !"rft".equals(s);

            if (iDim == 1) {
                s = getPar("phase");
            } else {
                s = getPar("phase" + ext);
            }
            if (s != null) {
                String[] f = s.split("\n");
                if (f.length > 1) {
                    return true;
                } else if (f.length == 1) {
                    return f[0].equals("0");
                } else {
                    return false;
                }
            } else {
                int td = getNI(iDim);
                boolean isComplex = false;
                if ((td > 1) && notRFT) {
                    isComplex = true;
                }
                return isComplex;
            }
        }
    }

    @Override
    public int getGroupSize(int iDim) {
        if (iDim == 0) {
            String s = getPar("proc");
            return "rft".equals(s) ? 1 : 2; // proc="ft" or "lp"
        } else {
            String ext = String.valueOf(iDim);
            String s;
            if (iDim == 1) {
                s = getPar("phase");
            } else {
                s = getPar("phase" + ext);
            }
            if (s != null) {
                String[] f = s.split("\n");
                return f.length;
            } else {
                return 1;
            }
        }
    }

    @Override
    public boolean getNegatePairs(int iDim) {
        return false;
    }

    @Override
    public String getSolvent() {
        String s;
        if ((s = getPar("solvent")) == null) {
            s = "";
        }
        return s;
    }

    @Override
    public double getTempK() {
        Double d;
// fixme what if temp is an array?
        if ((d = getParDouble("temp")) == null) {
// fixme what should we return if not present, is it ever not present
            d = 25.0;
        }
        d += 273.15;
        return d;
    }

    @Override
    public String getSequence() {
        String s;
        if ((s = getPar("seqfil")) != null) {
            if (s.equals("s2pul")) {
                s = getPar("pslabel");
            }
        }
        if (s == null) {
            s = "";
        }
        return s;
    }

    @Override
    public String getUser() {
        String s;
        if ((s = getPar("acquser")) == null) {
            if ((s = getPar("operator")) == null) {
                s = "";
            }
        }
        return s;
    }

    @Override
    public boolean arePhasesSet(int dim) {
        String ext = "";
        if (dim > 0) {
            ext += dim;
        }
        Double ph0 = getParDouble("rp" + ext);
        Double ph1 = getParDouble("lp" + ext);
        return (ph0 != null && Math.abs(ph0) > 1.0e-9) || (ph1 != null && Math.abs(ph1) > 1.0e-9);
    }

    @Override
    public double getPH0(int iDim) {
        double ph0 = 0.0;
        Double dpar;
        String ext = "";
        if (iDim > 0) {
            ext += iDim;
        }
        if ((dpar = getParDouble("rp" + ext)) != null) {
            ph0 = dpar;
            if ((dpar = getParDouble("lp" + ext)) != null) {
                ph0 += dpar;
            }
        }
        return ph0;
    }

    @Override
    public double getPH1(int iDim) {
        double ph1 = 0.0;
        Double dpar;
        String name = "lp";
        if (iDim > 0) {
            name += iDim;
        }
        if ((dpar = getParDouble(name)) != null) {
            ph1 = -dpar;
        }
        return ph1;
    }

    @Override
    public int getLeftShift(int iDim) {
        int shift = 0;
        Integer ipar;
        String name = "lsfid";
        if (iDim > 0) {
            name += iDim;
        }
        if ((ipar = getParInt(name)) != null) {
            shift = -ipar;
        }
        return shift;
    }

    @Override
    public double getExpd(int iDim) {
        double expd = 0.0;
        String spar;
        String name = "lb";
        if (iDim > 0) {
            name += iDim;
        }
        if ((spar = getPar(name)) != null) {
            if (spar.equals("n")) {
                expd = Double.parseDouble(spar);
            }
        }
        return expd;
    }

    @Override
    public SinebellWt getSinebellWt(int iDim) {
        return new VarianSinebellWt(iDim);
    }

    @Override
    public GaussianWt getGaussianWt(int iDim) {
        return new VarianGaussianWt(iDim);
    }

    @Override
    public FPMult getFPMult(int iDim) {
        return new VarianFPMult(iDim);
    }

    @Override
    public LPParams getLPParams(int iDim) {
        return new VarianLPParams(iDim);
    }

    @Override
    public void readVector(int iVec, Vec dvec) {
        if (dvec.isComplex()) {
            if (dvec.useApache()) {
                readVector(iVec, dvec.getCvec());
            } else {
                readVector(iVec, dvec.rvec, dvec.ivec);
            }
        } else {
            readVector(iVec, dvec.rvec);
        }
        dvec.dwellTime = 1.0 / getSW(0);
        dvec.centerFreq = getSF(0);
        dvec.setRefValue(getRef(0));
    }

    @Override
    public void readVector(int iDim, int iVec, Vec dvec) {
        if (dvec.isComplex()) {
            if (dvec.useApache()) {
                readVector(iDim, iVec, dvec.getCvec());
            } else {
                readVector(iVec, dvec.rvec, dvec.ivec);
            }
        } else {
            readVector(iVec, dvec.rvec);
        }
        dvec.dwellTime = 1.0 / getSW(iDim);
        dvec.centerFreq = getSF(iDim);
        dvec.setRefValue(getRef(iDim));
        dvec.setPh0(getPH0(iDim));
        dvec.setPh1(getPH1(iDim));
    }

    @Override
    public void readVector(int iVec, Complex[] cdata) {
        byte[] dataBuf = new byte[tbytes];
        readVecBlock(iVec, dataBuf);
        copyVecData(dataBuf, cdata);
    }

    public void readVector(int iDim, int iVec, Complex[] cdata) {
        int size = getSize(iDim);
        int nPer = getGroupSize(iDim);
        int nPoints = size * nPer;
        byte[] dataBuf = new byte[nPoints * ebytes * 2];
        if (isFloat) {
            FloatBuffer fbuf = ByteBuffer.wrap(dataBuf).asFloatBuffer();
            for (int j = 0; j < (nPoints * 2); j++) {
                fbuf.put(j, 0.0f);
            }
        } else if (isShort) {
            ShortBuffer sbuf = ByteBuffer.wrap(dataBuf).asShortBuffer();
            for (int j = 0; j < (nPoints * 2); j++) {
                sbuf.put(j, (short) 0);
            }
        } else {
            IntBuffer ibuf = ByteBuffer.wrap(dataBuf).asIntBuffer();
            for (int j = 0; j < (nPoints * 2); j++) {
                ibuf.put(j, 0);
            }
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
        if (isFloat) {
            FloatBuffer fbuf = ByteBuffer.wrap(dataBuf).asFloatBuffer();
            for (int j = 0; j < (nPoints * 2); j += 2) {
                cdata[j / 2] = new Complex((double) fbuf.get(j) / scale, (double) fbuf.get(j + 1) / scale);
            }
        } else if (isShort) {
            ShortBuffer sbuf = ByteBuffer.wrap(dataBuf).asShortBuffer();
            for (int j = 0; j < (nPoints * 2); j += 2) {
                cdata[j / 2] = new Complex((double) sbuf.get(j) / scale, (double) sbuf.get(j + 1) / scale);
            }
        } else {
            IntBuffer ibuf = ByteBuffer.wrap(dataBuf).asIntBuffer();
            for (int j = 0; j < (nPoints * 2); j += 2) {
                cdata[j / 2] = new Complex((double) ibuf.get(j) / scale, (double) ibuf.get(j + 1) / scale);
            }
        }

    }

    @Override
    public void readVector(int iVec, double[] data) {
        byte[] dataBuf = new byte[tbytes];
        readVecBlock(iVec, dataBuf);
        copyVecData(dataBuf, data);
    }

    @Override
    public void readVector(int iVec, double[] rdata, double[] idata) {
        byte[] dataBuf = new byte[tbytes];
        readVecBlock(iVec, dataBuf);
        copyVecData(dataBuf, rdata, idata);
    }

    // check for and open sample schedule
    final boolean checkAndOpenSampleSchedule(String parPath) {
        boolean gotSchedule = false;
        String schedulePath = "sampling.sch";
        if (parPath.endsWith(".fid")) {
            schedulePath = parPath + File.separator + "sampling.sch";
        } else if (parPath.endsWith("fid")) {
            schedulePath = parPath.substring(0, parPath.length() - 3) + "sampling.sch";
        }
        File scheduleFile = new File(schedulePath);
        if (scheduleFile.exists()) {
            log.info("exists");
            try {
                readSampleSchedule(scheduleFile.getPath(), false, false);
                gotSchedule = true;
            } catch (IOException ioE) {
                gotSchedule = false;
            }
        }
        if (gotSchedule) {
            log.info("success");
            int[] dims = sampleSchedule.getDims();
            for (int i = 0; i < dims.length; i++) {
                sizes[i + 1] = dims[i];
                maxSizes[i + 1] = dims[i];
                log.info("sched size {} {}", i, sizes[i + 1]);
            }
        }
        return gotSchedule;
    }

    // open and read Varian text file
    @Override
    public String getText() {
        if (text == null) {
            String textPath = "";
            text = "";
            if (fpath.endsWith(".fid")) {
                textPath = fpath + File.separator + "text";
            } else if (fpath.endsWith("fid")) {
                textPath = fpath.substring(0, fpath.length() - 3);
                textPath += "text";
            }
            if ((new File(textPath)).exists()) {
                try {
                    Path path = FileSystems.getDefault().getPath(textPath);
                    text = new String(Files.readAllBytes(path));
                } catch (IOException ex) {
                    text = "";
                }
            }
        }
        return text;
    }

    @Override
    public String getSamplePosition() {
        String position = "";
        String rack = getPar("vrack_");
        if (rack != null) {
            position = rack;
        }
        String loc = getPar("vloc_");
        if (loc != null) {
            position += " " + loc;
        } else {
            loc = getPar("loc_");
            if (loc != null) {
                position = loc;
            }
        }
        return position;
    }

    @Override
    public long getDate() {
        String timeRun = getPar("time_run");
        LocalDateTime localDateTime = null;
        if ((timeRun != null) && (!timeRun.equals(""))) {
            try {
                localDateTime = LocalDateTime.parse(timeRun, vTimeFormatter);
            } catch (DateTimeParseException dtpE) {
                log.warn("parse time {} {}", timeRun, dtpE.getMessage(), dtpE);
            }
        } else {
            String date = getPar("date");
            if ((date != null) && (!date.equals(""))) {
                try {
                    LocalDate localDate = LocalDate.parse(date, vDateFormatter);
                    localDateTime = localDate.atStartOfDay();
                } catch (DateTimeParseException dtpE) {
                    log.warn("parse date {} {}", date, dtpE.getMessage(), dtpE);
                }
            }
        }
        if (localDateTime != null) {
            return localDateTime.toEpochSecond(ZoneOffset.ofHours(0));
        } else {
            return 0;
        }
    }

    // open and read Varian parameter file
    private void openParFile(String parpath) {
        if (parpath.endsWith(".fid")) {
            parpath += File.separator + "procpar";
        } else if (parpath.endsWith("fid")) {
            parpath = parpath.substring(0, parpath.length() - 3);
            parpath += "procpar";
        }
        if ((new File(parpath)).exists()) {
            parMap = VNMRPar.getParMap(parpath);
        }
    }

    // open Varian data file, read header
    private void openDataFile(String datapath) {
        if (datapath.endsWith(".fid")) {
            datapath += File.separator + "fid";
        }
        try {
            fc = FileChannel.open(Paths.get(datapath), StandardOpenOption.READ);
            readFileHeader();
        } catch (IOException ex) {
            log.warn(fpath, ex);
            if (fc != null) {
                try {
                    fc.close();
                } catch (IOException e) {
                    log.warn(e.getMessage(), e);
                }
            }
        }
    }

    private void readFileHeader() {
        try {
            int size = 8;
            byte[] hbytes = new byte[4 * size]; // create buffer, read header
            int nread = fc.read(ByteBuffer.wrap(hbytes));
            IntBuffer ibuf = ByteBuffer.wrap(hbytes).asIntBuffer();
            for (int i = 0; i < size && nread > 31; i++) {  // read file header
                int c = ibuf.get();
                switch (i) {
                    case 0:
                        nblocks = c;        // number of blocks
                        break;
                    case 1:
                        ntraces = c;        // number of traces per block, usually 1 for FID
                        break;
                    case 2:
                        np = c;             // number of points per trace
                        break;
                    case 3:
                        ebytes = c;         // 2 is 16 bit, 4 is 32 bit data
                        break;
                    case 4:
                        tbytes = c;         // number of bytes per trace, np * ebytes
                        break;
                    case 6:
                        status = (short) c;  // status in hexadecimal
                        break;
                    case 7:
                        nbheaders = c;      // number of block headers per block
                        break;
                }
            }
//            isComplex = ((status & 0x20) != 0); // not set
            isFloat = ((status & 0x8) != 0);
            isShort = (!isFloat) && ((status & 0x4) == 0);
            isSpectrum = ((status & 0x2) != 0);
            // fixme is this correct.  Some FIDs have nblocks == 0, but seem otherwise valid
            if ((nblocks == 0) && (ntraces > 0)) {
                nblocks = 1;
            }
            checkPars(np, nblocks);
            if (ntraces > 1) {
                log.info(">> number of traces {} more than one", ntraces);
                np *= ntraces; // should read ntraces * nblocks into nvectors
            }
            if (badHeaderFormat()) {
                throw new IOException("improper format for Varian header");
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
    }  // end readFileHeader

    private void checkPars(int cKnp, int cKblocks) {
        Integer ipar;
        ipar = getParInt("np");
        if (ipar != null && ipar != cKnp) {
            log.info(">> np in header and procpar differ");
        }
        ipar = getParInt("arraydim");
        if (ipar != null && ipar != cKblocks) {
            log.info(">> arraydim in header {} and procpar differ {}", cKblocks, ipar);
        }
    }

    private boolean badHeaderFormat() {
        if ((nblocks < 1) || (ntraces < 1) || (np < 1) || (nbheaders < 1)
                || (ebytes != 2 && ebytes != 4) || (status < 1) || ((status & 0x1) != 1)
                || (tbytes != np * ebytes)) {
            log.info("nblocks {} ntraces {} np {} n {} ebytes {} status {} tbytes {}", nblocks, ntraces, np, nbheaders, ebytes, status, tbytes);
            return true;
        } else {
            return false;
        }
    }

    // read i'th data block
    private void readVecBlock(int i, byte[] dataBuf) {
        try {
            final int hskips = 8;
            final int bskips = 7;
            final int skips = (hskips + (i + 1) * bskips * nbheaders) * 4 + i * np * ebytes;
            ByteBuffer buf = ByteBuffer.wrap(dataBuf);
            int nread = fc.read(buf, skips);
            if (nread < np) {
                throw new ArrayIndexOutOfBoundsException("file index " + i + " out of bounds");
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
    private void readValue(int iDim, int fileIndex, int vecIndex, int xCol, byte[] dataBuf) {
        try {
            int hskips = 8, bskips = 7, nread = 0;
            int skips = (hskips + (fileIndex + 1) * bskips * nbheaders) * 4 + fileIndex * np * ebytes + xCol * ebytes * 2;
            ByteBuffer buf = ByteBuffer.wrap(dataBuf, vecIndex * ebytes * 2, ebytes * 2);
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

    // copy read data into double array
    private void copyVecData(byte[] dataBuf, double[] data) {
        int j;
        if (isFloat) {
            FloatBuffer fbuf = ByteBuffer.wrap(dataBuf).asFloatBuffer();
            for (j = 0; j < np; j++) {
                data[j] = (double) fbuf.get(j) / scale;
            }
        } else if (isShort) {
            ShortBuffer sbuf = ByteBuffer.wrap(dataBuf).asShortBuffer();
            for (j = 0; j < np; j++) {
                data[j] = (double) sbuf.get(j) / scale;
            }
        } else {
            IntBuffer ibuf = ByteBuffer.wrap(dataBuf).asIntBuffer();
            for (j = 0; j < np; j++) {
                data[j] = (double) ibuf.get(j) / scale;
            }
        }
    }  // end copyVecData

    // copy read data into Complex array
    private void copyVecData(byte[] dataBuf, Complex[] data) {
        int j;
        if (isFloat) {
            FloatBuffer fbuf = ByteBuffer.wrap(dataBuf).asFloatBuffer();
            for (j = 0; j < np; j += 2) {
                data[j / 2] = new Complex((double) fbuf.get(j) / scale, (double) fbuf.get(j + 1) / scale);
            }
        } else if (isShort) {
            ShortBuffer sbuf = ByteBuffer.wrap(dataBuf).asShortBuffer();
            for (j = 0; j < np; j += 2) {
                data[j / 2] = new Complex((double) sbuf.get(j) / scale, (double) sbuf.get(j + 1) / scale);
            }
        } else {
            IntBuffer ibuf = ByteBuffer.wrap(dataBuf).asIntBuffer();
            for (j = 0; j < np; j += 2) {
                data[j / 2] = new Complex((double) ibuf.get(j) / scale, (double) ibuf.get(j + 1) / scale);
            }
        }
    }  // end copyVecData

    // copy read data into double arrays of real, imaginary
    private void copyVecData(byte[] dataBuf, double[] rdata, double[] idata) {
        int j;
        if (isFloat) {
            FloatBuffer fbuf = ByteBuffer.wrap(dataBuf).asFloatBuffer();
            for (j = 0; j < np; j += 2) {
                rdata[j / 2] = (double) fbuf.get(j) / scale;
                idata[j / 2] = (double) fbuf.get(j + 1) / scale;
            }
        } else if (isShort) {
            ShortBuffer sbuf = ByteBuffer.wrap(dataBuf).asShortBuffer();
            for (j = 0; j < np; j += 2) {
                rdata[j / 2] = (double) sbuf.get(j) / scale;
                idata[j / 2] = (double) sbuf.get(j + 1) / scale;
            }
        } else {
            IntBuffer ibuf = ByteBuffer.wrap(dataBuf).asIntBuffer();
            for (j = 0; j < np; j += 2) {  // npoints defined in Varian header
                rdata[j / 2] = (double) ibuf.get(j) / scale;
                idata[j / 2] = (double) ibuf.get(j + 1) / scale;
            }
        }
    }  // end copyVecData

    // read i'th block header
    public void readBlockHeader(int iVec) {
        try {
            final int size = 7;
            int ct = 0;
            short iscale = 1, stat = 0, index = 0, mode = 0;
            final int hskips = 8;
            final int bskips = 7;
            int iBlock = 0; // fixme is this right
            int skips = (hskips + iBlock * bskips * nbheaders) * 4 + iBlock * np * ebytes;
            byte[] hbytes = new byte[4 * size];
            int nread = fc.read(ByteBuffer.wrap(hbytes), skips);
            IntBuffer ibuf = ByteBuffer.wrap(hbytes).asIntBuffer();
            StringBuilder cStrBuilder = new StringBuilder();
            for (int i = 0; i < size && nread > 27; i++) {  // read block header
                int c = ibuf.get();
                switch (i) {
                    case 0:
                        iscale = (short) (c >> 16); // scale
                        stat = (short) c;              // status
                        break;
                    case 1:
                        index = (short) (c >> 16); // block index
                        mode = (short) c;              // mode
                        break;
                    case 2:
                        ct = c;  // number of completed transients
                        break;
                }
                cStrBuilder.append(c).append(" ");
            }
            if (log.isInfoEnabled()) {
                log.info(cStrBuilder.toString());
            }
            log.info("blockheader: scale={} status={} index={} mode={} ct={}", iscale, stat, index, mode, ct);
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
    }  // end readBlockHeader

    @Override
    public void resetAcqOrder() {
        acqOrder = null;
    }

    @Override
    public String[] getAcqOrder() {
        if (acqOrder == null) {
            int nDim = getNDim() - 1;
            // p1,p2,d1,d2 or p2,p1,d1,d2
            boolean hasPhase = false;
            String arrayPar = getPar("array");
            String[] arrayElems = new String[0];
            if (!arrayPar.trim().equals("")) {
                arrayElems = arrayPar.split(",");
            }
            for (int j = arrayElems.length - 1; j >= 0; j--) {
                if (arrayElems[j].startsWith("phase")) {
                    hasPhase = true;
                    break;
                }
            }

            if (hasPhase) {
                int i = 0;
                acqOrder = new String[nDim + arrayElems.length];
                for (int j = arrayElems.length - 1; j >= 0; j--) {
                    if (arrayElems[j].startsWith("phase")) {
                        char dimChar = '1';
                        if (arrayElems[j].length() == 6) {
                            dimChar = arrayElems[j].charAt(5);
                        }
                        acqOrder[i++] = "p" + dimChar;
                    } else {
                        acqOrder[i++] = "a" + (nDim + 1);
                        String arrayValue = getPar(arrayElems[j]);
                        String[] arrayValueElems = arrayValue.split("\n");
                        arraysize[i - 1] = arrayValueElems.length;
                        arrayValues.clear();
                        for (String val : arrayValueElems) {
                            try {
                                double dVal = Double.parseDouble(val);
                                arrayValues.add(dVal);
                            } catch (NumberFormatException nfE) {
                                arrayValues.clear();
                                break;
                            }
                        }

                    }
                }
                for (int j = 0; j < nDim; j++) {
                    acqOrder[i++] = "d" + (j + 1);
                }
            } else {
                Integer arraydim = getParInt("arraydim");
                int i = 0;
                for (int j = 0; j < arraysize.length; j++) {
                    arraysize[j] = 0;
                }
                boolean hasArray = false;
                if ((arraydim != null) && (arraydim > 1)) {
                    for (int j = arrayElems.length - 1; j >= 0; j--) {
                        String arrayValue = getPar(arrayElems[j]);
                        String[] arrayValueElems = arrayValue.split("\n");
                        int aSize = arrayValueElems.length;
                        if (aSize > 0) {
                            hasArray = true;
                        }
                        if (arraysize[nDim] == 0) {
                            arraysize[nDim] = aSize;
                        } else {
                            arraysize[nDim] *= aSize;
                        }
                        arrayValues.clear();
                        for (String val : arrayValueElems) {
                            try {
                                double dVal = Double.parseDouble(val);
                                arrayValues.add(dVal);
                            } catch (NumberFormatException nfE) {
                                arrayValues.clear();
                                break;
                            }
                        }
                    }
                }
                int acqOrderSize = nDim * 2;
                if (hasArray) {
                    acqOrderSize++;
                }
                acqOrder = new String[acqOrderSize];
                i = 0;
                if (hasArray) {
                    acqOrder[i++] = "a" + (nDim + 1);
                }
                for (int k = 0; k < nDim; k++) {
                    acqOrder[k + i] = "p" + (k + 1);
                    acqOrder[nDim + k + i] = "d" + (k + 1);
                }
            }
        }
        return acqOrder;
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
                for (int i = (len - 1); i >= 0; i--) {
                    String dimStr = s.substring(i, i + 1);
                    if (!dimStr.equals(nDim + "")) {
                        acqOrder[j] = "p" + dimStr;
                        j++;
                    }
                }
                for (int i = 0; i < nIDim; i++) {
                    acqOrder[i + nIDim] = "d" + (i + 1);
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

    // write binary data into text file, using header info
    public void fileoutraw() {
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get("/tmp/bwraw.txt"), Charset.forName("US-ASCII"),
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             DataInputStream in = new DataInputStream(new FileInputStream(fpath))) {
            bw.write("rawfile header");
            bw.newLine();
            for (int k = 0; k < 8; k++) // file header
            {
                bw.write(in.readInt() + " ");
            }
            bw.newLine();
            for (int i = 0; i < nblocks; i++) {
                bw.write("blockhdr " + i + " ");
                for (int k = 0; k < 7; k++) // block header
                {
                    bw.write(in.readInt() + " ");
                }
                bw.newLine();
                // check if int/float/short
                for (int j = 0; j < np; j++) {  // data
                    if (j % 512 == 0) {
                        bw.write("\n  block " + i + ":" + (j / 512) + " : ");
                    }
                    if (isFloat) {
                        bw.write(in.readFloat() + " ");
                    } else if (isShort) {
                        bw.write(in.readShort() + " ");
                    } else {
                        bw.write(in.readInt() + " ");
                    }
                }
                bw.write(": endblock " + i);
                bw.newLine();
                bw.flush();
            }
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
    }

    class VarianSinebellWt extends SinebellWt {

        VarianSinebellWt(int iDim) {
            String ext = "";
            if (iDim > 0) {
                ext += iDim;
            }
            String spar;
            if ((spar = getPar("sb" + ext)) != null) {
                if (!spar.equals("n")) {
                    sb = Double.parseDouble(spar);
                    if (sb < 0.0) {
                        power = 2;
                        sb = -sb;
                    } else {
                        power = 1;
                    }
                    if ((spar = getPar("sbs" + ext)) != null) {
                        if (!spar.equals("n")) {
                            sbs = Double.parseDouble(spar);
                        }
                    }
                    if (sb != 0.0) {
                        offset = -0.5 * sbs / sb;
                    }
                    // size = (tdsize*(2*sb+sbs)) / (tdsize/sw) = (2*sb+sbs)*sw
                    size = (int) Math.round((2.0 * sb + sbs) * getSW(iDim));
                    end = 1.0;
                }
            }
        }

    }

    class VarianGaussianWt extends GaussianWt {

        VarianGaussianWt(int iDim) {
            String ext = "";
            if (iDim > 0) {
                ext += iDim;
            }
            String spar;
            if ((spar = getPar("gf" + ext)) != null) {
                if (!spar.equals("n")) {
                    gf = Double.parseDouble(spar);
                    if ((spar = getPar("gfs" + ext)) != null) {
                        if (!spar.equals("n")) {
                            gfs = Double.parseDouble(spar);
                        }
                    }
                }
            }
        }

    }

    class VarianFPMult extends FPMult {

        VarianFPMult(int iDim) {
// should default to 0.5 if dim>1? 1.0 if dim=1? what about Bruker default?
            String ext = "";
            if (iDim > 0) {
                ext += iDim;
            }
            String spar;
            if ((spar = getPar("fpmult" + ext)) != null) {
                if (!spar.equals("n")) {
                    fpmult = Double.parseDouble(spar);
                    exists = true;
                }
            }
        }

    }

    class VarianLPParams extends LPParams {

        VarianLPParams(int iDim) {
            String ext = "";
            if (iDim > 0) {
                ext += iDim;
            }
            String lpalg = getPar("lpalg" + ext);
            String lpopt = getPar("lpopt" + ext);
            Integer ipar = getParInt("lpfilt" + ext);
            Integer jpar = getParInt("lpnupts" + ext);
            if ((lpalg != null) && lpalg.equals("lpfft")
                    && (ipar != null) && (ipar > 0) && (jpar != null)) {
                if (jpar > 1024) {
                    jpar = 1024;
                }
                if (lpopt.equals("b")) {
                    ncoef = ipar;
                    exists = true;
                    if (((lpalg = getPar("proc" + ext)) != null)
                            && (lpalg.equals("lp"))) {
                        status = true;
                    }
                    if ((ipar = getParInt("strtlp" + ext)) != null) {
                        ipar--;
                        fitstart = ipar;
                        fitend = ipar + jpar - 1;
                    }
                    if (((ipar = getParInt("strtext" + ext)) != null)
                            && ((jpar = getParInt("lpext" + ext)) != null)) {
                        ipar--;
                        predictend = ipar;
                        predictstart = ipar - jpar + 1;
                    }
                } else if (lpopt.equals("f")) {
                    ncoef = ipar;
                    exists = true;
                    if (((lpalg = getPar("proc" + ext)) != null)
                            && (lpalg.equals("lp"))) {
                        status = true;
                    }
                    if ((ipar = getParInt("strtlp" + ext)) != null) {
                        ipar--;
                        fitend = ipar;
                        fitstart = ipar - jpar + 1;
                    }
                    if (((ipar = getParInt("strtext" + ext)) != null)
                            && ((jpar = getParInt("lpext" + ext)) != null)) {
                        ipar--;
                        predictstart = ipar;
                        predictend = ipar + jpar - 1;
                    }
                }
            }
        }

    }

    public static void main(String args[]) {

        String root = "/Users/bayardfetler/NVJ/dcdemo/";
        VarianData varian = new VarianData(root + "proton.fid");
        int npoints = varian.getNPoints();
        double[] data = new double[npoints * 2];
        varian.readVector(0, data);
        System.out.print("  data:");
        for (int i = 0; i < 4; i++) {
            System.out.print(" " + data[i]);
        }
        System.out.println(" : " + data[npoints * 2 - 1]);

        Vec vc = new Vec(npoints * 2, false);
        varian.readVector(0, vc);
        System.out.print("  vec1 data:");
        for (int i = 0; i < 4; i++) {
            System.out.print(" " + vc.rvec[i]);
        }
        System.out.println(" : " + vc.rvec[npoints * 2 - 1]);

        vc = new Vec(npoints, true);
        varian.readVector(0, vc);
        System.out.print("  vec2 data:");
        Complex[] cdata = vc.getCvec();
        for (int i = 0; i < 2; i++) {
            System.out.print(" " + cdata[i]);
        }
        System.out.println(" : " + cdata[npoints - 1]);

        Double sfrq = varian.getParDouble("sfrq");
        Double sw = varian.getParDouble("sw");
        Double rfl = varian.getParDouble("rfl");
        Double rfp = varian.getParDouble("rfp");
        Integer npts = varian.getParInt("np");
        Integer ni = varian.getParInt("ni");
        Integer arraydim = varian.getParInt("arraydim");
        String scooby = varian.getPar("scooby");  // undefined
        Integer acqdim = varian.getParInt("acqdim");
        System.out.print("sfrq=" + sfrq + " sw=" + sw + " rfl=" + rfl + " rfp=" + rfp);
        System.out.print(" np=" + npts + " ni=" + ni + " arraydim=" + arraydim);
        System.out.println(" acqdim=" + varian.getNDim() + " scooby=" + scooby);

        varian = new VarianData(root + "dept.fid/fid");
        npoints = varian.getNPoints();
        int nvectors = varian.getNVectors();
        vc = new Vec(npoints, true);
        for (int j = 0; j < nvectors; j++) {
            varian.readVector(j, vc);
            cdata = vc.getCvec();
            System.out.print("  vec3 data " + j + " :");
            for (int i = 0; i < 2; i++) {
                System.out.print(" " + cdata[i]);
            }
            System.out.println(" : " + cdata[npoints - 1]);
        }

        arraydim = varian.getParInt("arraydim");
        npts = varian.getParInt("np");
        sfrq = varian.getParDouble("sfrq");
        sw = varian.getParDouble("sw");
        rfl = varian.getParDouble("rfl");
        rfp = varian.getParDouble("rfp");
        acqdim = varian.getParInt("acqdim");
        System.out.print("sfrq=" + sfrq + " sw=" + sw + " rfl=" + rfl + " rfp=" + rfp);
        System.out.println(" np=" + npts + " acqdim=" + varian.getNDim() + " arraydim=" + arraydim);

        String adir = "/Users/bayardfetler/NVJ/NVJ_Data/vdata/";
        adir += "fidlib/auto_2007.05.23/";
        adir += "ethylindanone_001/";

        NMRData vdata;
        try {
            vdata = NMRDataUtil.getFID(adir + "Roesy_01.fid");

            vc = new Vec(vdata.getNPoints(), true);
            vdata.readVector(0, vc);
            System.out.print("  vec4 data:");
            cdata = vc.getCvec();
            for (int i = 0; i < 2; i++) {
                System.out.print(" " + cdata[i]);
            }
            System.out.println(" : " + cdata[vdata.getNPoints() - 1]);
            System.out.print("sequence=" + vdata.getSequence() + " solvent=" + vdata.getSolvent());
            System.out.println(" dim=" + vdata.getNDim() + " nvectors=" + vdata.getNVectors()
                    + " npoints=" + vdata.getNPoints() + " toString " + vdata.toString());

            vdata = NMRDataUtil.getFID(adir + "Roesy_01.fid/fid");
            vc = new Vec(vdata.getNPoints(), true);
            vdata.readVector(0, vc);
            System.out.print("  vec5 data:");
            cdata = vc.getCvec();
            for (int i = 0; i < 2; i++) {
                System.out.print(" " + cdata[i]);
            }
            System.out.println(" : " + cdata[vdata.getNPoints() - 1]);
            System.out.print("sequence=" + vdata.getSequence() + " solvent=" + vdata.getSolvent());
            System.out.println(" dim=" + vdata.getNDim() + " nvectors=" + vdata.getNVectors()
                    + " npoints=" + vdata.getNPoints() + " toString " + vdata.toString());

            vdata = NMRDataUtil.getFID(adir + "Tocsy_01.fid/");
            vc = new Vec(vdata.getNPoints(), true);
            vdata.readVector(0, vc);
            System.out.print("  vec6 data:");
            cdata = vc.getCvec();
            for (int i = 0; i < 2; i++) {
                System.out.print(" " + cdata[i]);
            }
            System.out.println(" : " + cdata[vdata.getNPoints() - 1]);
            System.out.print("sequence=" + vdata.getSequence() + " solvent=" + vdata.getSolvent());
            System.out.println(" dim=" + vdata.getNDim() + " nvectors=" + vdata.getNVectors()
                    + " npoints=" + vdata.getNPoints() + " toString " + vdata.toString());
            System.out.println("  f1coef=" + Arrays.toString(vdata.getCoefs(2)));
            SinebellWt sb = vdata.getSinebellWt(1);
            System.out.print("  sinebell: exists=" + sb.exists() + " power=" + sb.power() + " sb=" + sb.sb() + " sbs=" + sb.sbs());
            System.out.println(" size=" + sb.size() + " offset=" + sb.offset() + " end=" + sb.end());
            GaussianWt gb = vdata.getGaussianWt(1);
            System.out.println("  gaussian: exists=" + gb.exists() + " gf=" + gb.gf() + " gfs=" + gb.gfs() + " lb=" + gb.lb());
            FPMult fp = vdata.getFPMult(2);
            System.out.println("  fpmult: exists=" + fp.exists() + " fpmult=" + fp.fpmult());
            LPParams lp = vdata.getLPParams(2);
            System.out.print("  lppars: exists=" + lp.exists() + " status=" + lp.status() + " ncoef=" + lp.ncoef());
            System.out.print(" pstart=" + lp.predictstart() + " pend=" + lp.predictend());
            System.out.println(" fstart=" + lp.fitstart() + " fend=" + lp.fitend());

            vdata = NMRDataUtil.getFID(adir + "NOT.fid");
            vc = new Vec(vdata.getNPoints(), true);
            vdata.readVector(0, vc);
            System.out.print("  vecN data:");
            cdata = vc.getCvec();
            for (int i = 0; i < 2; i++) {
                System.out.print(" " + cdata[i]);
            }
            System.out.println(" : " + cdata[vdata.getNPoints() - 1]);
            System.out.print("sequence=" + vdata.getSequence() + " solvent=" + vdata.getSolvent());
            System.out.println(" dim=" + vdata.getNDim() + " nvectors=" + vdata.getNVectors()
                    + " npoints=" + vdata.getNPoints());

        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);
        }

        varian = new VarianData(adir + "Roesy_01.fid");

        System.out.println("VarianData Roesy local");
        System.out.print("sequence=" + varian.getSequence() + " solvent=" + varian.getSolvent());
        System.out.println(" dim=" + varian.getNDim() + " nvectors=" + varian.getNVectors()
                + " npoints=" + varian.getNPoints());
        System.out.println("  tdsize's: " + varian.getSize(1) + " " + varian.getSize(2)
                + " " + varian.getSize(3) + " " + varian.getSize(4));
        System.out.println("  tn's: " + varian.getTN(1) + " " + varian.getTN(2)
                + " " + varian.getTN(3) + " " + varian.getTN(4));
        System.out.println("  sfrq's: " + varian.getSF(1) + " " + varian.getSF(2)
                + " " + varian.getSF(3) + " " + varian.getSF(4));
        System.out.println("  sw's: " + varian.getSW(1) + " " + varian.getSW(2)
                + " " + varian.getSW(3) + " " + varian.getSW(4));
        System.out.println("  ref's: " + varian.getRef(1) + " " + varian.getRef(2)
                + " " + varian.getRef(3) + " " + varian.getRef(4));

        varian = new VarianData(adir + "Hmqc_01.fid");
        System.out.println("VarianData Hmqc local");
        System.out.print("sequence=" + varian.getSequence() + " solvent=" + varian.getSolvent());
        System.out.println(" dim=" + varian.getNDim() + " nvectors=" + varian.getNVectors()
                + " npoints=" + varian.getNPoints());
        System.out.println("  tdsize's: " + varian.getSize(1) + " " + varian.getSize(2)
                + " " + varian.getSize(3) + " " + varian.getSize(4));
        System.out.println("  isComplex: " + varian.isComplex(1) + " " + varian.isComplex(2)
                + " " + varian.isComplex(3) + " " + varian.isComplex(4));
        System.out.println("  tn's: " + varian.getTN(1) + " " + varian.getTN(2)
                + " " + varian.getTN(3) + " " + varian.getTN(4));
        System.out.println("  sfrq's: " + varian.getSF(1) + " " + varian.getSF(2)
                + " " + varian.getSF(3) + " " + varian.getSF(4));
        System.out.println("  sw's: " + varian.getSW(1) + " " + varian.getSW(2)
                + " " + varian.getSW(3) + " " + varian.getSW(4));
        System.out.println("  ref's: " + varian.getRef(1) + " " + varian.getRef(2)
                + " " + varian.getRef(3) + " " + varian.getRef(4));
        SinebellWt sb = varian.getSinebellWt(1);
        System.out.print("  sinebell: exists=" + sb.exists() + " power=" + sb.power() + " sb=" + sb.sb() + " sbs=" + sb.sbs());
        System.out.println(" size=" + sb.size() + " offset=" + sb.offset() + " end=" + sb.end());
    }
}
