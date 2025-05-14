package org.nmrfx.structure.chemistry.predict;

import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.NMRStarReader;
import org.nmrfx.chemistry.io.Sequence;
import org.nmrfx.star.ParseException;
import org.nmrfx.structure.chemistry.Molecule;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class RNAProteinPredictorTest {

    @Test
    public void predictProtein() throws MoleculeIOException, IOException, InvalidMoleculeException {
        Molecule.removeAll();
        Sequence sequence = new Sequence();
        var molecule = (Molecule) sequence.read("A", List.of("gly", "glu", "phe", "glu", "ile", "asn", "ser", "arg"), ".");
        Polymer polymer = molecule.getPolymers().getFirst();
        Residue residue = polymer.getResidues().get(2);
        ProteinPredictor proteinPredictor = new ProteinPredictor();
        proteinPredictor.init(molecule, 0);
        proteinPredictor.predict(residue, 0, 0);
        double ppmCA = residue.getAtom("CA").getPPM();
        Assert.assertEquals(57.5, ppmCA, 0.1);
        Molecule.removeAll();
    }

    @Test
    public void predictProteinMet() throws MoleculeIOException, IOException, InvalidMoleculeException {
        Molecule.removeAll();
        Sequence sequence = new Sequence();
        var molecule = (Molecule) sequence.read("A", List.of("met", "met", "met", "met", "ile", "asn", "ser", "arg"), ".");
        Polymer polymer = molecule.getPolymers().getFirst();
        Residue residue = polymer.getResidues().get(2);
        ProteinPredictor proteinPredictor = new ProteinPredictor();
        proteinPredictor.init(molecule, 0);
        proteinPredictor.predict(residue, 0, 0);
        double ppmCA = residue.getAtom("CG").getPPM();
        Assert.assertEquals(32, ppmCA, 0.5);
        Molecule.removeAll();
    }

    @Test
    public void predictProtein2() throws MoleculeIOException, IOException, InvalidMoleculeException {
        Molecule.removeAll();
        ProteinPredictor.initMinMax();
        Sequence sequence = new Sequence();
        var molecule = (Molecule) sequence.read("A", List.of("ala", "arg", "asn", "asp", "cys", "gln", "glu", "gly", "his",
                "ile", "leu", "lys", "met", "phe", "pro", "ser", "trp", "tyr"
        ), ".");
        ProteinPredictor proteinPredictor = new ProteinPredictor();
        proteinPredictor.init(molecule, 0);
        StringBuilder stringBuilder = new StringBuilder();
        List<String> skipList = List.of("ARG_CZ", "ARG_NH2", "ARG_NH1", "ASN_CG", "ASP_CG", "GLN_CD", "GLU_CD",
                "HIS_CG", "LYS_NZ", "PHE_CG", "PRO_N", "TRP_CG", "TYR_CG", "TYR_CZ");
        for (Residue residue : molecule.getPolymers().getFirst().getResidues()) {
            proteinPredictor.predict(residue, 0, 0);
            for (Atom atom : residue.getAtoms()) {
                int aNum = atom.getAtomicNumber();
                boolean exchangeable = (aNum == 1) && (atom.parent != null) && ((atom.parent.getAtomicNumber() == 8) ||
                        (atom.parent.getAtomicNumber() == 7) || atom.parent.getAtomicNumber() == 16);
                boolean hcn = (aNum == 1) || (aNum == 6) || (aNum == 7);
                String skipName = atom.getEntity().getName() + "_" + atom.getName();
                if (hcn && !exchangeable && !atom.getName().equals("H1") && !(atom.isMethyl() &&
                        !atom.isFirstInMethyl()) && !skipList.contains(skipName)) {
                    Double ppm = atom.getPPM();
                    if (ppm == null) {
                        stringBuilder.append("\"").append(skipName).append("\",");
                    }
                }
            }
        }
        Molecule.removeAll();
        Assert.assertEquals("", stringBuilder.toString());
    }

    @Test
    public void predict() throws MoleculeIOException, IOException {
        Sequence sequence = new Sequence();
        Molecule.removeAll();
        var molecule = (Molecule) sequence.read("A", List.of("G", "G", "U", "G", "G", "U", "G", "A", "G", "A", "G", "C", "C", "A", "C", "C"), ".");
        molecule.setDotBracket("((((((....))))))");
        RNAAttributes rnaAttributes = new RNAAttributes();

        rnaAttributes.predictFromAttr(molecule, 0);
        double sumX = 0.0;
        int nX = 0;
        double sumH = 0.0;
        int nH = 0;
        double sumC = 0.0;
        int nC = 0;
        for (Atom atom : molecule.getAtoms()) {
            Double ppmCA = atom.getPPM();
            if (ppmCA != null) {
                var rnaStats = RNAAttributes.getStats(atom);
                if ((rnaStats != null) && (rnaStats.getN() > 2)) {
                    double deltaMP = Math.abs(rnaStats.getMean() - rnaStats.getPredValue());
                    if (atom.getName().charAt(0) == 'H') {
                        sumH += deltaMP;
                        nH++;
                    } else if (atom.getName().charAt(0) == 'C') {
                        sumC += deltaMP;
                        nC++;
                    } else {
                        sumX += deltaMP;
                        nX++;
                    }
                }
            }
        }
        Molecule.removeAll();
        double madH = sumH / nH;
        double madX = sumX / nX;
        double madC = sumC / nC;
        Assert.assertTrue(madH < 0.1);
        Assert.assertTrue(madC < 0.5);
        Assert.assertTrue(madX < 2.5);
    }

    @Test
    public void getAttr() throws MoleculeIOException {
        Molecule.removeAll();
        Sequence sequence = new Sequence();
        var molecule = (Molecule) sequence.read("A", List.of("G", "G", "U", "G", "G", "U", "G", "A", "G", "A", "G", "C", "C", "A", "C", "C"), ".");
        molecule.setDotBracket("((((((....))))))");
        RNAAttributes rnaAttributes = new RNAAttributes();
        var rnaData = rnaAttributes.genRNAData();
        List<String> res5Attr = rnaData.get(5);
        var vList = List.of("Pp", "GC", "UG", "G-", "P-", "-", "-", "-", "gnra1", "gnra2", "-");
        for (int i = 0; i < res5Attr.size(); i++) {
            Assert.assertEquals(vList.get(i), res5Attr.get(i));
        }
        Molecule.removeAll();
    }

    @Test
    public void getAtomTypes() throws MoleculeIOException, IOException {
        Molecule.removeAll();
        ProteinPredictor.initMinMax();
        Sequence sequence = new Sequence();
        var molecule = (Molecule) sequence.read("A", List.of("ala", "arg", "asn", "asp", "cys", "gln", "glu", "gly", "his",
                "ile", "leu", "lys", "met", "phe", "pro", "ser", "trp", "tyr"
        ), ".");
        boolean missing = false;
        for (Atom atom : molecule.getAtoms()) {
            var nameOpt = ProteinPredictor.getAtomNameType(atom);
            int aNum = atom.getAtomicNumber();
            boolean exchangeable = (aNum == 1) && (atom.parent != null) && ((atom.parent.getAtomicNumber() == 8) || (atom.parent.getAtomicNumber() == 7) || atom.parent.getAtomicNumber() == 16);
            boolean hcn = (aNum == 1) || (aNum == 6) || (aNum == 7);
            if (nameOpt.isEmpty() && hcn && !exchangeable && !atom.getName().equals("H1") && !(atom.isMethyl() && !atom.isFirstInMethyl())) {
                missing = true;
                System.out.println(atom.getEntity().getName() + " " + atom.getShortName() + " " + aNum);
            }
        }
        Molecule.removeAll();
        Assert.assertFalse(missing);
    }

    class AtomErrors {
        double sumN = 0.0;
        double sumNA = 0.0;
        double sumN2 = 0.0;
        int nN = 0;
        double sumH = 0.0;
        double sumHA = 0.0;
        double sumH2 = 0.0;
        int nH = 0;
        double sumC = 0.0;
        double sumCA = 0.0;
        double sumC2 = 0.0;
        int nC = 0;
        int nViol = 0;

        void addViol() {
            nViol++;
        }

        void add(Atom atom, double delta, AtomErrors offsets) {
            if (atom.getName().startsWith("H")) {
                if (offsets != null) {
                    delta -= offsets.H();
                }
                sumH += delta;
                sumHA += Math.abs(delta);
                sumH2 += delta * delta;
                nH++;
            } else if (atom.getName().startsWith("C")) {
                if (offsets != null) {
                    delta -= offsets.C();
                }
                sumC += delta;
                sumCA += Math.abs(delta);
                sumC2 += delta * delta;
                nC++;
            } else if (atom.getName().startsWith("N")) {
                if (offsets != null) {
                    delta -= offsets.N();
                }
                sumN += delta;
                sumNA += Math.abs(delta);
                sumN2 += delta * delta;
                nN++;
            }
        }

        double H() {
            return sumH / nH;
        }

        double C() {
            return sumC / nC;
        }

        double N() {
            return sumN / nN;
        }

        double maeH() {
            return sumHA / nH;
        }

        double maeC() {
            return sumCA / nC;
        }

        double maeN() {
            return sumNA / nN;
        }

        double rmsH() {
            return Math.sqrt(sumH2 / nH);
        }

        double rmsC() {
            return Math.sqrt(sumC2 / nC);
        }

        double rmsN() {
            return Math.sqrt(sumN2 / nN);
        }

        int nViol() {
            return nViol;
        }

    }

    AtomErrors getErrors(Molecule molecule, AtomErrors offsets, FileWriter writer) throws IOException {
        AtomErrors atomErrors = new AtomErrors();
        String molName = molecule.getName();
        for (Atom atom : molecule.getAtoms()) {
            Double ppm = atom.getPPM();
            Double refPPM = atom.getRefPPM();
            if ((ppm != null) && (refPPM != null)) {
                double delta = Math.abs(ppm - refPPM);
                atomErrors.add(atom, delta, offsets);

                double err = atom.getSDevRefPPM();
                err = Math.max(err, 0.3);
                double ratio = delta / err;
                if (offsets != null) {
                    atomErrors.addViol();
                    int chainId = molecule.getPolymer(atom.getPolymerName()).getIDNum();
                    int resNum = atom.getResidueNumber();
                    String resName = atom.getResidueName();
                    String atomId = molName + ":" + chainId + "." + resNum + "." + atom.getShortName();
                    writer.write(atomId + " " + resName + " " + ratio + " " + refPPM + " " + ppm + "\n");
                }
            }
        }
        return atomErrors;
    }

    @Test
    public void predictProteinFromSTAR() throws MoleculeIOException, IOException, ParseException, InvalidMoleculeException {
        Molecule.removeAll();
        ProteinPredictor.initMinMax();
        InputStream stream = RNAProteinPredictorTest.class.getClassLoader().getResourceAsStream("data/star/17268.str");
        try (InputStreamReader reader = new InputStreamReader(stream)) {
            NMRStarReader.read(reader, null);
        }
        Molecule molecule = Molecule.getActive();
        ProteinPredictor proteinPredictor = new ProteinPredictor();
        proteinPredictor.init(molecule, 0);
        proteinPredictor.predict(molecule.getPolymers().getFirst(), -1, 0);
        AtomErrors atomErrors = getErrors(molecule, null, null);
        AtomErrors atomErrors1 = getErrors(molecule, atomErrors, null);

        Molecule.removeAll();
        Assert.assertTrue(atomErrors1.rmsH() < 0.3);
        Assert.assertTrue(atomErrors1.rmsC() < 1.0);
        Assert.assertTrue(atomErrors1.rmsN() < 2.0);
        Assert.assertTrue(atomErrors1.nViol() < 6);
    }

    public void predictAll(FileWriter writer) throws IOException, ParseException, InvalidMoleculeException {
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get("starFilePath"))) {
            for (Path path : dirStream) {
                if (!Files.isDirectory(path)) {
                    Molecule.removeAll();
                    ProteinPredictor.initMinMax();
                    InputStream stream = new FileInputStream(path.toFile());
                    try (InputStreamReader reader = new InputStreamReader(stream)) {
                        NMRStarReader.read(reader, null);
                    }
                    Molecule molecule = Molecule.getActive();
                    ProteinPredictor proteinPredictor = new ProteinPredictor();
                    proteinPredictor.init(molecule, 0);
                    proteinPredictor.predict(molecule.getPolymers().getFirst(), -1, 0);
                    AtomErrors atomErrors = getErrors(molecule, null, null);
                    AtomErrors atomErrors1 = getErrors(molecule, atomErrors, writer);

                    Molecule.removeAll();

                }
            }
        }
    }

    public void saveResults() throws IOException, InvalidMoleculeException, ParseException {
        try (FileWriter writer = new FileWriter("outFilename")) {
            String header = "atomId residue ratio refPPM PPM";
            writer.write(header);
            predictAll(writer);
        }
    }

}