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
/**
 *
 * @author audriguejean-louis
 */
package org.nmrfx.structure.chemistry;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class AllBasePairs {

    int type;
    public String res1;
    public String res2;
    String[] atomPairs;

    public AllBasePairs(int type, String res1, String res2, String[] atomPairs) {
        this.res1 = res1;
        this.res2 = res2;
        this.type = type;
        this.atomPairs = atomPairs;

    }

    @Override
    public String toString() {
        return type + ", " + res1 + ", " + res2 + ", " + Arrays.toString(atomPairs);
    }

    public static List<AllBasePairs> basePairList() {
        List<AllBasePairs> basePairs = new ArrayList<>();
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        InputStream istream = cl.getResourceAsStream("data/basepair.csv");
        Scanner inputStream = new Scanner(istream);
        while (inputStream.hasNextLine()) {
            String data = inputStream.nextLine();
            if (!data.isEmpty()) {
                String[] arrOfStr = data.split(",");
                if (arrOfStr.length >= 1) {

                    int type = Integer.parseInt(arrOfStr[0]);
                    String res1 = arrOfStr[1];
                    String res2 = arrOfStr[2];
                    int nPairs = (arrOfStr.length - 3) / 2;
                    String[] atomPairs = new String[nPairs]; ///populate list with basepairs
                    int firstindex = 3;
                    int secondindex = 4;
                    for (int i = 0; i < nPairs; i++) {
                        atomPairs[i] = arrOfStr[firstindex] + ":" + arrOfStr[secondindex];
                        firstindex += 2;
                        secondindex += 2;
                    }
                    AllBasePairs bp = new AllBasePairs(type, res1, res2, atomPairs);
                    basePairs.add(bp);
                }
            }
        }
        return basePairs;
    }

}
