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
package org.nmrfx.structure.rna;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.structure.chemistry.Molecule;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Bruce Johnson
 */
public class RNALabels {

    static Pattern pattern1 = Pattern.compile("(.*):(([0-9]*)-([0-9]*))");
    static Pattern pattern2 = Pattern.compile("([0-9]+)-([0-9]+)");
    static Pattern pattern3 = Pattern.compile("([^:]+):?");

    static Pattern pattern4 = Pattern.compile("(\\w+):([ACGU](,[ACGU])*)?([0-9]+)?-?([0-9]+)?\\.([a-zA-Z]+\\w*)");
    static Pattern pattern = Pattern.compile("(\\*|\\w+):([ACGU](,[ACGU])*)?((([0-9]+)?-?([0-9]+)?)|\\*)\\.(([HCN][^, ]*)(,[HCN][^, ]*)*)$");

    public void parse(Molecule molecule, String selGroup) {
        String[] groups = selGroup.split(" ");
        if (groups.length < 2) {
            throw new IllegalArgumentException("Improper selGroup format " + selGroup);
        }
        String entityRes = groups[0];
        Matcher matcher = pattern.matcher(entityRes);
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

    public void parseSelGroups(Molecule molecule, String selGroups) {

        List<Atom> allAtoms = molecule.getAtomArray();
        if ((selGroups == null) || (selGroups.trim().length() == 0)) {
            allAtoms.forEach((atom) -> {
                atom.setActive(true);
            });

        } else {
            allAtoms.forEach((atom) -> {
                atom.setActive(false);
            });
            String[] selGroupSets = selGroups.split(";");
            for (String selGroupSet : selGroupSets) {
                String[] groups = selGroupSet.split(" ");
                for (String group : groups) {
                    group = group.trim();
                    if (group.length() > 0) {
                        parseGroup(molecule, group);
                    }
                }
            }
        }
    }

    public void parseGroup(Molecule molecule, String group) {
        SelGroup selGroup = parseSelGroup(group);
        List<Polymer> polymers = molecule.getPolymers();
        String entityStr = selGroup.entityStr;
        for (Polymer polymer : polymers) {
            if (entityStr.equals("*") || entityStr.equals(polymer.getName())) {
                List<Residue> residues = polymer.getResidues();
                for (Residue residue : residues) {
                    String resName = residue.getName();
                    String resNumStr = residue.getNumber();

                    int resNum = Integer.parseInt(resNumStr);
                    boolean resMatches = checkResType(resName, selGroup.resTypes);

                    if (resMatches) {
                        if ((selGroup.firstRes != null) && (resNum < selGroup.firstRes)) {
                            continue;
                        }
                        if ((selGroup.lastRes != null) && (resNum > selGroup.lastRes)) {
                            continue;
                        }

                        List<Atom> atoms = residue.getAtoms();
                        for (Atom atom : atoms) {
                            NucleicAcidAtomType naType = new NucleicAcidAtomType(atom);
                            if (naType.hydroxyl) {
                                continue;
                            }
                            boolean ok = checkAtom(atom.getName(), atom.getElementName(), selGroup.gAtomNames, naType.sugar, naType.exchangable);
                            if (ok) {
                                atom.setActive(true);
                            }
                        }
                    }

                }

            }
        }
    }

    public static class NucleicAcidAtomType {

        final boolean hydroxyl;
        final boolean sugar;
        final boolean exchangable;

        public NucleicAcidAtomType(Atom atom) {
            if ((atom.getAtomicNumber() == 1) && (atom.parent != null) && (atom.getParent().getElementName().equals("O"))) {
                hydroxyl = true;
            } else {
                hydroxyl = false;
            }
            if (atom.getName().endsWith("'") || atom.getName().endsWith("\"")) {
                sugar = true;
            } else {
                sugar = false;
            }
            if (atom.getElementName().equals("H") && (atom.parent != null) && (atom.getParent().getElementName().equals("N") || hydroxyl)) {
                exchangable = true;
            } else {
                exchangable = false;
            }
        }
    }

    public static class SelGroup {

        public final Integer firstRes;
        public final Integer lastRes;
        public final String entityStr;
        public final String[] resTypes;
        public final String[] gAtomNames;

        SelGroup(Integer startRes, Integer endRes, String entityStr, String[] resTypes, String[] gAtomNames) {
            this.firstRes = startRes;
            this.lastRes = endRes;
            this.entityStr = entityStr;
            this.resTypes = resTypes;
            this.gAtomNames = gAtomNames;
        }
    }

    public static SelGroup parseSelGroup(String group) {
        Matcher matcher = pattern.matcher(group.trim());
        Integer startRes = null;
        Integer endRes = null;
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Group " + group + " can't be parsed");
        }
        String entityStr = matcher.group(1);//2,4,,5,6
        String resTypeStr = matcher.group(2);
        String resRangeStr = matcher.group(4);
        String startResStr = matcher.group(6);
        String endResStr = matcher.group(7);
        if ((startResStr != null) && (startResStr.length() > 0)) {
            startRes = Integer.parseInt(startResStr);
        }
        if ((endResStr != null) && (endResStr.length() > 0)) {
            endRes = Integer.parseInt(endResStr);
        }
        String atomStr = matcher.group(8);
        String[] resTypes = new String[0];
        if (resTypeStr != null) {
            resTypes = resTypeStr.split(",");
        }
        String[] gAtomNames = atomStr.split(",");
        if (entityStr.equals("")) {
            entityStr = "*";
        }

        SelGroup selGroup = new SelGroup(startRes, endRes, entityStr, resTypes, gAtomNames);
        return selGroup;

    }

    public static boolean checkResType(String resName, String[] resTypes) {
        boolean resMatches = resTypes.length == 0;
        for (String resType : resTypes) {
            if (resType.equals(resName)) {
                resMatches = true;
                break;
            }
        }
        return resMatches;
    }

    public static boolean checkAtom(String aName, String atomElement, String[] gAtomNames, boolean ribose, boolean exchangable) {
        boolean ok = false;
        for (String gAtomName : gAtomNames) {
            if (ribose && atomElement.equals("H") && gAtomName.equals("Hr")) {
                ok = true;
                break;
            } else if (ribose && atomElement.equals("C") && gAtomName.equals("Cr")) {
                ok = true;
                break;
            } else if (!ribose && !exchangable && atomElement.equals("H") && gAtomName.equals("Hn")) {
                ok = true;
                break;
            } else if (!ribose && !exchangable && atomElement.equals("C") && gAtomName.equals("Cn")) {
                ok = true;
                break;
            } else if (exchangable && atomElement.equals("H") && gAtomName.equals("He")) {
                ok = true;
                break;
            } else if (exchangable && atomElement.equals("N") && gAtomName.equals("Ne")) {
                ok = true;
                break;
            } else if (gAtomName.equals(aName)) {
                ok = true;
                break;
            }
        }
        return ok;

    }

}
