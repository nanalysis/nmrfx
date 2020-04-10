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

 /*
 * SpectralDim.java
 *
 * Created on January 26, 2007, 5:42 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets.peaks;

import java.util.DoubleSummaryStatistics;
import java.util.Optional;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.nmrfx.processor.datasets.Nuclei;

/**
 *
 * @author brucejohnson
 */
public class SpectralDim {

    static String loopStrings[] = {
        "_Spectral_dim.ID",
        "_Spectral_dim.Atom_type",
        "_Spectral_dim.Atom_isotope_number",
        "_Spectral_dim.Spectral_region",
        "_Spectral_dim.Magnetization_linkage_ID",
        "_Spectral_dim.Sweep_width",
        "_Spectral_dim.Spectrometer_frequency",
        "_Spectral_dim.Encoding_code",
        "_Spectral_dim.Encoded_source_dimension_ID",
        "_Spectral_dim.Dataset_dimension",
        "_Spectral_dim.Dimension_name",
        "_Spectral_dim.ID_tolerance",
        "_Spectral_dim.Pattern",
        "_Spectral_dim.Relation",
        "_Spectral_dim.Aliasing",
        "_Spectral_dim.Precision",};
    private PeakList peakList = null;
// fixme sf should come from dataset
    private double sf = 1.0;
    private double sw = 1.0;
    private double ref = 0.0;
    private int size = 0;
    private int precision = 5;
    private String dimName = "";
    private String nucName = "";
    private int dataDim = 0;
    private double tol = 0.0;
    private double idTol = 0.0;
    private String findLabel = "";
    private Integer atomIsotope = null;
    private String atomType = null;
    private String spectralRegion = "";
    private int magLinkage = 1;
    private String encodingCode = "";
    private int encodedSourceDim = 1;
    private int sDim = 0;
    private String pattern = "";
    private String relation = "";
    private String spatialRelation = "";
    private char foldMode = 'n';
    private int foldCount = 0;
    private boolean acqDim = false;
    private boolean absPos = false;
    private Optional<Double> meanWidthPPM = Optional.empty();

    /**
     * Creates a new instance of SpectralDim
     *
     * @param peakList The Peak List that this spectral dimension is part of
     * @param iDim The dimension number of this spectral dimension
     */
    public SpectralDim(PeakList peakList, int iDim) {
        this.peakList = peakList;
        dataDim = iDim;
        magLinkage = iDim;
        dimName = "D" + dataDim;
        encodedSourceDim = iDim;
        if (iDim == 0) {
            acqDim = true;
        }
    }

    public SpectralDim copy(PeakList peakList) {
        SpectralDim newSpectralDim = new SpectralDim(peakList, dataDim);
        newSpectralDim.sf = sf;
        newSpectralDim.sw = sw;
        newSpectralDim.ref = ref;
        newSpectralDim.size = size;
        newSpectralDim.precision = precision;
        newSpectralDim.dimName = dimName;
        newSpectralDim.dataDim = dataDim;
        newSpectralDim.tol = tol;
        newSpectralDim.idTol = idTol;
        newSpectralDim.findLabel = findLabel;
        newSpectralDim.atomIsotope = atomIsotope;
        newSpectralDim.atomType = atomType;
        newSpectralDim.spectralRegion = spectralRegion;
        newSpectralDim.magLinkage = magLinkage;
        newSpectralDim.encodingCode = encodingCode;
        newSpectralDim.encodedSourceDim = encodedSourceDim;
        newSpectralDim.sDim = sDim;
        newSpectralDim.pattern = pattern;
        newSpectralDim.relation = relation;
        newSpectralDim.spatialRelation = spatialRelation;
        newSpectralDim.foldMode = foldMode;
        newSpectralDim.foldCount = foldCount;

        return newSpectralDim;
    }

    public static String[] getSTAR3LoopStrings() {
        return loopStrings;
    }

    public PeakList getPeakList() {
        return peakList;
    }

