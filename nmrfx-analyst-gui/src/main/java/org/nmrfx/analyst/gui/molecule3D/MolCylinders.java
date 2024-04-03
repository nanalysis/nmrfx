package org.nmrfx.analyst.gui.molecule3D;

import javafx.collections.ObservableList;
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
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.nmrfx.chemistry.Bond;

import javax.vecmath.Vector3d;
import java.util.ArrayList;
import java.util.List;

public class MolCylinders extends Group implements MolItem {
    private static final double MIN_SEL_RADIUS = 0.1;
    private final static double DEGTORAD = 180.0 / Math.PI;

    String molName = null;
    int iStructure = 0;
    int xDivisions = 15;
    float radius = 0.2f;
    List<Bond> bonds = null;
    List<BondLine> bondLines = null;
    Vector3d a = new Vector3d(0.0, 0.0, 0.0);
    Vector3d b = new Vector3d(0.0, 0.0, 0.0);

    public MolCylinders(String molName, List<Bond> bonds, List<BondLine> bondLines, double radius, String tag) {
        this.molName = molName;
        this.radius = (float) radius;
        this.bonds = bonds;
        this.bondLines = bondLines;
        setId(tag);
        refresh();
    }

    public int getCylIndex(Node node, Point3D point) {
        int result = 1;
        String nodeID = node.getId();
        String[] nodeIDParts = nodeID.split("\\.");
        if (nodeIDParts.length == 4) {
            int nodeIndex = Integer.parseInt(nodeIDParts[0]);
            Cylinder cylinder = (Cylinder) node;
            double height = cylinder.getHeight();
            double f = 0.5 + point.getY() / height;
            if (nodeIDParts[3].equals("1")) {
                if (f < 0.33) {
                    result = 0;
                } else if (f > 0.66) {
                    result = 2;
                } else {
                    result = 1;
                }
            } else if (nodeIDParts[2].equals("1")) {
                if (f < 0.5) {
                    result = 0;
                } else {
                    result = 1;
                }
            } else if (f > 0.5) {
                result = 2;
            } else {
                result = 1;
            }
        }
        return result;
    }

    public String getNodeName(Node node, Point3D point) {
        String result = "";
        if (node instanceof Cylinder) {
            String nodeID = node.getId();
            String[] nodeIDParts = nodeID.split("\\.");
            if (nodeIDParts.length == 4) {
                int nodeIndex = Integer.parseInt(nodeIDParts[0]);
                Bond bond = bonds.get(nodeIndex);
                int cylIndex = getCylIndex(node, point);
                switch (cylIndex) {
                    case 0:
                        result = "atom " + bond.getBeginAtom().getFullName();
                        break;
                    case 2:
                        result = "atom " + bond.getEndAtom().getFullName();
                        break;
                    default:
                        result = "bond " + bond.getBeginAtom().getFullName() + " " + bond.getEndAtom().getFullName();
                        break;
                }
            }
        }
        return result;

    }

    enum CylPos {

        TOP, BOTTOM, CENTER, MISS

    }

