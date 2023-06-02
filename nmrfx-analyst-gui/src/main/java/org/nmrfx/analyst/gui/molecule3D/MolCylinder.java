package org.nmrfx.analyst.gui.molecule3D;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;
import org.nmrfx.chemistry.Point3;

import javax.vecmath.Vector3d;

public class MolCylinder extends Group implements MolItem {

    String molName = null;
    int iStructure = 0;
    int xDivisions = 15;
    float radius = 0.2f;
    Color color;
    Point3 p3dB;
    Point3 p3dE;
    private final static double DEGTORAD = 180.0 / Math.PI;

    public MolCylinder(double[] begin, double[] end, double radius, Color color, String tag) {
        this.radius = (float) radius;
        p3dB = new Point3(begin[0], begin[1], begin[2]);
        p3dE = new Point3(end[0], end[1], end[2]);
        this.color = color;
        setId(tag);
        refresh();
    }

    public String getNodeName(Node node, Point3D point) {
        return "cyl";
    }

    public void refresh() {
        this.getChildren().clear();
        Group group = makeCylinder(molName, iStructure);
        if (group != null) {
            this.getChildren().add(group);
            group.setId("cyl");
        }
    }

    public double getRadius() {
        return radius;
    }

    /**
     * @param radius
     */
    public void setRadius(double radius) {
        this.radius = (float) radius;
        refresh();
    }

    public Group makeCylinder(String molName, int iStructure) {

        Vector3d v3da;
        Vector3d v3db;
        v3da = new Vector3d(p3dB.getX(), p3dB.getY(), p3dB.getZ());
        v3db = new Vector3d(p3dE.getX(), p3dE.getY(), p3dE.getZ());
        Vector3d v3d = new Vector3d(v3da);
        v3d.sub(v3db);
        float length = (float) v3d.length();
        Vector3d center = new Vector3d();
        center.x = ((v3db.x - v3da.x) / 2.0) + v3da.x;
        center.y = ((v3db.y - v3da.y) / 2.0) + v3da.y;
        center.z = ((v3db.z - v3da.z) / 2.0) + v3da.z;
        Transform transform = makeTransform3D(v3da, v3db);
        if (transform == null) {
            return null;
        }
        PhongMaterial material = new PhongMaterial();
        material.setDiffuseColor(color);
        material.setSpecularColor(Color.WHITE);

        Cylinder cylinder = new Cylinder(radius, length, xDivisions);
        Translate translate = new Translate(center.x, center.y, center.z);
        cylinder.getTransforms().addAll(translate, transform);
        cylinder.setMaterial(material);

        Group bg = new Group(cylinder);
        return bg;
    }

    Transform makeTransform3D(final Vector3d base, final Vector3d apex) {
        // calculate center of object
        Vector3d center = new Vector3d();
        center.x = ((apex.x - base.x) / 2.0) + base.x;
        center.y = ((apex.y - base.y) / 2.0) + base.y;
        center.z = ((apex.z - base.z) / 2.0) + base.z;

        // calculate height of object and unit vector along cylinder axis
        Vector3d unit = new Vector3d();
        unit.sub(apex, base);

        unit.normalize();

        /* A Java3D cylinder is created lying on the Y axis by default.
         The idea here is to take the desired cylinder's orientation
         and perform a tranformation on it to get it ONTO the Y axis.
         Then this transformation matrix is inverted and used on a
         newly-instantiated Java 3D cylinder. */
        // calculate vectors for rotation matrix
        // rotate object in any orientation, onto Y axis (exception handled below)
        // (see page 418 of _Computer Graphics_ by Hearn and Baker)
        Vector3d uX = new Vector3d();
        Vector3d uY = new Vector3d();
        Vector3d uZ = new Vector3d();
        double magX;
        Rotate rotateFix = new Rotate();

        uY = new Vector3d(unit);
        uX.cross(unit, new Vector3d(0, 0, 1));
        magX = uX.length();

        // magX == 0 if object's axis is parallel to Z axis
        if (magX != 0) {
            uX.z = uX.z / magX;
            uX.x = uX.x / magX;
            uX.y = uX.y / magX;
            uZ.cross(uX, uY);
        } else {
            // formula doesn't work if object's axis is parallel to Z axis
            // so rotate object onto X axis first, then back to Y at end
            double magZ;

            // (switched z -> y, y -> x, x -> z from code above)
            uX = new Vector3d(unit);
            uZ.cross(unit, new Vector3d(0, 1, 0));
            magZ = uZ.length();
            uZ.x = uZ.x / magZ;
            uZ.y = uZ.y / magZ;
            uZ.z = uZ.z / magZ;
            uY.cross(uZ, uX);

            // rotate object 90 degrees CCW around Z axis--from X onto Y
            rotateFix.setAxis(Rotate.Z_AXIS);
            rotateFix.setAngle(DEGTORAD * Math.PI / 2.0);
        }

        // create the rotation matrix
        Transform rotateMatrix = Transform.affine(uX.x, uX.y,
                uX.z, 0, uY.x, uY.y, uY.z, 0, uZ.x, uZ.y, uZ.z, 0.0);

        // invert the matrix; need to rotate it off of the Z axis
        try {
            rotateMatrix = rotateMatrix.createInverse();
        } catch (NonInvertibleTransformException nITE) {
            return null;
        }
        // rotate the cylinder into correct orientation
        Transform transform = rotateMatrix.createConcatenation(rotateFix);
        return transform;
    }

}
