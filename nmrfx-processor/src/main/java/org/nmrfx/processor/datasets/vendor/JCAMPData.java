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

import com.nanalysis.jcamp.model.JCampBlock;
import com.nanalysis.jcamp.model.JCampDocument;
import com.nanalysis.jcamp.model.JCampPage;
import com.nanalysis.jcamp.model.JCampRecord;
import com.nanalysis.jcamp.parser.JCampParser;
import com.nanalysis.jcamp.util.JCampUtil;
import org.apache.commons.math3.complex.Complex;
import org.nmrfx.processor.datasets.DatasetType;
import org.nmrfx.processor.datasets.parameters.FPMult;
import org.nmrfx.processor.datasets.parameters.GaussianWt;
import org.nmrfx.processor.datasets.parameters.LPParams;
import org.nmrfx.processor.datasets.parameters.SinebellWt;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.SampleSchedule;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.nanalysis.jcamp.model.Label.*;

class JCAMPData implements NMRData {
    private static final List<String> MATCHING_EXTENSIONS = List.of(".jdx", ".dx");

    //TODO check all string-based labels, and define the common ones in JCamp parser's Label class

    private final String path;
    private final JCampDocument document;
    private final JCampBlock block;
    private String[] acqOrder;

    private DatasetType preferredDatasetType = DatasetType.NMRFX;
    private SampleSchedule sampleSchedule = null;


    public JCAMPData(String path) throws IOException {
        this.path = path;
        this.document = new JCampParser().parse(new File(path));

        if (document.getBlockCount() == 0) {
            throw new IOException("Invalid JCamp document, doesn't contain any block.");
        }
        this.block = document.blocks().findFirst()
                .orElseThrow(() -> new IOException("Invalid JCamp document, doesn't contain any block."));
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
        //XXX To confirm, looks like it is number of 1D rows in total
        // so 1 if 1D data, and number of 2D if 2D data.
        // no more than 2D data are supported in JCamp
        //XXX should we try to get it from page content instead?
        return block.optional($TD, 1)
                .map(r -> r.getInt() / 2)
                .orElse(1);
    }

    @Override
    public int getNPoints() {
        // XXX always /2, or only if complex data?
        // XXX should we try to get it from page content instead?
        return block.optional($TD, 0)
                .map(r -> r.getInt() / 2)
                .orElse(0);
    }

    @Override
    public int getNDim() {
        return block.getOrDefault(NUMDIM, "1").getInt();
    }

    @Override
    public int getSize(int dim) {
        // XXX should we try to get it from page content instead?
        return block.optional($TD, 0)
                .map(r -> r.getInt() / 2)
                .orElse(0);
    }

    @Override
    public void setSize(int dim, int size) {
        //TODO implement me?
        // Don't see how we would need this as we are accessing the size directly
        System.out.println("Called unimplemented method: setSize: " + dim + ", " + size);
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
        //XXX some doubts about the expected unit: Hz or MHz?
        //XXX Base freq, or observed freq? should we try to add offset?
        //Previous implementation was using OBSERVE_FREQUENCY but this is not defined in 2D
        if (dim == 0) {
            return block.optional($BF1, $BFREQ, $SF)
                    .map(r -> r.getDouble() * 1E6)
                    .orElseThrow(() -> new IllegalStateException("Unknown frequency, $BF1, $BFREQ and $SF undefined!"));
        } else if (dim == 1) {
            return block.optional($BF2)
                    .map(r -> r.getDouble() * 1e6)
                    .orElseThrow(() -> new IllegalStateException("Unknown frequency, $BF2 undefined!"));
        } else {
            throw new UnsupportedOperationException("Unsupported dimension " + dim + " in JCamp");
        }
    }

    @Override
    public void setSF(int dim, double value) {
        //TODO implement me?
        System.out.println("Called unimplemented method: setSF: " + dim + ", " + value);
    }

    @Override
    public void resetSF(int dim) {
        //TODO implement me?
        System.out.println("Called unimplemented method: resetSF: " + dim);
    }

    @Override
    public double getSW(int dim) {
        return block.optional($SW_H, dim)
                .map(JCampRecord::getDouble)
                .orElseThrow(() -> new IllegalStateException("Unknown spectral width, $SW_H undefined for dimension " + dim));
    }

    @Override
    public void setSW(int dim, double value) {
        //TODO implement me?
        System.out.println("Called unimplemented method: setSW: " + dim + ", " + value);
    }

    @Override
    public void resetSW(int dim) {
        //TODO implement me?
        System.out.println("Called unimplemented method: resetSW: " + dim);
    }

    @Override
    public double getRef(int dim) {
        String xUnit = JCampUtil.normalize(block.get(UNITS).getStrings().get(0));
        double firstX = block.get(FIRST).getDoubles()[0];
        if(xUnit.equals("HZ")) {
            return firstX / getSF(dim); //XXX original was always targetting Sf[0], not caring about dimension. Normal?
        } else if(xUnit.equals("PPM")) {
            return firstX;
        }
        return 0;
    }

