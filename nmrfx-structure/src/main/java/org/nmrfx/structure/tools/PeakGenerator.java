package org.nmrfx.structure.tools;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.PPMv;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.peaks.CouplingPattern;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.structure.chemistry.CouplingList;
import org.nmrfx.structure.chemistry.JCoupling;
import org.nmrfx.structure.chemistry.Molecule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PeakGenerator {
    Molecule molecule = (Molecule) MoleculeFactory.getActive();
    Map<Atom, List<JCoupling>> jCouplingMap = null;
    Map<Atom, List<JCoupling>> tocsyCouplingMap = null;
    Map<Atom, List<JCoupling>> hmbcCouplingMap = null;
    boolean refMode = true;

    void addPeak(PeakList peakList, double intensity, Atom... atoms) {
        int nAtoms = atoms.length;
        double[] ppms = new double[nAtoms];
        double[] eppms = new double[nAtoms];
        double[] widths = new double[nAtoms];
        double[] bounds = new double[nAtoms];
        String[] names = new String[nAtoms];
        boolean ok = true;
        for (int i = 0; i < nAtoms; i++) {
            PPMv ppmV = refMode ? atoms[i].getRefPPM(0) : atoms[i].getPPM(0);
            if ((ppmV == null) || !ppmV.isValid()) {
                ok = false;
                break;
            }

            ppms[i] = ppmV.getValue();
            eppms[i] = ppmV.getError();

            widths[i] = atoms[i].getElementNumber() == 1 ? 2.0 : 5.0;
            bounds[i] = widths[i] * 2.0;
            names[i] = atoms[i].getShortName();
        }
        if (ok) {
            addPeak(peakList, ppms, eppms, widths, bounds, intensity, names);
        }
    }

    void addPeak(PeakList peakList, double[] ppms, double[] eppms, double[] widths, double[] bounds, double intensity, String[] names) {
        var peak = peakList.getNewPeak();
        for (int i = 0; i < ppms.length; i++) {
            var peakDim = peak.getPeakDim(i);
            peakDim.setChemShiftValue((float) ppms[i]);
            peakDim.setChemShiftErrorValue((float) eppms[i]);
            peakDim.setLabel(names[i]);
            peakDim.setLineWidthHz((float) widths[i]);
            peakDim.setBoundsHz((float) bounds[i]);
        }
        peak.setIntensity((float) intensity);
        peak.setVolume1((float) intensity);
    }


    public void generate1DProton(String listName, DatasetBase dataset) {
        PeakList peakList = makePeakList(listName, dataset, 0);
        generate1DProton(peakList);
    }

    String getListName(DatasetBase dataset, String listName, String tail) {
        if ((listName == null) || listName.isBlank()) {
            String datasetName = dataset.getName();
            if (datasetName.contains(".")) {
                datasetName = datasetName.substring(0, datasetName.indexOf("."));
            }
            listName = datasetName + tail;
        }
        return listName;
    }

    public PeakList makePeakList(String listName, DatasetBase dataset, int nDim) {
        listName = getListName(dataset, listName, "_gen");
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
        peakList.setSampleConditionLabel("sim");

        return peakList;
    }

    public PeakList makePeakList(String listName, String[] labels, double[] sfs, double[] sws) {
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
        peakList.setSampleConditionLabel("sim");

        return peakList;
    }

    void generateCouplings() {
        jCouplingMap = new HashMap<>();
        tocsyCouplingMap = new HashMap<>();
        var entities = molecule.getCompoundsAndResidues();
        for (var entity : entities) {
            CouplingList couplingList = new CouplingList();
            couplingList.generateCouplings(entity, 3, 2, 2, 2);
            var jCouplings = couplingList.getJCouplings();
            for (var jCoupling : jCouplings) {
                Atom atom = jCoupling.getAtom(0);
                List<JCoupling> atomCouplings = jCouplingMap.computeIfAbsent(atom, k -> new ArrayList<>());
                atomCouplings.add(jCoupling);
            }
            var tocsyLinks = couplingList.getTocsyLinks();
            for (var tocsyLink : tocsyLinks) {
                var atom = tocsyLink.getAtom(0);
                List<JCoupling> atomCouplings = tocsyCouplingMap.computeIfAbsent(atom, k -> new ArrayList<>());
                atomCouplings.add(tocsyLink);
            }
            var hmbcLinks = couplingList.getHMBCLinks();
            for (var hmbcLink : hmbcLinks) {
                var atom = hmbcLink.getAtom(0);
                List<JCoupling> atomCouplings = hmbcCouplingMap.computeIfAbsent(atom, k -> new ArrayList<>());
                atomCouplings.add(hmbcLink);
            }
        }
    }

    public void generate1DProton(PeakList peakList) {
        if (jCouplingMap == null) {
            generateCouplings();
        }

        molecule.getAtoms().stream()
                .filter(atom -> atom.getAtomicNumber() == 1)
                .filter(atom -> !atom.isMethyl() || atom.isFirstInMethyl())
                .forEach(atom -> {
                    PPMv ppmV = refMode ? atom.getRefPPM(0) : atom.getPPM(0);
                    if ((ppmV != null) && ppmV.isValid()) {
                        var peak = peakList.getNewPeak();
                        float intensity = atom.isMethyl() ? 3.0f : 1.0f;
                        peak.setIntensity(intensity);
                        var peakDim = peak.getPeakDim(0);
                        peakDim.setLabel(atom.getShortName());
                        peakDim.setChemShiftValue(atom.getRefPPM().floatValue());
                        peakDim.setLineWidthHz(1.5f);
                        peakDim.setBoundsHz(3.0f);
                        var jCouplings = jCouplingMap.get(atom);
                        if ((jCouplings != null)) {
                            List<Double> values = new ArrayList<>();
                            List<Double> sin2thetas = new ArrayList<>();
                            List<String> types = new ArrayList<>();

                            var multiplet = peakDim.getMultiplet();
                            for (var jCoupling : jCouplingMap.get(atom)) {
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

                });

    }


    public void generateTOCSY(PeakList peakList) {
        if (tocsyCouplingMap == null) {
            generateCouplings();
        }
        Atom[] atoms = new Atom[2];
        molecule.getAtoms().stream().
                filter(atom -> atom.getAtomicNumber() == 1).
                filter(atom -> !atom.isMethyl() || atom.isFirstInMethyl()).
                forEach(atom -> {
                    var tocsyCouplings = tocsyCouplingMap.get(atom);
                    if ((tocsyCouplings != null)) {
                        for (var tocsyCoupling : tocsyCouplings) {
                            atoms[0] = tocsyCoupling.getAtom(0);
                            atoms[1] = tocsyCoupling.getAtom(1);
                            double intensity = atom.isMethyl() ? 3.0 : 1.0;
                            addPeak(peakList, intensity, atoms);
                        }
                    }
                });
    }

    public void generateHSQC(PeakList peakList, int parentElement) {
        Atom[] atoms = new Atom[2];
        molecule.getAtoms().stream().
                filter(atom -> atom.getAtomicNumber() == 1).
                filter(atom -> !atom.isMethyl() || atom.isFirstInMethyl()).
                filter(atom -> atom.getParent() != null && atom.getParent().getAtomicNumber() == parentElement).
                forEach(atom -> {
                            atoms[0] = atom;
                            atoms[1] = atom.getParent();
                            double intensity = atom.isMethyl() ? 3.0 : 1.0;
                            addPeak(peakList, intensity, atoms);
                });
    }
}
