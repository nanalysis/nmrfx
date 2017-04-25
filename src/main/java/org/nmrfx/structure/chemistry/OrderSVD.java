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

import java.util.ArrayList;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

public class OrderSVD {

    public OrderSVD(ArrayList<Vector3D> vectors, ArrayList<Double> dc, ArrayList<Double> t, ArrayList<Double> error) {
        int nVectors = vectors.size();
        double[][] A = new double[nVectors][5];
        int iRow = 0;
        for (Vector3D vec3D : vectors) {
            //length[i]=sqrt(coord[i][0]*coord[i][0]+coord[i][1]*coord[i][1]+coord[i][2]*coord[i][2]);
            Vector3D normVec = vec3D.normalize();
            double ddcosX = normVec.getX() * normVec.getX();
            double ddcosY = normVec.getY() * normVec.getY();
            double ddcosZ = normVec.getZ() * normVec.getZ();
            A[iRow][0] = ddcosY - ddcosX;
            A[iRow][1] = ddcosZ - ddcosX;
            A[iRow][2] = 2 * ddcosX * ddcosY;
            A[iRow][3] = 2 * ddcosX * ddcosZ;
            A[iRow][4] = 2 * ddcosY * ddcosZ;
            iRow++;
        }

        /*  calculate direction cosines  */
        //     for (i=0; i<m; i++) {
        //       l3[i]=length[i]*length[i]*length[i];
    }
}