    @Override
    public void setRef(int dim, double ref) {
        //TODO implement me?
        System.out.println("Called unimplemented method: setRef: " + dim + ", " + ref);
    }

    @Override
    public void resetRef(int dim) {
        //TODO implement me?
        System.out.println("Called unimplemented method: resetRef: " + dim);
    }

    @Override
    public double getRefPoint(int dim) {
        //XXX RS2D and Varian data return size / 2.
        // original JCAMP and Bruker data return 1
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
        if(dim == 0) {
            return !block.getPagesForYSymbol("I").isEmpty();
        }

        // For other dimensions, infer it from FnMODE
        // TODO check if FnMODE is really defined on the second dimension
        int fnMode = block.optional("$FnMODE", dim).map(JCampRecord::getInt).orElse(-1);
        if(fnMode == 2 || fnMode == 3)
            return false;
        if(fnMode == 1)
            return getValues(dim).isEmpty();

        return true;
    }

    @Override
    public String getFTType(int dim) {
        // known values: ft, rft (real), negate (hypercomplex)
        //XXX original JCamp has "ft" hardcoded.
        // Bruker is using AQ_Mode and FnMode, which is what I chose to copy here.
        // Not whether it should be filled for FID as well.

        if(dim == 0) {
            int aqMod = block.optional("$AQ_mod").map(JCampRecord::getInt).orElse(0);
            if(aqMod == 2)
                return "rft";
        } else {
            // TODO check if FnMODE is really defined on the second dimension
            int fnMode = block.optional("$FnMODE", dim).map(JCampRecord::getInt).orElse(-1);
            if (fnMode == 2 || fnMode == 3) {
                return "rft";
            } else if (fnMode == 0 || fnMode == 5) {
                return "negate";
            }
        }

        return "ft";
    }

    @Override
    public double[] getCoefs(int dim) {
        //TODO implement me
        //***What are the coefficients? Similar situation as the complex value
        return new double[0];

    }

    @Override
    public String getSymbolicCoefs(int dim) {
        //TODO implement me
        //***What are the coefficients? Similar situation as the complex value
        return null;
    }

    @Override
    public String getVendor() {
        //XXX some code expected "bruker" in lowercase
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
        int leftShift = block.optional("LS", dim)
                .map(JCampRecord::getInt)
                .orElse(0);

        // reversed betwen JCamp and NMRfx
        return -leftShift;
    }

    @Override
    public double getExpd(int dim) {
        int wdw = block.optional("$WDW", dim)
                .map(JCampRecord::getInt)
                .orElse(0);

        if(wdw == 1) {
            String lb = block.optional("$LB", dim).map(JCampRecord::getString).orElse("n");
            if(!lb.equalsIgnoreCase("n")) {
                return Double.parseDouble(lb);
            }
        }

        return wdw;
    }

    @Override
    public SinebellWt getSinebellWt(int dim) {
        //TODO implement me
        // *** what is the sinebellwt? Not sure how to change this as it's currently
        //calling BrukerSinebellWt()
        return null;
    }

    @Override
    public GaussianWt getGaussianWt(int dim) {
        //TODO implement me
        // *** what is the gaussianwt?
        return null;
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
    public String[] getSFNames() {
        //XXX check whether this is useful
        //RS2DData returns an array of empty strings
        //Original JCAMPData returned OBSERVEFREQUENCY,dim => doesn't work for 2D files
        String[] names = new String[getNDim()];
        Arrays.fill(names, "");
        return names;
    }

    @Override
    public String[] getSWNames() {
        //XXX check whether this is useful
        // original JCAMPData returned an array of empty strings
        String[] names = new String[getNDim()];
        Arrays.fill(names, "");
        return names;
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

    @Override
    public void readVector(int iVec, Vec dvec) {
        List<JCampPage> realPages = block.getPagesForYSymbol("R");
        List<JCampPage> imaginaryPages = block.getPagesForYSymbol("I");

        //TODO group delay
        //dvec.setGroupDelay(groupDelay);

        // XXX 1D only for now - is 2D supposed to be here or in another readVector()?
        double[] rValues = realPages.get(0).toArray();

        int n = rValues.length;

        if (imaginaryPages.isEmpty()) {
            dvec.resize(n, false);
            for (int i = 0; i < n; i++) {
                dvec.set(i, rValues[i]);
                dvec.setTDSize(n);
            }
        } else {
            double[] iValues = imaginaryPages.get(0).toArray();
            dvec.resize(n, true);
            dvec.setTDSize(n);
            for (int i = 0; i < n; i++) {
                dvec.set(i, rValues[i], iValues[i]);
            }
        }

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
    public String toString() {
        return getFilePath();
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
