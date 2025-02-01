package org.nmrfx.analyst.gui.molecule3D;

import javafx.geometry.Point3D;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import java.util.ArrayList;
import java.util.List;

public class Tube {

    public int nChords = 5;
    public int bSides = 8;
    public ArrayList<TubeNode> nodes;

    public Tube() {
        nodes = new ArrayList<>();
    }

    public void setColor(int iNode, float red, float green, float blue) {
        TubeNode node = nodes.get(iNode);
        node.red = red;
        node.green = green;
        node.blue = blue;

    }

    public void setRadius(int iNode, double a, double b, double rectWidth) {
        TubeNode node = nodes.get(iNode);
        node.a = a;
        node.b = b;
        node.rectWidth = rectWidth;

    }

    public void addNode(double x, double y, double z, double nx, double ny,
                        double nz) {
        TubeNode node = new TubeNode(x, y, z, nx, ny, nz);
        nodes.add(node);
    }

    public List<TubeNode> createPathPoly() {
        double[][] x = new double[3][nodes.size()];
        double[][] xn = new double[3][nodes.size()];
        double[] ix = new double[nodes.size()];

        for (int i = 0; i < nodes.size(); i++) {
            TubeNode node = nodes.get(i);
            for (int j = 0; j < 3; j++) {
                Point3D pt = node.pt;
                Point3D ptn = node.norm;
                double v;
                double vn;
                switch (j) {
                    case 0 -> {
                        v = pt.getX();
                        vn = ptn.getX();
                    }
                    case 1 -> {
                        v = pt.getY();
                        vn = ptn.getY();
                    }
                    default -> {
                        v = pt.getZ();
                        vn = ptn.getZ();
                    }
                }

                x[j][i] = v;
                xn[j][i] = vn;
            }
            ix[i] = i;
        }
        ArrayList<TubeNode> sNodes = new ArrayList<>();

        PolynomialSplineFunction[] polys = new PolynomialSplineFunction[3];
        PolynomialSplineFunction[] polyns = new PolynomialSplineFunction[3];
        for (int j = 0; j < 3; j++) {
            SplineInterpolator splineInterpolator = new SplineInterpolator();
            polys[j] = splineInterpolator.interpolate(ix, x[j]);
            polyns[j] = splineInterpolator.interpolate(ix, xn[j]);
        }
        double[] newv = new double[3];
        double[] newnv = new double[3];
        for (int i = 1; i < nodes.size(); i++) {
            TubeNode node0 = nodes.get(i - 1);
            TubeNode node1 = nodes.get(i);
            sNodes.add(node0);

            for (int k = 0; k < nChords; k++) {
                double f = (double) (k +1 ) / (nChords + 1);
                for (int j = 0; j < 3; j++) {
                    double ix0 = ix[i - 1] + f;
                    newv[j] = polys[j].value(ix0);
                    newnv[j] = polyns[j].value(ix0);
                }
                TubeNode node = new TubeNode(newv[0], newv[1], newv[2], newnv[0], newnv[1], newnv[2]);
                node.red = (float) (((1.0 - f) * node0.red)
                        + (f * node1.red));
                node.green = (float) (((1.0 - f) * node0.green)
                        + (f * node1.green));
                node.blue = (float) (((1.0 - f) * node0.blue)
                        + (f * node1.blue));
                node.a = (float) (((1.0 - f) * node0.a) + (f * node1.a));
                node.b = (float) (((1.0 - f) * node0.b) + (f * node1.b));
                node.rectWidth = (float) (((1.0 - f) * node0.rectWidth)
                        + (f * node1.rectWidth));
                sNodes.add(node);
            }
        }
        sNodes.add(nodes.getLast());
        return sNodes;
    }

}
