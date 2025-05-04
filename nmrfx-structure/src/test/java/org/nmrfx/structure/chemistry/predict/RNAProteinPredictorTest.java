package org.nmrfx.structure.chemistry.predict;

import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.Sequence;
import org.nmrfx.structure.chemistry.Molecule;

import java.io.IOException;
import java.util.List;

public class RNAProteinPredictorTest {

    @Test
    public void predictProtein() throws MoleculeIOException, IOException, InvalidMoleculeException {
        Sequence sequence = new Sequence();
        var molecule = (Molecule) sequence.read("A", List.of("gly", "glu", "phe", "glu", "asn", "ser", "arg"), ".");
        Polymer polymer = molecule.getPolymers().getFirst();
        Residue residue = polymer.getResidues().get(2);
        ProteinPredictor proteinPredictor = new ProteinPredictor();
        proteinPredictor.init(molecule, 0);
        proteinPredictor.predict(residue, 0, 0);
        double ppmCA = residue.getAtom("CA").getPPM();
        Assert.assertEquals(55.4, ppmCA, 0.1);
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
        double madC = sumC /nC;
        System.out.println(madH + " " + madX + " " + madC);
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
}