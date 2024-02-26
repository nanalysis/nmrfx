/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2022 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.processor.datasets.vendor.jcamp;

import com.nanalysis.jcamp.model.*;
import com.nanalysis.jcamp.parser.JCampParser;
import com.nanalysis.jcamp.util.JCampUtil;
import org.apache.commons.math3.complex.Complex;
import org.codehaus.commons.nullanalysis.Nullable;
import org.nmrfx.processor.datasets.*;
import org.nmrfx.processor.datasets.parameters.*;
import org.nmrfx.processor.datasets.vendor.NMRData;
import org.nmrfx.processor.datasets.vendor.NMRDataUtil;
import org.nmrfx.processor.datasets.vendor.VendorPar;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.SampleSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static com.nanalysis.jcamp.model.Label.*;

/**
 * A JCamp file that could contain a FID, a Spectra, or both.
 * When the file contain both, the FID is used.
 */
public class JCAMPData implements NMRData {
    private static final Logger log = LoggerFactory.getLogger(JCAMPData.class);

    private static final List<String> MATCHING_EXTENSIONS = List.of(".jdx", ".dx");
    private static final double AMBIENT_TEMPERATURE = 298.0; // in K, around 25° C
    private static final double SCALE = 1.0;
    private final AcquisitionType[] symbolicCoefs = new AcquisitionType[2];

    /**
     * JCamp-defined acquisition scheme.
     */
    enum AcquisitionScheme {
        UNDEFINED(AcquisitionType.HYPER),
        NOT_PHASE_SENSITIVE(AcquisitionType.SEP),
        TPPI(AcquisitionType.REAL),
        STATES(AcquisitionType.HYPER_R),
        STATES_TPPI(AcquisitionType.HYPER),
        ECHO_ANTIECHO(AcquisitionType.ECHO_ANTIECHO_R),
        QSEQ(AcquisitionType.REAL);

        private final AcquisitionType acquisitionType;

        AcquisitionScheme(AcquisitionType acquisitionType) {
            this.acquisitionType = acquisitionType;
        }

        public String getSymbolicCoefs() {
            return acquisitionType.getLabel();
        }

        public double[] getCoefs() {
            return acquisitionType.getCoefficients();
        }
    }

    /**
     * Bruker-specific acquisition scheme. Used only when the standard AcquisitionScheme isn't defined.
     */
    enum FnMode {
        UNDEFINED(AcquisitionScheme.UNDEFINED),
        QF(AcquisitionScheme.NOT_PHASE_SENSITIVE),
        QSEQ(AcquisitionScheme.QSEQ),// Quadrature detection in sequential mode
        TPPI(AcquisitionScheme.TPPI),
        STATES(AcquisitionScheme.STATES),
        STATES_TPPI(AcquisitionScheme.STATES_TPPI),
        ECHO_ANTIECHO(AcquisitionScheme.ECHO_ANTIECHO),
        QF_NO_FREQ(AcquisitionScheme.NOT_PHASE_SENSITIVE);

        public final AcquisitionScheme acquisitionScheme;

        FnMode(AcquisitionScheme scheme) {
            this.acquisitionScheme = scheme;
        }
    }

    /**
     * Bruker-specific $WDW values. Used to select a specific apodization.
     */
    private enum Wdw {
        NO, EM, GM, SINE, QSINE, TRAP, USER, SINC, QSINC, TRAF, TRAFS
    }

    private final String path;
    private final JCampDocument document;
    private final JCampBlock block;
    private final double[][] real;
    private final double[][] imaginary;

    private DatasetType preferredDatasetType = DatasetType.NMRFX;
    private SampleSchedule sampleSchedule = null; // used for NUS acquisition - not supported by JCamp directly

    // these values can be overridden from the outside, we need to cache them so that we can
    // either read them from JCamp or take the user-defined value
    private final Map<Integer, Double> sf = new HashMap<>();
    private final Map<Integer, Double> sw = new HashMap<>();
    private final Map<Integer, Double> ref = new HashMap<>();
    private final Map<Integer, Integer> size = new HashMap<>();
    private final List<DatasetGroupIndex> datasetGroupIndices = new ArrayList<>();

