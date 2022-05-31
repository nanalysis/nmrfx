package org.nmrfx.structure.tools;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.peaks.CouplingPattern;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.structure.chemistry.CouplingList;
import org.nmrfx.structure.chemistry.JCoupling;
import org.nmrfx.structure.chemistry.Molecule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PeakGenerator {


    Peak addPeak(PeakList peakList, double[] ppms, double[] eppms, double[] widths, double[] bounds, double intensity, String[] names) {
        var peak = peakList.getNewPeak();
        for (int i = 0; i < ppms.length; i++) {
            var peakDim = peak.getPeakDim(i);
            peakDim.setChemShiftValue((float) ppms[i]);
            peakDim.setChemShiftErrorValue((float) eppms[i]);
            peakDim.setLabel(names[i]);
            peakDim.setLineWidthValue((float) widths[i]);
            peakDim.setBoundsValue((float) bounds[i]);
        }
        peak.setIntensity((float) intensity);
        peak.setVolume1((float) intensity);
        return peak;
    }


    public static void generate1DProton(String listName, DatasetBase dataset) {
        PeakList peakList = makePeakList(listName, dataset, 0);
        generate1DProton(peakList);
    }

    String getListName(DatasetBase dataset, String listName, String tail) {
        if ((listName == null) || listName.isBlank()) {
            String datasetName = dataset.getName();
            if (datasetName.indexOf(".") != -1) {
                datasetName = datasetName.substring(0, datasetName.indexOf("."));
            }
            listName = datasetName + tail;
        }
        return listName;
    }

    public static PeakList makePeakList(String listName, DatasetBase dataset, int nDim) {
        if (nDim == 0) {
            nDim = dataset.getNDim();
        }
        PeakList peakList = new PeakList(listName, nDim);
        for (int i = 0; i < peakList.getNDim(); i++) {
            peakList.getSpectralDim(i).setSw(dataset.getSw(i));
            peakList.getSpectralDim(i).setSf(dataset.getSf(i));
            peakList.getSpectralDim(i).setDimName(dataset.getLabel(i));
        }
        peakList.setDatasetName(dataset.getName());
        return peakList;
    }

    public static PeakList makePeakList(String listName, String[] labels, double[] sfs, double[] sws) {
        int nDim = labels.length;
        if (sfs.length != nDim) {
            throw new IllegalArgumentException("number of labels and spectrometer frequencies is not equal");
        }
        if (sws.length != nDim) {
            throw new IllegalArgumentException("number of labels and sweep widths is not equal");
        }
        PeakList peakList = new PeakList(listName, nDim);
        for (int i = 0; i < peakList.getNDim(); i++) {
            peakList.getSpectralDim(i).setSw(sws[i]);
            peakList.getSpectralDim(i).setSf(sfs[i]);
            peakList.getSpectralDim(i).setDimName(labels[i]);
        }
        peakList.setDatasetName("peaks");
        return peakList;
    }

    public static void generate1DProton(PeakList peakList) {
        peakList.setSampleConditionLabel("sim");
        Molecule molecule = (Molecule) MoleculeFactory.getActive();
        var couplingList = new CouplingList();
        var entities = molecule.getCompoundsAndResidues();
        Map<Atom, List<JCoupling>> couplingMap = new HashMap<>();
        for (var entity : entities) {
            couplingList.generateCouplings(entity, 3, 2, 2, 2);
            var jCouplings = couplingList.getJCouplings();
            for (var jCoupling : jCouplings) {
                Atom atom = jCoupling.getAtom(0);
                List<JCoupling> atomCouplings = couplingMap.computeIfAbsent(atom, k -> new ArrayList<>());
                atomCouplings.add(jCoupling);
            }
        }
        for (var entity : entities) {
            for (var atom : entity.getAtoms()) {
                if (atom.getAtomicNumber() == 1) {
                    if (!atom.isMethyl() || (atom.isFirstInMethyl())) {
                        var peak = peakList.getNewPeak();
                        float intensity = atom.isMethyl() ? 3.0f : 1.0f;
                        peak.setIntensity(intensity);
                        var peakDim = peak.getPeakDim(0);
                        peakDim.setLabel(atom.getShortName());
                        if (atom.getRefPPM() != null) {
                            peakDim.setChemShiftValue(atom.getRefPPM().floatValue());
                            peakDim.setLineWidthHz(1.5f);
                            peakDim.setBoundsHz(3.0f);
                            var jCouplings = couplingMap.get(atom);
                            if ((jCouplings != null)) {
                                List<Double> values = new ArrayList<>();
                                List<Double> sin2thetas = new ArrayList<>();
                                List<String> types = new ArrayList<>();

                                var multiplet = peakDim.getMultiplet();
                                for (var jCoupling : couplingMap.get(atom)) {
                                    int nAtoms = jCoupling.getNAtoms();
                                    double value;
                                    if (nAtoms == 3) {
                                        value = 12.0;
                                    } else {
                                        value = 5.0;
                                    }
                                    values.add(value);
                                    types.add("d");
                                    sin2thetas.add(0.0);
                                }
                                var couplingPattern = new CouplingPattern(multiplet, values, types, sin2thetas, 1.0);
                                multiplet.setCoupling(couplingPattern);

                            }
                        }
                    }
                }
            }
        }

                /*
                        self.setWidths([self.widthH, self.widthH])
        if dataset != None and dataset != "":
            if not isinstance(dataset,DatasetBase):
                dataset = DatasetBase.getDataset(dataset)
            labelScheme = dataset.getProperty("labelScheme")
            self.setLabelScheme(labelScheme)
        peakList = self.getPeakList(dataset, listName)
        peakList.setSampleConditionLabel(condition)

        entities = self.getResiduesAndCompounds(self.mol)
        for entity in entities:
            cList = CouplingList()
            cList.generateCouplings(entity,3,2, transfers, 2)
            tLinks = cList.getTocsyLinks()
            for link in tLinks:
                a0 = link.getAtom(0)
                a1 = link.getAtom(1)
                shell = link.getShell()
                self.addPeak(peakList, [a0, a1])
        return peakList

                 */
    }
}
