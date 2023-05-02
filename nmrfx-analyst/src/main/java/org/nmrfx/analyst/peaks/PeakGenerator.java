package org.nmrfx.analyst.peaks;

import org.nmrfx.chemistry.*;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.peaks.CouplingPattern;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.structure.chemistry.CouplingList;
import org.nmrfx.structure.chemistry.JCoupling;
import org.nmrfx.structure.chemistry.Molecule;
import org.python.util.PythonInterpreter;

import java.util.*;

public class PeakGenerator {
    static final String[][] PATTERN_HSQC_15N = {{"H", "N"}};
    static final String[][] PATTERN_HSQC_13C = {{"H", "C*"}};
    static final String[][] PATTERN_HNCACO = {{"H", "N", "C"}, {"H", "N", "-1.C"}};
    static final String[][] PATTERN_HNCO = {{"H", "N", "-1.C"}};
    static final String[][] PATTERN_HNCOCA = {{"H", "N", "-1.CA"}};
    static final String[][] PATTERN_HNCOCACB = {{"H", "N", "-1.CB"}, {"H", "N", "-1.CA"}};
    static final String[][] PATTERN_HNCA = {{"H", "N", "CA"}, {"H", "N", "-1.CA"}};
    static final String[][] PATTERN_HNCACB = {{"H", "N", "CB"}, {"H", "N", "-1.CB"}, {"H", "N", "CA"}, {"H", "N", "-1.CA"}};

    public enum PeakGeneratorTypes {
        Proton_1D,
        HSQC_13C(PATTERN_HSQC_13C),
        HSQC_15N(PATTERN_HSQC_15N),
        HMBC,
        TOCSY,
        NOESY,
        RNA_NOESY_2nd_str,
        HNCO(PATTERN_HNCO),
        HNCOCA(PATTERN_HNCOCA),
        HNCOCACB(PATTERN_HNCOCACB),
        HNCACO(PATTERN_HNCACO),
        HNCA(PATTERN_HNCA),
        HNCACB(PATTERN_HNCACB);

        final String[][] pattern;

        PeakGeneratorTypes() {
            this.pattern = null;
        }

        PeakGeneratorTypes(String[][] pattern) {
            this.pattern = pattern;
        }
    }


    Molecule molecule = (Molecule) MoleculeFactory.getActive();
    Map<Atom, List<JCoupling>> jCouplingMap = null;
    Map<Atom, List<JCoupling>> tocsyCouplingMap = null;
    Map<Atom, List<JCoupling>> hmbcCouplingMap = null;
    int assignIndex;
    int refIndex;

    public PeakGenerator(int assignIndex, int refIndex) {
        this.assignIndex = assignIndex;
        this.refIndex = refIndex;
    }

    private PPMv getPPM(Atom atom) {
        PPMv ppmV = null;
        if (assignIndex >= 0) {
            ppmV = atom.getPPM(assignIndex);
        }
        if ((ppmV == null) || !ppmV.isValid()) {
            ppmV = atom.getRefPPM(refIndex);
        }
        return ppmV;
    }

