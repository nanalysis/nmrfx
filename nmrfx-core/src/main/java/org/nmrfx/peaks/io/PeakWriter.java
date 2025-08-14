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
import org.nmrfx.chemistry.AtomResonance;
import org.nmrfx.peaks.*;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.star.STAR3Base;

import static org.nmrfx.peaks.io.PeakWriter.PIPEDIMS.D;

/**
 * @author Bruce Johnson
 */
@PythonAPI("pscript")
public class PeakWriter {

    private static final String[] XPKDIMSTRINGS = {
            "label",
            "code",
            "units",
            "sf",
            "sw",
            "fp",
            "idtol",
            "pattern",
            "bonded",
            "spatial",
            "folding",
            "abspos",
            "acqdim"};
    private static final String[] NEF_PEAK_DIM_STRINGS = {"_nef_spectrum_dimension.dimension_id",
            "_nef_spectrum_dimension.axis_unit",
            "_nef_spectrum_dimension.axis_code",
            "_nef_spectrum_dimension.spectrometer_frequency",
            "_nef_spectrum_dimension.spectral_width",
            "_nef_spectrum_dimension.value_first_point",
            "_nef_spectrum_dimension.folding",
            "_nef_spectrum_dimension.absolute_peak_positions",
            "_nef_spectrum_dimension.is_acquisition"};

    // //     1   ppm   1H    500.13   4.998700337912143    9.898700337912143    circular   true   true
    private static final String[] NEF_PEAK_ROW_STRINGS = {"_nef_peak.ordinal",
            "_nef_peak.peak_id",
            "_nef_peak.volume",
            "_nef_peak.volume_uncertainty",
            "_nef_peak.height",
            "_nef_peak.height_uncertainty",
            "_nef_peak.position_1",
            "_nef_peak.position_uncertainty_1",
            "_nef_peak.position_2",
            "_nef_peak.position_uncertainty_2",
            "_nef_peak.position_3",
            "_nef_peak.position_uncertainty_3",
            "_nef_peak.chain_code_1",
            "_nef_peak.sequence_code_1",
            "_nef_peak.residue_type_1",
            "_nef_peak.atom_name_1",
            "_nef_peak.chain_code_2",
            "_nef_peak.sequence_code_2",
            "_nef_peak.residue_type_2",
            "_nef_peak.atom_name_2",
            "_nef_peak.chain_code_3",
            "_nef_peak.sequence_code_3",
            "_nef_peak.residue_type_3",
            "_nef_peak.atom_name_3"};
    private static final String[] ASSIGNED_PEAK_CHEMSHIFT_STRINGS = {
            "_Assigned_peak_chem_shift.Peak_ID",
            "_Assigned_peak_chem_shift.Spectral_dim_ID",
            "_Assigned_peak_chem_shift.Val",
            "_Assigned_peak_chem_shift.Resonance_ID",
            "_Assigned_peak_chem_shift.Spectral_peak_list_ID",};

    public static void writePeaksXPK2(String fileName, PeakList peakList) throws IOException, InvalidPeakException {
        try (FileWriter writer = new FileWriter(fileName)) {
            PeakWriter peakWriter = new PeakWriter();
            peakWriter.writePeaksXPK2(writer, peakList);
        }
    }