    public String toSTAR3LoopPeakCharString() {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        char stringQuote = '"';
        result.append(String.valueOf(getDataDim() + 1)).append(sep);
        result.append(getAtomType()).append(sep);
        result.append(getAtomIsotope());
        result.append(sep);
        result.append(stringQuote);
        result.append(getSpectralRegion());
        result.append(stringQuote);
        result.append(sep);
        result.append(getMagLinkage() + 1);
        result.append(sep);
        result.append(getSw());
        result.append(sep);
        result.append(getSf());
        result.append(sep);
        result.append(stringQuote);
        result.append(getEncodingCode());
        result.append(stringQuote);
        result.append(sep);
        result.append(getEncodedSourceDim() + 1);
        result.append(sep);
        result.append((getDataDim() + 1));
        result.append(sep);
        result.append(stringQuote);
        result.append(getDimName());
        result.append(stringQuote);
        result.append(sep);
        result.append(getIdTol());
        result.append(sep);
        result.append(stringQuote);
        result.append(getPattern());
        result.append(stringQuote);
        result.append(sep);
        result.append(stringQuote);
        result.append(getRelation());
        result.append(stringQuote);
        result.append(sep);
        result.append(stringQuote);
        result.append(getAliasing());
        result.append(stringQuote);
        result.append(sep);
        result.append(getPrecision());
        return result.toString();
    }
//     1   ppm   1H    500.13   4.998700337912143    9.898700337912143    circular   true   true

    public String toXPK2Dim() {
        StringBuilder result = new StringBuilder();
        String sep = "\t";

        result.append(getDimName());
        result.append(sep);

        result.append(getNucleus());
        result.append(sep);

        result.append("ppm");
        result.append(sep);

        result.append(getSf());
        result.append(sep);

        result.append(getSw());
        result.append(sep);

        result.append(getRef());
        result.append(sep);

        result.append(getIdTol());
        result.append(sep);

        result.append(getPattern());
        result.append(sep);

        result.append(getRelation());
        result.append(sep);

        result.append(getSpatialRelation());
        result.append(sep);

        result.append(getNEFAliasing());
        result.append(sep);

        result.append(isAbsPosition());
        result.append(sep);

        result.append(isAcqDim());

        return result.toString();
    }

    public int guessIsotope(double freq) {
        double[] Hfreqs = {1000.0, 950.0, 900.0, 800.0, 750.0, 700.0, 600.0, 500.0, 400.0};
        int[] isotopes = {1, 13, 15, 31};
        int H = 0;
        int C = 1;
        int N = 2;
        int P = 3;
        double[] ratio = new double[P + 1];
        ratio[H] = 1.0;
        ratio[C] = 0.25145004;
        ratio[N] = 0.10136783;
        ratio[P] = 0.40480737;
        double min = 1000.0;
        int jMin = 0;
        for (int i = 0; i < Hfreqs.length; i++) {
            for (int j = H; j <= P; j++) {
                double freqTest = ratio[j] * Hfreqs[i];
                double delta = Math.abs(freq - freqTest);
                if (delta < min) {
                    min = delta;
                    jMin = j;
                }
            }
        }
        return isotopes[jMin];
    }

    public Integer getAtomIsotope() {
        if (atomIsotope == null) {
            int isotope = guessIsotope(sf);
            atomIsotope = isotope;
        }
        return atomIsotope;
    }

    public void setAtomIsotopeValue(int iValue) {
        atomIsotope = iValue;
    }

    public int getAtomIsotopeValue() {
        int isotope = getAtomIsotope();
        return isotope;
    }

    public void setAtomType(String atomType) {
        this.atomType = atomType;
    }

    public String getAtomType() {
        if (atomType == null) {
            atomType = getAtomTypeFromIsotope();
        }
        return atomType;
    }

    public String getAtomTypeFromIsotope() {
        String isotopeAtomType;
        int isotope = getAtomIsotopeValue();
        switch (isotope) {
            case 1:
                isotopeAtomType = "H";
                break;
            case 13:
                isotopeAtomType = "C";
                break;
            case 15:
                isotopeAtomType = "N";
                break;
            case 31:
                isotopeAtomType = "P";
                break;
            default:
                isotopeAtomType = "X";
        }
        return isotopeAtomType;
    }

    public String getSpectralRegion() {
        return spectralRegion;
    }

    public void setSpectralRegion(String value) {
        spectralRegion = value;
    }

    public double getSf() {
        return sf;
    }

    public void setSf(double sf) {
        this.sf = sf;
    }

    public double getSw() {
        return sw;
    }

    public void setSw(double sw) {
        this.sw = sw;
    }

    public int getFoldCount() {
        return foldCount;
    }

    public void setFoldCount(int foldCount) {
        this.foldCount = foldCount;
    }

    public char getFoldMode() {
        return foldMode;
    }

    public void setFoldMode(char foldMode) {
        this.foldMode = foldMode;
    }

