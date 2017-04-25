/*
 * NMRFx Structure : A Program for Calculating Structures 
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.nmrfx.structure.chemistry;

import java.awt.*;
import java.util.*;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

public class Tube {

    public int nChords = 5;
    public int bSides = 8;
    public Vector nodes;
    public Vector triangles;
    public double lastAngle = -100000.0;
    Graphics g;
    private double xCenter;
    private double yCenter;
    private double scale;
    Dimension d;
    double[][] rBond1 = null;
    double[][] rBond3 = null;
    double[][] rotV = null;
    Vector3D lastCP = null;

    public Tube() {
        nodes = new Vector();
        triangles = new Vector();
    }

    public void setColor(int iNode, float red, float green, float blue) {
        Node node = (Node) nodes.elementAt(iNode);
        node.red = red;
        node.green = green;
        node.blue = blue;

        //System.out.println(node.red + " " + node.green + " " + node.blue);
    }

    public void setRadius(int iNode, double a, double b, double rectWidth) {
        Node node = (Node) nodes.elementAt(iNode);
        node.a = a;
        node.b = b;
        node.rectWidth = rectWidth;

        //System.out.println(node.red + " " + node.green + " " + node.blue);
    }

    public void setNSides(int nSides) {
        bSides = nSides;
    }

    public void setNChords(int nChords) {
        this.nChords = nChords;
    }

    public void addNode(double x, double y, double z, double nx, double ny,
            double nz) {
        Node node = new Node(x, y, z, nx, ny, nz);
        nodes.addElement(node);
    }

    void drawNode(Node node1, Node node2, Node node3, Node node4) {
        int start;
        int dir;
        int i;
        int j;
        int k;
        double delta1;
        double delta2;
        double del;
        int[] x = new int[3];
        int[] y = new int[3];
        int[] z = new int[3];
        double[][] rBond2 = new double[7][3];
        int x1a;
        int x1b;
        int x2a;
        int x2b;
        int y1a;
        int y1b;
        int y2a;
        int y2b;
        int z1a;
        int z1b;
        int z2a;
        int z2b;
        float red;
        float green;
        float blue;
        float shade;
        double[] light = {0.0, 0.0, 3.0};
        Color color;
        double lNorm;
        double rNorm2;
        lNorm = Math.sqrt((light[0] * light[0]) + (light[1] * light[1])
                + (light[2] * light[2]));

        if (rBond1 == null) {
            rBond1 = makeTubeBond(node1.pt, node2.norm, node3.pt, node2.a,
                    node2.b, node2.rectWidth);
        }

        rBond3 = makeTubeBond(node2.pt, node3.norm, node4.pt, node3.a, node3.b,
                node2.rectWidth);

        //  System.out.println(node2.pt.x+" "+node2.pt.y+" "+node2.pt.z+" "+node3.pt.x+" "+node3.pt.y+" "+node3.pt.z);
        delta1 = 0.0;
        i = 0;
        j = 0;
        start = 0;

        double deltaMin = 1.0e6;

        for (j = 0; j < rBond1.length; j++) {
            delta1 = 0.0;

            for (k = 0; k < 3; k++) {
                del = rBond1[0][k] - rBond3[j][k];
                delta1 += (del * del);
            }

            if (delta1 < deltaMin) {
                deltaMin = delta1;
                start = j;
            }
        }

        delta1 = 0.0;
        i = rBond1.length / 4;
        j = start - (rBond3.length / 4);

        if (j < 0) {
            j = (3 * rBond3.length) / 4;
        }

        for (k = 0; k < 3; k++) {
            del = rBond1[i][k] - rBond3[j][k];
            delta1 += (del * del);
        }

        delta2 = 0.0;
        j = start + (rBond3.length / 4);

        if (j >= rBond3.length) {
            j = rBond3.length / 4;
        }

        for (k = 0; k < 3; k++) {
            del = rBond1[i][k] - rBond3[j][k];
            delta2 += (del * del);
        }

        if (delta1 < delta2) {
            dir = -1;
        } else {
            dir = +1;
        }

        //start = 0;
        //dir = 1;
        j = 0;
        k = start;

        //System.out.println(start + " " + dir);
        for (i = 0; i < rBond1.length; i++) {
            //System.out.println(i + " " + j + " " + k + " " + rBond1[j][0] + " " + rBond1[j][1] + " " + rBond1[j][2]);
            //System.out.println(i + " " + j + " " + k + " " + rBond1[j][0] + " " + rBond1[j][1] + " " + rBond1[j][2] + " " + rNorm1);
            x1a = (int) ((((node2.pt.getX() + rBond1[j][0]) - xCenter) * scale)
                    + (d.width / 2));
            y1a = (int) (((yCenter - node2.pt.getY() - rBond1[j][1]) * scale)
                    + (d.height / 2));
            z1a = (int) ((node2.pt.getZ() + rBond1[j][2]) * scale);
            rBond2[0][0] = rBond1[j][0] + node2.pt.getX();
            rBond2[0][1] = rBond1[j][1] + node2.pt.getY();
            rBond2[0][2] = rBond1[j][2] + node2.pt.getZ();

            //System.out.println(i + " " + j + " " + k + " " + rBond1[j][0] + " " + rBond1[j][1] + " " + rBond1[j][2]);
            //x1a = (int) ((xCenter + rBond1[j][0] - xCenter) * scale + d.width / 2);
            //y1a = (int) ((yCenter + rBond1[j][1] - yCenter) * scale + d.height / 2);
            j++;

            if (j >= rBond1.length) {
                j = 0;
            }

            //System.out.println(i + " " + j + " " + k + " " + rBond1[j][0] + " " + rBond1[j][1] + " " + rBond1[j][2]);
            //System.out.println(i + " " + j + " " + k + " " + rBond1[j][0] + " " + rBond1[j][1] + " " + rBond1[j][2] + " " + rNorm1);
            x1b = (int) ((((node2.pt.getX() + rBond1[j][0]) - xCenter) * scale)
                    + (d.width / 2));
            y1b = (int) (((yCenter - node2.pt.getY() - rBond1[j][1]) * scale)
                    + (d.height / 2));
            z1b = (int) ((node2.pt.getZ() + rBond1[j][2]) * scale);
            rBond2[1][0] = rBond1[j][0] + node2.pt.getX();
            rBond2[1][1] = rBond1[j][1] + node2.pt.getY();
            rBond2[1][2] = rBond1[j][2] + node2.pt.getZ();

            //x1b = (int) ((xCenter + rBond1[j][0] - xCenter) * scale + d.width / 2);
            //y1b = (int) ((yCenter + rBond1[j][1] - yCenter) * scale + d.height / 2);
            x2a = (int) ((((node3.pt.getX() + rBond3[k][0]) - xCenter) * scale)
                    + (d.width / 2));
            y2a = (int) (((yCenter - node3.pt.getY() - rBond3[k][1]) * scale)
                    + (d.height / 2));
            z2a = (int) ((node3.pt.getZ() + rBond3[k][2]) * scale);

            //System.out.println(i + " " + j + " " + k + " " + rBond3[k][0] + " " + rBond3[k][1] + " " + rBond3[k][2]);
            rBond2[2][0] = rBond3[k][0] + node3.pt.getX();
            rBond2[2][1] = rBond3[k][1] + node3.pt.getY();
            rBond2[2][2] = rBond3[k][2] + node3.pt.getZ();
            k += dir;

            if (k < 0) {
                k = rBond3.length - 1;
            } else if (k >= rBond3.length) {
                k = 0;
            }

            x2b = (int) ((((node3.pt.getX() + rBond3[k][0]) - xCenter) * scale)
                    + (d.width / 2));
            y2b = (int) (((yCenter - node3.pt.getY() - rBond3[k][1]) * scale)
                    + (d.height / 2));
            z2b = (int) ((node3.pt.getZ() + rBond3[k][2]) * scale);
            rBond2[3][0] = rBond3[k][0] + node3.pt.getX();
            rBond2[3][1] = rBond3[k][1] + node3.pt.getY();
            rBond2[3][2] = rBond3[k][2] + node3.pt.getZ();
            rBond2[4][0] = rBond2[1][0] - rBond2[0][0];
            rBond2[4][1] = rBond2[1][1] - rBond2[0][1];
            rBond2[4][2] = rBond2[1][2] - rBond2[0][2];
            rBond2[5][0] = rBond2[2][0] - rBond2[0][0];
            rBond2[5][1] = rBond2[2][1] - rBond2[0][1];
            rBond2[5][2] = rBond2[2][2] - rBond2[0][2];

            //System.out.println(i + " " + j + " " + k + " " + rBond3[k][0] + " " + rBond3[k][1] + " " + rBond3[k][2]);
            rBond2[6] = crossProduct(rBond2[4], rBond2[5], rBond2[6]);

            //System.out.println(i + " " + j + " " + k + " " + rBond2[6][0] + " " + rBond2[6][1] + " " + rBond2[6][2]);
            if (rBond2[6][2] < 0.0) {
                continue;
            }

            rNorm2 = Math.sqrt((rBond2[6][0] * rBond2[6][0])
                    + (rBond2[6][1] * rBond2[6][1])
                    + (rBond2[6][2] * rBond2[6][2]));
            shade = (float) (((rBond2[6][0] * light[0])
                    + (rBond2[6][1] * light[1]) + (rBond2[6][2] * light[2])) / (rNorm2 * lNorm));
            shade = shade + 0.2f;

            //System.out.println(rNorm1 + " " + rNorm2 + " " + rNorm3);
            if (shade < 0.0) {
                shade = 0.1f;
            } else if (shade >= 1.0) {
                shade = 0.99999f;
            }

            //shade = 1.0f;
            //System.out.println(node2.red + " " + node2.green + " " + node2.blue + " " + shade);
            red = node2.red * shade;
            green = node2.green * shade;
            blue = node2.blue * shade;

            //System.out.println(red + " " + green + " " + blue + " " + shade);
            color = new Color(red, green, blue);

            x[0] = x1a;
            x[1] = x2a;
            x[2] = x1b;
            y[0] = y1a;
            y[1] = y2a;
            y[2] = y1b;
            z[0] = z1a;
            z[1] = z2a;
            z[2] = z1b;
            new Triangle(x, y, z, color);

            //g.setColor(color);
            //g.fillPolygon(x, y, 3);
            x[0] = x2a;
            x[1] = x1b;
            x[2] = x2b;
            y[0] = y2a;
            y[1] = y1b;
            y[2] = y2b;
            z[0] = z2a;
            z[1] = z1b;
            z[2] = z2b;
            new Triangle(x, y, z, color);

            //g.fillPolygon(x, y, 3);

            /*
             * g.setColor(color); g.drawLine(x1a,y1a,x1b,y1b);
             * g.drawLine(x2a,y2a,x2b,y2b); g.setColor(Color.green);
             * g.drawLine(x1a,y1a,x2a,y2a); g.drawLine(x1b,y1b,x2b,y2b);
             */
            //System.out.println(rBond1[j][0] + " " + rBond1[j][1] + " " + rBond1[j][2]);
        }

        rBond1 = rBond3;
    }

    int createTriangle(Node node1, Node node2, Node node3, Node node4,
            float[] coords, int ii, float[] norms, float[] colors) {

        int start;
        int dir;
        int i;
        int j;
        int k;
        int jj;
        double delta1;
        double delta2;
        double del;
        double[] x = new double[3];
        double[] y = new double[3];
        double[] z = new double[3];
        double[][] rBond2 = new double[7][3];
        double x1a;
        double x1b;
        double x2a;
        double x2b;
        double y1a;
        double y1b;
        double y2a;
        double y2b;
        double z1a;
        double z1b;
        double z2a;
        double z2b;
        double[] light = {0.0, 0.0, 3.0};
        double rNorm2;
        // fixme why do we do this double lNorm = Math.sqrt((light[0] * light[0]) + (light[1] * light[1]) +
        // (light[2] * light[2]));

        if (rBond1 == null) {
            rBond1 = makeTubeBond(node1.pt, node2.norm, node3.pt, node2.a,
                    node2.b, node2.rectWidth);
        }

        rBond3 = makeTubeBond(node2.pt, node3.norm, node4.pt, node3.a, node3.b,
                node2.rectWidth);

        //  System.out.println(node2.pt.x+" "+node2.pt.y+" "+node2.pt.z+" "+node3.pt.x+" "+node3.pt.y+" "+node3.pt.z);
        delta1 = 0.0;
        i = 0;
        j = 0;
        start = 0;

        double deltaMin = 1.0e6;

        for (j = 0; j < rBond1.length; j++) {
            delta1 = 0.0;

            for (k = 0; k < 3; k++) {
                del = rBond1[0][k] - rBond3[j][k];
                delta1 += (del * del);
            }

            if (delta1 < deltaMin) {
                deltaMin = delta1;
                start = j;
            }
        }

        delta1 = 0.0;
        i = rBond1.length / 4;
        j = start - (rBond3.length / 4);

        if (j < 0) {
            j = (3 * rBond3.length) / 4;
        }

        for (k = 0; k < 3; k++) {
            del = rBond1[i][k] - rBond3[j][k];
            delta1 += (del * del);
        }

        delta2 = 0.0;
        j = start + (rBond3.length / 4);

        if (j >= rBond3.length) {
            j = rBond3.length / 4;
        }

        for (k = 0; k < 3; k++) {
            del = rBond1[i][k] - rBond3[j][k];
            delta2 += (del * del);
        }

        if (delta1 < delta2) {
            dir = -1;
        } else {
            dir = +1;
        }

        //start = 0;
        //dir = 1;
        j = 0;
        k = start;

        for (i = 0; i < rBond1.length; i++) {
            //System.out.println(i + " " + j + " " + k + " " + rBond1[j][0] + " " + rBond1[j][1] + " " + rBond1[j][2]);
            //System.out.println(i + " " + j + " " + k + " " + rBond1[j][0] + " " + rBond1[j][1] + " " + rBond1[j][2] + " " + rNorm1);
            x1a = ((node2.pt.getX() + rBond1[j][0]));
            y1a = ((node2.pt.getY() + rBond1[j][1]));
            z1a = ((node2.pt.getZ() + rBond1[j][2]));
            rBond2[0][0] = rBond1[j][0];
            rBond2[0][1] = rBond1[j][1];
            rBond2[0][2] = rBond1[j][2];
            j++;

            if (j >= rBond1.length) {
                j = 0;
            }

            //System.out.println(i + " " + j + " " + k + " " + rBond1[j][0] + " " + rBond1[j][1] + " " + rBond1[j][2]);
            x1b = ((node2.pt.getX() + rBond1[j][0]));
            y1b = ((node2.pt.getY() + rBond1[j][1]));
            z1b = ((node2.pt.getZ() + rBond1[j][2]));
            rBond2[1][0] = rBond1[j][0];
            rBond2[1][1] = rBond1[j][1];
            rBond2[1][2] = rBond1[j][2];

            //System.out.println(i + " " + j + " " + k + " " + rBond3[k][0] + " " + rBond3[k][1] + " " + rBond3[k][2]);
            x2a = ((node3.pt.getX() + rBond3[k][0]));
            y2a = ((node3.pt.getY() + rBond3[k][1]));
            z2a = ((node3.pt.getZ() + rBond3[k][2]));
            rBond2[2][0] = rBond3[k][0];
            rBond2[2][1] = rBond3[k][1];
            rBond2[2][2] = rBond3[k][2];
            k += dir;

            if (k < 0) {
                k = rBond3.length - 1;
            } else if (k >= rBond3.length) {
                k = 0;
            }

            //System.out.println(i + " " + j + " " + k + " " + rBond3[k][0] + " " + rBond3[k][1] + " " + rBond3[k][2]);
            x2b = ((node3.pt.getX() + rBond3[k][0]));
            y2b = ((node3.pt.getY() + rBond3[k][1]));
            z2b = ((node3.pt.getZ() + rBond3[k][2]));

            rBond2[3][0] = rBond3[k][0];
            rBond2[3][1] = rBond3[k][1];
            rBond2[3][2] = rBond3[k][2];

            //System.out.println(i + " " + j + " " + k + " " + rBond3[k][0] + " " + rBond3[k][1] + " " + rBond3[k][2]);
            rBond2[6] = crossProduct(rBond2[4], rBond2[5], rBond2[6]);

            //System.out.println(i + " " + j + " " + k + " " + rBond2[6][0] + " " + rBond2[6][1] + " " + rBond2[6][2]);
            rNorm2 = Math.sqrt((rBond2[0][0] * rBond2[0][0])
                    + (rBond2[0][1] * rBond2[0][1])
                    + (rBond2[0][2] * rBond2[0][2]));

            jj = ii;
            rNorm2 = Math.sqrt((rBond2[0][0] * rBond2[0][0])
                    + (rBond2[0][1] * rBond2[0][1])
                    + (rBond2[0][2] * rBond2[0][2]));

            norms[jj++] = (float) (rBond2[0][0] / rNorm2);
            norms[jj++] = (float) (rBond2[0][1] / rNorm2);
            norms[jj++] = (float) (rBond2[0][2] / rNorm2);

            rNorm2 = Math.sqrt((rBond2[1][0] * rBond2[1][0])
                    + (rBond2[1][1] * rBond2[1][1])
                    + (rBond2[1][2] * rBond2[1][2]));
            norms[jj++] = (float) (rBond2[1][0] / rNorm2);
            norms[jj++] = (float) (rBond2[1][1] / rNorm2);
            norms[jj++] = (float) (rBond2[1][2] / rNorm2);

            rNorm2 = Math.sqrt((rBond2[2][0] * rBond2[2][0])
                    + (rBond2[2][1] * rBond2[2][1])
                    + (rBond2[2][2] * rBond2[2][2]));
            norms[jj++] = (float) (rBond2[2][0] / rNorm2);
            norms[jj++] = (float) (rBond2[2][1] / rNorm2);
            norms[jj++] = (float) (rBond2[2][2] / rNorm2);

            jj = ii;

            colors[jj++] = node2.red;
            colors[jj++] = node2.green;
            colors[jj++] = node2.blue;
            colors[jj++] = node2.red;
            colors[jj++] = node2.green;
            colors[jj++] = node2.blue;
            colors[jj++] = node2.red;
            colors[jj++] = node2.green;
            colors[jj++] = node2.blue;

            coords[ii++] = (float) x1a;
            coords[ii++] = (float) y1a;
            coords[ii++] = (float) z1a;
            coords[ii++] = (float) x1b;
            coords[ii++] = (float) y1b;
            coords[ii++] = (float) z1b;
            coords[ii++] = (float) x2a;
            coords[ii++] = (float) y2a;
            coords[ii++] = (float) z2a;
            jj = ii;

            rNorm2 = Math.sqrt((rBond2[1][0] * rBond2[1][0])
                    + (rBond2[1][1] * rBond2[1][1])
                    + (rBond2[1][2] * rBond2[1][2]));
            norms[jj++] = (float) (rBond2[1][0] / rNorm2);
            norms[jj++] = (float) (rBond2[1][1] / rNorm2);
            norms[jj++] = (float) (rBond2[1][2] / rNorm2);
            rNorm2 = Math.sqrt((rBond2[3][0] * rBond2[3][0])
                    + (rBond2[3][1] * rBond2[3][1])
                    + (rBond2[3][2] * rBond2[3][2]));
            norms[jj++] = (float) (rBond2[3][0] / rNorm2);
            norms[jj++] = (float) (rBond2[3][1] / rNorm2);
            norms[jj++] = (float) (rBond2[3][2] / rNorm2);
            rNorm2 = Math.sqrt((rBond2[2][0] * rBond2[2][0])
                    + (rBond2[2][1] * rBond2[2][1])
                    + (rBond2[2][2] * rBond2[2][2]));
            norms[jj++] = (float) (rBond2[2][0] / rNorm2);
            norms[jj++] = (float) (rBond2[2][1] / rNorm2);
            norms[jj++] = (float) (rBond2[2][2] / rNorm2);
            jj = ii;
            colors[jj++] = node2.red;
            colors[jj++] = node2.green;
            colors[jj++] = node2.blue;
            colors[jj++] = node2.red;
            colors[jj++] = node2.green;
            colors[jj++] = node2.blue;
            colors[jj++] = node2.red;
            colors[jj++] = node2.green;
            colors[jj++] = node2.blue;

            coords[ii++] = (float) x1b;
            coords[ii++] = (float) y1b;
            coords[ii++] = (float) z1b;
            coords[ii++] = (float) x2b;
            coords[ii++] = (float) y2b;
            coords[ii++] = (float) z2b;
            coords[ii++] = (float) x2a;
            coords[ii++] = (float) y2a;
            coords[ii++] = (float) z2a;
        }

        rBond1 = rBond3;

        return ii;
    }

    int createMeshLines(Node node1, Node node2, Node node3, Node node4,
            double[] coords, int ii, float[] norms, float[] colors) {

        int start;
        int dir;
        int i;
        int j;
        int k;
        int jj;
        double delta1;
        double delta2;
        double del;
        double x1a;
        double x1b;
        double x2a;
        double x2b;
        double y1a;
        double y1b;
        double y2a;
        double y2b;
        double z1a;
        double z1b;
        double z2a;
        double z2b;
        double rNorm2;
        // fixme why do we do this double lNorm = Math.sqrt((light[0] * light[0]) + (light[1] * light[1]) +
        // (light[2] * light[2]));

        if (rBond1 == null) {
            rBond1 = makeTubeBond(node1.pt, node2.norm, node3.pt, node2.a,
                    node2.b, node2.rectWidth);
        }

        rBond3 = makeTubeBond(node2.pt, node3.norm, node4.pt, node3.a, node3.b,
                node2.rectWidth);

        //  System.out.println(node2.pt.x+" "+node2.pt.y+" "+node2.pt.z+" "+node3.pt.x+" "+node3.pt.y+" "+node3.pt.z);
        delta1 = 0.0;
        i = 0;
        j = 0;
        start = 0;

        double deltaMin = 1.0e6;

        for (j = 0; j < rBond1.length; j++) {
            delta1 = 0.0;

            for (k = 0; k < 3; k++) {
                del = rBond1[0][k] - rBond3[j][k];
                delta1 += (del * del);
            }

            if (delta1 < deltaMin) {
                deltaMin = delta1;
                start = j;
            }
        }

        delta1 = 0.0;
        i = rBond1.length / 4;
        j = start - (rBond3.length / 4);

        if (j < 0) {
            j = (3 * rBond3.length) / 4;
        }

        for (k = 0; k < 3; k++) {
            del = rBond1[i][k] - rBond3[j][k];
            delta1 += (del * del);
        }

        delta2 = 0.0;
        j = start + (rBond3.length / 4);

        if (j >= rBond3.length) {
            j = rBond3.length / 4;
        }

        for (k = 0; k < 3; k++) {
            del = rBond1[i][k] - rBond3[j][k];
            delta2 += (del * del);
        }

        if (delta1 < delta2) {
            dir = -1;
        } else {
            dir = +1;
        }

        //start = 0;
        //dir = 1;
        j = 0;
        k = start;

        for (i = 0; i < rBond1.length; i++) {
            //     System.out.println(i + " " + j + " " + k + " " + rBond1[j][0] + " " + rBond1[j][1] + " " + rBond1[j][2]);
            //System.out.println(i + " " + j + " " + k + " " + rBond1[j][0] + " " + rBond1[j][1] + " " + rBond1[j][2] + " " + rNorm1);
            x1a = ((node2.pt.getX() + rBond1[j][0]));
            y1a = ((node2.pt.getY() + rBond1[j][1]));
            z1a = ((node2.pt.getZ() + rBond1[j][2]));
            j++;

            if (j >= rBond1.length) {
                j = 0;
            }
            x1b = ((node2.pt.getX() + rBond1[j][0]));
            y1b = ((node2.pt.getY() + rBond1[j][1]));
            z1b = ((node2.pt.getZ() + rBond1[j][2]));

            System.out.println(i + " " + j + " " + k + " " + rBond3[k][0] + " " + rBond3[k][1] + " " + rBond3[k][2]);
            x2a = ((node3.pt.getX() + rBond3[k][0]));
            y2a = ((node3.pt.getY() + rBond3[k][1]));
            z2a = ((node3.pt.getZ() + rBond3[k][2]));

            k += dir;

            if (k < 0) {
                k = rBond3.length - 1;
            } else if (k >= rBond3.length) {
                k = 0;
            }

            jj = ii;

            colors[jj++] = (1.0f * i / rBond1.length);
            colors[jj++] = (1.0f - 1.0f * i / rBond1.length);
            colors[jj++] = 0;
            colors[jj++] = (1.0f * i / rBond1.length);
            colors[jj++] = (1.0f - 1.0f * i / rBond1.length);
            colors[jj++] = 0;
            colors[jj++] = (1.0f * i / rBond1.length);
            colors[jj++] = (1.0f - 1.0f * i / rBond1.length);
            colors[jj++] = 0;
            colors[jj++] = (1.0f * i / rBond1.length);
            colors[jj++] = (1.0f - 1.0f * i / rBond1.length);
            colors[jj++] = 0;

            coords[ii++] = x1a;
            coords[ii++] = y1a;
            coords[ii++] = z1a;
            coords[ii++] = x2a;
            coords[ii++] = y2a;
            coords[ii++] = z2a;
            coords[ii++] = x1a;
            coords[ii++] = y1a;
            coords[ii++] = z1a;
            coords[ii++] = x1b;
            coords[ii++] = y1b;
            coords[ii++] = z1b;
            //System.out.println(x1a+" "+y1a+" "+z1a+" "+x2a+" "+y2a+" "+z2a+" "+ii);
            jj = ii;

        }

        rBond1 = rBond3;

        return ii;
    }

    public int createTriangleArray(float[] coord, int ii, float[] norms,
            float[] colors) {
        int j;
        Node node;
        Node node1;
        Node node2;
        Node node3;
        Node node4;

        if (nodes.size() < 3) {
            return 0;
        }

        rBond1 = null;
        this.g = g;

        double del;
        double[] a = new double[4];
        double[] val = new double[4];
        double[] na = new double[4];
        double[] nval = new double[4];
        Vector sNodes = new Vector();
        node1 = (Node) nodes.elementAt(0);

        final double x[] = new double[nChords];
        final double nx[] = new double[nChords];
        final double y[] = new double[nChords];
        final double ny[] = new double[nChords];
        final double z[] = new double[nChords];
        final double nz[] = new double[nChords];

        int iNode;

        for (iNode = 0; iNode < (nodes.size() - 1); iNode++) {
            node2 = (Node) nodes.elementAt(iNode);
            node3 = (Node) nodes.elementAt(iNode + 1);

            if (iNode == (nodes.size() - 2)) {
                node4 = node3;
            } else {
                node4 = (Node) nodes.elementAt(iNode + 2);
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
                node = new Node(x[j], y[j], z[j], nx[j], ny[j], nz[j]);
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
                sNodes.addElement(node);
                del += (1.0 / nChords);
            }

            node1 = node2;
        }

        node1 = (Node) sNodes.elementAt(0);

        for (iNode = 0; iNode < (sNodes.size() - 1); iNode++) {
            node2 = (Node) sNodes.elementAt(iNode);
            node3 = (Node) sNodes.elementAt(iNode + 1);

            if (iNode == (sNodes.size() - 2)) {
                node4 = node3;
            } else {
                node4 = (Node) sNodes.elementAt(iNode + 2);
            }

            ii = createTriangle(node1, node2, node3, node4, coord, ii, norms,
                    colors);
//            ii = createMeshLines(node1, node2, node3, node4, coord, ii, norms, colors);
            node1 = node2;
        }

        return ii;
    }

    public void drawNodes(Graphics g, double xCenter, double yCenter,
            double scale, Dimension d) {
        Node node;
        Node node1;
        Node node2;
        Node node3;
        Node node4;

        if (nodes.size() < 3) {
            return;
        }

        rBond1 = null;
        this.g = g;
        this.xCenter = xCenter;
        this.yCenter = yCenter;
        this.scale = scale;
        this.d = d;

        int nChords = 3;
        double del;
        double[] a = new double[4];
        double[] val = new double[4];
        double[] na = new double[4];
        double[] nval = new double[4];
        Vector sNodes = new Vector();
        node1 = (Node) nodes.elementAt(0);

        final double x[] = new double[nChords];
        final double nx[] = new double[nChords];
        final double y[] = new double[nChords];
        final double ny[] = new double[nChords];
        final double z[] = new double[nChords];
        final double nz[] = new double[nChords];

        triangles.setSize(0);

        for (int iNode = 0; iNode < (nodes.size() - 1); iNode++) {
            node2 = (Node) nodes.elementAt(iNode);
            node3 = (Node) nodes.elementAt(iNode + 1);

            if (iNode == (nodes.size() - 2)) {
                node4 = node3;
            } else {
                node4 = (Node) nodes.elementAt(iNode + 2);
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

            for (int j = 0; j < nChords; j++) {
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

            for (int j = 0; j < nChords; j++) {
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

            for (int j = 0; j < nChords; j++) {
                z[j] = a[0]
                        + (del * (a[1] + (del * (a[2] + (del * a[3])))));
                nz[j] = na[0]
                        + (del * (na[1] + (del * (na[2] + (del * na[3])))));
                del += (1.0 / nChords);
            }
            del = 0.0;
            for (int j = 0; j < nChords; j++) {
                node = new Node(x[j], y[j], z[j], nx[j], ny[j], nz[j]);
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
                sNodes.addElement(node);
                del += (1.0 / nChords);
            }

            node1 = node2;
        }

        node1 = (Node) sNodes.elementAt(0);

        for (int iNode = 0; iNode < (sNodes.size() - 1); iNode++) {
            node2 = (Node) sNodes.elementAt(iNode);
            node3 = (Node) sNodes.elementAt(iNode + 1);

            if (iNode == (sNodes.size() - 2)) {
                node4 = node3;
            } else {
                node4 = (Node) sNodes.elementAt(iNode + 2);
            }

            drawNode(node1, node2, node3, node4);
            node1 = node2;
        }

        sortTriangles();

        Triangle triangle;

        for (int i = 0; i < triangles.size(); i++) {
            triangle = (Triangle) triangles.elementAt(i);
            g.setColor(triangle.color);
            g.fillPolygon(triangle.x, triangle.y, 3);

            //System.out.println(triangle.x[0]+" "+triangle.y[0]);
        }
    }

    void sortTriangles() {
        int i;
        int j;
        int nn;
        int m = 0;
        int n = triangles.size();
        double ztest1;
        double ztest2;
        Triangle triangle1 = null;
        Triangle triangle2 = null;
        int lognb2 = (int) ((Math.log((double) n) * 1.442695022) + 1.0e-5);

        m = n;

        for (nn = 0; nn < lognb2; nn++) {
            m >>= 1;

            for (j = m; j < n; j++) {
                i = j - m;
                triangle1 = (Triangle) triangles.elementAt(j);
                ztest1 = triangle1.zmax;
                triangle2 = (Triangle) triangles.elementAt(i);
                ztest2 = triangle2.zmax;

                while ((i >= 0) && (ztest2 > ztest1)) {
                    triangles.setElementAt(triangle2, i + m);
                    i -= m;

                    if (i < 0) {
                        break;
                    }

                    triangle2 = (Triangle) triangles.elementAt(i);
                    ztest2 = triangle2.zmax;
                }

                triangles.setElementAt(triangle1, i + m);
            }
        }
    }

    /*
     * void sortTriangles() { int min,i,j; int n = triangles.size(); double
     * ztest; Triangle hold,triangleI=null,triangle1=null,triangle2=null;
     * System.out.println("nTriangles "+n); for (i = 0; i < n; i++) { min =
     * i; triangleI = (Triangle) triangles.elementAt(i); ztest =
     * triangleI.zmax;
     *
     * for (j = i + 1; j < n; j++) { triangle1 = (Triangle)
     * triangles.elementAt(j); if (triangle1.zmax < ztest) { min = j; ztest =
     * triangle1.zmax; } } if (min != i) { hold = (Triangle)
     * triangles.elementAt(min); triangles.setElementAt(triangleI,min);
     * triangles.setElementAt(hold,i); } } }
     *
     */
    double[][] makeTubeBond(Point3 pt1, Point3 pt2, Point3 pt3, double a,
            double b, double rectWidth) {
        double dx;
        double dy;
        double dz;
        double theta_x;
        double theta_z;
        double dist;
        int i;
        int j;
        int k;
        double[][] roter1;
        double[][] roter2;
        double[][] roter3;
        roter1 = new double[4][4];
        roter2 = new double[4][4];
        roter3 = new double[4][4];

        bSides = (bSides / 2) * 2;

        double inc = (2.0 * Math.PI) / (double) bSides / 2;
        double angle = (Math.PI / 2.0) - inc;

        double[][] rBond;
        rBond = new double[bSides * 2][4];

        double cosA;
        double sinA;
        double radius;
        double[] normV = new double[3];
        dx = pt1.getX() - pt3.getX();
        dy = pt1.getY() - pt3.getY();
        dz = pt1.getZ() - pt3.getZ();

        Vector3D pt1a = new Vector3D(dx, dy, dz);
        Vector3D pt2c = new Vector3D(0.0, 0.0, 0.0);

        dist = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
        theta_x = Math.acos(dz / dist);
        theta_z = Math.atan2(dx, dy);

        pt2c = Vector3D.crossProduct(pt1a, pt2);

        final double nLength = Math.sqrt((pt2c.getX() * pt2c.getX()) + (pt2c.getY() * pt2c.getY()) + (pt2c.getZ() * pt2c.getZ()));
        pt2c = pt2c.scalarMultiply(1.0 / nLength);

        if (lastCP != null) {
            double dot = pt2c.getX() * lastCP.getX() + pt2c.getY() * lastCP.getY() + pt2c.getZ() * lastCP.getZ();
            if (dot < 0.0) {
                pt2c = pt2c.negate();
            }
            pt2c = (lastCP.add(pt2c)).scalarMultiply(1.0 / 2);
        }
        lastCP = pt2c;

        init_matrix(roter3);
        rot_matrix(roter3, 0, theta_x);
        rot_matrix(roter3, 2, theta_z);
        rotV = null;

        if (rotV == null) {
            rotV = new double[bSides * 2][3];
            init_matrix(roter1);
            rot_matrix(roter1, 2, -theta_z);
            rot_matrix(roter1, 0, -theta_x);

            for (j = 0; j < 3; j++) {
                normV[j] = (pt2c.getX() * roter1[j][0]) + (pt2c.getY() * roter1[j][1])
                        + (pt2c.getZ() * roter1[j][2]);
            }

            angle = Math.atan2(normV[0], normV[1]);

            init_matrix(roter2);
            rot_matrix(roter2, 2, -inc);

            for (j = 0; j < 3; j++) {
                rotV[0][j] = (normV[0] * roter2[j][0])
                        + (normV[1] * roter2[j][1]);
            }

            for (j = 0; j < 3; j++) {
                normV[j] = rotV[0][j];
            }

            init_matrix(roter2);
            rot_matrix(roter2, 2, inc);

            int m = 0;

            for (k = -1; k <= 1; k += 2) {
                for (i = 0; i < bSides; i++) {
                    for (j = 0; j < 3; j++) {
                        rotV[m][j] = (normV[0] * roter2[j][0])
                                + (normV[1] * roter2[j][1]);
                    }

                    //rotV[m][j] += k * rectWidth;
                    //bndtpl[0] = cosA * radius + k * rectWidth;
                    //bndtpl[1] = sinA * radius;
                    //System.out.println(m + " " + normV[0] + "  " + normV[1] + " " + normV[2]);
                    //System.out.println(m + " " + rotV[0] + "  " + rotV[1] + " " + rotV[2]);
                    //bndtpl[2] = 0.0;
                    for (j = 0; j < 3; j++) {
                        normV[j] = rotV[m][j];
                    }

                    m++;
                }
            }
        }

        //angle -= inc;
        angle = (Math.PI / 2.0) - inc;

        //angle = -inc;
//System.out.println("a "+a+" b "+b);
        for (i = 0; i < (bSides * 2); i++) {
            angle += inc;
            cosA = Math.cos(angle);
            sinA = Math.sin(angle);
            radius = Math.sqrt((a * a * b * b) / ((a * a * sinA * sinA)
                    + (b * b * cosA * cosA)));

            //radius = 1.0;
            double sum2 = 0.0;
            for (j = 0; j < 3; j++) {
                rBond[i][j] = (rotV[i][0] * radius * roter3[j][0])
                        + (rotV[i][1] * radius * roter3[j][1]);
                sum2 += rBond[i][j] * rBond[i][j];
            }
            double radius2 = Math.sqrt(sum2);

            //System.out.println(rotV[i][0] + " " + rotV[i][1] + " " + radius + " "+radius+" "+ angle * 180.0 / Math.PI);
        }

        return (rBond);
    }

    /**
     * ****************** init_bond_tpl *******************
     */

    /*
     * Initialize the cylinder bond template.
     */
 /*
     * static void init_bond_tpl() { int             i; float
     * angle, inc;
     *
     * bndtpl = matrix(B_Sides, 3); b_points = matrix(B_Sides, 3); b_points2 =
     * matrix(B_Sides, 3); b_points3 = matrix(B_Sides, 3);
     *
     * inc = 6.283185307 / (float) B_Sides; angle = -inc;
     *
     * for (i = 0; i < B_Sides; i++) { angle += inc; bndtpl[i][0] = fcos(angle);
     * bndtpl[i][1] = fsin(angle); bndtpl[i][2] = 0.0; } }
     */
 /*
     * Routine to initialize a matrix to the unit matrix
     */
    static void init_matrix(double[][] mat) {
        int i;
        int j;

        for (i = 0; i < 4; i++) {
            for (j = 0; j < 4; j++) {
                mat[i][j] = 0.0;
            }
        }

        for (i = 0; i < 4; i++) {
            mat[i][i] = 1.0;
        }
    }

    static double[] crossProduct(double[] i, double[] j, double[] k) {
        k[0] = ((i[1]) * (j[2])) - ((j[1]) * (i[2]));
        k[1] = ((i[2]) * (j[0])) - ((j[2]) * (i[0]));
        k[2] = ((i[0]) * (j[1])) - ((j[0]) * (i[1]));

        return (k);
    }

    /*
     * Routine to calculate rotation matrix
     */
    static void rot_matrix(double[][] mat, int axis, double rad) {
        int i;
        int j;
        double cs;
        double sn;
        double[][] temp = new double[4][4];
        double[][] prod = new double[4][4];

        //rad = deg * (Math.PI / 180.0);
        cs = Math.cos(rad);
        sn = Math.sin(rad);

        /* initialize the temp matrix */
        for (i = 0; i < 4; i++) {
            for (j = 0; j < 4; j++) {
                temp[i][j] = 0.0;
            }
        }

        for (i = 0; i < 4; i++) {
            temp[i][i] = 1.0;
        }

        /* set up for rotation */
        if (axis == 0) {
            /* x-axis */

            temp[0][0] = 1.0;
            temp[0][1] = 0.0;
            temp[0][2] = 0.0;
            temp[1][0] = 0.0;
            temp[1][1] = cs;
            temp[1][2] = sn;
            temp[2][0] = 0.0;
            temp[2][1] = -sn;
            temp[2][2] = cs;
        } else if (axis == 1) {
            /* y-axis */

            temp[0][0] = cs;
            temp[0][1] = 0.0;
            temp[0][2] = sn;
            temp[1][0] = 0.0;
            temp[1][1] = 1.0;
            temp[1][2] = 0.0;
            temp[2][0] = -sn;
            temp[2][1] = 0.0;
            temp[2][2] = cs;
        } else if (axis == 2) {
            /* z-axis */

            temp[0][0] = cs;
            temp[0][1] = sn;
            temp[0][2] = 0.0;
            temp[1][0] = -sn;
            temp[1][1] = cs;
            temp[1][2] = 0.0;
            temp[2][0] = 0.0;
            temp[2][1] = 0.0;
            temp[2][2] = 1.0;
        }

        /* multiply by current matrix */
        for (i = 0; i < 4; i++) {
            for (j = 0; j < 4; j++) {
                prod[i][j] = (temp[i][0] * mat[0][j])
                        + (temp[i][1] * mat[1][j]) + (temp[i][2] * mat[2][j])
                        + (temp[i][3] * mat[3][j]);
            }
        }

        /* transfer matrix back */
        for (i = 0; i < 4; i++) {
            for (j = 0; j < 4; j++) {
                mat[i][j] = prod[i][j];
            }
        }
    }

    void Bspline(double[] y, double[] a) {
        a[3] = (((-1.0 * y[0]) + (3.0 * y[1])) - (3.0 * y[2]) + (1.0 * y[3])) / 6.0;
        a[2] = ((3.0 * y[0]) - (6.0 * y[1]) + (3.0 * y[2])) / 6.0;
        a[1] = ((-3.0 * y[0]) + (3.0 * y[2])) / 6.0;
        a[0] = ((1.0 * y[0]) + (4.0 * y[1]) + (1.0 * y[2])) / 6.0;
    }

    class Node {

        public Point3 pt;
        public Point3 norm;
        public double a;
        public double b;
        public double rectWidth;
        float red;
        float green;
        float blue;
        Color color;

        Node(double x, double y, double z, double nx, double ny, double nz) {
            pt = new Point3(x, y, z);

            double length;
            length = Math.sqrt((nx * nx) + (ny * ny) + (nz * nz));
            norm = new Point3(nx / length, ny / length, nz / length);
            a = 0.5;
            b = 0.5;
            rectWidth = 1.0;
            color = Color.green;
            red = 1.0f;
            green = 0.2f;
            blue = 0.2f;
        }
    }

    class Triangle {

        int[] x = {0, 0, 0};
        int[] y = {0, 0, 0};
        int[] z = {0, 0, 0};
        int zmax = (int) -1e6;
        Color color;

        Triangle(int[] x, int[] y, int[] z, Color color) {
            int i;
            zmax = (int) -1e6;

            for (i = 0; i < 3; i++) {
                this.x[i] = x[i];
                this.y[i] = y[i];
                this.z[i] = z[i];

                if (z[i] > zmax) {
                    zmax = z[i];
                }
            }

            this.color = color;
            triangles.addElement(this);
        }
    }
}
