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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AtomSpecifier {

    private static final String RESIDUE_STR = "^(([a-zA-Z]+):)?([a-zA-Z])?(-?[0-9]+)(\\.([a-zA-Z].*))";
    private static final Pattern RESIDUE_PATTERN = Pattern.compile(RESIDUE_STR);

    final String chainName;
    final String atomName;
    final String resName;
    final Integer resNum;
    final String resNumStr;

    public AtomSpecifier(final String resNumStr, final String resName, final String atomName) {
        this("", resNumStr, resName, atomName);
    }

    public AtomSpecifier(final String chainName, final String resNumStr, final String resName, final String atomName) {
        this.chainName = chainName;
        this.atomName = atomName;
        this.resNumStr = resNumStr;
        this.resNum = Integer.parseInt(resNumStr);
        this.resName = resName;
    }

    public AtomSpecifier(final String chainName, final Integer resNum, final String resName, final String atomName) {
        this.chainName = chainName;
        this.atomName = atomName;
        this.resNumStr = String.valueOf(resNum);
        this.resNum = resNum;
        this.resName = resName;
    }

    public AtomSpecifier setAtomName(String newName) {
        return new AtomSpecifier(chainName, resNum, resName, newName);

    }

    public String getResNumString() {
        return resNumStr;
    }

    public Integer getResNum() {
        return resNum;
    }

    public String getResName() {
        return resName;
    }

    public String getAtomName() {
        return atomName;
    }

    public static AtomSpecifier parseString(String s) {
        Matcher matcher = RESIDUE_PATTERN.matcher(s);
        String chainName = "";
        String resChar = "";
        String resNumStr = "";
        String atomName = "";
        if (matcher.matches()) {
            chainName = matcher.group(2);
            resChar = matcher.group(3);
            resNumStr = matcher.group(4);
            atomName = matcher.group(6);
        }
        return new AtomSpecifier(chainName, resNumStr, resChar, atomName);
    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        if ((chainName != null) && !chainName.isBlank()) {
            sBuilder.append(chainName).append(":");
        }
        if ((resName != null) && !resName.isBlank()) {
            sBuilder.append(resName);
        }
        sBuilder.append(resNumStr).append(".").append(atomName);
        return sBuilder.toString();
    }

}