    public JCAMPData(String path) throws IOException {
        this.path = path;

        log.info("Parsing JCAMP data: {}", path);
        this.document = new JCampParser().parse(new File(path));

        if (document.getBlockCount() == 0) {
            throw new IOException("Invalid JCamp document, doesn't contain any block.");
        }
        this.block = document.blocks().findFirst()
                .orElseThrow(() -> new IOException("Invalid JCamp document, doesn't contain any block."));
        this.real = toMatrix(extractRealPages());
        this.imaginary = toMatrix(extractImaginaryPages());
    }

    private List<JCampPage> extractRealPages() {
        List<JCampPage> realPages = block.getPagesForYSymbol("R");
        if (!realPages.isEmpty()) {
            return realPages;
        }

        // Files using XYDATA or Bruker 2D may define "Y" instead of "R" and "I".
        return block.getPagesForYSymbol("Y");
    }

    private List<JCampPage> extractImaginaryPages() {
        return block.getPagesForYSymbol("I");
    }

    private double[][] toMatrix(List<JCampPage> pages) {
        int height = pages.size();
        double[][] matrix = new double[height][];
        for (int i = 0; i < height; i++) {
            matrix[i] = pages.get(i).toArray();
        }
        return matrix;
    }

    public String getTitle() {
        return document.getTitle();
    }

    @Override
    public void close() {
        // Nothing to close
    }

    @Override
    public String getFilePath() {
        return path;
    }

    private JCampRecord getRecord(String name) {
        // parse dimension from record name for multi-dimensional records
        var items = name.split(":");
        int index = 0;
        if (items.length == 2) {
            name = items[0];
            index = Integer.parseInt(items[1]) - 1;
        }

        // try block-level records first
        try {
            return block.get(name, index);
        } catch (NoSuchElementException e) {
            // then try document-level records if they were not defined by the block
            return document.get(name, index);
        }
    }

    @Override
    public String getPar(String parname) {
        return getRecord(parname).getString();
    }

    @Override
    public Double getParDouble(String parname) {
        return getRecord(parname).getDouble();
    }

    @Override
    public Integer getParInt(String parname) {
        return getRecord(parname).getInt();
    }

    @Override
    public List<VendorPar> getPars() {
        Set<String> defined = new HashSet<>();
        List<VendorPar> vendorPars = new ArrayList<>();

        // get block-level records first
        for (String key : block.allRecordKeys()) {
            if (defined.add(key)) {
                // when a record is present several times (for multidimensional records for example), provide a way to differentiate them
                List<JCampRecord> records = block.list(key);
                vendorPars.add(new VendorPar(key, records.get(0).getString()));
                for (int i = 1; i < records.size(); i++) {
                    vendorPars.add(new VendorPar(key + ":" + (i + 1), records.get(i).getString()));
                }
            }
        }

        // then add document-level records if they were not defined by the block
        for (String key : document.allRecordKeys()) {
            if (defined.add(key)) {
                vendorPars.add(new VendorPar(key, document.get(key).getString()));
            }
        }

        return vendorPars;
    }

    @Override
    public int getNVectors() {
        return real.length;
    }

    @Override
    public int getNPoints() {
        if (real.length == 0)
            return 0;

        return real[0].length;
    }

    @Override
    public int getNDim() {
        return block.getOrDefault(NUMDIM, "1").getInt();
    }

    @Override
    public synchronized int getSize(int dim) {
        return size.computeIfAbsent(dim, this::extractSize);
    }

    private int extractSize(int dim) {
        if (dim == 0) {
            return getNPoints();
        } else if (dim == 1) {
            // size is expressed in number of complex pairs
            // so if the dimension is complex, we should divide per two
            int factor = isComplex(dim) ? 2 : 1;
            return getNVectors() / factor;
        } else {
            return 1;
        }
    }

    @Override
    public void setSize(int dim, int value) {
        size.put(dim, value);
    }

