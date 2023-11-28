/*
 * CDKInterface.java
 *
 */
package org.nmrfx.structure.chemistry;

import com.actelion.research.chem.SmilesParser;
import com.actelion.research.chem.StereoMolecule;
import org.nmrfx.chemistry.*;
import org.openmolecules.chem.conf.gen.ConformerGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.nmrfx.chemistry.Bond.*;

/**
 * @author brucejohnson
 */
public class OpenChemLibConverter {
    private static final Logger log = LoggerFactory.getLogger(OpenChemLibConverter.class);

    static final HashMap<String, AtomContainer> molecules = new HashMap<>();

    private OpenChemLibConverter() {
    }

    public static AtomContainer getMolecule(String name) {
        return molecules.get(name);

    }

    public static boolean putMolecule(String name, AtomContainer molecule) {
        return (molecules.put(name, molecule) != null);
    }

    public static void clearMolecules() {
        molecules.clear();
    }

    public static Molecule parseSmiles(String molName, String smilesString) throws IllegalArgumentException {
        SmilesParser smilesParser = new SmilesParser();
        StereoMolecule mol = smilesParser.parseMolecule(smilesString);
        if (mol == null) {
            throw new IllegalArgumentException("Invalid SMILES string");
        }
        ConformerGenerator.addHydrogenAtoms(mol);
        return convertFromStereoMolecule(mol, molName);
    }

    public static String getMolName(String fileName) {
        File file = new File(fileName);

        String fileTail = file.getName();
        String fileRoot;
        int dot = fileTail.indexOf(".");

        if (dot != -1) {
            fileRoot = fileTail.substring(0, dot);
        } else {
            fileRoot = fileTail;
        }
        return fileRoot;
    }

    public static List<Molecule> readSMILES(File file) throws IOException {
        List<Molecule> molecules = new ArrayList<>();
        Path path = file.toPath();
        String rootName = getMolName(file.getName());
        int i = 1;
        List<String> lines = Files.readAllLines(path);
        for (String line : lines) {
            String[] fields = line.split("\t");
            String molName;
            String smileString;
            if (fields.length == 2) {
                molName = fields[0];
                smileString = fields[1];
            } else {
                if (lines.size() > 1) {
                    molName = rootName + "_" + i;
                } else {
                    molName = rootName;
                }
                smileString = fields[0];
            }
            smileString = smileString.trim();
            if (!smileString.isEmpty()) {
                try {
                    Molecule molecule = parseSmiles(molName, smileString);
                    molecules.add(molecule);
                    i++;
                } catch (IllegalArgumentException iAE) {
                    log.error("Can't parse SMILES", iAE);
                }
            }
        }
        return molecules;
    }


    public static Molecule convertFromStereoMolecule(StereoMolecule stereoMolecule, String molName) {
        Molecule molecule = new Molecule(molName);
        molecule.entities.clear();
        Compound compound = new Compound("1", "A");
        molecule.structures.add(0);
        compound.molecule = molecule;
        molecule.addEntity(compound, molName);
        convertFromStereoMolecule(stereoMolecule, compound);
        return molecule;
    }

