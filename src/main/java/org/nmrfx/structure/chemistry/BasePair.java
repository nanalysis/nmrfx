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

import java.util.Arrays;

/**
 *
 * @author bajlabuser
 */
public class BasePair {
    public Residue res1;
    public Residue res2;
    
    public BasePair(Residue res1, Residue res2){
        this.res1 = res1;
        this.res2 = res2;
    }
     @Override
    public String toString() {
        return res1.iRes + ":" + res2.iRes;
    }
    public static boolean isCanonical(Residue res1, Residue res2) {
        boolean canon = false;
        if (res1.basePairType(res2) == 1) {
            canon = true;
        }
        return canon;
    }
}
