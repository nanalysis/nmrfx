/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.peaks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Bruce Johnson
 */
public class Solvent {

    final String name;
    final String isoname;
    final List<String> synonyms;
    final Double h2oShift;
    List<SolventPeak> hPeaks = new ArrayList<>();
    List<SolventPeak> cPeaks = new ArrayList<>();

    class SolventPeak {

        double shift;
        double j;
        int n;

        SolventPeak(double shift, double j, int n) {
            this.shift = shift;
            this.j = j;
            this.n = n;
        }

        boolean overlaps(double sf, double testShift) {
            double tol = 0.05;
            double width = n * (j / sf) + tol;
            double min = shift - width / 2;
            double max = shift + width / 2;
            return (testShift >= min) && (testShift <= max);
        }
    }

    public Solvent(String name, String isoname, List<String> synonyms, Double h2oShift) {
        this.name = name;
        this.isoname = isoname;
        this.synonyms = new ArrayList<>();
        this.synonyms.addAll(synonyms);
        this.h2oShift = h2oShift;
    }

    public void addShifts(String nucleus, List<Map<String, Number>> shifts) {
        for (Map<String, Number> map : shifts) {
            if (map.containsKey("s")) {
                double shift = map.get("s").doubleValue();
                double j = map.get("j").doubleValue();
                int n = map.get("n").intValue();
                SolventPeak peak = new SolventPeak(shift, j, n);
                if (nucleus.equals("H")) {
                    hPeaks.add(peak);
                } else if (nucleus.equals("C")) {
                    cPeaks.add(peak);
                } else {
                    throw new IllegalArgumentException("Invalid nucleus " + nucleus);
                }
            }

        }
    }

    public boolean overlapsH2O(String nucleus, double sf, double shift) {
        boolean result = false;
        double tol = 5.0 / sf;

        if (nucleus.equals("H") && (h2oShift != null)) {
            double delta = Math.abs(h2oShift - shift);
            result = delta < tol;
        }
        return result;
    }

    public boolean overlaps(String nucleus, double sf, double shift) {
        List<SolventPeak> solventPeaks = nucleus.equals("H") ? hPeaks : cPeaks;
        boolean result = false;
        for (SolventPeak solventPeak : solventPeaks) {
            if (solventPeak.overlaps(sf, shift)) {
                result = true;
                break;
            }
        }
        return result;
    }

}
