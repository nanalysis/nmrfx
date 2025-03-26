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

package org.nmrfx.chemistry;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import java.io.Serializable;

public class Point3 extends Vector3D implements Serializable {

    public Point3(final double x, final double y, final double z) {
        super(x, y, z);
    }

    public Point3(final Vector3D vec3D) {
        super(vec3D.getX(), vec3D.getY(), vec3D.getZ());
    }

    public Point3 add(Point3 p2) {
        return new Point3(getX()+p2.getX(), getY()+p2.getY(), getZ() + p2.getZ());
    }
}
