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
package org.nmrfx.processor.datasets.peaks.io;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import org.nmrfx.processor.datasets.peaks.InvalidPeakException;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.Resonance;
import org.nmrfx.processor.datasets.peaks.SpectralDim;

/**
 *
 * @author Bruce Johnson
 */
public class PeakWriter {

    static final String[] XPKDIMSTRINGS = {
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
    static final String[] NEF_PEAK_DIM_STRINGS = {"_nef_spectrum_dimension.dimension_id",
        "_nef_spectrum_dimension.axis_unit",
        "_nef_spectrum_dimension.axis_code",
        "_nef_spectrum_dimension.spectrometer_frequency",
        "_nef_spectrum_dimension.spectral_width",
        "_nef_spectrum_dimension.value_first_point",
        "_nef_spectrum_dimension.folding",
        "_nef_spectrum_dimension.absolute_peak_positions",
        "_nef_spectrum_dimension.is_acquisition"};

    // //     1   ppm   1H    500.13   4.998700337912143    9.898700337912143    circular   true   true
    static final String[] NEF_PEAK_ROW_STRINGS = {"_nef_peak.ordinal",
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
    static String[] ASSIGNED_PEAK_CHEMSHIFT_STRINGS = {
        "_Assigned_peak_chem_shift.Peak_ID",
        "_Assigned_peak_chem_shift.Spectral_dim_ID",
        "_Assigned_peak_chem_shift.Val",
        "_Assigned_peak_chem_shift.Resonance_ID",
        "_Assigned_peak_chem_shift.Spectral_peak_list_ID",};

    public static void writePeaksXPK2(String fileName, PeakList peakList) throws IOException, InvalidPeakException {
        try (FileWriter writer = new FileWriter(fileName)) {
            PeakWriter peakWriter = new PeakWriter();
            peakWriter.writePeaksXPK2(writer, peakList);
            writer.close();
        }
    }

    public void writePeaksXPK2(FileWriter chan, PeakList peakList) throws IOException, InvalidPeakException {
        peakList.getMultiplets();  // call this to ensure that mulitplets are sorted with number starting at 0

        Map<String, String> properties = peakList.getProperties();
        chan.write("peaklist\tdataset\tndim\tcondition\tscale");
        StringBuilder propBuilder = new StringBuilder();
        for (String propName : properties.keySet()) {
            String propValue = properties.get(propName);
            if (propValue.length() > 0) {
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
        if (propBuilder.length() > 0) {
            sBuilder.append(propBuilder.toString());
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

    public void writePeakMeasures(FileWriter chan, PeakList peakList) throws IOException, InvalidPeakException {
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
                    int nMeasure = peak.getMeasures().get().length;
                    double[] xValues = null;
                    if (peakList.hasMeasures()) {
                        xValues = peakList.getMeasureValues();
                    }
                    for (int j = 0; j < nMeasure; j++) {
                        if ((xValues != null) && (xValues.length == nMeasure)) {
                            result.append(xValues[j]).append(sep);
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

    public void writePeaksXPK(FileWriter chan, PeakList peakList) throws IOException, IllegalArgumentException, InvalidPeakException {
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

    public void writePeaks(FileWriter chan, PeakList peakList) throws IOException, IllegalArgumentException, InvalidPeakException {
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

    public void writePeaksNEF(FileWriter chan, PeakList peakList) throws IOException, InvalidPeakException {
        char stringQuote = '"';
        chan.write("save_nef_nmr_spectrum_" + peakList.getName() + "\n");
        chan.write("_nef_nmr_spectrum.sf_category                 ");
        chan.write("nef_nmr_spectrum\n");
        chan.write("_nef_nmr_spectrum.sf_framecode                 ");
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

    public void writePeaksSTAR3(FileWriter chan, PeakList peakList) throws IOException, InvalidPeakException {
        char stringQuote = '"';
        chan.write("save_" + peakList.getName() + "\n");
        chan.write("_Spectral_peak_list.Sf_category                 ");
        chan.write("spectral_peak_list\n");
        chan.write("_Spectral_peak_list.Sf_framecode                 ");
        chan.write(peakList.getName() + "\n");
        chan.write("_Spectral_peak_list.ID                          ");
        chan.write(peakList.getId() + "\n");
        chan.write("_Spectral_peak_list.Data_file_name               ");
        chan.write(".\n");
        chan.write("_Spectral_peak_list.Sample_ID                   ");
        chan.write(".\n");
        chan.write("_Spectral_peak_list.Sample_label                 ");
        if (peakList.getSampleLabel().length() != 0) {
            chan.write("$" + peakList.getSampleLabel() + "\n");
        } else {
            chan.write(".\n");
        }
        chan.write("_Spectral_peak_list.Sample_condition_list_ID     ");
        chan.write(".\n");
        chan.write("_Spectral_peak_list.Sample_condition_list_label  ");
        if (peakList.getSampleConditionLabel().length() != 0) {
            chan.write("$" + peakList.getSampleConditionLabel() + "\n");
        } else {
            chan.write(".\n");
        }
        chan.write("_Spectral_peak_list.Slidable                      ");
        String slidable = peakList.isSlideable() ? "yes" : "no";
        chan.write(slidable + "\n");

        chan.write("_Spectral_peak_list.Experiment_ID                 ");
        chan.write(".\n");
        chan.write("_Spectral_peak_list.Experiment_name               ");
        if (peakList.fileName.length() != 0) {
            chan.write("$" + peakList.fileName + "\n");
        } else {
            chan.write(".\n");
        }
        chan.write("_Spectral_peak_list.Number_of_spectral_dimensions ");
        chan.write(String.valueOf(peakList.nDim) + "\n");
        chan.write("_Spectral_peak_list.Details                       ");
        if (peakList.getDetails().length() != 0) {
            chan.write(stringQuote + peakList.getDetails() + stringQuote + "\n");
        } else {
            chan.write(".\n");
        }
        chan.write("\n");
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
        chan.write("stop_\n");
        chan.write("\n");
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
                Resonance resonance = peakDim.getResonance();
                if (resonance != null) {
                    long resID = resonance.getID();
                    chan.write(peakDim.toSTAR3LoopAssignedPeakChemShiftString(iContrib++, resID) + "\n");
                }
            }
        }
        chan.write("stop_\n");
        chan.write("\nsave_\n\n");
    }

    public void writePeaksToXML(FileWriter chan, PeakList peakList) throws IOException, IllegalArgumentException, InvalidPeakException {
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
}