    public Node getSelectorNode(Node node, Point3D point) {
        CylPos cylPos = CylPos.MISS;
        Node selNode = null;
        if (node instanceof Cylinder) {
            Cylinder cylinder = (Cylinder) node;
            double selRadius = cylinder.getRadius() * 1.2;
            if (selRadius < MIN_SEL_RADIUS) {
                selRadius = MIN_SEL_RADIUS;
            }
            String nodeID = node.getId();
            String[] nodeIDParts = nodeID.split("\\.");
            if (nodeIDParts.length == 4) {
                int nodeIndex = Integer.parseInt(nodeIDParts[0]);
                int index = Integer.parseInt(nodeIDParts[1]);
                BondLine bondLine = bondLines.get(index);
                Vector3D p3dB = bondLine.ptB;
                Vector3D p3dE = bondLine.ptE;
                int cylIndex = getCylIndex(node, point);
                if (cylIndex == 0) {
                    double[] center = new double[3];
                    center[0] = p3dB.getX();
                    center[1] = p3dB.getY();
                    center[2] = p3dB.getZ();
                    selNode = new MolSphere(center, selRadius * 1.2, Color.GOLD, "selection");
                } else if (cylIndex == 2) {
                    double[] center = new double[3];
                    center[0] = p3dE.getX();
                    center[1] = p3dE.getY();
                    center[2] = p3dE.getZ();
                    selNode = new MolSphere(center, selRadius * 1.2, Color.GOLD, "selection");
                } else {
                    Vector3D pCenter = p3dB.add(p3dE).scalarMultiply(0.5);
                    float length = (float) p3dB.distance(p3dE);
                    Bond bond = bonds.get(nodeIndex);
                    Point3D selPoint1 = new Point3D(0, -length / 2, 0);
                    Point3D selPoint2 = new Point3D(0, length / 2, 0);
                    Translate translate = new Translate(pCenter.getX(), pCenter.getY(), pCenter.getZ());

                    ObservableList<Transform> transforms = cylinder.getTransforms();
                    selPoint1 = transforms.get(1).transform(selPoint1);
                    selPoint1 = translate.transform(selPoint1);
                    double[] center1 = new double[3];
                    center1[0] = selPoint1.getX();
                    center1[1] = selPoint1.getY();
                    center1[2] = selPoint1.getZ();
                    selPoint2 = transforms.get(1).transform(selPoint2);
                    selPoint2 = translate.transform(selPoint2);

                    double[] center2 = new double[3];
                    center2[0] = selPoint2.getX();
                    center2[1] = selPoint2.getY();
                    center2[2] = selPoint2.getZ();
                    selNode = new MolCylinder(center1, center2, selRadius, Color.GOLD, "selection");
                }
            }
        }
        return selNode;

    }

    public void refresh() {
        this.getChildren().clear();
        Group group;
        if (radius < 0.01) {
            group = makeLines(molName, iStructure);
        } else {
            group = makeCylinders(molName, iStructure);
        }
        if (group != null) {
            this.getChildren().add(group);
            group.setId("bonds " + molName);
        }
    }