    @Override
    public String getSolvent() {
        return block.optional(_SOLVENT_NAME, $SOLVENT)
                .map(JCampRecord::getString)
                .orElse("");
    }

    @Override
    public double getTempK() {
        return block.optional(TEMPERATURE, $TE)
                .map(r -> r.getDouble() > 150 ? r.getDouble() : 273.15 + r.getDouble())
                .orElse(AMBIENT_TEMPERATURE);
    }

    @Override
    public String getSequence() {
        return block.optional(_PULSE_SEQUENCE, $PULPROG)
                .map(JCampRecord::getString).orElse("");
    }

    @Override
    public synchronized double getSF(int dim) {
        return sf.computeIfAbsent(dim, this::extractSF);
    }

    private double extractSF(int dim) {
        Label label = getSFLabel(dim).orElseThrow(() -> new IllegalStateException("Unknown frequency, unable to extract SF for dimension " + dim));
        return block.get(label, dim).getDouble();
    }

    @Override
    public void setSF(int dim, double value) {
        sf.put(dim, value);
    }

    @Override
    public void resetSF(int dim) {
        sf.remove(dim);
    }

    @Override
    public String[] getSFNames() {
        String[] names = new String[getNDim()];
        Arrays.fill(names, "");
        for (int dim = 0; dim < names.length; dim++) {
            // multi-dimensional records are suffixed by ":dim", starting at 1.
            String suffix = dim > 0 ? ":" + (dim + 1) : "";
            names[dim] = getSFLabel(dim)
                    .map(Label::normalized)
                    .map(name -> name + suffix)
                    .orElse("");
        }
        return names;
    }

    private Optional<Label> getSFLabel(int dim) {
        return Stream.of($SFO1, _OBSERVE_FREQUENCY)
                .filter(label -> block.optional(label, dim).isPresent())
                .findFirst();
    }

    @Override
    public synchronized double getSW(int dim) {
        return sw.computeIfAbsent(dim, this::extractSW);
    }

    private double extractSW(int dim) {
        // try from specific SW record first
        // if none is present, then try to guess from first/last timestamps

        Optional<Label> label = getSWLabel(dim);
        if (label.isPresent()) {
            return block.get(label.get(), dim).getDouble();
        } else if (dim == 0 && block.contains(FIRST) && block.contains(LAST)) {
            log.debug("Trying to guess SW from FIRST and LAST records for dimension {}", dim);
            double first = block.get(FIRST).getDoubles()[0];
            double last = block.get(LAST).getDoubles()[0];
            double time = Math.abs(last - first);
            int nbPoints = real[0].length;
            return nbPoints / time;
        } else {
            throw new IllegalStateException("Unknown spectral width, unable to extract SW for dimension " + dim);
        }
    }

    @Override
    public void setSW(int dim, double value) {
        sw.put(dim, value);
    }

    @Override
    public void resetSW(int dim) {
        sw.remove(dim);
    }

    @Override
    public String[] getSWNames() {
        String[] names = new String[getNDim()];
        Arrays.fill(names, "");

        for (int dim = 0; dim < names.length; dim++) {
            // multi-dimensional records are suffixed by ":dim", starting at 1.
            String suffix = dim > 0 ? ":" + (dim + 1) : "";
            names[dim] = getSWLabel(dim)
                    .map(Label::normalized)
                    .map(name -> name + suffix)
                    .orElse("");
        }

        return names;
    }

    private Optional<Label> getSWLabel(int dim) {
        return Stream.of($SW_H)
                .filter(label -> block.optional(label, dim).isPresent())
                .findFirst();
    }

    @Override
    public synchronized double getRef(int dim) {
        return ref.computeIfAbsent(dim, this::extractRef);
    }

    private double extractRef(int dim) {
        double offsetHz = block.optional($O1, dim).map(JCampRecord::getDouble).orElse(0d);
        return offsetHz / getSF(dim);
    }

    @Override
    public void setRef(int dim, double value) {
        ref.put(dim, value);
    }

