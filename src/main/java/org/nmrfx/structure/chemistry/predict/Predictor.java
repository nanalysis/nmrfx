/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.predict;

import java.util.Map;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Entity;
import org.nmrfx.structure.chemistry.HoseCodeGenerator;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.Polymer;
import org.nmrfx.structure.chemistry.ProteinPredictor;
import org.nmrfx.structure.chemistry.Residue;
import org.nmrfx.structure.chemistry.miner.NodeValidator;
import org.nmrfx.structure.chemistry.miner.PathIterator;

/**
 *
 * @author Bruce Johnson
 */
public class Predictor {

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
            }
        }
        for (Entity entity:mol.getLigands()) {
            predictWithShells(entity, iRef);
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
