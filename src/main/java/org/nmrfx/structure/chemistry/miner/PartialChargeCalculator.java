package org.nmrfx.structure.chemistry.miner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nmrfx.chemistry.AtomContainer;
import org.nmrfx.chemistry.IAtom;
import org.nmrfx.chemistry.IBond;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Bond;
import org.nmrfx.chemistry.Order;

public class PartialChargeCalculator {

    AtomContainer ac = null;
    Map tripletMap = new HashMap();

    public PartialChargeCalculator(AtomContainer ac) {
        this.ac = ac;
    }

    public void execute(double hardnessScale) {
        int nAtoms = ac.getAtomCount();
        ArrayList<ArrayList<PCBond>> pcBonds = new ArrayList<>();
        HashMap<IAtom, Integer> atomMap = new HashMap<>();
        for (int iAtom = 0; iAtom < nAtoms; iAtom++) {
            IAtom atom1 = ac.getAtom(iAtom);
            atomMap.put(atom1, iAtom);
        }
        double[] electroNegativities = new double[nAtoms];
        double[] hardnesses = new double[nAtoms];
        double[] formalCharges = new double[nAtoms];
        int[] chargeGroups = new int[nAtoms];
        for (int iAtom = 0; iAtom < nAtoms; iAtom++) {
            IAtom atom1 = ac.getAtom(iAtom);
            if (atom1.getProperty("elec") == null) {
                electroNegativities[iAtom] = 40.0;
                System.err.println("no elec " + atom1.getSymbol());
            } else {
                electroNegativities[iAtom] = Double.parseDouble((String) atom1.getProperty("elec"));
            }
            if (atom1.getProperty("hard") == null) {
                System.err.println("no hard " + atom1.getSymbol());
                hardnesses[iAtom] = 100.0;
            } else {
                hardnesses[iAtom] = (Double) Double.parseDouble((String) atom1.getProperty("hard"));
            }
            chargeGroups[iAtom] = -1;

            ArrayList<PCBond> pcBonds2 = new ArrayList<>();
            pcBonds.add(pcBonds2);
            List<IBond> bonds = ac.getConnectedBondsList(atom1);
            IAtom[] atoms = new Atom[bonds.size()];
            int nAtoms2 = 0;
            for (IBond bond : bonds) {
                IAtom atom = bond.getConnectedAtom(atom1);
                int atomNum = atomMap.get(atom);
                int bondType = -1;
                if (bond.getFlag(Bond.ISAROMATIC)) {
                    bondType = PCBond.AROMATIC;
                } else if (bond.getOrder() == Order.SINGLE) {
                    bondType = PCBond.SINGLE;
                } else if (bond.getOrder() == Order.DOUBLE) {
                    bondType = PCBond.DOUBLE;
                } else if (bond.getOrder() == Order.TRIPLE) {
                    bondType = PCBond.TRIPLE;
                }
                PCBond pcBond = new PCBond(atomNum, bondType);
                pcBonds2.add(pcBond);
                atoms[nAtoms2++] = atom;
            }
            for (int j = 0; j < (nAtoms2); j++) {
                List<IAtom> neighbors = ac.getConnectedAtomsList(atoms[j]);
                neighbors.stream().filter(n -> n != atom1).forEach(neighbor -> {
                    int atomNum = atomMap.get(neighbor);
                    PCBond pcBond = new PCBond(atomNum, PCBond.ONE_THREE);
                    pcBonds2.add(pcBond);
                });
            }
        }
        PartialCharge pCharge = new PartialCharge();
        double[] pcharges = pCharge.optimizeCharges(electroNegativities, hardnesses, formalCharges, chargeGroups, pcBonds, hardnessScale);
        for (int iAtom = 0; iAtom < nAtoms; iAtom++) {
            IAtom atom1 = ac.getAtom(iAtom);
            atom1.setProperty("pcharge", pcharges[iAtom]);
        }

        for (int iAtom = 0; iAtom < nAtoms; iAtom++) {
            ArrayList<PCBond> pcBonds2 = pcBonds.get(iAtom);
            double sphere1Charges = 0.0;
            double sphere2Charges = 0.0;
            for (PCBond pcBond : pcBonds2) {
                int partner = pcBond.getPartner();
                int bondType = pcBond.getType();
                if (bondType == PCBond.ONE_THREE) {
                    sphere2Charges += pcharges[partner];
                } else {
                    sphere1Charges += pcharges[partner];
                }
            }
            IAtom atom1 = ac.getAtom(iAtom);
            atom1.setProperty("pcharge1", sphere1Charges);
            atom1.setProperty("pcharge2", sphere2Charges);
        }
    }
}