    public void setAliasing(String aliasSpecifier) {
        foldMode = 'n';
        foldCount = 0;
        if (aliasSpecifier.length() == 2) {
            char tfoldMode = aliasSpecifier.charAt(0);
            char tfoldCountChar = aliasSpecifier.charAt(1);
            if (Character.isDigit(tfoldCountChar)) {
                if ((tfoldMode == 'f') || (tfoldMode == 'a')) {
                    foldMode = tfoldMode;
                    foldCount = tfoldCountChar - '0';
                }
            }
        }
    }

    public String getAliasing() {
        String aliasSpecifier;
        if (foldMode == 'n') {
            aliasSpecifier = "n";
        } else {
            aliasSpecifier = foldMode + "" + foldCount;
        }
        return aliasSpecifier;
    }

    public String getNEFAliasing() {
        String aliasSpecifier = "circular";
        return aliasSpecifier;
    }

    public void setNEFAliasing(String aliasing) {
    }

    public double getRef() {
        return ref;
    }

    public void setRef(double ref) {
        this.ref = ref;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getMagLinkage() {
        return magLinkage;
    }

    public void setMagLinkage(int magLinkage) {
        this.magLinkage = magLinkage;
    }

    public int getEncodedSourceDim() {
        return encodedSourceDim;
    }

    public void setEncodedSourceDim(int encodedSourceDim) {
        this.encodedSourceDim = encodedSourceDim;
    }

    public String getEncodingCode() {
        return encodingCode;
    }

    public void setEncodingCode(String encodingCode) {
        this.encodingCode = encodingCode;
    }

    public int getPrecision() {
        return precision;
    }

    public void setPrecision(int precision) {
        this.precision = precision;
    }

    public String getNucleus() {
        if (nucName.equals("")) {
            Nuclei[] nuclei = getPeakList().guessNuclei();
            nucName = nuclei[dataDim].getNumberName();
        }
        return nucName;
    }

    public void setNucleus(String nucName) {
        this.nucName = nucName;
    }

    public String getDimName() {
        return dimName;
    }

    public void setDimName(String dimName) {
        this.dimName = dimName;
    }

    public double getTol() {
        return tol;
    }

    public void setTol(double tol) {
        this.tol = tol;
    }

    public double getIdTol() {
        return idTol;
    }

    public void setIdTol(double idTol) {
        this.idTol = idTol;
    }

    public String getFindLabel() {
        return findLabel;
    }

    public void setFindLabel(String findLabel) {
        this.findLabel = findLabel;
    }

    public int getSDim() {
        return sDim;
    }

    public void setSDim(int sDim) {
        this.sDim = sDim;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getRelation() {
        return relation;
    }

    public String getRelationDim() {
        String dimRelation = "";
        if (relation.length() == 2) {
            if (((relation.charAt(0) == 'D') || (relation.charAt(0) == 'P'))
                    && Character.isDigit(relation.charAt(1))) {
                int iDim = Integer.valueOf(relation.substring(1)) - 1;
                dimRelation = getPeakList().getSpectralDim(iDim).getDimName();
            }
        }
        return dimRelation;
    }

    public void setRelation(String relation) {
        int iDim = getPeakList().getListDim(relation);
        if (iDim >= 0) {
            relation = "D" + (iDim + 1);
        }
        this.relation = relation;
    }

    public String getSpatialRelation() {
        return spatialRelation;
    }

    public String getSpatialRelationDim() {
        String dimRelation = "";
        if (spatialRelation.length() == 2) {
            if (((spatialRelation.charAt(0) == 'D') || (spatialRelation.charAt(0) == 'P'))
                    && Character.isDigit(spatialRelation.charAt(1))) {
                int iDim = Integer.valueOf(spatialRelation.substring(1)) - 1;
                dimRelation = getPeakList().getSpectralDim(iDim).getDimName();
            }
        }
        return dimRelation;
    }

    public void setSpatialRelation(String relation) {
        int iDim = getPeakList().getListDim(relation);
        if (iDim >= 0) {
            relation = "D" + (iDim + 1);
        }
        this.spatialRelation = relation;
    }

    public int getDataDim() {
        return dataDim;
    }

    public void setDataDim(int dataDim) {
        this.dataDim = dataDim;
    }

    public boolean isAcqDim() {
        return acqDim;
    }

    public void setAcqDim(boolean state) {
        acqDim = state;
    }

    public boolean isAbsPosition() {
        return absPos;
    }

    public void setAbsPosition(boolean state) {
        absPos = state;
    }

    public double getMeanWidthPPM() {
        if (!meanWidthPPM.isPresent()) {
            DoubleSummaryStatistics stats = peakList.widthStatsPPM(dataDim);
            meanWidthPPM = Optional.of(stats.getAverage());
        }
        return meanWidthPPM.get();
    }
}
