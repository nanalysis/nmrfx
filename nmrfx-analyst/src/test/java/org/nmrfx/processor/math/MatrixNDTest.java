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
package org.nmrfx.processor.math;


import org.junit.Assert;
import org.junit.Test;

public class MatrixNDTest {

    private double[][] testNonSquare2D = {
            {1.0, 2.0, 3.0},
            {4.0, 5.0, 6.0},
            {7.0, 8.0, 9.0},
            {10.0, 11.0, 12.0},};

    private double[][] testSquare2D = {
            {1.0, 2.0, 3.0, 4.0},
            {5.0, 6.0, 7.0, 8.0},
            {9.0, 10.0, 11.0, 12.0},
            {13.0, 14.0, 15.0, 16.0},};
    private double[][] testSquare2DCol1 = {
            {1.0, 0.0, 0.0, 0.0},
            {1.0, 0.0, 0.0, 0.0},
            {1.0, 0.0, 0.0, 0.0},
            {1.0, 0.0, 0.0, 0.0},};
    private double[][] testSquare2DRow1 = {
            {1.0, 1.0, 1.0, 1.0},
            {0.0, 0.0, 0.0, 0.0},
            {0.0, 0.0, 0.0, 0.0},
            {0.0, 0.0, 0.0, 0.0},};
    private double[][] testSquare2DZF = {
            {1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
            {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
            {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
            {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
            {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
            {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
            {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
            {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},};

    @Test
    public void testMatrix() {
        double tol = 1.0e-10;
        MatrixND md = new MatrixND(testNonSquare2D);
        Assert.assertEquals(md.getNElems(), 12);
        Assert.assertEquals(md.getValue(2, 1), 8.0, tol);
        for (int i = 0; i < md.getNElems(); i++) {
            Assert.assertEquals(md.getValueAtIndex(i), i + 1.0, tol);
        }
    }

    @Test
    public void testVector() {
        double tol = 1.0e-10;
        MatrixND md = new MatrixND(testNonSquare2D);
        double[] vector = md.getVector(0, 0);
        Assert.assertEquals(vector.length, 4);
        for (int i = 0; i < vector.length; i++) {
            Assert.assertEquals(vector[i], md.getValue(i, 0), tol);
        }
        vector = md.getVector(0, 1);
        Assert.assertEquals(vector.length, 4);
        for (int i = 0; i < vector.length; i++) {
            Assert.assertEquals(vector[i], md.getValue(i, 1), tol);
        }
        vector = md.getVector(1, 0);
        Assert.assertEquals(vector.length, 3);
        for (int i = 0; i < vector.length; i++) {
            Assert.assertEquals(vector[i], md.getValue(0, i), tol);
        }
        vector = md.getVector(1, 2);
        Assert.assertEquals(vector.length, 3);
        for (int i = 0; i < vector.length; i++) {
            Assert.assertEquals(vector[i], md.getValue(2, i), tol);
        }
    }

    @Test
    public void testFT2D() {
        double tol = 1.0e-10;
        MatrixND md = new MatrixND(testSquare2DCol1);
        md.doFTtoReal(1);
        int nElems = md.getNElems();
        for (int i = 0; i < nElems; i++) {
            Assert.assertEquals(md.getValueAtIndex(i), 1.0, tol);
        }
        md = new MatrixND(testSquare2DRow1);
        md.doFTtoReal(0);
        for (int i = 0; i < nElems; i++) {
            Assert.assertEquals(md.getValueAtIndex(i), 1.0, tol);
        }
    }

    @Test
    public void testFT2DInv() {
        double tol = 1.0e-10;
        MatrixND md = new MatrixND(testSquare2DZF);
        md.doFTtoReal();
        md.doHIFT(0.5);
        int nElems = md.getNElems();
        Assert.assertEquals(md.getValueAtIndex(0), 1.0, tol);
        for (int i = 1; i < nElems; i++) {
            Assert.assertEquals(md.getValueAtIndex(i), 0.0, tol);
        }
    }

}
