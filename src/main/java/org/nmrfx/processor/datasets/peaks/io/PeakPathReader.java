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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.PeakPath;
import org.nmrfx.processor.datasets.peaks.PeakPath.Path;
import org.nmrfx.processor.star.Loop;
import org.nmrfx.processor.star.ParseException;
import org.nmrfx.processor.star.Saveframe;
import org.nmrfx.processor.utilities.NvUtil;

/**
 *
 * @author brucejohnson
 */
public class PeakPathReader {

    public void processPeakPaths(Saveframe saveframe) throws ParseException {
        String listName = saveframe.getValue("_NMRFx_peak_path", "Sf_framecode");
        String units = saveframe.getLabelValue("_NMRFx_peak_path", "Units");
        String nDimString = saveframe.getValue("_NMRFx_peak_path", "Number_of_spectral_dimensions");
        String details = saveframe.getOptionalValue("_NMRFx_peak_path", "Details");
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
            value = saveframe.getValueIfPresent("_Applied", "Spectral_dim_ID", i);
            if (value != null) {
                iDim = NvUtil.toInt(value) - 1;

            }
            value = saveframe.getValueIfPresent("_Applied", "Spectral_dim_Scale", i);
            if (value != null) {
                weights[iDim] = NvUtil.toDouble(value);
            }
            value = saveframe.getValueIfPresent("_Applied", "Spectral_dim_Tolerance", i);
            if (value != null) {
                tols[iDim] = NvUtil.toDouble(value);
            }
        }

        Loop loop = saveframe.getLoop("_Peak_list");
        if (loop != null) {
            List<Integer> idColumn = loop.getColumnAsIntegerList("Spectral_peak_list_ID", 0);
            List<String> peakListLabels = loop.getColumnAsList("Spectral_peak_list_label");
            List<Double> ligandConc = loop.getColumnAsDoubleList("Ligand_conc", 0.0);
            List<Double> macroMoleculeConc = loop.getColumnAsDoubleList("Macromolecule_conc", 0.0);
            int nConcs = idColumn.size();
            double[] binderConcs = new double[nConcs];
            double[] concentrations = new double[nConcs];
            List<PeakList> peakLists = new ArrayList<>();
            Map<Integer, PeakList> idMap = new HashMap<>();
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
            PeakPath peakPath = new PeakPath(listName, peakLists, concentrations, binderConcs, weights, tols, PeakPath.PATHMODE.TITRATION);
            peakPath.store();

            loop = saveframe.getLoop("_Path");
            Map<Integer, Path> pathMap = new HashMap<>();
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
                        Path path = makePath(peakPath, peakListIDColumn.subList(start, i), peakIDColumn.subList(start, i), idMap);
                        int useID = pathIDColumn.get(start);
                        pathMap.put(useID, path);
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
                int nPars = 2;
                List<Integer> parIDColumn = loop.getColumnAsIntegerList("ID", 0);
                List<Integer> parPathIdColumn = loop.getColumnAsIntegerList("Path_ID", 0);
                List<Integer> parDimColumn = loop.getColumnAsIntegerList("Dim", 0);
                List<String> parConfirmedColumn = loop.getColumnAsList("Confirmed");
                List<String> parActiveColumn = loop.getColumnAsList("Active");
                List<List<Double>> parColumns = new ArrayList<>();
                List<List<Double>> errColumns = new ArrayList<>();
                int jPar = 0;
                for (String parName : peakPath.getBaseParNames()) {
                    parColumns.add(loop.getColumnAsDoubleList(parName + "_val", 0.0));
                    errColumns.add(loop.getColumnAsDoubleList(parName + "_val_err", 0.0));
                    jPar++;
                }
                int nPaths = parIDColumn.size();
                System.out.println("npaths " + nPaths);
                for (int i = 0; i < nPaths; i++) {
                    double[] pars = new double[nPars];
                    double[] errors = new double[nPars];
                    for (int iPar = 0; iPar < nPars; iPar++) {
                        pars[iPar] = parColumns.get(iPar).get(i);
                        errors[iPar] = errColumns.get(iPar).get(i);
                    }
                    int id = parPathIdColumn.get(i);
                    Path path = pathMap.get(id);
                    path.setFitPars(pars);
                    path.setFitErrs(errors);
                    if (parConfirmedColumn.get(i).equals("yes")) {
                        path.confirm();
                    }
                    if (parActiveColumn.get(i).equals("yes")) {
                        path.setActive(true);
                    }
                }
            }
        }
    }

    Path makePath(PeakPath peakPath, List<Integer> listIDs, List<Integer> peakIDs, Map<Integer, PeakList> idMap) {
        List<Peak> peaks = new ArrayList<>();
        for (int i = 0; i < listIDs.size(); i++) {
            int listID = listIDs.get(i);
            int peakID = peakIDs.get(i);
            PeakList peakList = idMap.get(listID);
            Peak peak = peakID < 0 ? null : peakList.getPeakByID(peakID);
            peaks.add(peak);
        }
        return peakPath.addPath(peaks);

    }
}