    void addPeak(PeakList peakList, double intensity, Atom... atoms) {
        int nAtoms = atoms.length;
        double[] ppms = new double[nAtoms];
        double[] eppms = new double[nAtoms];
        double[] widths = new double[nAtoms];
        double[] bounds = new double[nAtoms];
        String[] names = new String[nAtoms];
        boolean ok = true;
        for (int i = 0; i < nAtoms; i++) {
            PPMv ppmV = getPPM(atoms[i]);
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

    public PeakList makePeakList(DatasetBase dataset) {
        String listName = PeakList.getNameForDataset(dataset.getName());
        listName = listName + "_sim";
        return makePeakList(listName, dataset, dataset.getNDim());
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
        peakList.setScale(1.0);

        return peakList;
    }

    void generateCouplings() {
        jCouplingMap = new HashMap<>();
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
        }
    }

    void generateTocsyCouplings(int limit) {
        tocsyCouplingMap = new HashMap<>();
        var entities = molecule.getCompoundsAndResidues();
        int nShells = Math.max(2, limit);
        for (var entity : entities) {
            CouplingList couplingList = new CouplingList();
            couplingList.generateCouplings(entity, nShells, 2, limit, 2);
            var tocsyLinks = couplingList.getTocsyLinks();
            for (var tocsyLink : tocsyLinks) {
                var atom = tocsyLink.getAtom(0);
                List<JCoupling> atomCouplings = tocsyCouplingMap.computeIfAbsent(atom, k -> new ArrayList<>());
                atomCouplings.add(tocsyLink);
            }
        }
    }

    void generateHMBCCouplings(int limit) {
        hmbcCouplingMap = new HashMap<>();
        var entities = molecule.getCompoundsAndResidues();
        int nShells = Math.max(2, limit);
        for (var entity : entities) {
            CouplingList couplingList = new CouplingList();
            couplingList.generateCouplings(entity, nShells, 2, 2, limit);
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
                    PPMv ppmV = getPPM(atom);
                    if ((ppmV != null) && ppmV.isValid()) {
                        var peak = peakList.getNewPeak();
                        float intensity = atom.isMethyl() ? 3.0f : 1.0f;
                        peak.setIntensity(intensity);
                        var peakDim = peak.getPeakDim(0);
                        peakDim.setLabel(atom.getShortName());
                        peakDim.setChemShiftValue((float) ppmV.getValue());
                        peakDim.setLineWidthHz(1.5f);
                        peak.setVolume1(intensity);
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

    public void generateHMBC(PeakList peakList, int limit) {
        generateHMBCCouplings(limit);
        Atom[] atoms = new Atom[2];
        molecule.getAtoms().stream()
                .filter(atom -> atom.getAtomicNumber() == 1)
                .filter(atom -> !atom.isMethyl() || atom.isFirstInMethyl())
                .forEach(atom -> {
                    var hmbcCouplings = hmbcCouplingMap.get(atom);
                    if ((hmbcCouplings != null)) {
                        for (var hmbcCoupling : hmbcCouplings) {
                            atoms[0] = hmbcCoupling.getAtom(0);
                            atoms[1] = hmbcCoupling.getAtom(1);
                            double intensity = atom.isMethyl() ? 3.0 : 1.0;
                            addPeak(peakList, intensity, atoms);
                        }
                    }
                });
    }


    public void generateTOCSY(PeakList peakList, int limit) {
        generateTocsyCouplings(limit);
        Atom[] atoms = new Atom[2];
        molecule.getAtoms().stream()
                .filter(atom -> atom.getAtomicNumber() == 1)
                .filter(atom -> !atom.isMethyl() || atom.isFirstInMethyl())
                .forEach(atom -> {
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
        molecule.getAtoms().stream()
                .filter(atom -> atom.getAtomicNumber() == 1)
                .filter(atom -> !atom.isMethyl() || atom.isFirstInMethyl())
                .filter(atom -> atom.getParent() != null && atom.getParent().getAtomicNumber() == parentElement)
                .forEach(atom -> {
                    atoms[0] = atom;
                    atoms[1] = atom.getParent();
                    double intensity = atom.isMethyl() ? 3.0 : 1.0;
                    addPeak(peakList, intensity, atoms);
                });
    }

    /*
        def genDistancePeaks(self, dataset, listName="", condition="sim", scheme="", tol=5.0):
        self.setWidths([self.widthH, self.widthH])
        if dataset != None and dataset != "":
            if not isinstance(dataset,DatasetBase):
                dataset = DatasetBase.getDataset(dataset)
            labelScheme = dataset.getProperty("labelScheme")
            self.setLabelScheme(labelScheme)
            if scheme == "":
                scheme = dataset.getProperty("editScheme")
        if scheme == "":
            scheme = "aa"

        (d1Edited, d2Edited) = editingModes[scheme]
        peakList = self.getPeakList(dataset, listName)
        self.mol.selectAtoms("*.H*")
        protonPairs = self.mol.getDistancePairs(tol, False)
        for protonPair in protonPairs:
            dis = protonPair.getDistance()
            if dis > 4.0:
                volume = 0.2
            elif dis > 3.0:
                volume = 0.5
            else:
                volume = 1.0
            intensity = volume
            self.addProtonPairPeak(peakList, protonPair.getAtom1(), protonPair.getAtom2(), intensity, d1Edited, d2Edited)
            self.addProtonPairPeak(peakList, protonPair.getAtom2(), protonPair.getAtom1(), intensity, d1Edited, d2Edited)
        return peakList

     */

    public void generateNOESY(PeakList peakList, double tol) throws InvalidMoleculeException {
        int nDim = peakList.getNDim();
        Atom[] atoms = new Atom[nDim];
        molecule.selectAtoms("*.H*");
        var protonPairs = molecule.getDistancePairs(tol, false);
        int[] indices = new int[atoms.length];
        Arrays.fill(indices, -1);
        int aNum = 1;
        for (int i = 0; i < nDim; i++) {
            var sDim = peakList.getSpectralDim(i);
            if (sDim.getNucleus().equals("1H")) {
                if (indices[0] == -1) {
                    indices[0] = i;
                } else {
                    indices[1] = i;
                }
            } else if (sDim.getNucleus().equals("15N")) {
                indices[2] = i;
                aNum = 7;
            } else if (sDim.getNucleus().equals("13C")) {
                indices[2] = i;
                aNum = 6;
            }
        }
        if (indices[1] == -1) {
            throw new IllegalArgumentException("Can't find two proton dimensions");
        }

        final int testANum = aNum;
        double exponent = -5.0;
        double scale = Math.pow(2.0, exponent);
        protonPairs.forEach(aP -> {
            atoms[indices[0]] = aP.getAtom1();
            atoms[indices[1]] = aP.getAtom2();
            final boolean ok;
            if (indices.length == 2) {
                ok = true;
            } else {
                if ((indices[2] != -1)) {
                    atoms[indices[2]] = atoms[indices[0]].getParent();
                    ok = (atoms[indices[2]] != null) && (atoms[indices[2]].getElementNumber() == testANum);
                } else {
                    ok = false;
                }
            }
            if (ok) {
                double distance = Math.max(2.0, Math.abs(aP.getDistance()));
                double intensity = 100.0 * Math.pow(distance, exponent) / scale;
                addPeak(peakList, intensity, atoms);
            }
        });
    }

    public void generateRNANOESYSecStr(Dataset dataset, PeakList peakList, int useN) {
        String script = String.format("molGen.genRNASecStrPeaks(datasetName, listName=listName, useN=%d)", useN);
        try (PythonInterpreter interp = new PythonInterpreter()) {
            interp.exec("import molpeakgen");
            interp.exec("molGen=molpeakgen.MolPeakGen()");
            interp.set("datasetName", dataset.getName());
            interp.set("listName", peakList.getName());
            interp.exec(script);
        }
    }

    public void generateProteinPeaks(DatasetBase dataset, PeakList peakList, PeakGeneratorTypes expType) {
        String[][] specifiers = expType.pattern;
        if (specifiers == null) {
            throw new IllegalArgumentException("Invalid experiment type " + expType);
        }
        generateProteinPeaks(dataset, peakList, specifiers);
    }

    public void generateProteinPeaks(DatasetBase dataset, PeakList peakList, String[][] specifiers) {
        int nDim = peakList.getNDim();
        List<String> nucNames = new ArrayList<>();
        for (int i = 0; i < nDim; i++) {
            String nucName;
            if (dataset == null) {
                nucName = peakList.getSpectralDim(i).getDimName().substring(0, 1);
            } else {
                nucName = dataset.getNucleus(i).getName();
            }
            nucNames.add(nucName);
        }

        for (var polymer : molecule.getPolymers()) {
            for (var residue : polymer.getResidues()) {
                boolean firstSpec = true;
                for (String[] specifier : specifiers) {
                    addResiduePeak(peakList, residue, nucNames, specifier, firstSpec);
                    firstSpec = false;
                }
            }
        }
    }

    private Atom getAtom(Residue residue, String atomSpec) {
        int dot = atomSpec.indexOf(".");
        String aName = dot == -1 ? atomSpec : atomSpec.substring(dot + 1);
        Residue atomResidue = residue;
        if (dot != -1) {
            String resSpec = atomSpec.substring(0, dot);
            if (resSpec.equals("-1")) {
                atomResidue = residue.getPrevious();
            } else if (resSpec.equals("1")) {
                atomResidue = residue.getNext();
            }
        }
        return atomResidue != null ? atomResidue.getAtom(aName) : null;
    }

    private void addResiduePeak(PeakList peakList, Residue residue,
                                List<String> nucNames, String[] specifier, boolean firstSpec) {
        int nDim = peakList.getNDim();
        Atom[] atoms = new Atom[nDim];
        boolean ok = true;
        for (String atomSpec : specifier) {
            Atom atom = getAtom(residue, atomSpec);
            if (atom == null) {
                ok = false;
                break;
            }
            String eName = atom.getElementName();
            int eIndex = nucNames.indexOf(eName);
            if (eIndex == -1) {
                ok = false;
                break;
            }
            atoms[eIndex] = atom;
        }
        if (ok) {
            double intensity;
            if (firstSpec) {
                intensity = 1.0;
            } else {
                intensity = 0.5;
            }
            addPeak(peakList, intensity, atoms);
        }
    }
}
