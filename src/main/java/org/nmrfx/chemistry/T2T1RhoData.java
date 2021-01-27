/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.chemistry;

import java.util.List;
import java.util.Map;

/**
 *
 * @author mbeckwith
 */
public class T2T1RhoData extends RelaxationData {

        Double RexValue; 
        Double RexError;

        public T2T1RhoData(String ID, relaxTypes expType, List<Atom> extraAtoms, double field, double temperature,
                Double value, Double error, Double RexValue, Double RexError, Map<String, String> extras) {
            
            super(ID, expType, extraAtoms, field, temperature, value, error, extras);
            
            this.RexValue = RexValue; 
            this.RexError = RexError;
        }
        
        public Double getRexValue() {
            return RexValue;
        }

        public Double getRexError() {
            return RexError;
        }
        
}
