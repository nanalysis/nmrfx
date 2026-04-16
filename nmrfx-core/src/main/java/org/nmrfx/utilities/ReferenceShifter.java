package org.nmrfx.utilities;

import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.peaks.PeakDim;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.SpectralDim;

public class ReferenceShifter {

    public static void shiftRefByNucleus(String nucName, double delta) {
        Nuclei nucleus = Nuclei.findNuclei(nucName);
        DatasetBase.datasets().stream().forEach(datasetBase -> {
            for (int iDim = 0; iDim < datasetBase.getNDim(); iDim++) {
                if (datasetBase.getNucleus(iDim) == nucleus) {
                    shiftRef(datasetBase, datasetBase.getLabel(iDim), delta);
                }
            }
        });
    }
    public static void shiftRefByDimName(String dimName, double delta) {
        DatasetBase.datasets().stream().forEach(datasetBase -> {
            for (int iDim = 0; iDim < datasetBase.getNDim(); iDim++) {
                String dataDimName = datasetBase.getLabel(iDim);
                if (dataDimName.equals(dimName)) {
                    shiftRef(datasetBase, dataDimName, delta);
                }
            }
        });
    }

    public static void shiftRef(DatasetBase dataset, String dimName, double delta) {
        int dDim = dataset.getDim(dimName);
        dataset.setRefValue(dDim, dataset.getRefValue(dDim) + delta);
        PeakList.peakLists().stream()
                .filter(peakList -> !peakList.getDatasetName().isEmpty() && peakList.getDatasetName().equals(dataset.getName()))
                .forEach(peakList -> {
                    SpectralDim spectralDim = peakList.getSpectralDim(dimName);
                    if (spectralDim != null) {
                        peakList.peaks().forEach(peak -> {
                            PeakDim peakDim = peak.getPeakDim(spectralDim.getIndex());
                            peakDim.setChemShiftValue(peakDim.getChemShiftValue() + (float) delta);
                        });
                    }
                });
    }
}
