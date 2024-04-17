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

package org.nmrfx.chemistry.io;

import org.nmrfx.chemistry.*;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;

/**
 * @author brucejohnson
 */
public class CoordinateSTARWriter {

    public static void writeToSTAR3(Writer chan, MoleculeBase molecule, int setNum)
            throws IOException {

        String saveFrameName = "ensemble_of_conformers";
        String saveFrameCategory = "conformer_family_coord_set";
        String thisCategory = "_Conformer_family_coord_set";
        chan.write("save_" + saveFrameName + "\n");

        chan.write(thisCategory + ".Sf_category    ");
        chan.write(saveFrameCategory + "\n");

        chan.write(thisCategory + ".Sf_framecode   ");
        chan.write(saveFrameName + "\n");

        chan.write(thisCategory + ".Details        ");
        chan.write(".\n");

        chan.write("\n");

        String[] loopStrings = NMRStarWriter.getCoordLoopStrings();
        char sep = ' ';
        int whichStruct = -1;
        chan.write("loop_\n");
        for (String loopString : loopStrings) {
            chan.write(loopString + "\n");
        }
        chan.write("\n");

        StringBuilder result = new StringBuilder();
        int[] structureList = molecule.getActiveStructures();
        int i = 1;
        for (int jStruct = 0; jStruct < structureList.length; jStruct++) {
            int iStruct = structureList[jStruct];

            if ((whichStruct >= 0) && (iStruct != whichStruct)) {
                continue;
            }

            Iterator e = molecule.coordSets.values().iterator();
            CoordSet coordSet;

            while (e.hasNext()) {
                coordSet = (CoordSet) e.next();

                Iterator entIterator = coordSet.getEntities().values().iterator();

                while (entIterator.hasNext()) {
                    Entity entity = (Entity) entIterator.next();
                    for (Atom atom : entity.getAtoms()) {
                        if (atom.getAtomicNumber() != 0) {
                            SpatialSet spatialSet = atom.spatialSet;
                            result.setLength(0);
                            result.append(". ");
                            result.append(iStruct);
                            result.append(sep);
                            result.append('.');
                            result.append(sep);
                            result.append(i);
                            result.append(sep);
                            spatialSet.addToSTARString(result, false);
                            result.append(sep);
                            if (spatialSet.addXYZtoSTAR(result, iStruct)) {
                                result.append(sep);
                                result.append(". ");  // details

                                result.append("."); // ssID

                                result.append(sep);
                                result.append("1");
                                chan.write(result.toString() + '\n');
                            }
                        }
                    }
                }
            }
        }

        chan.write("stop_\n");
        chan.write("\n");

        chan.write("save_\n\n");
    }
}