    public void writePeaksXPK2(Writer chan, PeakList peakList) throws IOException, InvalidPeakException {

        Map<String, String> properties = peakList.getProperties();
        chan.write("peaklist\tdataset\tndim\tcondition\tscale");
        StringBuilder propBuilder = new StringBuilder();
        for (String propName : properties.keySet()) {
            String propValue = properties.get(propName);
            if (!propValue.isEmpty()) {
                chan.write('\t');
                chan.write("prop:");
                chan.write(propName);
                propBuilder.append('\t');
                propBuilder.append(propValue);
            }
        }

        chan.write("\n");
        StringBuilder sBuilder = new StringBuilder();
        char sep = '\t';
        sBuilder.append(peakList.getName()).append(sep);
        sBuilder.append(peakList.getDatasetName()).append(sep);
        sBuilder.append(peakList.getNDim()).append(sep);
        sBuilder.append(peakList.getSampleConditionLabel()).append(sep);
        sBuilder.append(peakList.getScale());
        if (!propBuilder.isEmpty()) {
            sBuilder.append(propBuilder);
        }
        sBuilder.append('\n');
        chan.write(sBuilder.toString());
        for (int j = 0; j < XPKDIMSTRINGS.length; j++) {
            if (j > 0) {
                chan.write("\t");
            }
            chan.write(XPKDIMSTRINGS[j]);
        }
        chan.write("\n");
        //     1   ppm   1H    500.13   4.998700337912143    9.898700337912143    circular   true   true
        //     2   ppm   1H    500.13   10.986153600089578   10.393076800044788   circular   true   false
        //     3   ppm   15N   50.666   24.002901353965186   128.00145067698259   circular   true   false
        int nDim = peakList.nDim;
        for (int j = 0; j < nDim; j++) {
            chan.write(peakList.getSpectralDim(j).toXPK2Dim() + "\n");
        }
        chan.write(peakList.getXPK2Header());
        chan.write("\n");
        int nPeaks = peakList.size();
        for (int i = 0; i < nPeaks; i++) {
            Peak peak = peakList.getPeak(i);
            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            chan.write(peak.toXPK2String(i) + "\n");
        }
    }

    public void writePeakMeasures(Writer chan, PeakList peakList) throws IOException, InvalidPeakException {
        int nDim = peakList.nDim;
        StringBuilder result = new StringBuilder();
        String sep = "\t";
        result.append("id").append(sep);

        for (int j = 0; j < nDim; j++) {
            result.append("lab").append((j + 1)).append(sep);
        }
        int nPeaks = peakList.size();
        boolean wroteHeader = false;
        for (int i = 0; i < nPeaks; i++) {
            Peak peak = peakList.getPeak(i);
            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            if (peak.getMeasures().isPresent()) {
                if (!wroteHeader) {
                    double[][] values = peak.getMeasures().get();
                    int nMeasure = values[0].length;
                    double[] xValues = null;
                    if (peakList.hasMeasures()) {
                        xValues = peakList.getMeasureValues();
                    }
                    for (int j = 0; j < nMeasure; j++) {
                        if ((xValues != null) && (xValues.length == nMeasure)) {
                            result.append(xValues[j]).append(sep).append("err").append(sep);
                        } else {
                            result.append("val").append((j + 1)).append(sep);
                        }
                    }
                    chan.write(result.toString().trim());
                    chan.write("\n");
                    wroteHeader = true;
                }
                chan.write(peak.toMeasureString(i) + "\n");
            }
        }
    }

    public void writePeaksXPK(Writer chan, PeakList peakList) throws IOException, IllegalArgumentException, InvalidPeakException {
        if (chan == null) {
            throw new IllegalArgumentException("Channel null");
        }
        chan.write(peakList.getXPKHeader());
        int nPeaks = peakList.size();
        for (int i = 0; i < nPeaks; i++) {
            Peak peak = peakList.getPeak(i);
            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            chan.write(peak.toXPKString() + "\n");
        }
    }

    public void writePeaks(Writer chan, PeakList peakList) throws IOException, IllegalArgumentException, InvalidPeakException {
        if (chan == null) {
            throw new IllegalArgumentException("Channel null");
        }
        int nPeaks = peakList.size();
        for (int i = 0; i < nPeaks; i++) {
            Peak peak = peakList.getPeak(i);
            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            chan.write(peak.toMyString() + "\n");
        }
    }

