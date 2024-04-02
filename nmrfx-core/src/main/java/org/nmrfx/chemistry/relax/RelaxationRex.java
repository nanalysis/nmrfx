/*
 * NMRFx Analyst :
 * Copyright (C) 2004-2021 One Moon Scientific, Inc., Westfield, N.J., USA
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
package org.nmrfx.chemistry.relax;

import org.nmrfx.annotations.PluginAPI;

import java.util.Map;

/**
 * @author mbeckwith
 */
@PluginAPI("ring")
public class RelaxationRex extends RelaxationData {

    Double RexValue;
    Double RexError;

    public RelaxationRex(RelaxationSet relaxationSet, ResonanceSource resSource,
                         Double value, Double error, Double RexValue, Double RexError) {

        super(relaxationSet, resSource, value, error);

        this.RexValue = RexValue;
        this.RexError = RexError;
    }

    public Double getRexValue() {
        return RexValue;
    }

    public Double getRexError() {
        return RexError;
    }

    @Override
    public String[] getParNames() {
        String[] parNames = {getRelaxationSet().relaxType().getName(), "Rex"};
        return parNames;
    }

    @Override
    public Double getValue(String name) {
        if (name.equals(getRelaxationSet().relaxType().getName())) {
            return getValue();
        } else if (name.equals("Rex")) {
            return RexValue;
        } else {
            return null;
        }
    }

    @Override
    public Double getError(String name) {
        if (name.equals(getRelaxationSet().relaxType().getName())) {
            return getError();
        } else if (name.equals("Rex")) {
            return RexError;
        } else {
            return null;
        }
    }

}
