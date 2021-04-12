package org.nmrfx.structure.chemistry.predict;

import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.RotationConvention;
import org.apache.commons.math3.geometry.euclidean.threed.RotationOrder;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

/**
 *
 * @author brucejohnson
 */
public class AlignmentCalc {

    Vector3D[] vectors;

    public AlignmentCalc(Vector3D[] vectors) {
        this.vectors = vectors;
    }

    public void adjust(double alpha, double beta) {
        Rotation rot = new Rotation(RotationOrder.XYZ, RotationConvention.VECTOR_OPERATOR, alpha, beta, 0.0);
        double minX = Double.MAX_VALUE;
        for (Vector3D vector : vectors) {
            Vector3D rotVector = rot.applyTo(vector);
            minX = Math.min(minX, rotVector.getX());
        }
    }

}
