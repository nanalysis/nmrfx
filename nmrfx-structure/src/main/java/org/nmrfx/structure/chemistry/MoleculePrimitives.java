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

/*
 * MoleculePrimitives.java
 *
 * Created on September 5, 2003, 2:08 PM
 */
package org.nmrfx.structure.chemistry;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Line3;
import org.nmrfx.chemistry.Point3C;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Johnbruc
 */
public class MoleculePrimitives {

    public List<Line3> lines = new ArrayList<>();
    public List<Point3C> pointCs = new ArrayList<>();
    public float[] lineColors = null;
    public int nSelected = 0;
    public float[] selectionCoords = null;

    public int[] selectionLevels = null;
    public int nLabels = 0;
    public float[] labelCoords = null;
    public Atom[] atoms = null;
    public String[] labels = null;

    /**
     * Creates a new instance of MoleculePrimitives
     */
    public MoleculePrimitives() {
    }

    /*
    public void unCompXYZToArray(byte[] byteArray, int nBytes) {
        atoms = null;

        int nAtoms = 1024;
        float[] x = new float[nAtoms];
        float[] y = new float[nAtoms];
        float[] z = new float[nAtoms];
        sphereCoords = new float[4096];
        sphereColors = new float[4096];
        sphereValues = new float[1024];
        labels = new String[1024];
        lineCoords = new float[4096];
        lineColors = new float[4096];
        nLines = 0;
        nSpheres = 0;

        int p1 = 0;
        int p2 = 0;
        x[0] = 0.0f;
        y[0] = 0.0f;
        z[0] = 0.0f;

        float dx = 0.0f;
        float dy = 0.0f;
        float dz = 0.0f;
        float maxX = 0.0f;
        float maxY = 0.0f;
        float maxZ = 0.0f;

        int i = 0; // version number in byte 0
        int version = byteArray[i++];
        boolean mode3D = ((version & 2) == 2);
        int coordRes = 31;

        if (mode3D) {
            coordRes = 63;
        }

        int byte1 = byteArray[i++];
        int byte2 = byteArray[i++];
        int byte3 = 0;
        int byte4 = 0;
        maxX = ((byte1 % 0x100) * 5.0f) / 255.0f;
        maxY = ((byte2 % 0x100) * 5.0f) / 255.0f;

        if (mode3D) {
            byte3 = byteArray[i++];
            maxZ = ((byte3 % 0x100) * 5.0f) / 255.0f;
        }

        byte1 = byteArray[i++];
        byte2 = byteArray[i++];

        float deltaX = ((byte1 % 0x100) * 100.0f) / 255.0f * maxX;
        float deltaY = ((byte2 % 0x100) * 100.0f) / 255.0f * maxY;
        float deltaZ = 0.0f;

        if (mode3D) {
            byte3 = byteArray[i++];
            deltaZ = ((byte3 % 0x100) * 100.0f) / 255.0f * maxZ;
        }

        int iC = 0;
        int iColor = 0;
        int iSC = 0;
        int nonC = byteArray[i++];

        if (nonC < 0) {
            nonC += 256;
        }

        int nonCStart = i;

        i += (nonC * 2);

        int nAtomValues = byteArray[i++];

        if (nAtomValues < 0) {
            nAtomValues += 256;
        }

        int intMaxValue = 0;

        if (nAtomValues > 0) {
            intMaxValue += ((byteArray[i++] << 24) & 0xFF000000);
            intMaxValue += ((byteArray[i++] << 16) & 0x00FF0000);
            intMaxValue += ((byteArray[i++] << 8) & 0x0000FF00);
            intMaxValue += (byteArray[i++] & 0x000000FF);
        }

        int valuesStart = i;
        float maxValue = Float.intBitsToFloat(intMaxValue);
        i += nAtomValues;

        nSpheres = nAtomValues;

        float[] colors = AtomColors.getAtomColor(6);

        for (int j = 0; j < nSpheres; j++) {
            sphereColors[j * 3] = colors[0];
            sphereColors[(j * 3) + 1] = colors[1];
            sphereColors[(j * 3) + 2] = colors[2];
            labels[j] = "C";
        }

        int iNC = nonCStart;

        for (int j = 0; j < nonC; j++) {
            int iAtom = byteArray[iNC++];

            if (iAtom < 0) {
                iAtom += 256;
            }

            int type = byteArray[iNC++];

            if (type < 0) {
                type += 256;
            }

            colors = AtomColors.getAtomColor(type);
            sphereColors[iAtom * 3] = colors[0];
            sphereColors[(iAtom * 3) + 1] = colors[1];
            sphereColors[(iAtom * 3) + 2] = colors[2];

            if (type > 0) {
                labels[iAtom] = Atom.getElementName(type);
            } else {
                System.out.println("0 type " + j + " " + type);
            }
        }

        float bondSpace = 12.0f;

        // note: the order of atoms in x,y is not the same as that in the original molecule
        // the order is the order of the occurence the atoms were encountered in the search
        while (i < nBytes) {
            byte1 = byteArray[i++];
            byte1 = (byte1 + 0x100) % 0x100;
            byte2 = byteArray[i++];
            byte2 = (byte2 + 0x100) % 0x100;

            int order = 0;

            if (mode3D) {
                byte3 = byteArray[i++];
                byte3 = (byte3 + 0x100) % 0x100;
                order = (byte1 >> 6) & 3;
                p2 = (byte1 >> 2) & 15;
                dx = ((byte1 & 3) << 4) + ((byte2 >> 4) & 15);
                dy = ((byte2 & 15) << 2) + ((byte3 >> 6) & 3);
                dz = byte3 & coordRes;

                if (order == 0) {
                    byte4 = byteArray[i++];
                    byte4 = (byte4 + 0x100) % 0x100;
                    order = (byte4 >> 6) & 3;
                    p1 = byte4 & 63;
                }
            } else {
                order = (byte1 >> 6) & 3;
                p2 = (byte1 >> 2) & 15;
                dx = ((byte1 & 3) << 3) + ((byte2 >> 5) & 7);
                dy = byte2 & coordRes;

                if (order == 0) {
                    byte3 = byteArray[i++];
                    byte3 = (byte3 + 0x100) % 0x100;
                    order = (byte3 >> 6) & 3;
                    p1 = byte3 & 63;
                }
            }

            dx = (((1.0f * dx) / coordRes) - 0.5f) * maxX * 2.0f;
            dy = (((1.0f * dy) / coordRes) - 0.5f) * maxY * 2.0f;
            dz = (((1.0f * dz) / coordRes) - 0.5f) * maxZ * 2.0f;
            p2 = p1 + p2 + 1;

            x[p2] = x[p1] + dx;
            y[p2] = y[p1] + dy;
            z[p2] = z[p1] + dz;

            if (order > 1) {
                float x1;
                float y1;
                float z1;
                float x2;
                float y2;
                float z2;
                float x3;
                float y3;
                float z3;
                x3 = -dy / bondSpace;
                y3 = dx / bondSpace;
                z3 = dz / bondSpace;
                x1 = x[p1] + x3;
                y1 = y[p1] + y3;
                z1 = z[p1] + z3;
                x2 = x[p2] + x3;
                y2 = y[p2] + y3;
                z2 = z[p2] + z3;
                lineCoords[iC++] = x1 - deltaX;
                lineCoords[iC++] = y1 - deltaY;
                lineCoords[iC++] = z1 - deltaZ;
                lineColors[iColor++] = sphereColors[p1 * 3];
                lineColors[iColor++] = sphereColors[(p1 * 3) + 1];
                lineColors[iColor++] = sphereColors[(p1 * 3) + 2];
                lineCoords[iC++] = x2 - deltaX;
                lineCoords[iC++] = y2 - deltaY;
                lineCoords[iC++] = z2 - deltaZ;
                lineColors[iColor++] = sphereColors[p2 * 3];
                lineColors[iColor++] = sphereColors[(p2 * 3) + 1];
                lineColors[iColor++] = sphereColors[(p2 * 3) + 2];
                x1 = x[p1] - (2 * x3);
                y1 = y[p1] - (2 * y3);
                z1 = z[p1] - (2 * z3);
                x2 = x[p2] - (2 * x3);
                y2 = y[p2] - (2 * y3);
                z2 = z[p2] - (2 * z3);

                lineCoords[iC++] = x1 - deltaX;
                lineCoords[iC++] = y1 - deltaY;
                lineCoords[iC++] = z1 - deltaZ;
                lineColors[iColor++] = sphereColors[p1 * 3];
                lineColors[iColor++] = sphereColors[(p1 * 3) + 1];
                lineColors[iColor++] = sphereColors[(p1 * 3) + 2];

                lineCoords[iC++] = x2 - deltaX;
                lineCoords[iC++] = y2 - deltaY;
                lineCoords[iC++] = z2 - deltaZ;
                lineColors[iColor++] = sphereColors[p2 * 3];
                lineColors[iColor++] = sphereColors[(p2 * 3) + 1];
                lineColors[iColor++] = sphereColors[(p2 * 3) + 2];
            } else {
                lineCoords[iC++] = x[p1] - deltaX;
                lineCoords[iC++] = y[p1] - deltaY;
                lineCoords[iC++] = z[p1] - deltaZ;
                lineColors[iColor++] = sphereColors[p1 * 3];
                lineColors[iColor++] = sphereColors[(p1 * 3) + 1];
                lineColors[iColor++] = sphereColors[(p1 * 3) + 2];

                lineCoords[iC++] = x[p2] - deltaX;
                lineCoords[iC++] = y[p2] - deltaY;
                lineCoords[iC++] = z[p2] - deltaZ;
                lineColors[iColor++] = sphereColors[p2 * 3];
                lineColors[iColor++] = sphereColors[(p2 * 3) + 1];
                lineColors[iColor++] = sphereColors[(p2 * 3) + 2];
            }
        }

        nLines = iC / 6;

        i = valuesStart;

        for (int j = 0; j < nAtomValues; j++) {
            sphereCoords[iSC++] = x[j] - deltaX;
            sphereCoords[iSC++] = y[j] - deltaY;
            sphereCoords[iSC++] = z[j] - deltaZ;
            sphereValues[j] = (byteArray[i++] / 128.0f) * maxValue;
        }
    }
    */
}
