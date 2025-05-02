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

import static org.junit.Assert.*;

public class ProteinPredictorTest {

    @Test
    public void predict() throws MoleculeIOException, IOException, InvalidMoleculeException {
        Sequence sequence = new Sequence();
        var molecule = (Molecule) sequence.read("A", List.of("gly","glu", "phe","glu","asn","ser","arg"),".");
        Polymer polymer = molecule.getPolymers().getFirst();
        Residue residue = polymer.getResidues().get(2);
        ProteinPredictor proteinPredictor = new ProteinPredictor();
        proteinPredictor.init(molecule, 0);
        proteinPredictor.predict(residue, 0, 0);
        double ppmCA = residue.getAtom("CA").getPPM();
        Assert.assertEquals(55.4, ppmCA, 0.1);
    }
}