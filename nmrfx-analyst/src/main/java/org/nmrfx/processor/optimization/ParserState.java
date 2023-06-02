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
package org.nmrfx.processor.optimization;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author graham
 */
public enum ParserState {

    INIT_OPERATION(0),
    INIT_EQUATION(1),
    EQUATION_NAME(2),
    DATA_NAME(3),
    DATA_VALUE(4);
    private final int value;
    private static Map<Integer, ParserState> lu = new HashMap<Integer, ParserState>();

    static {
        for (ParserState pstate : EnumSet.allOf(ParserState.class)) {
            lu.put(pstate.get(), pstate);
        }
    }

    ParserState(int value) {
        this.value = value;
    }

    public int get() {
        return value;
    }

    public static ParserState get(int i) {
        return lu.get(i);
    }
}
