package org.nmrfx.analyst.gui.molecule3D;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.nmrfx.chemistry.Atom;

import javax.vecmath.Vector3d;
import java.util.ArrayList;
import java.util.List;

public class MolTube extends Group implements MolItem {

    String molName = null;
    int iStructure = 0;
    int nChords = 5;
    int maxChords = 20;
    int bSides = 30;
    int maxSides = 30;
    Vector3d a = new Vector3d(0.0, 0.0, 0.0);
    Vector3d b = new Vector3d(0.0, 0.0, 0.0);
    float radius = 0.4f;
    List<Atom> atoms = null;
    List<AtomSphere> atomSpheres = null;

    public MolTube(String molName, List<Atom> atoms, List<AtomSphere> atomSpheres, double radius, String tag) {
        this.molName = molName;
        this.radius = (float) radius;
        this.atoms = atoms;
        this.atomSpheres = atomSpheres;
        setId(tag);
        refresh();
    }

    public void refresh() {
        this.getChildren().clear();
        Group group = makeTube(molName, iStructure);
        if (group != null) {
            this.getChildren().add(group);
            group.setId("tube " + molName);
        }
    }

    public Image makeTubeColors(int nColors) {
        int width = 2;
        int height = nColors;
        Image textureImage = new WritableImage(width, height);
        PixelWriter pw = ((WritableImage) textureImage).getPixelWriter();
        for (int i = 0; i < nColors; i++) {
            double red = 1.0;
            double green = (double) i / (nColors - 1);
            Color color = Color.color(red, green, 0.0);
            pw.setColor(0, i, color);
            pw.setColor(1, i, color);
        }
        return textureImage;
    }

    public String getNodeName(Node node, Point3D point) {
        return "tube";
    }

    public void setNChords(int value) {
        if (value < 2) {
            value = 2;
        }
        if (value > maxChords) {
            value = maxChords;
        }
        nChords = value;
    }

    public void setBSides(int value) {
        if (value < 1) {
            value = 1;
        }
        if (value > maxSides) {
            value = maxSides;
        }
        bSides = value;
    }

    public String getMolname() {
        return molName;
    }

    public void setMolname(final String molName) {
        this.molName = molName;
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

    public Group makeTube(String molName, int iStructure) {
        Tube tube = new Tube();
        tube.nChords = nChords;
        tube.bSides = bSides;
        int j = 0;
        for (int i = 0; i < atomSpheres.size(); i += 2) {
            AtomSphere asphere0 = atomSpheres.get(i);
            AtomSphere asphere1 = atomSpheres.get(i + 1);
            double x = asphere0.pt.getX();
            double y = asphere0.pt.getY();
            double z = asphere0.pt.getZ();
            Vector3D v3d = asphere1.pt.subtract(asphere0.pt).normalize().add(asphere0.pt);
            tube.addNode(x, y, z, v3d.getX(), v3d.getY(), v3d.getZ());
            tube.setColor(j, (float) asphere0.color.getRed(), (float) asphere0.color.getGreen(), (float) asphere0.color.getBlue());
            double radiusA = radius;
            double radiusB = radius;
            if ((asphere0.value != 0.0) && (asphere1.value != 0.0)) {
                radiusA = asphere0.value;
                radiusB = asphere1.value;
            }
            tube.setRadius(j, radiusA, radiusB, 0.0);
            j++;
        }
        return makeTube(tube);
    }

    public Group makeTube(Tube tube) {
        Tessellation tesselation = new Tessellation();
        ArrayList<TubeNode> nodes = tube.createPathPoly();
        if (nodes.isEmpty()) {
            return null;
        }
        tesselation.makeTube(nodes);
        TriangleMesh mesh = tesselation.makeTubeMesh(nodes.size());
        MeshView meshView = new MeshView(mesh);
        PhongMaterial material = new PhongMaterial();
        material.setSpecularColor(Color.WHITE);
        meshView.setMaterial(material);
        Image image = makeTubeColors(nodes.size());
        material.setDiffuseMap(image);

        Sphere startSphere = new Sphere(radius, 15);
        Sphere endSphere = new Sphere(radius, 15);
        AtomSphere asphereStart = atomSpheres.get(0);
        AtomSphere asphereEnd = atomSpheres.get(atomSpheres.size() - 2);

        startSphere.setTranslateX(asphereStart.pt.getX());
        startSphere.setTranslateY(asphereStart.pt.getY());
        startSphere.setTranslateZ(asphereStart.pt.getZ());

        endSphere.setTranslateX(asphereEnd.pt.getX());
        endSphere.setTranslateY(asphereEnd.pt.getY());
        endSphere.setTranslateZ(asphereEnd.pt.getZ());

        PhongMaterial startMaterial = new PhongMaterial();
        startMaterial.setSpecularColor(Color.WHITE);
        startMaterial.setDiffuseColor(Color.RED);

        PhongMaterial endMaterial = new PhongMaterial();
        endMaterial.setSpecularColor(Color.WHITE);
        endMaterial.setDiffuseColor(Color.color(1.0, 1.0, 0.0));

        startSphere.setMaterial(startMaterial);
        endSphere.setMaterial(endMaterial);

        Xform group = new Xform();
        group.getChildren().addAll(meshView, startSphere, endSphere);

        return group;
    }
}