    @Override
    public void resetRef(int dim) {
        ref.remove(dim);
    }

    @Override
    public double getRefPoint(int dim) {
        // reference defined by getRef() is for the center of spectra
        return getSize(dim) / 2.0;
    }

    @Override
    public String getTN(int dim) {
        if (dim == 0) {
            return block.optional(_OBSERVE_NUCLEUS, $NUC_1, $T2_NUCLEUS)
                    .map(JCampRecord::getString)
                    .map(JCampUtil::toNucleusName)
                    .orElse(NMRDataUtil.guessNucleusFromFreq(getSF(dim)).toString());
        } else if (dim == 1) {
            return block.optional($NUC_2, $T1_NUCLEUS)
                    .or(() -> block.optional($NUC_1, dim)) // NUC_2 isn't always defined (for homo-nuclear 2D for example)
                    .map(JCampRecord::getString)
                    .map(JCampUtil::toNucleusName)
                    .orElse(NMRDataUtil.guessNucleusFromFreq(getSF(dim)).toString());
        } else {
            throw new UnsupportedOperationException("Unsupported dimension " + dim + " in JCamp");
        }
    }

    @Override
    public boolean isComplex(int dim) {
        // For first dimension, check if the JCamp block contains imaginary pages
        if (dim == 0) {
            return imaginary.length > 0;
        }

        // For other dimensions, infer it from acquisition scheme
        AcquisitionScheme scheme = getAcquisitionScheme();
        if (scheme == null || scheme == AcquisitionScheme.QSEQ || scheme == AcquisitionScheme.TPPI) {
            return false;
        }
        if (scheme == AcquisitionScheme.NOT_PHASE_SENSITIVE) {
            return getValues(dim).isEmpty();
        }

        return true;
    }

    @Override
    public int getGroupSize(int dim) {
        return isComplex(dim) ? 2 : 1;
    }

    @Override
    public String getFTType(int dim) {
        // known values: "ft", "rft" (real), "negate" (hypercomplex)

        if (dim == 0) {
            int aqMod = block.optional($AQ_MOD).map(JCampRecord::getInt).orElse(0);
            if (aqMod == 2)
                return "rft";
        } else {
            AcquisitionScheme scheme = getAcquisitionScheme();
            if (scheme == AcquisitionScheme.QSEQ || scheme == AcquisitionScheme.TPPI) {
                return "rft";
            } else if (scheme == AcquisitionScheme.UNDEFINED || scheme == AcquisitionScheme.STATES_TPPI) {
                return "negate";
            }
        }

        return isComplex(dim) ? "ft" : "rft";
    }

    @Override
    public double[] getCoefs(int dim) {
        if (dim == 0) {
            return new double[0];
        }

        AcquisitionScheme scheme = getAcquisitionScheme();
        return scheme == null ? new double[0] : scheme.getCoefs();
    }

    @Override
    public String getSymbolicCoefs(int dim) {
        if (dim == 0) {
            return null;
        }

        AcquisitionScheme scheme = getAcquisitionScheme();
        return scheme == null ? null : scheme.getSymbolicCoefs();
    }

    public void setUserSymbolicCoefs(int iDim, AcquisitionType coefs) {
        symbolicCoefs[iDim] = coefs;
    }

    public AcquisitionType getUserSymbolicCoefs(int iDim) {
        return symbolicCoefs[iDim];
    }


    @Override
    public String getVendor() {
        //XXX some code expected "bruker" in lowercase
        // RefManager.setupItems() to add getNDim() in a string builder
        // RefManager.setupItems() to add a getFixDSP() option
        return block.optional(ORIGIN)
                .map(JCampRecord::getString)
                .orElse("JCamp");
    }

    @Override
    public double getPH0(int dim) {
        double ph0 = block.optional($PHC0, dim)
                .map(JCampRecord::getDouble)
                .orElse(0.0);

        if (dim == 0) {
            ph0 -= 90; // empirical
        }

        // phase is reversed between JCamp and NMRfx
        return -ph0;
    }

