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

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.Util;

public class AtomUtil {
    // FIXME  modify to use new atom data expression  like i.c{int < 0}

    public static int checkPattern(Atom[] atoms,
                                   String[] pattern, String[] relation) throws IllegalArgumentException {
        {
            int i;
            int idel;
            int jdel;

            int ndim = atoms.length;
            String[] atomPattern = new String[ndim];
            String[] resPattern = new String[ndim];
            String[] atomTest = new String[ndim];
            Residue[] resTest = new Residue[ndim];
            boolean[] patternIsSym = new boolean[ndim];

            int[] parent = new int[ndim];
            int dotPos;
            int iPass;

            String ires = null;
            String jres = null;

            for (i = 0; i < ndim; i++) {
                /*printf("%s %s\n",pattern[i],relation[i]);*/
                if (relation[i].length() > 0) {
                    if (!relation[i].startsWith("D")
                            && !relation[i].startsWith("d")) {
                        throw new IllegalArgumentException("Invalid Atom Relation");
                    }

                    parent[i] = Integer.parseInt(relation[i].substring(1));
                    parent[i]--;

                    /*printf("Parent %d %d %d\n",parent[i],atoms[atmNums[i]].parent,atmNums[parent[i]]);*/
                    if (((atoms[i].bonds.get(0))).end != atoms[parent[i]]) {
                        return (0);
                    }
                }
            }

            for (i = 0; i < ndim; i++) {
                atomTest[i] = atoms[i].name.toLowerCase();

                if (atoms[i].entity instanceof Residue) {
                    resTest[i] = (Residue) atoms[i].entity;
                }

                dotPos = pattern[i].indexOf(".");

                if (dotPos != -1) {
                    resPattern[i] = pattern[i].substring(0, dotPos);
                } else {
                    resPattern[i] = "";
                }

                atomPattern[i] = pattern[i].substring(dotPos + 1,
                        pattern[i].length()).toLowerCase();

                patternIsSym[i] = resPattern[i].startsWith("i")
                        || resPattern[i].startsWith("j");
            }

            for (iPass = 0; iPass < 2; iPass++) {
                for (i = 0; i < ndim; i++) {
                    if (iPass == 0) {
                        if (pattern[i].length() == 0) {
                            continue;
                        }

                        if (pattern[i].startsWith("*")) {
                            continue;
                        }

                        if (!Util.stringMatch(atomTest[i], atomPattern[i])) {
                            return (0);
                        }
                    }

                    if (!patternIsSym[i]) {
                        if (!resPattern[i].equals(resTest[i].number)) {
                            return (0);
                        }
                    }

                    if (Util.stringMatch(resPattern[i], "i[+-][0-9]")) {
                        idel = Integer.parseInt(resPattern[i].substring(1));

                        if (ires != null) {
                            String itest = String.valueOf(Integer.parseInt(ires)
                                    + idel);

                            if (!resPattern[i].equals(itest)) {
                                return (0);
                            }
                        }
                    } else if (resPattern[i].equals("i")) {
                        if ((ires != null) && (!ires.equals(resPattern[i]))) {
                            return (0);
                        } else {
                            ires = resPattern[i];
                        }
                    } else if (Util.stringMatch(resPattern[i], "j[+-][0-9]")) {
                        jdel = Integer.parseInt(resPattern[i].substring(1));

                        if (jres != null) {
                            String jtest = String.valueOf(Integer.parseInt(jres)
                                    + jdel);

                            if (!resPattern[i].equals(jtest)) {
                                return (0);
                            }
                        }
                    } else if (resPattern[i].equals("j")) {
                        if ((jres != null) && (!jres.equals(resPattern[i]))) {
                            return (0);
                        } else {
                            jres = resPattern[i];
                        }
                    } else {
                        return (-1);
                    }
                }
            }
        }

        return (1);
    }
}
