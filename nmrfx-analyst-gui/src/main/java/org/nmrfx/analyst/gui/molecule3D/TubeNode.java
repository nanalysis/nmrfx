package org.nmrfx.analyst.gui.molecule3D;

import javafx.geometry.Point3D;
import javafx.scene.paint.Color;

/**
 * @author brucejohnson
 */
public class TubeNode {

    public Point3D pt;
    public Point3D norm;
    public double a;
    public double b;
    public double rectWidth;
    float red;
    float green;
    float blue;
    Color color;

    TubeNode(double x, double y, double z, double nx, double ny, double nz) {
        pt = new Point3D(x, y, z);
        Point3D n = new Point3D(nx, ny, nz);
        norm = n.subtract(pt).normalize().add(pt);
        a = 0.5;
        b = 0.5;
        rectWidth = 1.0;
        color = Color.GREEN;
        red = 1.0f;
        green = 0.2f;
        blue = 0.2f;
    }

}
