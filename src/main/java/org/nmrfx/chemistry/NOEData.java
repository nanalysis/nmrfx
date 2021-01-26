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

/**
 *
 * @author mbeckwith
 */
public class NOEData {
        
        String ID;
        Atom pairAtom;
        double field;
        double temperature;
        Double value; 
        Double error;
        Map<String, String> extras;

        public NOEData(String ID, Atom pairAtom, double field, double temperature,
                Double value, Double error, Map<String, String> extras) {
            
            this.ID = ID;
            this.pairAtom = pairAtom;
            this.field = field;
            this.temperature = temperature;
            this.value = value; 
            this.error = error;
            this.extras = extras;
        }
        
        public String getID() {
            return ID;
        }
        
        public Atom getPairAtom() {
            return pairAtom;
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
        
        public static Collection<NOEData> getNOEData(List<Atom> atoms) {
            List<NOEData> noeDataSet = new ArrayList<>();
            atoms.forEach((atom) -> {
                atom.noeData.keySet().forEach((key) -> {
                    noeDataSet.add(atom.getNOEData(key));
                });
            });
            
            return noeDataSet;
        }
        
}
