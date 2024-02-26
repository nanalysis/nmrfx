package org.nmrfx.analyst.gui.molecule3D;

import javafx.scene.paint.Color;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

/**
 * @author brucejohnson
 */
class BondLine {

    int bondIndex;
    Vector3D ptB;
    Vector3D ptE;
    Color colorB;
    Color colorE;

    BondLine(int index, Vector3D ptB, Vector3D ptE, Color colorB, Color colorE) {
        this.bondIndex = index;
        this.ptB = ptB;
        this.ptE = ptE;
        this.colorB = colorB;
        this.colorE = colorE;
    }

}
