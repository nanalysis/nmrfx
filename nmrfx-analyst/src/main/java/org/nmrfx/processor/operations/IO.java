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

import org.nmrfx.annotations.PythonAPI;
import org.nmrfx.processor.math.Vec;

/**
 * IO class does IO operations on the vectors inside of a processor (right now it's solely output). If this class is the
 * last in the chain of operations then it will output the results of the operations on all of the vectors in the
 * process.
 *
 * @author johnsonb
 */
@PythonAPI("pyproc")
public class IO extends Operation { //make abstract

    /**
     * Dummy print, not thread safe.
     *
     * @param vector
     * @return
     */
    @Override
    public Operation eval(Vec vector) {
        StringBuilder str = new StringBuilder();
        int[][] pt = vector.getPt();
        if (pt != null) {
            str.append("Printing vector\n");
            str.append(pt[0][0] + ", " + pt[0][1] + " " + pt[1][0] + ", " + pt[1][1] + "\n");

        }
        for (int i = 0; i < vector.getSize(); ++i) {
            str.append(i + " " + vector.getString(i));
            str.append("\n");
        }

        System.out.println(str);

        return this;
    }
}
