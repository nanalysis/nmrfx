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

package org.nmrfx.structure.chemistry.io;

import org.nmrfx.structure.chemistry.MissingCoordinatesException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.SuperMol;

import java.io.IOException;

/**
 * @author Bruce Johnson
 */
public class TrajectoryWriter {

    final Molecule molecule;
    final SuperMol superMol;
    int trajectoryFileNum = 0;
    String directory;
    String fileRoot;
    boolean initialized = false;

    public TrajectoryWriter(Molecule molecule, String directory, String fileRoot) {
        this.molecule = molecule;
        this.directory = directory;
        this.fileRoot = fileRoot;
        superMol = new SuperMol(molecule);
    }

    public void init() {
        molecule.copyStructure(0, 1);
        molecule.copyStructure(0, 2);
        initialized = true;
    }

    private String getFileName() {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(directory);
        sBuilder.append("/");
        sBuilder.append(fileRoot);
        sBuilder.append(trajectoryFileNum);
        sBuilder.append(".pdb");
        return sBuilder.toString();
    }

    public void writeStructure() throws MissingCoordinatesException {
        if (!initialized) {
            init();
        }

        try {
            molecule.copyStructure(0, 2);
            superMol.doSuper(1, 2, true);
            molecule.centerStructure(2);
            String fileName = getFileName();
            molecule.writeXYZToPDB(fileName, 2);
            trajectoryFileNum++;
        } catch (IOException ioE) {
            System.out.println(ioE.getMessage());
        }

    }
}