    public void writePeaksNEF(Writer chan, PeakList peakList) throws IOException, InvalidPeakException {
        chan.write(STAR3Base.SAVE + "nef_nmr_spectrum_" + peakList.getName() + "\n");
        chan.write("_nef_nmr_spectrum.Sf_category                 ");
        chan.write("nef_nmr_spectrum\n");
        chan.write("_nef_nmr_spectrum.Sf_framecode                 ");
        chan.write("nef_nmr_spectrum_" + peakList.getName() + "\n");
        chan.write("_nef_nmr_spectrum.chemical_shift_list                          ");
        chan.write(".\n");
        chan.write("_nef_nmr_spectrum.experiment_classification               ");
        chan.write(".\n");
        chan.write("_nef_nmr_spectrum.expriment_type                   ");
        chan.write(".\n");
        chan.write("loop_\n");
        for (String nefString : NEF_PEAK_DIM_STRINGS) {
            chan.write(nefString + "\n");
        }
        chan.write("\n");
        //     1   ppm   1H    500.13   4.998700337912143    9.898700337912143    circular   true   true
        //     2   ppm   1H    500.13   10.986153600089578   10.393076800044788   circular   true   false
        //     3   ppm   15N   50.666   24.002901353965186   128.00145067698259   circular   true   false
        int nDim = peakList.nDim;
        for (int j = 0; j < nDim; j++) {
            chan.write(peakList.getSpectralDim(j).toSTAR3LoopPeakCharString() + "\n");
        }

        chan.write("stop_\n");
        chan.write("\n");
        chan.write("loop_\n");
        for (String nefString : NEF_PEAK_ROW_STRINGS) {
            chan.write(nefString + "\n");
        }
        int nPeaks = peakList.size();
        for (int i = 0; i < nPeaks; i++) {
            Peak peak = peakList.getPeak(i);
            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            chan.write(peak.toNEFString(i) + "\n");
        }
        chan.write("stop_\n");
        chan.write("\n");
        chan.write("\nsave_\n\n");
    }

