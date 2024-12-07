package org.nmrfx.structure.chemistry.energy;

import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Compound;
import org.nmrfx.chemistry.Entity;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.io.NMRNEFReader;
import org.nmrfx.chemistry.io.NMRStarReader;
import org.nmrfx.star.ParseException;
import org.nmrfx.star.STAR3;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.miner.NodeValidator;
import org.nmrfx.structure.chemistry.miner.PathIterator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

@PythonAPI("refine")
public class NEFSTARStructureCalculator {

    private NEFSTARStructureCalculator() {

    }
    public static Molecule setup(String fileName, boolean nefMode) throws FileNotFoundException, ParseException {
        Molecule molecule = getMolecule(fileName, nefMode);
        molecule.setMethylRotationActive(true);
        var energyList = new EnergyLists(molecule);
        molecule.setEnergyLists(energyList);
        var dihedral = new Dihedral(energyList, false);
        molecule.setDihedrals(dihedral);
        dihedral.clearBoundaries();
        var energyLists = dihedral.energyList;
        energyLists.makeCompoundList(molecule);
        setupAtomProperties(molecule);
        setupLinks(molecule);
        molecule.genCoords(false);

        return molecule;
    }

    private static Molecule getMolecule(String fileName, boolean nefMode) throws FileNotFoundException, ParseException {
        var fileReader = new FileReader(fileName);
        var bfR = new BufferedReader(fileReader);
        var star = new STAR3(bfR, "star3");
        star.scanFile();
        File file = new File(fileName);
        Molecule molecule;
        if (nefMode) {
            var reader = new NMRNEFReader(file, star);
            molecule = (Molecule) reader.processNEF();
        } else {
            var reader = new NMRStarReader(file, star);
            reader.process();
            molecule = (Molecule) MoleculeFactory.getActive();
        }
        return molecule;
    }

    private static void setupLinks(Molecule molecule) {
        var entities = molecule.getEntities();
        Entity firstEntity = entities.remove(0);
        Atom startAtom = firstEntity.getLastAtom();
        AngleTreeGenerator angleTreeGenerator = new AngleTreeGenerator();
        for (Entity entity : entities) {
            if (entity instanceof Compound) {
                Atom endAtom = angleTreeGenerator.findStartAtom(entity);
                AngleTreeGenerator.genMeasuredTree(entity, endAtom);
                molecule.createLinker(startAtom, endAtom, 6, 5.0, 110.0, 135.0);
            }
        }
    }

    private static void setupAtomProperties(Molecule molecule) {
        for (Entity entity : molecule.getEntities()) {
            var pI = new PathIterator(entity);
            var nodeValidator = new NodeValidator();
            pI.init(nodeValidator);
            pI.processPatterns();
            pI.setProperties("ar", "AROMATIC");
            pI.setProperties("res", "RESONANT");
            pI.setProperties("namide", "AMIDE");
            pI.setProperties("r", "RING");
            pI.setHybridization();
        }

    }
}