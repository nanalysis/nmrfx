/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.optimization;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * @author tedcolon
 */
public class HausdorffDistance {

    private final Set<Collection> setA;
    private final Set<Collection> setB;

    public HausdorffDistance(Set<Collection> setA, Set<Collection> setB) {
        this.setA = setA;
        this.setB = setB;
    }

    public static Double distance(Collection<Double> ptA, Collection<Double> ptB) throws IllegalArgumentException {
        if (ptA.size() != ptB.size()) {
            throw new IllegalArgumentException(String.format("Collection sizes do not match. (%d != %d)", ptA.size(), ptB.size()));
        }
        Iterator<Double> aCoor = ptA.iterator(); // coordinates for ptA, i.e. (X1, Y1, Z1)
        Iterator<Double> bCoor = ptB.iterator(); // coordinates for ptB, i.e. (X2, Y2, Z2)
        double sum = 0.0;
        double diff;
        while (aCoor.hasNext() && bCoor.hasNext()) {
            diff = bCoor.next() - aCoor.next();
            sum += Math.pow(diff, 2);
        }
        return Math.sqrt(sum);
    }

    public Double calculate() throws IllegalArgumentException {
        // brute force algorithm to calculate Hausdorff distance b/t sets A,B
        // - find minimum distances for each pt from A(i) to all B(j) (asymmetric/unidirectional).
        // - find the maximum distance out of all minimum distances calculated.
        if (setA.isEmpty() || setB.isEmpty()) {
            throw new IllegalArgumentException("Empty sets are invalid.");
        }
        Double finalDist = 0.0;
        Double shortest;
        Double curDist;
        for (Collection ai : setA) {
            shortest = Double.POSITIVE_INFINITY;
            for (Collection bj : setB) {
                curDist = distance(ai, bj);
                if (curDist < shortest) {
                    shortest = curDist;
                }
            }
            if (shortest > finalDist) {
                finalDist = shortest;
            }
        }
        return finalDist;
    }
}
