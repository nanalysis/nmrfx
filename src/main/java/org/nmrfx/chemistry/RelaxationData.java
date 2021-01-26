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
            T1("T1"), T2("T2"), T1RHO("T1rho");
            
            private final String name;
            
            private relaxTypes(final String name) {
                this.name = name;
            }

            public String getName() {
                return name;
            }
        }
        
        String ID;
        relaxTypes expType;
        double field;
        double temperature;
        Map<String, Double> values; 
        Map<String, Double> errors;
        Map<String, String> extras;

        public RelaxationData(String ID, relaxTypes expType, double field, double temperature,
                Map<String, Double> values, Map<String, Double> errors, Map<String, String> extras) {
            
            this.ID = ID;
            this.expType = expType;
            this.field = field;
            this.temperature = temperature;
            this.values = values; 
            this.errors = errors;
            this.extras = extras;
        }
        
        public String getID() {
            return ID;
        }
        
        public relaxTypes getExpType() {
            return expType;
        }

        public double getField() {
            return field;
        }
        
        public double getTemperature() {
            return temperature;
        }

        public Map<String, Double> getValues() {
            return values;
        }

        public Map<String, Double> getErrors() {
            return errors;
        }

        public Map<String, String> getExtras() {
            return extras;
        }
        
        public static Collection<RelaxationData> getRelaxationData(List<Atom> atoms) {
            List<RelaxationData> relaxDataSet = new ArrayList<>();
            atoms.forEach((atom) -> {
                atom.relaxData.keySet().forEach((key) -> {
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
