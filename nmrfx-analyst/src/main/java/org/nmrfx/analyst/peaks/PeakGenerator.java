package org.nmrfx.analyst.peaks;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.peaks.CouplingPattern;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.structure.chemistry.CouplingList;
import org.nmrfx.structure.chemistry.JCoupling;
import org.nmrfx.structure.chemistry.Molecule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PeakGenerator {

    public static void generate1DProton(Dataset dataset, String listName) {
        PeakList peakList = makePeakList(dataset, listName);
        generate1DProton(peakList);
    }

    public static PeakList makePeakList(Dataset dataset, String listName) {
        PeakList peakList = new PeakList(listName, dataset.getNDim());
        for (int i = 0; i < peakList.getNDim(); i++) {
            peakList.getSpectralDim(i).setSw(dataset.getSw(i));
            peakList.getSpectralDim(i).setSf(dataset.getSf(i));
            peakList.getSpectralDim(i).setDimName(dataset.getLabel(i));
        }
        return peakList;
    }

    public static void generate1DProton(PeakList peaklist) {
        Molecule molecule = (Molecule) MoleculeFactory.getActive();
        var couplingList = new CouplingList();
        var entities = molecule.getCompoundsAndResidues();
        Map<Atom, List<JCoupling>> couplingMap = new HashMap<>();
        for (var entity : entities) {
            couplingList.generateCouplings(entity, 3, 2, 2, 2);
            var jCouplings = couplingList.getJCouplings();
            for (var jCoupling : jCouplings) {
                int nAtoms = jCoupling.getNAtoms();
                Atom atom = jCoupling.getAtom(0);
                List<JCoupling> atomCouplings = couplingMap.get(atom);
                if (atomCouplings == null) {
                    atomCouplings = new ArrayList<>();
                    couplingMap.put(atom, atomCouplings);
                }
                atomCouplings.add(jCoupling);
            }
        }
        for (var entity : entities) {
            for (var atom : entity.getAtoms()) {
                if (atom.getAtomicNumber() == 1) {
                    var peak = peaklist.getNewPeak();
                    var peakDim = peak.getPeakDim(0);
                    peakDim.setLabel(atom.getShortName());
                    if (atom.getRefPPM() != null) {
                        peakDim.setChemShiftValue(atom.getRefPPM().floatValue());
                        var jCouplings = couplingMap.get(atom);
                        //    CouplingPattern(final Multiplet multiplet, final double[] values, final int[] n, final double intensity, final double[] sin2thetas) {
                        if ((jCouplings != null)) {
                            int nCouplings = jCouplings.size();
                            List<Double> values = new ArrayList<>();
                            List<Double> sin2thetas = new ArrayList<>();
                            List<String> types = new ArrayList<>();

                            var multiplet = peakDim.getMultiplet();
                            int i = 0;
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
                                i++;
                            }
                            var couplingPattern = new CouplingPattern(multiplet, values, types, sin2thetas, 1.0);
                            multiplet.setCoupling(couplingPattern);

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
