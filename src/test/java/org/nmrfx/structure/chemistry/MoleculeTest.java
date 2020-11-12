package org.nmrfx.structure.chemistry;

import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.structure.chemistry.io.MoleculeIOException;
import org.nmrfx.structure.chemistry.io.Sequence;
import org.nmrfx.structure.utilities.Util;

public class MoleculeTest {

    static Molecule mol = null;

    static {
        Sequence seq = new Sequence();
        List<String> residueList = Arrays.asList("ALA", "GLY", "SER", "VAL");
        try {
            Molecule.removeAll();
            mol = seq.read("test", residueList, null);
        } catch (MoleculeIOException ex) {
        }

    }

    @Test
    public void testMolFromSequence() {
        Assert.assertNotNull(mol);
        Assert.assertEquals(1, mol.getPolymers().size());
    }

    @Test
    public void testNEFMatchMethyleneX() {
        Atom atom = Molecule.getAtomByName("3.HB3");
        Assert.assertNotNull(atom);
        boolean matched = Util.nefMatch(atom, "HBx");
        Assert.assertTrue(matched);
    }

    @Test
    public void testNEFMatchMethyleneY() {
        Atom atom = Molecule.getAtomByName("3.HB2");
        Assert.assertNotNull(atom);
        boolean matched = Util.nefMatch(atom, "HBy");
        Assert.assertTrue(matched);
    }

    @Test
    public void testNEFNotMatchMethyleneX() {
        Atom atom = Molecule.getAtomByName("3.HB2");
        Assert.assertNotNull(atom);
        boolean matched = Util.nefMatch(atom, "HBx");
        Assert.assertTrue(!matched);
    }

    @Test
    public void testNEFMatchMethyl1y() {
        Atom atom1 = Molecule.getAtomByName("4.HG11");
        Assert.assertNotNull(atom1);
        boolean matched1y = Util.nefMatch(atom1, "HGy");
        Assert.assertTrue(!matched1y);
    }

    @Test
    public void testNEFMatchMethyl1x() {
        Atom atom1 = Molecule.getAtomByName("4.HG11");
        Assert.assertNotNull(atom1);
        boolean matched1x = Util.nefMatch(atom1, "HGx");
        Assert.assertTrue(matched1x);
    }

    @Test
    public void testNEFMatchMethyl2x() {
        Atom atom2 = Molecule.getAtomByName("4.HG21");
        Assert.assertNotNull(atom2);
        boolean matched2x = Util.nefMatch(atom2, "HGx");
        Assert.assertTrue(!matched2x);
    }

    @Test
    public void testNEFMatchMethyl2y() {
        Atom atom2 = Molecule.getAtomByName("4.HG21");
        Assert.assertNotNull(atom2);
        boolean matched2y = Util.nefMatch(atom2, "HGy");
        Assert.assertTrue(matched2y);
    }

}
