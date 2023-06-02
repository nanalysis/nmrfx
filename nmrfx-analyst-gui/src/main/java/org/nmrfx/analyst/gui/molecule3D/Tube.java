package org.nmrfx.analyst.gui.molecule3D;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import java.util.ArrayList;

public class Tube {

    public int nChords = 5;
    public int bSides = 8;
    public ArrayList<TubeNode> nodes;
    public double lastAngle = -100000.0;
    double[][] rBond1 = null;
    double[][] rBond3 = null;
    double[][] rotV = null;
    Vector3D lastCP = null;

    public Tube() {
        nodes = new ArrayList<>();
    }

    public void setColor(int iNode, float red, float green, float blue) {
        TubeNode node = (TubeNode) nodes.get(iNode);
        node.red = red;
        node.green = green;
        node.blue = blue;

    }

    public void setRadius(int iNode, double a, double b, double rectWidth) {
        TubeNode node = (TubeNode) nodes.get(iNode);
        node.a = a;
        node.b = b;
        node.rectWidth = rectWidth;

    }

    public void setNSides(int nSides) {
        bSides = nSides;
    }

    public void setNChords(int nChords) {
        this.nChords = nChords;
    }

    public void addNode(double x, double y, double z, double nx, double ny,
                        double nz) {
        TubeNode node = new TubeNode(x, y, z, nx, ny, nz);
        nodes.add(node);
    }

    public ArrayList<TubeNode> createPath() {
        int j;
        TubeNode node;
        TubeNode node1;
        TubeNode node2;
        TubeNode node3;
        TubeNode node4;
        ArrayList<TubeNode> sNodes = new ArrayList<>();

        if (nodes.size() < 3) {
            return sNodes;
        }

        rBond1 = null;

        double del;
        double[] a = new double[4];
        double[] val = new double[4];
        double[] na = new double[4];
        double[] nval = new double[4];
        node1 = (TubeNode) nodes.get(0);

        final double x[] = new double[nChords];
        final double nx[] = new double[nChords];
        final double y[] = new double[nChords];
        final double ny[] = new double[nChords];
        final double z[] = new double[nChords];
        final double nz[] = new double[nChords];

        int iNode;

        for (iNode = 0; iNode < (nodes.size() - 1); iNode++) {
            node2 = (TubeNode) nodes.get(iNode);
            node3 = (TubeNode) nodes.get(iNode + 1);

            if (iNode == (nodes.size() - 2)) {
                node4 = node3;
            } else {
                node4 = (TubeNode) nodes.get(iNode + 2);
            }

            val[0] = node1.pt.getX();
            val[1] = node2.pt.getX();
            val[2] = node3.pt.getX();
            val[3] = node4.pt.getX();
            nval[0] = node1.norm.getX();
            nval[1] = node2.norm.getX();
            nval[2] = node3.norm.getX();
            nval[3] = node4.norm.getX();
            Bspline(val, a);
            Bspline(nval, na);
            del = 0.0;
            if (iNode == 0) {
                node = new TubeNode(node2.pt.getX(), node2.pt.getY(), node2.pt.getZ(),
                        node2.norm.getX(), node2.norm.getY(), node2.norm.getZ());
                node.red = node2.red;
                node.green = node2.green;
                node.blue = node2.blue;
                node.a = node2.a;
                node.b = node2.b;
                node.rectWidth = node2.rectWidth;
                sNodes.add(node);
            }

            for (j = 0; j < nChords; j++) {
                x[j] = a[0]
                        + (del * (a[1] + (del * (a[2] + (del * a[3])))));
                nx[j] = na[0]
                        + (del * (na[1] + (del * (na[2] + (del * na[3])))));
                del += (1.0 / nChords);
            }

            val[0] = node1.pt.getY();
            val[1] = node2.pt.getY();
            val[2] = node3.pt.getY();
            val[3] = node4.pt.getY();
            nval[0] = node1.norm.getY();
            nval[1] = node2.norm.getY();
            nval[2] = node3.norm.getY();
            nval[3] = node4.norm.getY();
            Bspline(val, a);
            Bspline(nval, na);
            del = 0.0;
            for (j = 0; j < nChords; j++) {
                y[j] = a[0]
                        + (del * (a[1] + (del * (a[2] + (del * a[3])))));
                ny[j] = na[0]
                        + (del * (na[1] + (del * (na[2] + (del * na[3])))));
                del += (1.0 / nChords);
            }

            val[0] = node1.pt.getZ();
            val[1] = node2.pt.getZ();
            val[2] = node3.pt.getZ();
            val[3] = node4.pt.getZ();
            nval[0] = node1.norm.getZ();
            nval[1] = node2.norm.getZ();
            nval[2] = node3.norm.getZ();
            nval[3] = node4.norm.getZ();
            Bspline(val, a);
            Bspline(nval, na);
            del = 0.0;

            for (j = 0; j < nChords; j++) {
                z[j] = a[0]
                        + (del * (a[1] + (del * (a[2] + (del * a[3])))));
                nz[j] = na[0]
                        + (del * (na[1] + (del * (na[2] + (del * na[3])))));
                del += (1.0 / nChords);
            }
            del = 0;
            for (j = 0; j < nChords; j++) {
                node = new TubeNode(x[j], y[j], z[j], nx[j], ny[j], nz[j]);
                node.red = (float) (((1.0 - del) * node2.red)
                        + (del * node3.red));
                node.green = (float) (((1.0 - del) * node2.green)
                        + (del * node3.green));
                node.blue = (float) (((1.0 - del) * node2.blue)
                        + (del * node3.blue));
                node.a = (float) (((1.0 - del) * node2.a) + (del * node3.a));
                node.b = (float) (((1.0 - del) * node2.b) + (del * node3.b));
                node.rectWidth = (float) (((1.0 - del) * node2.rectWidth)
                        + (del * node3.rectWidth));
                sNodes.add(node);
                del += (1.0 / nChords);
            }
            if (iNode == (nodes.size() - 2)) {
                node = new TubeNode(node3.pt.getX(), node3.pt.getY(), node3.pt.getZ(),
                        node3.norm.getX(), node3.norm.getY(), node3.norm.getZ());
                node.red = node3.red;
                node.green = node3.green;
                node.blue = node3.blue;
                node.a = node3.a;
                node.b = node3.b;
                node.rectWidth = node3.rectWidth;
                sNodes.add(node);
            }

            node1 = node2;
        }
        return sNodes;

    }

    void Bspline(double[] y, double[] a) {
        a[3] = (((-1.0 * y[0]) + (3.0 * y[1])) - (3.0 * y[2]) + (1.0 * y[3])) / 6.0;
        a[2] = ((3.0 * y[0]) - (6.0 * y[1]) + (3.0 * y[2])) / 6.0;
        a[1] = ((-3.0 * y[0]) + (3.0 * y[2])) / 6.0;
        a[0] = ((1.0 * y[0]) + (4.0 * y[1]) + (1.0 * y[2])) / 6.0;
    }

}
