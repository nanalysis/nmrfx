package org.nmrfx.structure.chemistry.miner;

import com.actelion.research.chem.Molecule3D;
import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.chem.descriptor.flexophore.calculator.StructureCalculator;
import com.actelion.research.chem.forcefield.mmff.ForceFieldMMFF94;
import com.actelion.research.chem.forcefield.mmff.MMFFMolecule;
import com.actelion.research.chem.forcefield.mmff.Tables;
import com.actelion.research.chem.forcefield.mmff.type.Charge;
import com.actelion.research.chem.interactionstatistics.InteractionAtomTypeCalculator;
import com.actelion.research.chem.io.pdb.converter.GeometryCalculator;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class ChemPropertyCalculator {

    static Map<Coupling, Double> couplings = new HashMap<>();

    record Coupling(String molName, int iAtom, int jAtom) {

    }
    public static void loadCouplings(String fileName, String targetType) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(fileName));
            String line = reader.readLine();

            while (line != null) {
                System.out.println(line);
                String[] fields = line.split(",");
                String jType = fields[4];
                if (jType.equals(targetType)) {
                    String molName = fields[1];
                    int iAtom = Integer.parseInt(fields[2]);
                    int jAtom = Integer.parseInt(fields[3]);
                    double jValue = Double.parseDouble(fields[5]);
                    Coupling coupling = new Coupling(molName, iAtom, jAtom);
                    couplings.put(coupling, jValue);
                }
                // read next line
                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static List<Integer> getAttachedHydrogens(StereoMolecule mol, int a) {
        List<Integer> atoms = new ArrayList<>();
        for (int i = 0; i < mol.getAllConnAtoms(a); i++) {
            int aa = mol.getConnAtom(a, i);
            int aNum = mol.getAtomicNo(aa);
            if (aNum == 1) {
                atoms.add(aa);
            }
        }
        return atoms;
    }

    public static List<Integer> getAttachedNonHydrogens(StereoMolecule mol, int a) {
        List<Integer> atoms = new ArrayList<>();
        for (int i = 0; i < mol.getAllConnAtoms(a); i++) {
            int aa = mol.getConnAtom(a, i);
            int aNum = mol.getAtomicNo(aa);
            if (aNum != 1) {
                atoms.add(aa);
            }
        }
        return atoms;
    }

    private static List<Double> oneHot(int active, int min, int max) {
        List<Double> result = new ArrayList<>();
        int n = max - min + 1;
        for (int i = 0; i < n; i++) {
            result.add((double) (i + min) == active ? 1.0 : 0.0);
        }
        return result;
    }

    private static int[] oneHotANum(int aNum) {
        //    H, C, O, N, P, S, F, Cl}
        int[] aNums = {1, 6, 8, 7, 15, 16, 9, 17};
        int[] result = new int[aNums.length + 1];
        boolean found = false;
        for (int i = 0; i < aNums.length; i++) {
            if (aNums[i] == aNum) {
                result[i] = 1;
                found = true;
                break;
            }
        }
        if (!found) {
            result[result.length - 1] = 1;
        }
        return result;
    }

    public static int getValence(StereoMolecule mol, int atm) {
        int res = 0;
        for (int i = 0; i < mol.getAllConnAtoms(atm); i++) {
            if (mol.getAtomicNo(mol.getConnAtom(atm, i)) > 1) res += mol.getConnBondOrder(atm, i);
        }
        return res;
    }

    public static List<Double> atomProps(Molecule3D stereoMolecule, int iAtom) {
        List<Double> result = new ArrayList<>();
        int aNum = stereoMolecule.getAtomicNo(iAtom);
        double charge = stereoMolecule.getPartialCharge(iAtom);
        int atomType = InteractionAtomTypeCalculator.getAtomType(stereoMolecule, iAtom);
        int hybrid = (atomType & InteractionAtomTypeCalculator.AtomPropertyMask.HYBRID.getMask()) >> InteractionAtomTypeCalculator.AtomPropertyShift.HYBRID_SHIFT.getShift();
        boolean aromatic = InteractionAtomTypeCalculator.isAromatic(atomType);
        int ringSize = stereoMolecule.getAtomRingSize(iAtom);
        int maxValence = StructureCalculator.getMaxValence(aNum);
        int fgValue = (atomType & InteractionAtomTypeCalculator.AtomPropertyMask.FUNCTIONAL_GROUP.getMask()) >> InteractionAtomTypeCalculator.AtomPropertyShift.FUNCTIONAL_GROUP_SHIFT.getShift();
        int valence = getValence(stereoMolecule, iAtom);
        result.add(charge);
        result.addAll(oneHot(hybrid, 0, 6));
        result.add(aromatic ? 1.0 : 0.0);
        result.addAll(oneHot(ringSize, 3, 7));
        result.addAll(oneHot(valence, 1, 6));
        result.add((double) maxValence);
        result.addAll(oneHot(fgValue, 0, 14));
        return result;
    }

    public static List<Double> props(Molecule3D stereoMolecule, List<Integer> atoms) {
        List<Double> result = new ArrayList<>();
        for (int iAtom : atoms) {
            int aNum = stereoMolecule.getAtomicNo(iAtom);
            result.add((double) aNum);
            int[] oneHotANum = oneHotANum(aNum);
            for (int oneHot : oneHotANum) {
                result.add((double) oneHot);
            }
            result.addAll(atomProps(stereoMolecule, iAtom));
        }
        return result;
    }

    public static List<Integer> getNeighbors(int hAtom, int cAtom, List<Integer> hAtoms, List<Integer> xAtoms) {
        List<Integer> iAtoms = new ArrayList<>();
        for (int a : hAtoms) {
            if (a != hAtom) {
                iAtoms.add(a);
            }
        }
        for (int a : xAtoms) {
            if (a != cAtom) {
                iAtoms.add(a);
            }
        }
        return iAtoms;
    }


    public static List<List<Double>>  getPredictionParameters(StereoMolecule stereoMolecule) {
        stereoMolecule.ensureHelperArrays(StereoMolecule.cHelperBitParities);
        ForceFieldMMFF94.initialize(ForceFieldMMFF94.MMFF94SPLUS);
        ForceFieldMMFF94 ff = new ForceFieldMMFF94(stereoMolecule, ForceFieldMMFF94.MMFF94SPLUS);

        var mmffMOl = new MMFFMolecule(stereoMolecule);
        double[] charges = Charge.getCharges(Tables.newMMFF94(ForceFieldMMFF94.MMFF94SPLUS), ff.getMMFFMolecule());
        Molecule3D mol3D = new Molecule3D(stereoMolecule);
        mol3D.ensureHelperArrays(StereoMolecule.cHelperBitParities);
        int nAtoms = mol3D.getAllAtoms();
        for (int i = 0; i < nAtoms; i++) {
            mol3D.setPartialCharge(i, charges[i]);
        }
        int nBonds = mol3D.getBonds();

        List<List<Double>> result = new ArrayList<>();
        for (int i = 0; i < nBonds; i++) {
            int iAtom = mol3D.getBondAtom(0, i);
            int jAtom = mol3D.getBondAtom(1, i);
            int iNum = mol3D.getAtomicNo(iAtom);
            int jNum = mol3D.getAtomicNo(jAtom);
            if ((iNum == 6) && (jNum == 6)) {
                var iHAtoms = getAttachedHydrogens(mol3D, iAtom);
                var iXAtoms = getAttachedNonHydrogens(mol3D, iAtom);
                var jHAtoms = getAttachedHydrogens(mol3D, jAtom);
                var jXAtoms = getAttachedNonHydrogens(mol3D, jAtom);
                if (!iHAtoms.isEmpty() && !jHAtoms.isEmpty()) {
                    for (var h1 : iHAtoms) {
                        for (var h2 : jHAtoms) {
                            List<Double> output = new ArrayList<>();
                            double dihedral = GeometryCalculator.getDihedral(mol3D, h1, iAtom, jAtom, h2);
                            var iNeighbors = getNeighbors(h1, jAtom, iHAtoms, iXAtoms);
                            output.add(Math.sin(dihedral));
                            output.add(Math.cos(dihedral));
                            output.add(Math.sin(dihedral)*Math.sin(dihedral));
                            output.add(Math.cos(dihedral)*Math.cos(dihedral));
                            output.addAll(atomProps(mol3D, iAtom));
                            output.addAll(atomProps(mol3D, jAtom));
                            double hhDistance = stereoMolecule.getCoordinates(h1).
                                    distance(stereoMolecule.getCoordinates(h2));
                            double ccDistance = stereoMolecule.getCoordinates(iAtom).
                                    distance(stereoMolecule.getCoordinates(jAtom));
                            double hc1Distance = stereoMolecule.getCoordinates(h1).
                                    distance(stereoMolecule.getCoordinates(jAtom));
                            double hc2Distance = stereoMolecule.getCoordinates(h2).
                                    distance(stereoMolecule.getCoordinates(iAtom));
                            output.add(hhDistance);
                            output.add(ccDistance);
                            output.add(hc1Distance);
                            output.add(hc2Distance);
                            var iProps = props(mol3D, iNeighbors);
                            output.addAll(iProps);
                            if (iNeighbors.size() == 1) {
                                List<Double> zeroOps =  Collections.nCopies(iProps.size(), 0.0);
                                output.addAll(zeroOps);
                            }
                            var jNeighbors = getNeighbors(h2, iAtom, jHAtoms, jXAtoms);
                            var jProps = props(mol3D, jNeighbors);
                            output.addAll(jProps);
                            if (jNeighbors.size() == 1) {
                                List<Double> zeroOps =  Collections.nCopies(jProps.size(), 0.0);
                                output.addAll(zeroOps);
                            }
                            Coupling coupling = new Coupling(stereoMolecule.getName(), h1, h2);
                            Double jValue = couplings.get(coupling);
                            if (jValue != null) {
                                jValue = (jValue + 3.0) / 20.0;
                                output.add(jValue);
                                result.add(output);
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

}
