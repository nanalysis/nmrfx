package org.nmrfx.analyst.gui.molecule3D;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import org.nmrfx.chemistry.Point3;

import javax.vecmath.Vector3d;

public class MolSphere extends Group implements MolItem {

    int iStructure = 0;
    int xDivisions = 15;
    float radius = 0.2f;
    Color color;
    Point3 p3d;

    public MolSphere(double[] coords, double radius, Color color, String tag) {
        this.radius = (float) radius;
        p3d = new Point3(coords[0], coords[1], coords[2]);
        this.color = color;
        setId(tag);
        refresh();
    }

    public String getNodeName(Node node, Point3D point) {
        return "sphere";
    }

    public void refresh() {
        this.getChildren().clear();
        Group group = makeSphere();
        if (group != null) {
            this.getChildren().add(group);
            group.setId("sphere");
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

    public Group makeSphere() {
        Vector3d v3da = new Vector3d();
        PhongMaterial material = new PhongMaterial();
        material.setDiffuseColor(color);
        material.setSpecularColor(Color.WHITE);
        Sphere sphere = new Sphere(radius, xDivisions);
        sphere.setTranslateX(p3d.getX());
        sphere.setTranslateY(p3d.getY());
        sphere.setTranslateZ(p3d.getZ());
        sphere.setMaterial(material);
        Group bg = new Group(sphere);
        return bg;
    }

}
