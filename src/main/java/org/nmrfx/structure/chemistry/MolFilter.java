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

import org.nmrfx.structure.utilities.Util;
import java.util.ArrayList;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MolFilter {

    public Vector atomNames = new Vector(4, 4);
    ArrayList<CoordsetAndEntity> csAndENames = new ArrayList();
    String molName = "*";
    public String firstRes = "";
    public String lastRes = "";
    public String firstResType = "*";
    public String lastResType = "*";
    public int structureNum = 0;
    String string = "";
    private static String resresPatternStr = "(([a-zA-Z]?-?[0-9]+)-)?([a-zA-Z]?-?[0-9]+)";
    private static Pattern resresPattern = Pattern.compile(resresPatternStr);

    private class CoordsetAndEntity {

        String entityName = "*";
        String coordSetName = "*";
        String oneName = "";
        boolean checkOneName = false;

        CoordsetAndEntity(final String coordSetName, final String entityName) {
            if (entityName.length() != 0) {
                this.entityName = entityName;
            }
            if (coordSetName.length() != 0) {
                this.coordSetName = coordSetName;
            }
        }

        CoordsetAndEntity(final String oneName) {
            this.oneName = oneName;
            checkOneName = true;
        }
    }

    public MolFilter(String string) {
        this.string = string;
        String resAtom;
        String firstResTmp = "*";
        String lastResTmp = "*";
        atomNames.setSize(0);

        String anames;
        String aname;
        int commaPos;

        int colonPos = string.indexOf(':');

        if (colonPos >= 0) {
            molName = string.substring(0, colonPos);
        }
        int periodPos = molName.indexOf(".");

        if (periodPos >= 0) {
            String coordSetName = molName.substring(0, periodPos);
            String entityName = molName.substring(periodPos + 1);
            csAndENames.add(new CoordsetAndEntity(coordSetName, entityName));
        } else {
            csAndENames.add(new CoordsetAndEntity(molName));
        }

        resAtom = string.substring(colonPos + 1);

        periodPos = resAtom.indexOf(".");
        int underPos = resAtom.indexOf('_', periodPos + 1);

        if (underPos == -1) {
            underPos = resAtom.length();
        }

        if (periodPos < 0) {
            anames = resAtom.substring(0, underPos);
            firstResTmp = "*";
            lastResTmp = "*";
        } else {
            String residue = resAtom.substring(0, periodPos);
            int dashPos = residue.indexOf('-');

            if (dashPos < 0) {
                firstResTmp = residue;
                lastResTmp = residue;
            } else {
                int dashPos2 = residue.lastIndexOf('-');
                if ((dashPos == 0) && (dashPos == dashPos2)) {
                    firstResTmp = residue;
                    lastResTmp = residue;
                } else {
                    Matcher matcher = resresPattern.matcher(residue);
                    if (matcher.matches()) {
                        firstResTmp = matcher.group(2);
                        lastResTmp = matcher.group(3);
                        if ((firstResTmp == null) && (lastResTmp == null)) {
                            return;
                        } else if ((firstResTmp == null) || firstResTmp.equals("")) {
                            firstResTmp = lastResTmp;
                        }
                    } else {
                        return;
                    }
                }
            }
            anames = resAtom.substring(periodPos + 1, underPos);
        }

        do {
            commaPos = anames.lastIndexOf(',');
            aname = anames.substring(commaPos + 1);

            if (aname.equals("")) {
                aname = "*";
            }

            atomNames.addElement(aname);

            if (commaPos < 0) {
                break;
            }

            anames = anames.substring(0, commaPos);
        } while (true);

        if (firstResTmp.equals("")) {
            firstResTmp = "*";
        }

        if (lastResTmp.equals("")) {
            lastResTmp = "*";
        }
        getResidueTypes(firstResTmp, true);
        getResidueTypes(lastResTmp, false);
        if (underPos != resAtom.length()) {
            String structString = resAtom.substring(underPos + 1);
            if (structString.equals("R")) {
                structureNum = -1;
            } else {
                structureNum = Integer.parseInt(structString);
            }
        } else {
            structureNum = 0;
        }

        return;
    }

    int getStructureNum() {
        return structureNum;
    }

    void getResidueTypes(String resTmp, boolean firstResMode) {
        int len = resTmp.length();
        int digStart = -1;
        String resNum;
        String resType;
        for (int i = 0; i < len; i++) {
            char resChar = resTmp.charAt(i);
            if ((resChar == '*') || (resChar == '-') || Character.isDigit(resChar)) {
                digStart = i;
                break;
            }
        }
        if (digStart == -1) {
            resNum = "*";
            if (len > 0) {
                resType = resTmp;
            } else {
                resType = "*";
            }
        } else {
            resNum = resTmp.substring(digStart);
            if (digStart > 0) {
                resType = resTmp.substring(0, digStart);
            } else {
                resType = "*";
            }
        }
        if (firstResMode) {
            firstRes = resNum;
            firstResType = resType;
        } else {
            lastRes = resNum;
            lastResType = resType;
        }
    }

    public String getCoordEntity() {
        return molName;
    }

    public String getString() {
        return string;
    }

    public String getCoordSetName() {
        String result = "*";
        if (csAndENames.size() > 0) {
            CoordsetAndEntity csAndE = csAndENames.get(0);
            if (csAndE.checkOneName) {
                result = csAndE.oneName;
            } else {
                result = csAndE.coordSetName;
            }
        }
        return result;
    }

    public boolean matchCoordSetAndEntity(String cName, String eName) {
        boolean result = false;
        for (CoordsetAndEntity csAndE : csAndENames) {
            if (csAndE.checkOneName) {
                if (Util.stringMatch(cName, csAndE.oneName) || Util.stringMatch(eName, csAndE.oneName)) {
                    result = true;
                    break;
                }
            } else if (Util.stringMatch(cName, csAndE.coordSetName) && Util.stringMatch(eName, csAndE.entityName)) {
                result = true;
                break;
            }
        }
        return result;
    }
}
