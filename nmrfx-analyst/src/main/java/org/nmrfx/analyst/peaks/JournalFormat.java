/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.analyst.peaks;

import org.apache.commons.lang3.text.StrSubstitutor;
import org.nmrfx.datasets.Nuclei;
import org.nmrfx.peaks.*;
import org.nmrfx.processor.datasets.Dataset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author brucejohnson
 */
public class JournalFormat {

    String name;
    String solventMode;
    String header;
    String m;
    String s;
    String o;
    String sep;
    Integer jPrec;
    Integer ppmPrec;
    Double broad;

    public String getName() {
        return name;
    }

    public String genOutput(PeakList peakList) {
        boolean ascending = true;
        Dataset dataset = Dataset.getDataset(peakList.getDatasetName());
        double sfrq = dataset.getSf(0);
        Nuclei nucleus = dataset.getNucleus(0);
        String nucName = nucleus.getName();
        String isotop = nucleus.getNumber();
        String solvent = dataset.getSolvent();
        String solventString;
        StringBuilder sBuilder = new StringBuilder();

        switch (solventMode) {
            case "canonical-iso":
                solventString = Solvents.canonicalIso(solvent);
                break;
            case "canonical":
                solventString = Solvents.canonical(solvent);
                break;
            default:
                solventString = solvent;

        }
        Map<String, String> valuesMap = new HashMap<>();
        valuesMap.put("sol", solventString);
        valuesMap.put("iso", String.valueOf(isotop));
        valuesMap.put("nuc", nucName);
        valuesMap.put("frq", String.format("%.2f", sfrq));
        StrSubstitutor strSub = new StrSubstitutor(valuesMap);
        String headerString = strSub.replace(header);
        sBuilder.append(headerString).append(" ");

        Map<String, Object> peakValuesMap = new HashMap<>();
        String ppmFormat = "%." + ppmPrec + "f";
        String jFormat = "%." + jPrec + "f";
        for (Peak peak : peakList.peaks()) {
            if (peak.getType() != Peak.COMPOUND) {
                continue;
            }
            Multiplet multiplet = peak.getPeakDim(0).getMultiplet();

            double ppm = multiplet.getPeakDim().getChemShiftValue();
            int nH = (int) Math.round(multiplet.getVolume() / peakList.getScale());
            double bounds = multiplet.getPeakDim().getBounds().doubleValue();
            double lw = multiplet.getPeakDim().getLineWidthHz();
            double ppm1;
            double ppm2;
            if (ascending) {
                ppm1 = ppm - bounds / 2.0;
                ppm2 = ppm + bounds / 2.0;
            } else {
                ppm1 = ppm + bounds / 2.0;
                ppm2 = ppm - bounds / 2.0;

            }
            String coupString = "";
            Coupling coupling = multiplet.getCoupling();
            if (coupling instanceof CouplingPattern) {
                CouplingPattern cPattern = (CouplingPattern) coupling;
                double[] jArray = cPattern.getValues();

                List<String> jStrValues = new ArrayList<>();
                for (double jValue : jArray) {
                    jStrValues.add(String.format(jFormat, jValue));
                }

                coupString = String.join(sep, jStrValues);

            }
            String ppmStr = String.format(ppmFormat, ppm);
            String ppm1Str = String.format(ppmFormat, ppm1);
            String ppm2Str = String.format(ppmFormat, ppm2);

            String mulString = Multiplets.getCouplingPattern(multiplet);
            String ppmInt = String.format("%d", nH);
            peakValuesMap.put("ppm", ppmStr);
            peakValuesMap.put("ppm1", ppm1Str);
            peakValuesMap.put("ppm2", ppm2Str);
            peakValuesMap.put("n", ppmInt);
            if (lw > broad) {
                peakValuesMap.put("mul", "br " + mulString);
            } else {
                peakValuesMap.put("mul", mulString);

            }

            peakValuesMap.put("cpl", coupString);

            StrSubstitutor peakSub = new StrSubstitutor(peakValuesMap);
            String peakString = "";
            if (coupling instanceof CouplingPattern) {
                peakString = peakSub.replace(o);
            } else if (coupling instanceof Singlet) {
                peakString = peakSub.replace(s);
            } else {
                peakString = peakSub.replace(m);

            }
            sBuilder.append(" ").append(peakString);
        }
        return sBuilder.toString();
    }

}

/*
   proc getJournalFormattedAnalysis {journal mode} {
        global JPFormatData
        set plist [getAnalyzedList]
        if {$plist == ""} {
            return ""
        }
        set dataset [nv_peak dataset $plist]
        set sfrq [lindex [nv_peak sf $plist] 0]
        set frq [format "%.0f" $sfrq]
        set tn [nv_dataset nucleus $dataset 1]
        if {![regexp {([0-9]+)([A-Za-z]+)} $tn all iso nuc]} {
            if {![regexp {([A-Za-z]+)([0-9]+)} $tn all nuc iso]} {
                error "Can't get isotope and nucleus from transmitter $tn"
            }
        }

        set iso [makeSuperscriptsHTML $iso]
        set solvent [nv_dataset solvent $dataset]

        switch $JPFormatData($journal,solvent) {
            canonical-iso {
                set sol [makeSubscriptsHTML [canonSolvent iso $solvent]]
            }
            canonical {
                set sol [makeSubscriptsHTML [canonSolvent std $solvent]]
            }
            default {
                set sol [makeSubscriptsHTML $solvent]
            }
        }


        set result2 [subst $JPFormatData($journal,header)]

        if {[string equal $nuc "H"]} {
            append result2 [getDetailedFormat $journal $mode]
        } else {
            append result2 [getSimpleFormat $journal $mode]
        }




        set result2 [string trim  "$result2"]
        set result2 [string trimright "$result2" ";"]
        set result2 [string trimright "$result2" ","]
        append result2 "."
        set result2 "<html><p style=\"font-size:9pt\">$result2</p></html>"
        #set result2 [formatToRTF $result2]
        return $result2
    }

 */
