package org.nmrfx.analyst.gui.molecule3D;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import javax.vecmath.Vector3d;
import java.util.ArrayList;
import java.util.List;

public class Spheres extends Group implements MolItem {
    private static final double MIN_SEL_RADIUS = 0.15;

    String molName = null;
    int iStructure = 0;
    int xDivisions = 15;
    float radius = 0.4f;
    double scale;
    List<Vector3D> vecs = null;
    Vector3D center;
    Vector3d a = new Vector3d(0.0, 0.0, 0.0);
    Vector3d b = new Vector3d(0.0, 0.0, 0.0);
    Color color;

    public Spheres(String molName, List<Vector3D> vecs, Color color, double radius, Vector3D center, double scale, String tag) {
        this.molName = molName;
        this.radius = (float) radius;
        this.scale = scale;
        this.vecs = vecs;
        this.center = center;
        this.color = color;
        setId(tag);
        refresh();
    }

    public String getNodeName(Node node, Point3D point) {
        String nodeID = node.getId();
        int nodeIndex = Integer.parseInt(nodeID);
        return "sphere " + nodeIndex;
    }

    public Node getSelectorNode(Node node, Point3D point) {
        double[] center = new double[3];
        center[0] = node.getTranslateX();
        center[1] = node.getTranslateY();
        center[2] = node.getTranslateZ();
        double selRadius = ((javafx.scene.shape.Sphere) node).getRadius() * 1.3;
        if (selRadius < MIN_SEL_RADIUS) {
            selRadius = MIN_SEL_RADIUS;
        }
        MolSphere sphere = new MolSphere(center, selRadius, Color.GOLD, "selection");
        return sphere;
    }

    public void refresh() {
        this.getChildren().clear();
        Group group = makeSpheres(molName, iStructure);
        if (group != null) {
            this.getChildren().add(group);
            group.setId("atoms " + molName);
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
    }

    void makeObjectNode() {
        Group group = makeSpheres(molName, iStructure);
    }

    public String getMolname() {
        return molName;
    }

    public void setMolname(final String molName) {
        this.molName = molName;
        this.getChildren().clear();
        Group group = makeSpheres(molName, iStructure);
        this.getChildren().add(group);
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
    }

    public String hit(double x, double y) {
        String pickResult = "";
        return pickResult;
    }

    public Group makeSpheres(String molName, int iStructure) {

        List<Node> spheres = new ArrayList<>();
        int i = 0;
        for (Vector3D vec : vecs) {
            PhongMaterial material = new PhongMaterial();
            material.setDiffuseColor(color);
            material.setSpecularColor(Color.WHITE);
            double sphereRadius = radius;
            Sphere sphere = new Sphere(sphereRadius, xDivisions);
            sphere.setTranslateX(vec.getX() * scale + center.getX());
            sphere.setTranslateY(vec.getY() * scale + center.getY());
            sphere.setTranslateZ(vec.getZ() * scale + center.getZ());
            sphere.setMaterial(material);
            sphere.setId(String.valueOf(i++));
            spheres.add(sphere);

        }
        Group bg = new Group(spheres);
        return bg;
    }
}
