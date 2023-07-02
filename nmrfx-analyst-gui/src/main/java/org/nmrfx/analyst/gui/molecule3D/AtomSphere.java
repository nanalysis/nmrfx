package org.nmrfx.analyst.gui.molecule3D;

import javafx.scene.paint.Color;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

/**
 * @author brucejohnson
 */
class AtomSphere {

    int atomIndex;
    Vector3D pt;
    Color color;
    float value;
    float radius;

    AtomSphere(int atomIndex, Vector3D pt, Color color, float radius, float value) {
        this.atomIndex = atomIndex;
        this.pt = pt;
        this.color = color;
        this.value = value;
        this.radius = radius;
    }

}