    public void writePeaksSTAR3(Writer chan, PeakList peakList) throws IOException, InvalidPeakException {
        peakList.writeSTAR3Header(chan);
        String[] loopStrings = SpectralDim.getSTAR3LoopStrings();
        chan.write("loop_\n");
        for (String loopString : loopStrings) {
            chan.write(loopString + "\n");
        }
        chan.write("\n");
        int nDim = peakList.nDim;
        for (int j = 0; j < nDim; j++) {
            chan.write(peakList.getSpectralDim(j).toSTAR3LoopPeakCharString() + "\n");
        }
        chan.write("stop_\n");
        chan.write("\n");
        loopStrings = Peak.getSTAR3Strings();
        chan.write("loop_\n");
        for (String loopString : loopStrings) {
            chan.write(loopString + "\n");
        }
        chan.write("\n");
        int nPeaks = peakList.size();
        for (int i = 0; i < nPeaks; i++) {
            Peak peak = peakList.getPeak(i);
            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            chan.write(peak.toSTAR3LoopPeakString() + "\n");
        }
        chan.write(STAR3Base.STOP + "\n");
        chan.write("\n");

        if (peakList.hasMeasures()) {
            peakList.getMeasures().get().writeMeasures(chan);

            loopStrings = Peak.getSTAR3MeasureCharStrings();
            chan.write("loop_\n");
            for (String loopString : loopStrings) {
                chan.write(loopString + "\n");
            }

            chan.write("\n");

            for (int i = 0; i < nPeaks; i++) {
                Peak peak = peakList.getPeak(i);
                chan.write(peak.toMeasureSTARString(i));
            }
            chan.write(STAR3Base.STOP + "\n");
            chan.write("\n");
        }

        loopStrings = Peak.getSTAR3GeneralCharStrings();
        chan.write("loop_\n");
        for (String loopString : loopStrings) {
            chan.write(loopString + "\n");
        }
        chan.write("\n");
        for (int i = 0; i < nPeaks; i++) {
            Peak peak = peakList.getPeak(i);
            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            chan.write(peak.toSTAR3LoopIntensityString(0) + "\n");
            chan.write(peak.toSTAR3LoopIntensityString(1) + "\n");
        }
        chan.write("stop_\n");
        chan.write("\n");
        loopStrings = Peak.getSTAR3CharStrings();
        chan.write("loop_\n");
        for (String loopString : loopStrings) {
            chan.write(loopString + "\n");
        }
        chan.write("\n");
        for (int i = 0; i < nPeaks; i++) {
            Peak peak = peakList.getPeak(i);
            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            PeakDim[] peakDims = peak.getPeakDims();
            for (PeakDim peakDim : peakDims) {
                chan.write(peakDim.toSTAR3LoopPeakCharString(0) + "\n");
            }
        }
        chan.write("stop_\n");
        loopStrings = ASSIGNED_PEAK_CHEMSHIFT_STRINGS;
        chan.write("loop_\n");
        for (String loopString : loopStrings) {
            chan.write(loopString + "\n");
        }
        chan.write("\n");
        int iContrib = 0;
        for (int i = 0; i < nPeaks; i++) {
            Peak peak = peakList.getPeak(i);
            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            PeakDim[] peakDims = peak.getPeakDims();
            for (PeakDim peakDim : peakDims) {
                AtomResonance resonance = peakDim.getResonance();
                if (resonance != null) {
                    long resID = resonance.getID();
                    chan.write(peakDim.toSTAR3LoopAssignedPeakChemShiftString(iContrib++, resID) + "\n");
                }
            }
        }
        chan.write("stop_\n");
        chan.write("\n");

        loopStrings = Peak.getSTAR3SpectralTransitionStrings();
        chan.write("loop_\n");
        for (String loopString : loopStrings) {
            chan.write(loopString + "\n");
        }
        chan.write("\n");

        int index = 1;
        for (int i = 0; i < nPeaks; i++) {
            Peak peak = peakList.getPeak(i);
            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            PeakDim[] peakDims = peak.getPeakDims();
            for (PeakDim peakDim : peakDims) {
                Multiplet multiplet = peakDim.getMultiplet();
                if (multiplet != null) {
                    Coupling coupling = multiplet.getCoupling();
                    if ((coupling instanceof ComplexCoupling complexCoupling)) {
                        for (AbsMultipletComponent comp : complexCoupling.getAbsComponentList()) {
                            String value = peak.toSTAR3LoopSpectralTransitionString(index++);
                            chan.write(value);
                            chan.write('\n');
                        }
                    }
                }
            }
        }
        chan.write("stop_\n");

        loopStrings = Peak.getSTAR3SpectralTransitionCharStrings();
        chan.write("loop_\n");
        for (String loopString : loopStrings) {
            chan.write(loopString + "\n");
        }
        chan.write("\n");

        index = 1;
        for (int i = 0; i < nPeaks; i++) {
            Peak peak = peakList.getPeak(i);
            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            PeakDim[] peakDims = peak.getPeakDims();
            for (PeakDim peakDim : peakDims) {
                Multiplet multiplet = peakDim.getMultiplet();
                if (multiplet != null) {
                    Coupling coupling = multiplet.getCoupling();
                    if ((coupling instanceof ComplexCoupling complexCoupling)) {
                        for (AbsMultipletComponent comp : complexCoupling.getAbsComponentList()) {
                            String value = peakDim.toSTAR3LoopSpectralTransitionCharString(comp, index);
                            chan.write(value);
                            chan.write('\n');
                            index++;
                        }
                    }
                }
            }
        }
        chan.write("stop_\n");
        chan.write("\n");

        loopStrings = Peak.getSTAR3SpectralTransitionGeneralCharStrings();
        chan.write("loop_\n");
        for (String loopString : loopStrings) {
            chan.write(loopString + "\n");
        }
        chan.write("\n");

        index = 1;
        for (int i = 0; i < nPeaks; i++) {
            Peak peak = peakList.getPeak(i);
            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            PeakDim[] peakDims = peak.getPeakDims();
            for (PeakDim peakDim : peakDims) {
                Multiplet multiplet = peakDim.getMultiplet();
                if (multiplet != null) {
                    Coupling coupling = multiplet.getCoupling();
                    if ((coupling instanceof ComplexCoupling complexCoupling)) {
                        for (AbsMultipletComponent comp : complexCoupling.getAbsComponentList()) {
                            String value = peakDim.toSTAR3LoopSpectralTransitionGeneralCharString(comp, index, true);
                            chan.write(value);
                            chan.write('\n');
                            value = peakDim.toSTAR3LoopSpectralTransitionGeneralCharString(comp, index, false);
                            chan.write(value);
                            chan.write('\n');
                            index++;
                        }
                    }
                }
            }
        }
        chan.write("stop_\n");
        chan.write("\n");

        loopStrings = Peak.getSTAR3CouplingPatternStrings();
        chan.write("loop_\n");
        for (String loopString : loopStrings) {
            chan.write(loopString + "\n");
        }
        chan.write("\n");
        index = 1;
        for (int i = 0; i < nPeaks; i++) {
            Peak peak = peakList.getPeak(i);
            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            PeakDim[] peakDims = peak.getPeakDims();
            for (PeakDim peakDim : peakDims) {
                Multiplet multiplet = peakDim.getMultiplet();
                if (multiplet != null) {
                    Coupling coupling = multiplet.getCoupling();
                    if ((coupling instanceof CouplingPattern)) {
                        List<String> values = peakDim.toSTAR3CouplingPatternString(index);
                        for (String value : values) {
                            chan.write(value);
                            chan.write('\n');
                            index++;
                        }
                    }
                }
            }
        }
        chan.write("stop_\n");

        chan.write("\nsave_\n\n");
    }