    @Override
    public double getPH1(int dim) {
        double ph1 = block.optional($PHC1, dim)
                .map(JCampRecord::getDouble)
                .orElse(0.0);

        // phase is reversed between JCamp and NMRfx
        return -ph1;
    }

    @Override
    public int getLeftShift(int dim) {
        int leftShift = block.optional($LS, dim)
                .map(JCampRecord::getInt)
                .orElse(0);

        // reversed between JCamp and NMRfx
        return -leftShift;
    }

    @Override
    public double getExpd(int dim) {
        Wdw wdw = getWdw(dim);
        if (wdw == Wdw.EM) {
            String lb = block.optional($LB, dim).map(JCampRecord::getString).orElse("n");
            if (!lb.equalsIgnoreCase("n")) {
                return Double.parseDouble(lb);
            }
        }

        return 0;
    }

    @Override
    public SinebellWt getSinebellWt(int dim) {
        int power = 0;
        int size = 0;
        double sb = 0;
        double sbs = 0;
        double offset = 0;
        double end = 0;

        Wdw wdw = getWdw(dim);
        if (wdw == Wdw.SINE || wdw == Wdw.QSINE) {
            String ssbString = block.optional($SSB, dim).map(JCampRecord::getString).orElse("n");
            if (!ssbString.equalsIgnoreCase("n")) {
                power = (wdw == Wdw.QSINE) ? 2 : 1;
                sb = 1.0;
                sbs = Double.parseDouble(ssbString);
                offset = (sbs >= 2) ? 1 / sbs : 0;
                end = 1;
            }
        }

        return new DefaultSinebellWt(power, size, sb, sbs, offset, end);
    }

    @Override
    public GaussianWt getGaussianWt(int dim) {
        double gf = 0;
        double gfs = 0;
        double lb = 0;

        Wdw wdw = getWdw(dim);
        if (wdw == Wdw.GM) {
            String gbString = block.optional($GB, dim).map(JCampRecord::getString).orElse("n");
            if (!gbString.equalsIgnoreCase("n")) {
                gf = Double.parseDouble(gbString);
                String lbString = block.optional($LB, dim).map(JCampRecord::getString).orElse("n");
                if (!lbString.equalsIgnoreCase("n")) {
                    lb = Double.parseDouble(lbString);
                }
            }
        }

        return new DefaultGaussianWt(gf, gfs, lb);
    }

    @Override
    public FPMult getFPMult(int dim) {
        // not implemented, return default object
        return new FPMult();
    }

    @Override
    public LPParams getLPParams(int dim) {
        // not implemented, return default object
        return new LPParams();
    }

    @Override
    public String[] getLabelNames() {
        int nDim = getNDim();

        List<String> names = new ArrayList<>();
        for (int i = 0; i < nDim; i++) {
            String name = getTN(i);
            if (names.contains(name)) {
                name = name + "_" + (i + 1);
            }
            names.add(name);
        }
        return names.toArray(new String[0]);
    }

    private int getGroupDelay() {
        return block.optional($GRPDLY).map(JCampRecord::getInt).orElse(0);
    }

    @Override
    public void readVector(int index, Vec dvec) {
        double[] rValues = real[index];
        int n = rValues.length;

        if (imaginary.length == 0) {
            dvec.resize(n, false);
            dvec.setTDSize(n);
            for (int i = 0; i < n; i++) {
                dvec.set(i, rValues[i]);
            }
        } else {
            double[] iValues = imaginary[index];
            dvec.resize(n, true);
            dvec.setTDSize(n);
            for (int i = 0; i < n; i++) {
                //WARNING: real and imaginaries are inverted on purpose
                dvec.set(i, iValues[i], rValues[i]);
            }
        }

        dvec.setGroupDelay(getGroupDelay());

        dvec.dwellTime = 1.0 / getSW(0);
        dvec.centerFreq = getSF(0);

        dvec.setRefValue(getRef(0));
    }

