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
package org.nmrfx.chemistry;

import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MolFilter {
    private static final String RESRES_PATTERN_STR = "(([a-zA-Z]?-?[0-9]+)-)?([a-zA-Z]?-?[0-9]+)";
    private static final Pattern RESRES_PATTERN = Pattern.compile(RESRES_PATTERN_STR);

    public Vector atomNames = new Vector(4, 4);
    CoordsetAndEntity csAndE = null;
    String molName = "*";
    String seqID = "1";
    public String firstRes = "";
    public String lastRes = "";
    public String firstResType = "*";
    public String lastResType = "*";
    public int structureNum = 0;
    public String entityName = null;
    String string = "";

    private class CoordsetAndEntity {

        String entityName = "*";
        int entityID = -1;
        String coordSetName = "*";
        int coordID = -1;
        String oneName = "";
        boolean checkOneName = false;
        int oneID = -1;

        CoordsetAndEntity(final String coordSetName, final String entityName) {
            if (entityName.length() != 0) {
                try {
                    entityID = Integer.parseInt(entityName);
                    this.entityName = "";
                } catch (NumberFormatException nfE) {
                    this.entityName = entityName;
                }

            }
            if (coordSetName.length() != 0) {
                try {
                    coordID = Integer.parseInt(entityName);
                } catch (NumberFormatException nfE) {
                    this.coordSetName = entityName;
                    this.coordSetName = "";
                }
            }
        }

        CoordsetAndEntity(final String oneName) {
            checkOneName = true;
            try {
                oneID = Integer.parseInt(oneName);
            } catch (NumberFormatException nfE) {
                this.oneName = oneName;
            }
        }
    }

    public MolFilter(String string) {
        this.string = string;
        String resAtom;
        String firstResTmp;
        String lastResTmp;
        atomNames.setSize(0);

        String anames;
        String aname;
        int commaPos;

        int colonPos = string.indexOf(':');

        if (colonPos >= 0) {
            molName = string.substring(0, colonPos);
            int sPeriodPos = string.indexOf('.');

            if (sPeriodPos >= 0) {
                seqID = string.substring(colonPos + 1, sPeriodPos);
            }
        }
        int periodPos = molName.indexOf(".");

        if (periodPos >= 0) {
            String coordSetName = molName.substring(0, periodPos);
            String entityName = molName.substring(periodPos + 1);
            this.entityName = entityName;
            csAndE = new CoordsetAndEntity(coordSetName, entityName);
        } else {
            csAndE = new CoordsetAndEntity(molName);
            if (!"*".equals(molName)) {
                entityName = molName;
            }
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
                    Matcher matcher = RESRES_PATTERN.matcher(residue);
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

    }

    public int getStructureNum() {
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

    public String getCoordSeqID() {
        return seqID;
    }

    public String getString() {
        return string;
    }

    public String getCoordSetName() {
        String result = "*";
        if (csAndE != null) {
            if (csAndE.checkOneName) {
                result = csAndE.oneName;
            } else {
                result = csAndE.coordSetName;
            }
        }
        return result;
    }

    public boolean matchCoordSetAndEntity(CoordSet cSet, Entity entity) {
        boolean result = false;
        if (csAndE.checkOneName) {
            if ((csAndE.oneID != -1) && ((csAndE.oneID == cSet.getID()) || (csAndE.oneID == entity.entityID))) {
                result = true;
            } else if ((csAndE.oneID == -1) && (Util.stringMatch(cSet.getName(), csAndE.oneName) || Util.stringMatch(entity.name, csAndE.oneName))) {
                result = true;
            }
        } else {

            boolean csMatch = (csAndE.coordID == cSet.getID()) || Util.stringMatch(cSet.getName(), csAndE.coordSetName);
            boolean eMatch = (csAndE.entityID == entity.entityID) || Util.stringMatch(cSet.getName(), csAndE.entityName);

            result = csMatch && eMatch;
        }
        return result;
    }
}
