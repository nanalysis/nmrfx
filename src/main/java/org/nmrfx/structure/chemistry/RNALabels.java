/*
 * NMRFx Structure : A Program for Calculating Structures 
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
package org.nmrfx.structure.chemistry;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Bruce Johnson
 */
public class RNALabels {

    static Pattern pattern1 = Pattern.compile("(.*):(([0-9]*)-([0-9]*))");
    static Pattern pattern2 = Pattern.compile("([0-9]+)-([0-9]+)");
    static Pattern pattern3 = Pattern.compile("([^:]+):?");

    public void parse(Molecule molecule, String selGroup) {
        String[] groups = selGroup.split(" ");
        if (groups.length < 2) {
            throw new IllegalArgumentException("Improper selGroup format " + selGroup);
        }
        String entityRes = groups[0];
        Matcher matcher = pattern1.matcher(entityRes);
        Integer startRes = null;
        Integer endRes = null;
        String eS = "";
        if (matcher.matches()) {
            eS = matcher.group(1);
            String group1 = matcher.group(3);
            String group2 = matcher.group(4);
            startRes = Integer.parseInt(group1);
            endRes = Integer.parseInt(group2);
        } else {
            matcher = pattern2.matcher(entityRes);
            if (matcher.matches()) {
                String group1 = matcher.group(1);
                String group2 = matcher.group(2);
                startRes = Integer.parseInt(group1);
                endRes = Integer.parseInt(group2);

            } else {
                matcher = pattern3.matcher(entityRes);
                if (matcher.matches()) {
                    eS = matcher.group(1);
                }
            }
        }

        if (eS.equals("")) {
            eS = "All";
        }
        List<Atom> allAtoms = molecule.getAtomArray();
        for (Atom atom : allAtoms) {
            atom.setActive(false);
        }
        List<Polymer> polymers = molecule.getPolymers();
        for (Polymer polymer : polymers) {
            if (eS.equals("All") || eS.equals(polymer.getName())) {
                List<Residue> residues = polymer.getResidues();
                for (int i = 1; i < groups.length; i++) {
                    String group = groups[i];
                    String[] groupParts = group.split("\\.");
                    if (groupParts.length < 2) {
                        throw new IllegalArgumentException("Invalid group " + group);
                    }
                    String gNuc = groupParts[0];
                    String gAtoms = groupParts[1];

                    for (Residue residue : residues) {
                        if (residue.getName().equals(gNuc)) {
                            List<Atom> atoms = residue.getAtoms();
                            String[] gAtomNames = gAtoms.split(",");
                            for (Atom atom : atoms) {
                                boolean ribose = false;
                                boolean exchangable = false;
                                if ((atom.parent != null) && (atom.getParent().getElementName().equals("O"))) {
                                    continue;
                                }
                                if (atom.getName().endsWith("'") || atom.getName().endsWith("\"")) {
                                    ribose = true;
                                } else if (atom.getElementName().equals("H") && atom.getParent().getElementName().equals("C")) {
                                    exchangable = false;
                                } else if (atom.getElementName().equals("C")) {
                                    exchangable = false;
                                } else {
                                    exchangable = true;
                                }
                                boolean ok = false;
                                for (String gAtomName : gAtomNames) {
                                    if (ribose && atom.getElementName().equals("H") && gAtomName.equals("Hr")) {
                                        ok = true;
                                        break;
                                    } else if (ribose && atom.getElementName().equals("C") && gAtomName.equals("Cr")) {
                                        ok = true;
                                        break;
                                    } else if (!ribose && !exchangable && atom.getElementName().equals("H") && gAtomName.equals("Hn")) {
                                        ok = true;
                                        break;
                                    } else if (!ribose && !exchangable && atom.getElementName().equals("C") && gAtomName.equals("Cn")) {
                                        ok = true;
                                        break;
                                    } else if (exchangable && atom.getElementName().equals("H") && gAtomName.equals("He")) {
                                        ok = true;
                                        break;
                                    } else if (exchangable && atom.getElementName().equals("N") && gAtomName.equals("Ne")) {
                                        ok = true;
                                        break;
                                    } else if (gAtomName.equals(atom.getName())) {
                                        ok = true;
                                        break;
                                    }
                                }
                                if (ok) {
                                    atom.setActive(true);
                                }
                            }
                        }

                    }

                }
            }
        }
    }
}
