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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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


    static class AtomErrors {
        Map<String, AtomError> atomTypes = new HashMap<>();
        int nViol = 0;

        AtomErrors(List<String> atomNames) {
            for (String name : atomNames) {
                atomTypes.put(name, new AtomError());
            }
        }

        static class AtomError {
            double sum = 0.0;
            double sumAbs = 0.0;
            double sumSq = 0.0;
            int n;

            double average() {
                return sum/n;
            }

            double rms() {
                return Math.sqrt(sumSq/n);
            }

            double mae() {
                return sumAbs/n;
            }
        }

        void addViol() {
            nViol++;
        }

        int nViol() {
            return nViol;
        }

        void add(Atom atom, double delta, AtomErrors offsets) {
            String aName = atom.getName().substring(0,2);
            aName = atomTypes.containsKey(aName) ? aName : atom.getName().substring(0,1);
            if (atomTypes.containsKey(aName)) {
                AtomError e = atomTypes.get(aName);
                if (offsets != null) {
                    delta -= offsets.average(aName);
                }
                e.sum += delta;
                e.sumAbs += Math.abs(delta);
                e.sumSq += delta * delta;
                e.n++;
            }
        }

        double rms(String aName) {
            return atomTypes.get(aName).rms();
        }

        double average(String aName) {
            return atomTypes.get(aName).average();
        }

        void aggregate(AtomErrors atomErrors) {
            for (Map.Entry<String, AtomError> entry : atomErrors.atomTypes.entrySet()) {
                String aName = entry.getKey();
                atomTypes.get(aName).sumSq += entry.getValue().sumSq;
                atomTypes.get(aName).n += entry.getValue().n;
            }
        }
    }

    AtomErrors getErrors(Molecule molecule, AtomErrors offsets, StringBuilder stringBuilder) {
        List<String> aNames = List.of("N","C","H");
        AtomErrors atomErrors = new AtomErrors(aNames);
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
                    String resName = atom.getResidueName();
                    String atomId = molName + ":" + chainId + "." + atom.getShortName();
                    stringBuilder.append(atomId).append(" ")
                            .append(resName).append(" ");
                    stringBuilder.append(String.format("%-2.3f", ratio)).append(" ");
                    stringBuilder.append(refPPM).append(" ")
                            .append(ppm).append(" ");
                    stringBuilder.append(String.format("%-2.3f", delta)).append( "\n");
                }
            }
        }
        return atomErrors;
    }

    AtomErrors getAtomErrors(StringBuilder stringBuilder) throws IOException, InvalidMoleculeException {
        Molecule molecule = Molecule.getActive();
        ProteinPredictor proteinPredictor = new ProteinPredictor();
        proteinPredictor.init(molecule, 0);
        proteinPredictor.predict(molecule.getPolymers().getFirst(), -1, 0);
        AtomErrors atomErrors = getErrors(molecule,null, null);
        return getErrors(molecule, atomErrors, stringBuilder);
    }

    @Test
    public void predictProteinFromSTAR() throws IOException, ParseException, InvalidMoleculeException {
        Molecule.removeAll();
        ProteinPredictor.initMinMax();
        InputStream stream = RNAProteinPredictorTest.class.getClassLoader().getResourceAsStream("data/star/17268.str");
        assert stream != null;
        try (InputStreamReader reader = new InputStreamReader(stream)) {
            NMRStarReader.read(reader, null);
        }
        AtomErrors atomErrors = getAtomErrors(null);

        Molecule.removeAll();
        Assert.assertTrue(atomErrors.rms("H") < 0.3);
        Assert.assertTrue(atomErrors.rms("C") < 1.0);
        Assert.assertTrue(atomErrors.rms("N") < 2.0);
        Assert.assertTrue(atomErrors.nViol() < 6);
    }

    AtomErrors predictAll(StringBuilder stringBuilder) throws IOException, ParseException, InvalidMoleculeException {
        List<String> aNames = List.of("N", "CA", "CB", "C", "H", "HA");
        AtomErrors allAtomErrors = new AtomErrors(aNames);
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get("/Users/ekoag/fitshifts/testDataset/starshift"))) {
            for (Path path : dirStream) {
                if (!Files.isDirectory(path)) {
                    Molecule.removeAll();
                    ProteinPredictor.initMinMax();
                    InputStream stream = new FileInputStream(path.toFile());
                    try (InputStreamReader reader = new InputStreamReader(stream)) {
                        NMRStarReader.read(reader, null);
                    }
                    AtomErrors atomErrors = getAtomErrors(stringBuilder);
                    allAtomErrors.aggregate(atomErrors);
                    Molecule.removeAll();
                }
            }
        }
        return allAtomErrors;
    }

    @Test
    public void predictOnTestSet() throws IOException, InvalidMoleculeException, ParseException {
        try (FileWriter writer = new FileWriter("/Users/ekoag/fitshifts/results.txt")) {
            String header = "atomId residue ratio refPPM PPM delta \n";
            writer.write(header);
            StringBuilder stringBuilder = new StringBuilder();
            AtomErrors allAtomErrors1 = predictAll(stringBuilder);
            stringBuilder.append(String.format("%-2.3f", allAtomErrors1.rms("H"))).append("\n");
            stringBuilder.append(String.format("%-2f.3f", allAtomErrors1.rms("C"))).append("\n");
            stringBuilder.append(String.format("%-2f.3f", allAtomErrors1.rms("N"))).append("\n");
            writer.write(stringBuilder.toString());
        }
    }

}