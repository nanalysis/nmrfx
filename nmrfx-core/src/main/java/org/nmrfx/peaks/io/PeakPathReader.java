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

import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.PeakPath;
import org.nmrfx.peaks.PeakPaths;
import org.nmrfx.star.Loop;
import org.nmrfx.star.ParseException;
import org.nmrfx.star.Saveframe;
import org.nmrfx.utilities.NvUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author brucejohnson
 */
public class PeakPathReader {

    public void processPeakPaths(Saveframe saveframe) throws ParseException {
        String listName = saveframe.getValue("_NMRFx_peak_path", "Sf_framecode");
        String units = saveframe.getLabelValue("_NMRFx_peak_path", "Units");
        String nDimString = saveframe.getValue("_NMRFx_peak_path", "Number_of_spectral_dimensions");
        String details = saveframe.getOptionalValue("_NMRFx_peak_path", "Details");
        String type = saveframe.getOptionalValue("_NMRFx_peak_path", "Type");
        String sampleLabel = saveframe.getOptionalValue("_NMRFx_peak_path", "Sample_label");
        String sampleID = saveframe.getOptionalValue("_NMRFx_peak_path", "Sample_ID");

        if (nDimString.equals("?")) {
            return;
        }
        if (nDimString.equals(".")) {
            return;
        }
        int nDim = NvUtil.toInt(nDimString);

        double[] weights = new double[nDim];
        double[] tols = new double[nDim];
        for (int i = 0; i < nDim; i++) {
            String value = null;
            int iDim = 0;
            value = saveframe.getValueIfPresent("_Fit_criteria", "Spectral_dim_ID", i);
            if (value != null) {
                iDim = NvUtil.toInt(value) - 1;

            }
            value = saveframe.getValueIfPresent("_Fit_criteria", "Spectral_dim_Scale", i);
            if (value != null) {
                weights[iDim] = NvUtil.toDouble(value);
            }
            value = saveframe.getValueIfPresent("_Fit_criteria", "Spectral_dim_Tolerance", i);
            if (value != null) {
                tols[iDim] = NvUtil.toDouble(value);
            }
        }

        Loop loop = saveframe.getLoop("_Peak_list");
        PeakPaths.PATHMODE pathMode = type.equalsIgnoreCase("pressure")
                ? PeakPaths.PATHMODE.PRESSURE : PeakPaths.PATHMODE.TITRATION;
        if (loop != null) {
            List<Integer> idColumn = loop.getColumnAsIntegerList("Spectral_peak_list_ID", 0);
            List<String> peakListLabels = loop.getColumnAsList("Spectral_peak_list_label");
            PeakPaths peakPath;
            Map<Integer, PeakList> idMap = new HashMap<>();
            if (pathMode == PeakPaths.PATHMODE.TITRATION) {
                List<Double> ligandConc = loop.getColumnAsDoubleList("Ligand_conc", 0.0);
                List<Double> macroMoleculeConc = loop.getColumnAsDoubleList("Macromolecule_conc", 0.0);
                int nConcs = idColumn.size();
                double[] binderConcs = new double[nConcs];
                double[] concentrations = new double[nConcs];
                List<PeakList> peakLists = new ArrayList<>();
                for (int iConc = 0; iConc < nConcs; iConc++) {
                    int id = idColumn.get(iConc);
                    binderConcs[iConc] = macroMoleculeConc.get(iConc);
                    concentrations[iConc] = ligandConc.get(iConc);
                    String peakListLabel = peakListLabels.get(iConc);
                    if (peakListLabel.startsWith("$")) {
                        peakListLabel = peakListLabel.substring(1);
                    }
                    PeakList peakList = PeakList.get(peakListLabel);
                    peakLists.add(peakList);
                    idMap.put(id, peakList);
                }
                peakPath = new PeakPaths(listName, peakLists, concentrations, binderConcs, weights, tols, PeakPaths.PATHMODE.TITRATION);
            } else {
                List<Double> pressureList = loop.getColumnAsDoubleList("Pressure", 0.0);
                int nPressures = idColumn.size();
                double[] pressures = new double[nPressures];
                double[] emptyArray = new double[nPressures];
                List<PeakList> peakLists = new ArrayList<>();
                for (int iPressure = 0; iPressure < nPressures; iPressure++) {
                    int id = idColumn.get(iPressure);
                    pressures[iPressure] = pressureList.get(iPressure);
                    String peakListLabel = peakListLabels.get(iPressure);
                    if (peakListLabel.startsWith("$")) {
                        peakListLabel = peakListLabel.substring(1);
                    }
                    PeakList peakList = PeakList.get(peakListLabel);
                    peakLists.add(peakList);
                    idMap.put(id, peakList);
                }
                peakPath = new PeakPaths(listName, peakLists, pressures, emptyArray, weights, tols, PeakPaths.PATHMODE.PRESSURE);

            }
            peakPath.store();

            loop = saveframe.getLoop("_Path");
            Map<Integer, PeakPath> pathMap = new HashMap<>();
            if (loop != null) {
                List<Integer> pathComponentIDColumn = loop.getColumnAsIntegerList("Index_ID", 0);
                List<Integer> pathIDColumn = loop.getColumnAsIntegerList("Path_ID", 0);
                List<Integer> peakListIDColumn = loop.getColumnAsIntegerList("Spectral_peak_list_ID", 0);
                List<Integer> peakIDColumn = loop.getColumnAsIntegerList("Peak_ID", -1);
                int lastID = -1;
                int start = 0;
                for (int i = 0, n = pathComponentIDColumn.size(); i < n; i++) {
                    int id = pathIDColumn.get(i);
                    if (((lastID != -1) && (id != lastID)) || (i == (n - 1))) {
                        if (i == (n - 1)) {
                            i++;
                        }
                        PeakPath path = makePath(peakPath, peakListIDColumn.subList(start, i), peakIDColumn.subList(start, i), idMap);
                        if (path != null) {
                            int useID = pathIDColumn.get(start);
                            pathMap.put(useID, path);
                        }
                        start = i;
                    }
                    lastID = id;

                }
            }

            loop = saveframe.getLoop("_Par");
            if (loop == null) {
                throw new ParseException("No \"_Par\" loop");
            }
            if (loop != null) {
                List<Integer> parIDColumn = loop.getColumnAsIntegerList("ID", 0);
                List<Integer> parPathIdColumn = loop.getColumnAsIntegerList("Path_ID", 0);
                List<Integer> parDimColumn = loop.getColumnAsIntegerList("Dim", 0);
                List<String> parConfirmedColumn = loop.getColumnAsList("Confirmed");
                List<String> parActiveColumn = loop.getColumnAsList("Active");
                Map<String, List<Double>> parColumns = new HashMap<>();
                Map<String, List<Double>> errColumns = new HashMap<>();
                List<String> parNames = peakPath.getBaseParNames();
                for (String parName : parNames) {
                    String useName = parName;
                    boolean hasTag = loop.hasTag(useName + "_val");
                    // translate name if older star file
                    if (parName.equals("K1") && !hasTag) {
                        useName = "K";
                    } else if (parName.equals("D1") && !hasTag) {
                        useName = "C";
                    }
                    parColumns.put(parName, loop.getColumnAsDoubleList(useName + "_val", null));
                    errColumns.put(parName, loop.getColumnAsDoubleList(useName + "_val_err", null));

                }
                int useDims = pathMode == PeakPaths.PATHMODE.PRESSURE ? nDim : 1;
                int nParsPerDim = parNames.size();
                int nPars = nParsPerDim * useDims;
                int nPaths = parIDColumn.size() / useDims;
                int i = 0;
                for (int iPath = 0; iPath < nPaths; iPath++) {
                    Double[] pars = new Double[nPars];
                    Double[] errors = new Double[nPars];
                    boolean ok = true;
                    boolean confirmed = false;
                    boolean active = false;
                    int id = parPathIdColumn.get(i);
                    if (parConfirmedColumn.get(i).equals("yes")) {
                        confirmed = true;
                    }
                    if (parActiveColumn.get(i).equals("yes")) {
                        active = true;
                    }
                    for (int iDim = 0; iDim < useDims; iDim++) {
                        if (ok) {
                            for (int iPar = 0; iPar < nParsPerDim; iPar++) {
                                List<Double> parColumn = parColumns.get(parNames.get(iPar));
                                List<Double> errColumn = errColumns.get(parNames.get(iPar));
                                if (parColumn != null) {
                                    pars[iDim * nParsPerDim + iPar] = parColumn.get(i);
                                    errors[iDim * nParsPerDim + iPar] = errColumn.get(i);
                                }
                            }
                        }
                        i++;
                    }
                    PeakPath path = pathMap.get(id);
                    if (path != null) {
                        if (ok) {
                            path.setFitParErrors(pars, errors);
                        }
                        if (confirmed) {
                            path.confirm();
                        }
                        path.setActive(active);
                    }
                }
            }
        }
    }

    PeakPath makePath(PeakPaths peakPath, List<Integer> listIDs, List<Integer> peakIDs, Map<Integer, PeakList> idMap) {
        List<Peak> peaks = new ArrayList<>();
        for (int i = 0; i < listIDs.size(); i++) {
            int listID = listIDs.get(i);
            int peakID = peakIDs.get(i);
            PeakList peakList = idMap.get(listID);
            Peak peak = peakID < 0 ? null : peakList.getPeakByID(peakID);
            if ((i==0) && (peak ==null)) {
                return null;
            }
            peaks.add(peak);
        }
        return peakPath.addPath(peaks);

    }
}
