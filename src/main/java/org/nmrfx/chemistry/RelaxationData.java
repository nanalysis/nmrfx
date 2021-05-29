/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.chemistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author mbeckwith
 */
public class RelaxationData {

    public enum relaxTypes {
        T1("T1"), T2("T2"), T1RHO("T1rho"), NOE("NOE"), S2("S2");

        private final String name;

        private relaxTypes(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public String ID;
    public relaxTypes expType;
    public List<Atom> extraAtoms;
    public double field;
    public double temperature;
    public Double value;
    public Double error;
    public Map<String, String> extras;

    public RelaxationData(String ID, relaxTypes expType, List<Atom> extraAtoms, double field, double temperature,
            Double value, Double error, Map<String, String> extras) {

        this.ID = ID;
        this.expType = expType;
        this.extraAtoms = extraAtoms;
        this.field = field;
        this.temperature = temperature;
        this.value = value;
        this.error = error;
        this.extras = extras;
    }

    public String getID() {
        return ID;
    }

    public relaxTypes getExpType() {
        return expType;
    }

    public List<Atom> getExtraAtoms() {
        return extraAtoms;
    }

    public double getField() {
        return field;
    }

    public double getTemperature() {
        return temperature;
    }

    public Double getValue() {
        return value;
    }

    public Double getError() {
        return error;
    }

    public Map<String, String> getExtras() {
        return extras;
    }

    public static Collection<RelaxationData> getRelaxationData(List<Atom> atoms) {
        List<RelaxationData> relaxDataSet = new ArrayList<>();
        atoms.forEach((atom) -> {
            atom.getRelaxationData().keySet().forEach((key) -> {
                relaxDataSet.add(atom.getRelaxationData(key));
            });
        });
        return relaxDataSet;
    }

    public static Set<relaxTypes> getExpTypes(MoleculeBase molecule) {
        Set<relaxTypes> expTypeSet = new TreeSet<>();
        Collection<RelaxationData> molRelaxData = getRelaxationData(molecule.getAtomArray());
        molRelaxData.forEach((relaxData) -> {
            expTypeSet.add(relaxData.getExpType());
        });

        return expTypeSet;
    }

//        public static Collection<RelaxationData> getRelaxationData(MoleculeBase molecule, Entity entity) {
//            List<Atom> atoms = new ArrayList<>();
//            if (molecule != null) {
//                atoms = molecule.getAtomArray();
//            }
//            if (entity instanceof Polymer) {
//                atoms = ((Polymer) entity).getAtomArray();
//            }
//            if (entity instanceof Residue) {
//                atoms = ((Residue) entity).getAtomArray();
//            }
//            
//            return getRelaxationData(atoms);
//        }
}