    public void coords(double[] coords) {
        a.x = coords[0];
        a.y = coords[1];
        a.z = coords[2];
        b.x = coords[3];
        b.y = coords[4];
        b.z = coords[5];
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

    void makeObjectNode() {
        Group group = makeCylinders(molName, iStructure);
    }

    public String getMolname() {
        return molName;
    }

    public void setMolname(final String molName) {
        this.molName = molName;
        this.getChildren().clear();
        Group group = makeCylinders(molName, iStructure);
        this.getChildren().add(group);
        refresh();
    }

    public int getStructure() {
        return iStructure;
    }

    public void setStructure(final int value) {
        if (value < 0) {
            iStructure = 0;
        } else {
            iStructure = value;
        }
        refresh();
    }

    public String hit(double x, double y) {
        String pickResult = "";
        return pickResult;
    }

    public Group makeCylinders(String molName, int iStructure) {
        ArrayList cylinders = new ArrayList<>();
        String seg01 = ".1.1";
        String seg02 = ".1.2";
        String seg12 = ".2.2";
        String segDescrip;
        int iLine = 0;
        for (BondLine bondLine : bondLines) {
            Vector3D p3dB = bondLine.ptB;
            Vector3D p3dE = bondLine.ptE;
            float length = (float) p3dB.distance(p3dE);
            int nSegs = 1;
            if (!bondLine.colorB.equals(bondLine.colorE)) {
                nSegs = 2;
                length /= 2;
            }
            for (int iSeg = 0; iSeg < nSegs; iSeg++) {
                Color color;
                Vector3d v3da;
                Vector3d v3db;
                if (nSegs == 1) {
                    v3da = new Vector3d(p3dB.getX(), p3dB.getY(), p3dB.getZ());
                    v3db = new Vector3d(p3dE.getX(), p3dE.getY(), p3dE.getZ());
                    color = bondLine.colorB;
                    segDescrip = seg01;
                } else if (iSeg == 0) {
                    v3da = new Vector3d(p3dB.getX(), p3dB.getY(), p3dB.getZ());
                    v3db = new Vector3d((p3dE.getX() + p3dB.getX()) / 2, (p3dE.getY() + p3dB.getY()) / 2, (p3dE.getZ() + p3dB.getZ()) / 2);
                    color = bondLine.colorB;
                    segDescrip = seg02;
                } else {
                    v3db = new Vector3d(p3dE.getX(), p3dE.getY(), p3dE.getZ());
                    v3da = new Vector3d((p3dE.getX() + p3dB.getX()) / 2, (p3dE.getY() + p3dB.getY()) / 2, (p3dE.getZ() + p3dB.getZ()) / 2);
                    color = bondLine.colorE;
                    segDescrip = seg12;

                }
                Vector3d center = new Vector3d();
                center.x = ((v3db.x - v3da.x) / 2.0) + v3da.x;
                center.y = ((v3db.y - v3da.y) / 2.0) + v3da.y;
                center.z = ((v3db.z - v3da.z) / 2.0) + v3da.z;
                Transform transform = makeTransform3D(v3da, v3db);
                if (transform == null) {
                    continue;
                }
                PhongMaterial material = new PhongMaterial();
                material.setDiffuseColor(color);
                material.setSpecularColor(Color.WHITE);

                Cylinder cylinder = new Cylinder(radius, length, xDivisions);
                Translate translate = new Translate(center.x, center.y, center.z);
                cylinder.getTransforms().addAll(translate, transform);
                cylinder.setMaterial(material);
                cylinder.setId(String.valueOf(bondLine.bondIndex) + "." + String.valueOf(iLine) + segDescrip);
                cylinders.add(cylinder);
            }
            iLine++;
        }
        Group bg = new Group(cylinders);
        return bg;
    }

    public Group makeLines(String molName, int iStructure) {
        ArrayList cylinders = new ArrayList<>();
        String seg01 = ".1.1";
        String seg02 = ".1.2";
        String seg12 = ".2.2";
        String segDescrip;
        int iLine = 0;
        for (BondLine bondLine : bondLines) {
            Vector3D p3dB = bondLine.ptB;
            Vector3D p3dE = bondLine.ptE;
            float length = (float) p3dB.distance(p3dE);
            int nSegs = 1;
            if (!bondLine.colorB.equals(bondLine.colorE)) {
                nSegs = 2;
                length /= 2;
            }
            for (int iSeg = 0; iSeg < nSegs; iSeg++) {
                Color color;
                Vector3d v3da;
                Vector3d v3db;
                if (nSegs == 1) {
                    v3da = new Vector3d(p3dB.getX(), p3dB.getY(), p3dB.getZ());
                    v3db = new Vector3d(p3dE.getX(), p3dE.getY(), p3dE.getZ());
                    color = bondLine.colorB;
                    segDescrip = seg01;
                } else if (iSeg == 0) {
                    v3da = new Vector3d(p3dB.getX(), p3dB.getY(), p3dB.getZ());
                    v3db = new Vector3d((p3dE.getX() + p3dB.getX()) / 2, (p3dE.getY() + p3dB.getY()) / 2, (p3dE.getZ() + p3dB.getZ()) / 2);
                    color = bondLine.colorB;
                    segDescrip = seg02;
                } else {
                    v3db = new Vector3d(p3dE.getX(), p3dE.getY(), p3dE.getZ());
                    v3da = new Vector3d((p3dE.getX() + p3dB.getX()) / 2, (p3dE.getY() + p3dB.getY()) / 2, (p3dE.getZ() + p3dB.getZ()) / 2);
                    color = bondLine.colorE;
                    segDescrip = seg12;

                }
                Vector3d center = new Vector3d();
                center.x = ((v3db.x - v3da.x) / 2.0) + v3da.x;
                center.y = ((v3db.y - v3da.y) / 2.0) + v3da.y;
                center.z = ((v3db.z - v3da.z) / 2.0) + v3da.z;
                Transform transform = makeTransform3D(v3da, v3db);
                if (transform == null) {
                    continue;
                }
                PhongMaterial material = new PhongMaterial();
                material.setDiffuseColor(color);
                material.setSpecularColor(Color.WHITE);

                Cylinder cyl = new Cylinder(0.05, length, 6);
                Translate translate = new Translate(center.x, center.y, center.z);
                cyl.getTransforms().addAll(translate, transform);
                cyl.setMaterial(material);
                cyl.setId(String.valueOf(bondLine.bondIndex) + "." + String.valueOf(iLine) + segDescrip);
                cylinders.add(cyl);
            }
            iLine++;
        }
        Group bg = new Group(cylinders);
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

            // (switched position -> y, y -> x, x -> position from code above)
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
