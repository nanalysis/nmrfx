/*
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

import com.nanalysis.jcamp.model.*;
import com.nanalysis.jcamp.parser.JCampParser;
import com.nanalysis.jcamp.util.JCampUtil;
import org.apache.commons.math3.complex.Complex;
import org.nmrfx.processor.datasets.DatasetType;
import org.nmrfx.processor.datasets.parameters.*;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.SampleSchedule;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

import static com.nanalysis.jcamp.model.Label.*;

class JCAMPData implements NMRData {
    private static final List<String> MATCHING_EXTENSIONS = List.of(".jdx", ".dx");

    private enum FnMode {
        UNDEFINED, QF, QSEQ, TPPI, STATES, STATES_TPPI, ECHO_ANTIECHO, QF_NO_FREQ
    }

    private enum Wdw {
        NO, EM, GM, SINE, QSINE, TRAP, USER, SINC, QSINC, TRAF, TRAFS
    }

    private final String path;
    private final JCampDocument document;
    private final JCampBlock block;
    private final List<JCampPage> realPages;
    private final List<JCampPage> imaginaryPages;

    private DatasetType preferredDatasetType = DatasetType.NMRFX;
    private SampleSchedule sampleSchedule = null;
    private String[] acqOrder;

    // these values can be overridden from the outside, we need to cache them so that we can
    // either read them from JCamp or take the user-defined value
    private final Map<Integer, Double> sf = new HashMap<>();
    private final Map<Integer, Double> sw = new HashMap<>();
    private final Map<Integer, Double> ref = new HashMap<>();
    private final Map<Integer, Integer> size = new HashMap<>();

    public JCAMPData(String path) throws IOException {
        this.path = path;
        this.document = new JCampParser().parse(new File(path));

        if (document.getBlockCount() == 0) {
            throw new IOException("Invalid JCamp document, doesn't contain any block.");
        }
        this.block = document.blocks().findFirst()
                .orElseThrow(() -> new IOException("Invalid JCamp document, doesn't contain any block."));
        this.realPages = extractRealPages();
        this.imaginaryPages = extractImaginaryPages();
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

    @Override
    public void close() {
        // Nothing to close
    }

    @Override
    public String getFilePath() {
        return path;
    }

    @Override
    public String getPar(String parname) {
        return block.get(parname).getString();
    }

    @Override
    public Double getParDouble(String parname) {
        return block.get(parname).getDouble();
    }

    @Override
    public Integer getParInt(String parname) {
        return block.get(parname).getInt();
    }

    @Override
    public List<VendorPar> getPars() {
        Set<String> defined = new HashSet<>();
        List<VendorPar> vendorPars = new ArrayList<>();

        // get block-level records first
        for (String key : block.allRecordKeys()) {
            if (defined.add(key)) {
                vendorPars.add(new VendorPar(key, block.get(key).getString()));
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
        return realPages.size();
    }

    @Override
    public int getNPoints() {
        if (realPages.isEmpty())
            return 0;

        return realPages.get(0).toArray().length;
    }

    @Override
    public int getNDim() {
        return block.getOrDefault(NUMDIM, "1").getInt();
    }

    @Override
    public int getSize(int dim) {
        return size.computeIfAbsent(dim, this::extractSize);
    }

    private int extractSize(int dim) {
        if (dim == 0) {
            return getNPoints();
        } else if (dim == 1) {
            return getNVectors();
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
                .orElse(298.0); //XXX default value was already a question in original JCAMP data
    }

    @Override
    public String getSequence() {
        return block.optional(_PULSE_SEQUENCE, $PULPROG)
                .map(JCampRecord::getString).orElse("");
    }

    @Override
    public double getSF(int dim) {
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
        for (int dim = 0; dim < getNDim(); dim++) {
            names[dim] = getSFLabel(dim).map(Label::normalized).orElse("");
        }
        return names;
    }

    private Optional<Label> getSFLabel(int dim) {
        //XXX Base freq, or observed freq? should we try to add offset?
        //Previous implementation was using OBSERVE_FREQUENCY but this is not defined in 2D
        return Stream.of(_OBSERVE_FREQUENCY, $SFO1, $BF1, $BFREQ, $SF)
                .filter(label -> block.optional(label, dim).isPresent())
                .findFirst();
    }

    @Override
    public double getSW(int dim) {
        return sw.computeIfAbsent(dim, this::extractSW);
    }

    private double extractSW(int dim) {
        return block.optional($SW_H, dim)
                .map(JCampRecord::getDouble)
                .orElseThrow(() -> new IllegalStateException("Unknown spectral width, $SW_H undefined for dimension " + dim));
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
        //XXX check whether this is useful
        // original JCAMPData returned an array of empty strings
        String[] names = new String[getNDim()];
        Arrays.fill(names, "");
        names[0] = block.optional($SW_H).map(JCampRecord::getNormalizedLabel).orElse("");
        return names;
    }

    @Override
    public double getRef(int dim) {
        return ref.computeIfAbsent(dim, this::extractRef);
    }

    private double extractRef(int dim) {
        Label offsetLabel = dim == 1 ? $O2 : $O1;
        double offsetHz = block.optional(offsetLabel).map(JCampRecord::getDouble).orElse(0d);
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
            return block.optional($NUC_2)
                    .map(JCampRecord::getString)
                    .map(JCampUtil::toNucleusName)
                    .orElse(NMRDataUtil.guessNucleusFromFreq(getSF(dim)).toString());
        } else {
            throw new UnsupportedOperationException("Unsupported dimension " + dim + " in JCamp");
        }
    }

    @Override
    public boolean isComplex(int dim) {
        // For first dimension, check if the jcamp block contains imaginary pages
        if (dim == 0) {
            return !imaginaryPages.isEmpty();
        }

        // For other dimensions, infer it from FnMODE
        FnMode fnMode = getFnMode(dim);
        if (fnMode == FnMode.QSEQ || fnMode == FnMode.TPPI)
            return false;
        if (fnMode == FnMode.QF)
            return getValues(dim).isEmpty();

        return true;
    }

    @Override
    public String getFTType(int dim) {
        // known values: ft, rft (real), negate (hypercomplex)
        //XXX original JCamp has "ft" hardcoded.
        // Bruker is using AQ_Mod and FnMode, which is what I chose to copy here.
        // Not whether it should be filled for FID as well.

        if (dim == 0) {
            int aqMod = block.optional($AQ_MOD).map(JCampRecord::getInt).orElse(0);
            if (aqMod == 2)
                return "rft";
        } else {
            FnMode fnMode = getFnMode(dim);
            if (fnMode == FnMode.QSEQ || fnMode == FnMode.TPPI) {
                return "rft";
            } else if (fnMode == FnMode.UNDEFINED || fnMode == FnMode.STATES_TPPI) {
                return "negate";
            }
        }

        return "ft";
    }

    @Override
    public double[] getCoefs(int dim) {
        //XXX Was not implemented in original JCAMPData.
        //Inspired from BrukerData instead.
        if (dim == 0) {
            return new double[0];
        }

        FnMode fnMode = getFnMode(dim);
        if (fnMode == null || fnMode == FnMode.QF || fnMode == FnMode.QSEQ || fnMode == FnMode.TPPI) {
            return new double[0];
        } else if (fnMode == FnMode.STATES) {
            return new double[]{1, 0, 0, 0, 0, 0, 1, 0};
        } else if (fnMode == FnMode.UNDEFINED || fnMode == FnMode.STATES_TPPI) {
            return new double[]{1, 0, 0, 0, 0, 0, 1, 0};
        } else if (fnMode == FnMode.ECHO_ANTIECHO) {
            return new double[]{1, 0, -1, 0, 0, 1, 0, 1};
        }
        return new double[]{1, 0, 0, 1};
    }

    @Override
    public String getSymbolicCoefs(int dim) {
        //XXX Was not implemented in original JCAMPData.
        //Inspired from BrukerData instead.
        if (dim == 0) {
            return null;
        }

        FnMode fnMode = getFnMode(dim);
        if (fnMode == null) {
            return null;
        } else if (fnMode == FnMode.QSEQ || fnMode == FnMode.TPPI) {
            return "real";
        } else if (fnMode == FnMode.STATES) {
            return "hyper-r";
        } else if (fnMode == FnMode.UNDEFINED || fnMode == FnMode.STATES_TPPI) {
            return "hyper";
        } else if (fnMode == FnMode.ECHO_ANTIECHO) {
            return "echo-antiecho-r";
        }
        return "sep";
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
            ph0 -= 90; //XXX from original JCAMPData, but I don't know why
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
        double[] rValues = realPages.get(index).toArray();
        int n = rValues.length;

        if (imaginaryPages.isEmpty()) {
            dvec.resize(n, false);
            for (int i = 0; i < n; i++) {
                dvec.set(i, rValues[i]);
                dvec.setTDSize(n);
            }
        } else {
            double[] iValues = imaginaryPages.get(index).toArray();
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

        double delRef = (dvec.getSize() / 2d) * (1.0 / dvec.dwellTime) / dvec.centerFreq / dvec.getSize();
        dvec.refValue = getRef(0) + delRef;
    }

    @Override
    public void readVector(int iVec, Complex[] cdata) {
        //TODO implement me? needed?
        throw new UnsupportedOperationException("Not yet implemented: readVector(int iVec, Complex[] cdata)");
    }

    @Override
    public void readVector(int iVec, double[] rdata, double[] idata) {
        //TODO implement me? needed?
        throw new UnsupportedOperationException("Not yet implemented: readVector(int iVec, double[] rdata, double[] idata)");
    }

    @Override
    public void readVector(int iVec, double[] data) {
        //TODO implement me? needed?
        throw new UnsupportedOperationException("Not yet implemented: readVector(int iVec, double[] data)");
    }

    @Override
    public void readVector(int iDim, int iVec, Vec dvec) {
        //TODO implement me? needed?
        throw new UnsupportedOperationException("Not yet implemented: readVector(int iDim, int iVec, Vec dvec)");
    }

    @Override
    public void resetAcqOrder() {
        acqOrder = null;
    }

    @Override
    public String[] getAcqOrder() {
        //XXX I have no idea what this tries to do.
        if (acqOrder == null) {
            int idNDim = getNDim() - 1;
            acqOrder = new String[idNDim * 2];
            // p1,d1,p2,d2
            for (int i = 0; i < idNDim; i++) {
                acqOrder[i * 2] = "p" + (i + 1);
                acqOrder[i * 2 + 1] = "d" + (i + 1);
            }
        }
        return acqOrder;
    }

    @Override
    public void setAcqOrder(String[] newOrder) {
        //XXX I have no idea what this tries to do.
        // Taken from RS2DData.java
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
    public SampleSchedule getSampleSchedule() {
        return sampleSchedule;
    }

    @Override
    public void setSampleSchedule(SampleSchedule sampleSchedule) {
        this.sampleSchedule = sampleSchedule;
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
        //XXX doesn't seem to be called. Is this useful?
        return "negate".equals(getFTType(dim));
    }

    @Override
    public boolean getNegateImag(int dim) {
        //XXX doesn't seem to be called. Is this useful?
        return dim > 0;
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

    private FnMode getFnMode(int dim) {
        int value = block.optional($FN_MODE, dim).map(JCampRecord::getInt).orElse(-1);
        if (value < 0 || value >= FnMode.values().length)
            return null; // XXX should really be "no FnMode defined" be different from FnMode == "0/undefined"?

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
}
