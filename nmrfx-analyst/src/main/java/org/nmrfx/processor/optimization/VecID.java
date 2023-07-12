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

/**
 * @author graham
 */
public enum VecID {

    A(0, 'a'), B(1, 'b'), C(2, 'c'), D(3, 'd'), E(4, 'e'),
    F(5, 'f'), G(6, 'g'), H(7, 'h'),
    I(8, 'i'), J(9, 'j'), K(10, 'k'), L(11, 'l'), M(12, 'm'),
    N(13, 'n'), O(14, 'o'), P(15, 'p'),
    Q(16, 'q'), R(17, 'r'), S(18, 's'), T(19, 't'), U(20, 'u'), V(21, 'v'),
    W(22, 'w'), X(23, 'x'), Y(24, 'y'), Z(25, 'z');
    private final int val;
    private final char alpha;

    VecID(int val, char alpha) {
        this.val = val;
        this.alpha = alpha;
    }

    public int getValue() {
        return val;
    }

    @Override
    public String toString() {
        return String.valueOf(alpha);
    }

    public int getVarIndex() {
        return VarHash.getVarIndex(this);
    }

    public void setVarIndex(int index) {
        VarHash.setVarIndex(this, index);
    }

    private static class VarHash {
        private static final int HASH_SIZE = 26;
        private static final int[] h = new int[HASH_SIZE];

        public static int getVarIndex(VecID varName) {
            return h[varName.getValue()];
        }

        public static void setVarIndex(VecID varName, int index) {
            h[varName.getValue()] = index;
        }

        public static void clear() {
            for (int n = 0; n < HASH_SIZE; n++) {
                h[n] = 0;
            }
        }
    }
}
