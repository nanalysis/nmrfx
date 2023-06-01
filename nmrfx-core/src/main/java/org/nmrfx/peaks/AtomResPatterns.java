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
package org.nmrfx.peaks;

import java.util.List;

/**
 * @author brucejohnson
 */
public class AtomResPatterns {

    boolean ambiguousResidue;
    boolean ambiguousANames;
    List<AtomResPattern> atomResPatterns;

    public AtomResPatterns(List<AtomResPattern> atomResPatterns) {
        this.atomResPatterns = atomResPatterns;
        String firstAName;
        if (atomResPatterns.size() > 1) {
            AtomResPattern firstPattern = atomResPatterns.get(0);
            for (AtomResPattern atomResPattern : atomResPatterns) {
                if (!atomResPattern.aName.equals(firstPattern.aName)
                        || atomResPattern.aName.contains("*")) {
                    ambiguousANames = true;
                }
                if (atomResPattern.resDelta != firstPattern.resDelta) {
                    ambiguousResidue = true;
                }

            }
        } else if (atomResPatterns.size() == 1) {
            if (atomResPatterns.get(0).aName.contains("*")) {
                ambiguousANames = true;
            }
        } else {
            ambiguousResidue = false;
            ambiguousANames = false;
        }

    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        for (var pat : atomResPatterns) {
            sBuilder.append(pat.toString()).append(" ");
        }
        sBuilder.append(ambiguousResidue).append(" ").append(ambiguousANames);
        return sBuilder.toString();
    }
}
