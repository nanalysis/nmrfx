package org.nmrfx.analyst.gui.molecule3D;

// inspired (and some translation from python) from 
// https://github.com/prideout/blog-source/tree/master/p44
// see comment:
//     f you want, you can download all the python scripts necessary for generating these meshes as a zip file.
//     I consider this code to be on the public domain, so don't worry about licensing.
// at:
// https://github.com/prideout/blog-source/blob/master/p44/deploy/Article.txt


import javafx.geometry.Point3D;
import javafx.scene.shape.TriangleMesh;
import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author brucejohnson
 */
public class Tessellation {

    List<Vector3D> vertices = null;
    List<Face> faces = null;
    Vector3D lastPerpVec = null;
    List<TubeNode> tubeNodes = null;

    record Face(int vert0, int vert1, int vert2, int tex0, int tex1, int tex2) {

    }

    void createTube(int nNodes, int nStacks, BiFunction<Integer, Double, Vector3D> surfFunc) {
        vertices = new ArrayList<>();
        faces = new ArrayList<>();
        for (int i = 0; i < (nNodes); i++) {
            for (int j = 0; j < nStacks; j++) {
                double phi = (float) (j * 2.0 * Math.PI / nStacks);
                Vector3D vector = surfFunc.apply(i, phi);
                vertices.add(vector);
            }
        }
        int v = 0;
        for (int i = 0; i < (nNodes - 1); i++) {
            for (int j = 0; j < nStacks; j++) {
                int next = (j + 1) % nStacks;
                faces.add(new Face(v + j, v + next, v + j + nStacks, i, i, i));
                faces.add(new Face(v + next, v + next + nStacks, v + j + nStacks, i, i, i));
            }
            v += nStacks;
        }
    }

    public TriangleMesh makeTubeMesh(int nTextures) {
        TriangleMesh mesh = new TriangleMesh();
        float[] values = new float[vertices.size() * 3];
        int i = 0;
        for (Vector3D vertex : vertices) {
            values[i++] = (float) vertex.getX();
            values[i++] = (float) vertex.getY();
            values[i++] = (float) vertex.getZ();
        }
        mesh.getPoints().addAll(values);
        float[] textureCoords = new float[nTextures * 2];
        for (int j = 0; j < nTextures; j++) {
            textureCoords[2 * j] = 0.0f;
            textureCoords[2 * j + 1] = (float) j / (nTextures - 1);

        }
        mesh.getTexCoords().addAll(textureCoords);
        i = 0;
        int[] faceIndicies = new int[faces.size() * 6];

        for (Face face : faces) {
            faceIndicies[i++] = face.vert0;
            faceIndicies[i++] = face.tex0;
            faceIndicies[i++] = face.vert1;
            faceIndicies[i++] = face.tex1;
            faceIndicies[i++] = face.vert2;
            faceIndicies[i++] = face.tex2;
        }
        mesh.getFaces().addAll(faceIndicies);
        return mesh;
    }

    public Vector3D perp(Vector3D u) {
        Vector3D u_prime1 = u.crossProduct(new Vector3D(1, 0, 0));
        Vector3D u_prime2 = u.crossProduct(new Vector3D(-1, 0, 0));
        double dot1 = 1.0;
        double dot2 = 0.0;
        if (u_prime1.getNormSq() < 0.01) {
            u_prime1 = u.crossProduct(new Vector3D(0, 1, 0));
            u_prime2 = u.crossProduct(new Vector3D(0, -1, 0));
            u_prime1.normalize();
            u_prime2.normalize();
            if (lastPerpVec != null) {
                dot1 = lastPerpVec.dotProduct(u_prime1);
                dot2 = lastPerpVec.dotProduct(u_prime2);
            }
        } else {
            u_prime1.normalize();
            u_prime2.normalize();
            if (lastPerpVec != null) {
                dot1 = lastPerpVec.dotProduct(u_prime1);
                dot2 = lastPerpVec.dotProduct(u_prime2);
            }
        }
        if (lastPerpVec == null) {
            return u_prime1;
        } else if (dot1 > dot2) {
            return u_prime1;
        } else {
            return u_prime2;
        }

    }

    Vector3D tubeSpokeInt(int nodeNum, double v, Function<Integer, Vector3D> pathFunction, float radius) {
        Vector3D p1 = pathFunction.apply(nodeNum);
        Vector3D A;
        if (nodeNum < (tubeNodes.size() - 1)) {
            Vector3D p2 = pathFunction.apply(nodeNum + 1);
            A = p2.subtract(p1).normalize();
        } else {
            Vector3D p2 = pathFunction.apply(nodeNum - 1);
            A = p1.subtract(p2).normalize();
        }
        Vector3D B = perp(A);
        lastPerpVec = B;
        Vector3D C = A.crossProduct(B).normalize();

        Rotation rotation = new Rotation(A, v);
        Vector3D spoke = rotation.applyTo(C);

        return p1.add(radius, spoke);
    }

    Vector3D tubeShape(int nodeNum, double v) {
        TubeNode node = tubeNodes.get(nodeNum);
        float radius = (float) node.a;
        return tubeSpokeInt(nodeNum, v, this::tubePath, radius);
    }

    Vector3D tubePath(int nodeNum) {
        TubeNode node = tubeNodes.get(nodeNum);
        Point3D pt1 = node.pt;
        double x = pt1.getX();
        double y = pt1.getY();
        double z = pt1.getZ();
        return new Vector3D(x, y, z);
    }

    void makeTube(List<TubeNode> tubeNodes) {
        int nStacks = 10;
        int nSlices = tubeNodes.size();
        this.tubeNodes = tubeNodes;
        createTube(nSlices, nStacks, this::tubeShape);
    }
}