    @Override
    public void readVector(int iVec, Complex[] cdata) {
        // should not be called, internal implementation detail
        throw new UnsupportedOperationException("Not implemented: readVector(int iVec, Complex[] cdata)");
    }

    @Override
    public void readVector(int iVec, double[] rdata, double[] idata) {
        // should not be called, internal implementation detail
        throw new UnsupportedOperationException("Not implemented: readVector(int iVec, double[] rdata, double[] idata)");
    }

    @Override
    public void readVector(int iVec, double[] data) {
        // should not be called, internal implementation detail
        throw new UnsupportedOperationException("Not implemented: readVector(int iVec, double[] data)");
    }

    @Override
    public void readVector(int dim, int index, Vec dvec) {
        if (dim == 0) {
            readVector(index, dvec);
        } else if (dim == 1) {
            readIndirectVector(index, dvec);
        } else {
            throw new UnsupportedOperationException("Unsupported dimension " + dim + " in JCamp");
        }
    }

    public void readIndirectVector(int index, Vec dvec) {
        int n = getSize(1);

        if (isComplex(1)) {
            dvec.resize(n, true);
            dvec.setTDSize(n);
            // real and imaginary are interlaced
            for (int row = 0; row < n * 2; row += 2) {
                double rValue = real[row][index];
                double iValue = real[row + 1][index];
                dvec.set(row / 2, rValue, iValue);
            }
        } else {
            dvec.resize(n, false);
            dvec.setTDSize(n);
            for (int row = 0; row < n; row++) {
                double rValue = real[row][index];
                dvec.set(row, rValue);
            }
        }

        dvec.setGroupDelay(0);

        dvec.dwellTime = 1.0 / getSW(1);
        dvec.centerFreq = getSF(1);

        dvec.setRefValue(getRef(1));
    }

    @Override
    public void resetAcqOrder() {
        // Not implemented: changing acq order isn't supported.
    }

    @Override
    public String[] getAcqOrder() {
        if (getNDim() == 1) {
            return new String[0];
        } else {
            return new String[]{"p1", "d1"};
        }
    }

    @Override
    public void setAcqOrder(String[] newOrder) {
        // doesn't support changing the order.
        // this is mostly useful for 3D, which isn't supported by JCamp
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

    @Override
    public DatasetType getPreferredDatasetType() {
        return preferredDatasetType;
    }

    @Override
    public void setPreferredDatasetType(DatasetType datasetType) {
        this.preferredDatasetType = datasetType;
    }

    @Override
    public boolean getNegatePairs(int dim) {
        return "negate".equals(getFTType(dim));
    }

    @Override
    public boolean getNegateImag(int dim) {
        if (dim == 0) {
            return false;
        }

        if (AcquisitionType.SEP.getLabel().equals(getSymbolicCoefs(dim))) {
            return false;
        }
        boolean reverse = block.optional($REVERSE, dim).map(JCampRecord::getString)
                .map("yes"::equalsIgnoreCase)
                .orElse(true);
        AcquisitionScheme scheme = getAcquisitionScheme();
        // For certain schemes use the opposite of reverse
        return (scheme == AcquisitionScheme.ECHO_ANTIECHO || scheme == AcquisitionScheme.STATES_TPPI) != reverse;
    }

    @Override
    public long getDate() {
        return block.getDate().getTime() / 1000;
    }

    @Override
    public boolean isFID() {
        return block.getDataType().isFID();
    }

    @Override
    public String toString() {
        return getFilePath();
    }

    @Nullable
    private AcquisitionScheme getAcquisitionScheme() {
        String schemeName = block.optional(_ACQUISITION_SCHEME)
                .map(JCampRecord::getString).map(JCampUtil::normalize)
                .orElse("");

        return Arrays.stream(AcquisitionScheme.values())
                .filter(value -> JCampUtil.normalize(value.name()).equals(schemeName))
                .findFirst()
                .orElseGet(this::getAcquisitionSchemeFromFnMode);
    }

    @Nullable
    private AcquisitionScheme getAcquisitionSchemeFromFnMode() {
        FnMode fnMode = getFnMode(1);
        return fnMode != null ? fnMode.acquisitionScheme : null;
    }

    @Nullable
    private FnMode getFnMode(int dim) {
        int value = block.optional($FN_MODE, dim).map(JCampRecord::getInt).orElse(-1);
        if (value < 0 || value >= FnMode.values().length) {
            return null; // Warning: considering no record present differently that a record containing UNDEFINED
        }

        return FnMode.values()[value];
    }

    private Wdw getWdw(int dim) {
        int value = block.optional($WDW, dim).map(JCampRecord::getInt).orElse(0);
        if (value < 0 || value >= Wdw.values().length)
            return Wdw.NO;

        return Wdw.values()[value];
    }

    /**
     * Check whether the path contains a JCamp FID file
     *
     * @param bpath the path to check
     * @return true if the path correspond to a JCamp FID file.
     */
    public static boolean findFID(StringBuilder bpath) {
        String lower = bpath.toString().toLowerCase();
        return MATCHING_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }


    /**
     * Check whether the path contains a JCamp dataset file
     *
     * @param bpath the path to check
     * @return true if the path correspond to a JCamp dataset file.
     */
    public static boolean findData(StringBuilder bpath) {
        // FID and Dataset have the same extensions
        return findFID(bpath);
    }

    /**
     * Get the total size of a dimension including both real and imaginary.
     *
     * @param dim The dimension to use.
     * @return The total size.
     */
    private int getTotalSize(int dim) {
        int factor = isComplex(dim) ? 2 : 1;
        return getSize(dim) * factor;
    }

    /**
     * Get a name to use for a dataset based on the provided filename.
     *
     * @param file The File object to parse the dataset name from.
     * @return A String dataset name.
     */
    public String suggestName(File file) {
        String fileName = file.getName();
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot != -1) {
            fileName = fileName.substring(0, lastDot);
        }
        return fileName;
    }