    public void writePeaksToXML(Writer chan, PeakList peakList) throws IOException, IllegalArgumentException, InvalidPeakException {
        int i;
        if (chan == null) {
            throw new IllegalArgumentException("Channel null");
        }
        int nPeaks = peakList.size();
        for (i = 0; i < nPeaks; i++) {
            Peak peak = peakList.getPeak(i);
            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            chan.write(peak.toXMLString() + "\n");
        }
    }

    public void writePeaksToSparky(Writer chan, PeakList peakList) throws IOException, IllegalArgumentException, InvalidPeakException {
        /*
                  Assignment       w1      w2      w3   Data Height
  
     ?-?-?  125.395   55.758    8.310      2164733.500
     ?-?-?  122.041   54.953    8.450      1275542.375

)*/
        if (chan == null) {
            throw new IllegalArgumentException("Channel null");
        }
        chan.write(peakList.getSparkyHeader());
        chan.write("\n");
        int nPeaks = peakList.size();
        for (int i = 0; i < nPeaks; i++) {
            Peak peak = peakList.getPeak(i);
            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + i);
            }
            chan.write(peak.toSparkyString());
            chan.write("\n");
        }
    }

    public enum PIPEDIMS {
        AXIS("_AXIS", "%9.3f"),
        D("D", "%6.3f"),
        PPM("_PPM", "%8.3f"),
        HZ("_HZ", "%9.3f"),
        W("W", "%7.3f"),
        W_HZ("W_HZ", "%8.3f"),
        ONE("1", "%4d"),
        THREE("3", "%4d");
        final String label;
        final String format;

        PIPEDIMS(String label, String format) {
            this.label = label;
            this.format = format;
        }
    }
    public void writePeakstoNMRPipe(Writer chan, PeakList peakList) throws IOException, InvalidPeakException {
        DatasetBase dataset = DatasetBase.getDataset(peakList.getDatasetName());
        String[] labels = {"X", "Y", "Z", "A", "B", "C"};
        int nDim = peakList.getNDim();
        for (int iDim = 0; iDim < nDim; iDim++) {
            SpectralDim spectralDim = peakList.getSpectralDim(iDim);
            int size = dataset.size(iDim);
            double ppm0 = dataset.pointToPPM(iDim, 0);
            double ppm1 = dataset.pointToPPM(iDim, size - 1.0);
            String dataLine = String.format("DATA %s_AXIS %s %d %d %8.3fppm %8.3fppm",
                    labels[iDim], spectralDim.getDimName(),  1, size, ppm0, ppm1);
            chan.write(dataLine + "\n");
        }


        /*
        DATA  X_AXIS HN           1   659   10.297ppm    5.798ppm
DATA  Y_AXIS 15N          1  1024  129.088ppm  107.091ppm
DATA  Z_AXIS CA           1   512   69.128ppm   41.069ppm

         */


        /*
        VARS   INDEX X_AXIS Y_AXIS Z_AXIS DX DY DZ X_PPM Y_PPM Z_PPM X_HZ Y_HZ Z_HZ XW YW ZW XW_HZ YW_HZ ZW_HZ X1 X3 Y1 Y3 Z1 Z3 HEIGHT DHEIGHT VOL PCHI2 TYPE ASS CLUSTID MEMCNT
FORMAT %5d %9.3f %9.3f %9.3f %6.3f %6.3f %6.3f %8.3f %8.3f %8.3f %9.3f %9.3f %9.3f %7.3f %7.3f %7.3f %8.3f %8.3f %8.3f %4d %4d %4d %4d %4d %4d %+e %+e %+e %.5f %d %s %4d %4d

         */
        StringBuilder stringBuilder = new StringBuilder();
        StringBuilder stringBuilderF = new StringBuilder();
        stringBuilder.append("VARS  INDEX ");
        stringBuilderF.append("FORMAT %5d ");
        for (PIPEDIMS type: PIPEDIMS.values()) {
            for (int i = 0; i < nDim; i++) {
                if (type == D) {
                    stringBuilder.append(type.label).append(labels[i]).append(" ");
                } else {
                    stringBuilder.append(labels[i]).append(type.label).append(" ");
                }
                stringBuilderF.append(type.format).append(" ");
            }
        }
        stringBuilder.append("HEIGHT DHEIGHT VOL PCHI2 TYPE ASS CLUSTID MEMCNT");
        stringBuilderF.append(" %+e %+e %+e %.5f %d %s %4d %4d");
        chan.write(stringBuilder + "\n");
        chan.write(stringBuilderF +"\n");
        int nPeaks = peakList.size();
        for (int iPeak = 0; iPeak < nPeaks; iPeak++) {
            Peak peak = peakList.getPeak(iPeak);

            if (peak == null) {
                throw new InvalidPeakException("PeakList.writePeaks: peak null at " + iPeak);
            }
            StringBuilder sBuilderPeak = new StringBuilder();
            sBuilderPeak.append(String.format("%d ", iPeak + 1));
            for (PIPEDIMS type : PIPEDIMS.values()) {
                for (int iDim = 0; iDim < nDim; iDim++) {
                    PeakDim peakDim = peak.getPeakDim(iDim);
                    double ppm = peakDim.getChemShiftValue();
                    double pt = dataset.ppmToDPoint(iDim, ppm);
                    double wHz = peakDim.getLineWidthHz();
                    double wPt = dataset.hzWidthToPoints(iDim, wHz);
                    double boundsHz = peakDim.getBoundsHz();
                    double bWidth = dataset.hzWidthToPoints(iDim, boundsHz);
                    double hz = dataset.pointToHz(iDim, pt);
                    int one = (int) Math.floor(pt - bWidth / 2.0);
                    int three = (int) Math.ceil(pt + bWidth / 2.0);
                    String result = switch (type) {
                        case AXIS -> String.format(type.format, pt);
                        case D -> String.format(type.format, wPt);
                        case PPM -> String.format(type.format, ppm);
                        case HZ -> String.format(type.format, hz);
                        case W -> String.format(type.format, wPt);
                        case W_HZ -> String.format(type.format, wHz);
                        case ONE -> String.format(type.format, one);
                        case THREE -> String.format(type.format, three);
                    };
                    sBuilderPeak.append(result).append(" ");
                }
            }
            sBuilderPeak.append(String.format("%+e ", peak.getIntensity()));
            sBuilderPeak.append(String.format("%+e ", peak.getIntensity()));
            sBuilderPeak.append(String.format("%+e ", peak.getVolume1()));
            sBuilderPeak.append(String.format("%.5f ", 1.0));
            sBuilderPeak.append(String.format("%d ", 1));
            sBuilderPeak.append(String.format("%s ", "None"));
            sBuilderPeak.append(String.format("%4d ", iPeak + 1));
            sBuilderPeak.append(String.format("%4d", 1));
            chan.write(sBuilderPeak + "\n");
        }
    }

}
