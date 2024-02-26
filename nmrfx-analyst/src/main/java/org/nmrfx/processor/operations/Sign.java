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
package org.nmrfx.processor.operations;

import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.math.Vec;
import org.nmrfx.processor.processing.ProcessingException;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * @author johnsonb
 */
@PythonAPI("pyproc")
@PluginAPI("parametric")
public class Sign extends Operation implements Invertible {

    enum ModeType {

        NEGIMAG("i") {
            @Override
            public void execute(Vec vec) {
                vec.negateImaginary();
            }

        },
        NEGREAL("r") {
            @Override
            public void execute(Vec vec) {
                vec.negateReal();

            }
        },
        NEGRI("ri") {
            @Override
            public void execute(Vec vec) {
                vec.negateAll();

            }
        },
        NEGALT("alt") {
            @Override
            public void execute(Vec vec) {
                vec.negatePairs();
            }

        },
        ;

        String name;

        ModeType(String name) {
            this.name = name;
        }

        String getName() {
            return name;
        }

        public abstract void execute(Vec vec);

    }

    private static final Map<String, ModeType> lookup
            = new HashMap<String, ModeType>();

    static {
        for (ModeType s : EnumSet.allOf(ModeType.class)) {
            lookup.put(s.getName(), s);
        }

    }

    ModeType modeType;

    /**
     * Sign change operation.
     *
     * @param mode
     * @throws ProcessingException
     * @see Vec
     */
    public Sign(String mode) throws ProcessingException {
        modeType = lookup.get(mode);

    }

    @Override
    public Sign eval(Vec vector) throws ProcessingException {
        modeType.execute(vector);
        return this;
    }

}