    /**
     * Create a Dataset from the JCAMP data. This method assumes the JCAMP data has already
     * been processed.
     *
     * @param datasetName The String name to use for the new Dataset.
     * @return The newly created Dataset.
     * @throws IOException
     * @throws DatasetException
     */
    public Dataset toDataset(String datasetName) throws IOException, DatasetException {
        File file = new File(path);
        Path fpath = file.toPath();

        int[] dimSizes = new int[getNDim()];
        for (int i = 0; i < getNDim(); i++) {
            dimSizes[i] = getTotalSize(i);
        }

        if (datasetName == null) {
            datasetName = suggestName(fpath.toFile());
        }
        // Create a dataset in memory
        Dataset dataset = new Dataset(datasetName, file, dimSizes, true);
        dataset.newHeader();
        // Set the processed data into the dataset
        boolean hasImaginaryData = this.imaginary.length != 0;
        Vec complex;
        for (int index = 0; index < this.real.length; index++) {
            if (hasImaginaryData) {
                complex = new Vec(this.real[index], this.imaginary[index]);
            } else {
                complex = new Vec(this.real[index]);
            }
            complex.setFreqDomain(true);
            dataset.writeVector(complex, index, 0);
        }
        // Set the header information in the dataset
        for (int i = 0; i < dataset.getNDim(); i++) {
            dataset.setValues(i, getValues(i));
            // The first dimension is set when the vectors are set.
            if (i > 0) {
                dataset.setComplex(i, false);
            }
            dataset.setSf(i, getSF(i));
            dataset.setSw(i, getSW(i));
            dataset.setRefValue(i, getRef(i));
            dataset.setRefPt(i, dataset.getSizeReal(i) / 2.0);
            dataset.setFreqDomain(i, true);
            String nucLabel = getTN(i);
            dataset.setNucleus(i, nucLabel);
            dataset.setLabel(i, nucLabel + (i + 1));
            dataset.syncPars(i);
        }
        dataset.setSolvent(getSolvent());
        dataset.setTempK(getTempK());
        dataset.setScale(SCALE);
        dataset.setDataType(0);
        dataset.writeHeader();
        return dataset;
    }
}