    public static void convertFromStereoMolecule(StereoMolecule stereoMolecule, Compound compound) {
        stereoMolecule.ensureHelperArrays(StereoMolecule.cHelperNeighbours);
        compound.removeAllAtoms();
        int structureNumber = 0;
        int nAtoms = stereoMolecule.getAllAtoms();
        for (int i = 0; i < nAtoms; i++) {
            String aName = stereoMolecule.getAtomLabel(i);
            Atom atom = Atom.genAtomWithElement(aName, stereoMolecule.getAtomicNo(i));
            atom.setPointValidity(structureNumber, true);
            double x = stereoMolecule.getAtomX(i);
            double y = stereoMolecule.getAtomY(i);
            double z = stereoMolecule.getAtomZ(i);
            Point3 pt = new Point3(x, y, z);
            atom.setPoint(structureNumber, pt);
            String name = stereoMolecule.getAtomCustomLabel(i);
            if ((name == null) || name.isEmpty()) {
                atom.setName(aName + (i + 1));
            } else {
                atom.setName(name);
            }
            compound.addAtom(atom);
        }
        int nBonds = stereoMolecule.getAllBonds();
        for (int i = 0; i < nBonds; i++) {
            int iAtom = stereoMolecule.getBondAtom(0, i);
            int jAtom = stereoMolecule.getBondAtom(1, i);
            int order = stereoMolecule.getBondOrder(i);
            int bondType = stereoMolecule.getBondType(i);
            int stereo = switch (bondType) {
                case com.actelion.research.chem.Molecule.cBondTypeUp -> STEREO_BOND_UP;
                case com.actelion.research.chem.Molecule.cBondTypeDown -> STEREO_BOND_DOWN;
                case com.actelion.research.chem.Molecule.cBondTypeCross -> STEREO_BOND_CROSS;
                default -> 0;
            };
            Atom atomStart = compound.getAtom(iAtom);
            Atom atomEnd = compound.getAtom(jAtom);
            Order bondOrder = Order.getOrder(order);
            Atom.addBond(atomStart, atomEnd, bondOrder, stereo, false);
        }
        compound.molecule.invalidateAtomArray();
        compound.molecule.updateAtomArray();
        compound.molecule.updateBondArray();
    }

    private static int toBondType(int order, int stereo) {
        return switch (stereo) {
            case STEREO_BOND_UP -> com.actelion.research.chem.Molecule.cBondTypeUp;
            case STEREO_BOND_CROSS -> com.actelion.research.chem.Molecule.cBondTypeCross;
            case STEREO_BOND_EITHER -> // we interpret 'either' as being racemic
                    com.actelion.research.chem.Molecule.cBondTypeUp;
            case STEREO_BOND_DOWN -> com.actelion.research.chem.Molecule.cBondTypeDown;
            default -> switch (order) {
                case 2 -> com.actelion.research.chem.Molecule.cBondTypeDouble;
                case 3 -> com.actelion.research.chem.Molecule.cBondTypeTriple;
                case 4 -> com.actelion.research.chem.Molecule.cBondTypeDelocalized;
                case 9 -> // exists in version 3 only
                        com.actelion.research.chem.Molecule.cBondTypeMetalLigand;
                default -> com.actelion.research.chem.Molecule.cBondTypeSingle;
            };
        };
    }

    public static StereoMolecule convertToStereoMolecule(AtomContainer molecule) {
        var stereoMolecule = new StereoMolecule();
        int structureNumber = 0;
        HashMap<Atom, Integer> atomHash = new HashMap<>();
        for (var atomI : molecule.atoms()) {
            Atom atom = (Atom) atomI;
            int iAtom = stereoMolecule.addAtom(atom.getAtomicNumber());
            stereoMolecule.setAtomCustomLabel(iAtom, atom.getName());
            Point3 pt = atom.getPoint(structureNumber);
            stereoMolecule.setAtomX(iAtom, pt.getX());
            stereoMolecule.setAtomY(iAtom, pt.getY());
            stereoMolecule.setAtomZ(iAtom, pt.getZ());
            atomHash.put(atom, iAtom);
        }
        for (IBond bondI : molecule.bonds()) {
            Bond bond = (Bond) bondI;
            Atom atom1 = bond.begin;
            Atom atom2 = bond.end;
            int order = bond.order.getOrderNum();
            int stereo = bond.stereo;

            int iAtom = atomHash.get(atom1);
            int jAtom = atomHash.get(atom2);
            int bondType = toBondType(order, stereo);
            stereoMolecule.addBond(iAtom, jAtom, bondType);
        }
        return stereoMolecule;
    }

    public static void to3D(Molecule molecule) {
        for (var ligand : molecule.getLigands()) {
            StereoMolecule sMol = OpenChemLibConverter.convertToStereoMolecule(ligand);
            ConformerGenerator cg = new ConformerGenerator();
            var conf = cg.getOneConformer(sMol);
            var mol3D = conf.toMolecule(sMol);
            ConformerGenerator.addHydrogenAtoms(mol3D);
            OpenChemLibConverter.convertFromStereoMolecule(mol3D, ligand);
        }
        molecule.inactivateAtoms();
        molecule.updateAtomArray();
        molecule.updateBondArray();
    }
}
