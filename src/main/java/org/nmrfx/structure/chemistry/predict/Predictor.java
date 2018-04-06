/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.predict;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Entity;
import org.nmrfx.structure.chemistry.HoseCodeGenerator;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.PPMv;
import org.nmrfx.structure.chemistry.Polymer;
import org.nmrfx.structure.chemistry.ProteinPredictor;
import org.nmrfx.structure.chemistry.Residue;
import org.nmrfx.structure.chemistry.energy.RingCurrentShift;
import org.nmrfx.structure.chemistry.miner.NodeValidator;
import org.nmrfx.structure.chemistry.miner.PathIterator;

/**
 *
 * @author Bruce Johnson
 */
public class Predictor {

    static final Map<String, Double> RNA_REF_SHIFTS = new HashMap<>();

    static {
        RNA_REF_SHIFTS.put("A.H2", 7.93);
        RNA_REF_SHIFTS.put("G.H8", 7.87);
        RNA_REF_SHIFTS.put("C.H5", 5.84);
        RNA_REF_SHIFTS.put("U.H5", 5.76);

        RNA_REF_SHIFTS.put("C.H6", 8.02);
        RNA_REF_SHIFTS.put("U.H6", 8.01);
        RNA_REF_SHIFTS.put("A.H1'", 5.38);
        RNA_REF_SHIFTS.put("G.H1'", 5.37);
        RNA_REF_SHIFTS.put("C.H1'", 5.45);

        RNA_REF_SHIFTS.put("U.H1'", 5.50);
        RNA_REF_SHIFTS.put("A.H2'", 4.54);
        RNA_REF_SHIFTS.put("G.H2'", 4.59);
        RNA_REF_SHIFTS.put("C.H2'", 4.54);
        RNA_REF_SHIFTS.put("U.H2'", 4.54);

        RNA_REF_SHIFTS.put("A.H3'", 4.59);
        RNA_REF_SHIFTS.put("G.H3'", 4.59);
        RNA_REF_SHIFTS.put("C.H3'", 4.59);
        RNA_REF_SHIFTS.put("U.H3'", 4.59);
    }

    private boolean isRNA(Polymer polymer) {
        boolean rna = false;
        for (Residue residue : polymer.getResidues()) {
            String resName = residue.getName();
            if (resName.equals("A") || resName.equals("C") || resName.equals("G") || resName.equals("U")) {
                rna = true;
                break;
            }
        }
        return rna;
    }

    public void predictMolecule(Molecule mol, int iRef) throws InvalidMoleculeException {

        for (Polymer polymer : mol.getPolymers()) {
            if (!isRNA(polymer)) {
                predictPeptidePolymer(polymer, iRef);
            } else {
                predictRNAWithRingCurrent(polymer, iRef);
            }
        }
        boolean hasPolymer = !mol.getPolymers().isEmpty();
        for (Entity entity : mol.getLigands()) {
            predictWithShells(entity, iRef);
            if (hasPolymer) {
                predictLigandWithRingCurrent(entity, iRef);
            }
        }
    }

    public void predictPeptidePolymer(Polymer polymer, int iRef) throws InvalidMoleculeException {
        String[] atomNames = {"N", "CA", "CB", "C", "H", "HA", "HA3"};
        ProteinPredictor predictor = new ProteinPredictor(polymer);
        polymer.getResidues().forEach((residue) -> {
            for (String atomName : atomNames) {
                if (residue.getName().equals("GLY") && atomName.equals("HA")) {
                    atomName = "HA2";
                }
                Atom atom = residue.getAtom(atomName);
                if (atom != null) {
                    Double value = predictor.predict(residue.getAtom(atomName), false);
                    if (value != null) {
                        atom.setRefPPM(iRef, value);
                    }
                }
            }
        });
    }

    public void predictRNAWithRingCurrent(Polymer polymer, int iRef) throws InvalidMoleculeException {
        RingCurrentShift ringShifts = new RingCurrentShift();
        ringShifts.makeRingList(polymer.molecule);

        double ringRatio = 0.56;
        List<Atom> atoms = polymer.getAtoms();
        for (Atom atom : atoms) {
            String name = atom.getShortName();
            String aName = atom.getName();
            String nucName = atom.getEntity().getName();
            String nucAtom = nucName + "." + aName;
            if (RNA_REF_SHIFTS.containsKey(nucAtom)) {
                double basePPM = RNA_REF_SHIFTS.get(nucName + "." + aName);
                double ringPPM = ringShifts.calcRingContributions(atom.getSpatialSet(), 0, ringRatio);
                double ppm = basePPM + ringPPM;
                atom.setRefPPM(iRef, ppm);
            }
        }
    }

    public void predictLigandWithRingCurrent(Entity ligand, int iRef) throws InvalidMoleculeException {
        RingCurrentShift ringShifts = new RingCurrentShift();
        ringShifts.makeRingList(ligand.molecule);

        double ringRatio = 0.56;
        List<Atom> atoms = ligand.getAtoms();
        for (Atom atom : atoms) {
            String name = atom.getShortName();
            String aName = atom.getName();
            double ringPPM = ringShifts.calcRingContributions(atom.getSpatialSet(), 0, ringRatio);
            PPMv ppmV = atom.getRefPPM(iRef);
            if (ppmV != null) {
                double basePPM = ppmV.getValue();
                double ppm = basePPM + ringPPM;
                atom.setRefPPM(iRef, ppm);
            }
        }
    }

    public void predictWithShells(Entity aC, int iRef) {
        HosePrediction hosePred = HosePrediction.getDefaultPredictor();
        PathIterator pI = new PathIterator(aC);
        NodeValidator nodeValidator = new NodeValidator();
        pI.init(nodeValidator);
        pI.processPatterns();
        pI.setProperties("ar", "AROMATIC");
        pI.setHybridization();
        HoseCodeGenerator hoseGen = new HoseCodeGenerator();
        Map<Integer, String> result = hoseGen.genHOSECodes(aC, 5);
        for (Atom atom : aC.getAtoms()) {
            String predAtomType = "";
            Atom hoseAtom = null;
            double roundScale = 1.0;

            if (atom.getAtomicNumber() == 6) {
                hoseAtom = atom;
                predAtomType = "13C";
                roundScale = 10.0;
            } else if (atom.getAtomicNumber() == 1) {
                hoseAtom = atom.parent;
                predAtomType = "1H";
                roundScale = 100.0;
            }
            if ((hoseAtom != null) && (hoseAtom.getAtomicNumber() == 6)) {
                String hoseCode = (String) hoseAtom.getProperty("hose");
                System.out.println(atom.getShortName() + " " + hoseAtom.getShortName() + " " + hoseCode);
                if (hoseCode != null) {
                    PredictResult predResult;
                    HosePrediction.HOSEPPM hosePPM = new HosePrediction.HOSEPPM(hoseCode);
                    predResult = hosePred.predict(hosePPM, predAtomType);
                    HOSEStat hoseStat = predResult.getStat(predAtomType);
                    double shift = hoseStat.dStat.getPercentile(50);
                    shift = Math.round(shift * roundScale) / roundScale;
                    System.out.println(atom.getShortName() + " " + predResult.getShell() + " " + shift);
                    atom.setRefPPM(iRef, shift);
                }
            }
        }
    }
}
