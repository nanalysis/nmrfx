/*
 * NMRFx Processor : A Program for Processing NMR Data 
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

 /*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.peaks;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.Residue;

/**
 *
 * @author brucejohnson
 */
public class PeakLabeller {

    static Pattern rPat = Pattern.compile("^(.+:)?(([a-zA-Z]+)([0-9\\-]+))\\.(['a-zA-Z0-9u]+)$");
    static Pattern rPat2 = Pattern.compile("^(.+:)?([0-9\\-]+)\\.(['a-zA-Z0-9u]+)$");
    static Pattern rPat3 = Pattern.compile("^(.+:)?([a-zA-Z]+)([0-9\\-]+)\\.(['a-zA-Z0-9u]+)$");

    public static void labelWithSingleResidueChar(PeakList peakList) {
        peakList.peaks().stream().forEach(pk -> {
            for (PeakDim peakDim : pk.getPeakDims()) {
                String label = peakDim.getLabel();
                Matcher matcher1 = rPat.matcher(label);
                if (!matcher1.matches()) {
                    Matcher matcher2 = rPat2.matcher(label);
                    if (matcher2.matches()) {
                        String chain = matcher2.group(1);
                        String resNum = matcher2.group(2);
                        String aName = matcher2.group(3);
                        String atomSpec = chain + resNum + "." + aName;
                        Atom atom = MoleculeBase.getAtomByName(atomSpec);
                        if (atom != null) {
                            if (atom.getEntity() instanceof Residue) {
                                char oneChar = ((Residue) atom.getEntity()).getOneLetter();
                                StringBuilder sBuilder = new StringBuilder();
                                if (chain != null) {
                                    sBuilder.append(chain);
                                }
                                sBuilder.append(oneChar).append(resNum);
                                sBuilder.append(".").append(aName);
                                peakDim.setLabel(sBuilder.toString());
                            }
                        }
                    }
                }
            }
        });
    }

    public static void removeSingleResidueChar(PeakList peakList) {
        peakList.peaks().stream().forEach(pk -> {
            for (PeakDim peakDim : pk.getPeakDims()) {
                String label = peakDim.getLabel();
                Matcher matcher1 = rPat3.matcher(label);
                if (matcher1.matches()) {
                    String chain = matcher1.group(1);
                    String resNum = matcher1.group(3);
                    String aName = matcher1.group(4);
                    StringBuilder sBuilder = new StringBuilder();
                    if (chain != null) {
                        sBuilder.append(chain);
                    }
                    sBuilder.append(resNum);
                    sBuilder.append(".").append(aName);
                    peakDim.setLabel(sBuilder.toString());
                }
            }
        }
        );
    }
}
