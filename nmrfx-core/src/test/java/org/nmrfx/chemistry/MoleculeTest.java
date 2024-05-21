package org.nmrfx.chemistry;

import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.Sequence;

import java.util.Arrays;
import java.util.List;

public class MoleculeTest {

    static MoleculeBase mol = null;

    static {
        Sequence seq = new Sequence();
        List<String> residueList = Arrays.asList("ALA", "GLY", "SER", "VAL");
        try {
            MoleculeBase.removeAll();
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
        Atom atom = mol.findAtom("3.HB3");
        Assert.assertNotNull(atom);
        boolean matched = Util.nefMatch(atom, "HBy");
        Assert.assertTrue(matched);
    }

    @Test
    public void testNEFMatchMethyleneY() {
        Atom atom = mol.findAtom("3.HB2");
        Assert.assertNotNull(atom);
        boolean matched = Util.nefMatch(atom, "HBx");
        Assert.assertTrue(matched);
    }

    @Test
    public void testNEFNotMatchMethyleneX() {
        Atom atom = mol.findAtom("3.HB2");
        Assert.assertNotNull(atom);
        System.out.println(atom.getFullName() + " " + Util.nefMatch(atom, "HBx") + " " + Util.nefMatch(atom, "HBy"));
        boolean matched = Util.nefMatch(atom, "HBy");
        Assert.assertTrue(!matched);
    }

    @Test
    public void testNEFMatchMethyl1y() {
        Atom atom1 = mol.findAtom("4.HG11");
        Assert.assertNotNull(atom1);
        boolean matched1y = Util.nefMatch(atom1, "HGx");
        Assert.assertTrue(!matched1y);
    }

    @Test
    public void testNEFMatchMethyl1x() {
        Atom atom1 = mol.findAtom("4.HG11");
        Assert.assertNotNull(atom1);
        boolean matched1x = Util.nefMatch(atom1, "HGy");
        Assert.assertTrue(matched1x);
    }

    @Test
    public void testNEFMatchMethyl2x() {
        Atom atom2 = mol.findAtom("4.HG21");
        Assert.assertNotNull(atom2);
        boolean matched2x = Util.nefMatch(atom2, "HGy");
        Assert.assertTrue(!matched2x);
    }

    @Test
    public void testNEFMatchMethyl2y() {
        Atom atom2 = mol.findAtom("4.HG21");
        Assert.assertNotNull(atom2);
        boolean matched2y = Util.nefMatch(atom2, "HGx");
        Assert.assertTrue(matched2y);
    }

}
