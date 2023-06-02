/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.structure.chemistry.predict;

import org.nmrfx.chemistry.Residue;

import java.util.ArrayList;
import java.util.List;

/**
 * @author brucejohnson
 */
public class ProteinResidueAnalysis {

    static final String[] CLASSES8 = {"H", "G", "E", "B", "I", "S", "T", "C"};
    static final String[] CLASSES3 = {"HG", "ISTC", "EB"};
    static final String[] CLASSES4 = {"HGI", "T", "SC", "EB"};
    Residue residue;
    double[] state8;
    double[] state3;
    double[] state4;
    double zIRD;
    List<Integer> sortedStates4;

    ProteinResidueAnalysis(Residue residue, double zIRD, double[] state8) {
        this.residue = residue;
        this.state8 = state8.clone();
        this.state3 = new double[3];
        this.state4 = new double[4];
        int iClass = 0;
        for (var classGroup : CLASSES3) {
            int jClass = 0;
            for (var className : CLASSES8) {
                if (classGroup.contains(className)) {
                    state3[iClass] += state8[jClass];
                }
                jClass++;
            }
            iClass++;
        }
        iClass = 0;
        for (var classGroup : CLASSES4) {
            int jClass = 0;
            for (var className : CLASSES8) {
                if (classGroup.contains(className)) {
                    state4[iClass] += state8[jClass];
                }
                jClass++;
            }
            iClass++;
        }
        sortedStates4 = sortStates(state4);
        this.zIRD = zIRD;
    }

    public List<Integer> getSortedStates4() {
        return sortedStates4;
    }

    private List<Integer> sortStates(double[] states) {
        List<Integer> stateList = new ArrayList<>();
        for (int i = 0; i < states.length; i++) {
            stateList.add(i);
        }
        stateList.sort((a, b) -> Double.compare(states[b], states[a]));
        return stateList;
    }

    public static String[] getClasses3() {
        return CLASSES3;
    }

    public static String[] getClasses4() {
        return CLASSES4;
    }

    public static String[] getClasses8() {
        return CLASSES8;
    }

    public double[] getState8() {
        return state8;
    }

    public double[] getState3() {
        return state3;
    }

    public double[] getState4() {
        return state4;
    }

    public double getHelix() {
        return state3[0];
    }

    public double getExtended() {
        return state3[1];
    }

    public double getCoil() {
        return state3[2];
    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(residue.getNumber());
        for (var v : state4) {
            sBuilder.append(" ").append(String.format("%.3f", v));
        }
        return sBuilder.toString();
    }

}
