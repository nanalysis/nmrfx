package org.nmrfx.processor.gui.annotations;

import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class AnnoPolygon extends AnnoShape {
    private static final Logger log = LoggerFactory.getLogger(AnnoPolygon.class);
    double[] xCPoints;
    double[] yCPoints;
    double[] xPoints;
    double[] yPoints;
    double[] startX;
    double[] startY;
    double xp1;
    double xp2;
    double yp1;
    double yp2;
    int activeHandle = -1;

    public AnnoPolygon() {

    }

    public AnnoPolygon(List<Double> xList, List<Double> yList,
                       POSTYPE xPosType, POSTYPE yPosType) {
        this.xPoints = new double[xList.size()];
        this.yPoints = new double[yList.size()];
        xCPoints = new double[xPoints.length];
        yCPoints = new double[yPoints.length];

        for (int i = 0; i < xPoints.length; i++) {
            this.xPoints[i] = xList.get(i);
            this.yPoints[i] = yList.get(i);
        }

        this.xPosType = xPosType;
        this.yPosType = yPosType;

    }

    public double[] getxPoints() {
        return xPoints;
    }

    public void setxPoints(double[] xPoints) {
        this.xPoints = xPoints.clone();
        xCPoints = new double[xPoints.length];
    }

    public double[] getyPoints() {
        return yPoints;
    }

    public void setyPoints(double[] yPoints) {
        this.yPoints = yPoints.clone();
        yCPoints = new double[yPoints.length];
    }

    @Override
    public boolean hit(double x, double y, boolean selectMode) {
        double width = xp2 - xp1;
        double height = yp2 - yp1;

        Rectangle2D bounds2D = new Rectangle2D(xp1, yp1, width, height);
        boolean hit = bounds2D.contains(x, y);
        if (hit) {
            startX = xPoints.clone();
            startY = yPoints.clone();
        }
        if (selectMode && selectable) {
            selected = hit;
        }

        return hit;
    }

    @Override
    public void draw(GraphicsContextInterface gC, double[][] bounds, double[][] world) {
        try {
            gC.setLineWidth(lineWidth);
            double xMax = -1.0;
            double xMin = 10000.0;
            double yMax = -1.0;
            double yMin = 10000.0;
            for (int i = 0; i < xPoints.length; i++) {
                xCPoints[i] = xPosType.transform(xPoints[i], bounds[0], world[0]);
                yCPoints[i] = yPosType.transform(yPoints[i], bounds[1], world[1]);
                if (xCPoints[i] > xMax) {
                    xMax = xCPoints[i];
                }
                if (xCPoints[i] < xMin) {
                    xMin = xCPoints[i];
                }
                if (yCPoints[i] > yMax) {
                    yMax = yCPoints[i];
                }
                if (yCPoints[i] < yMin) {
                    yMin = yCPoints[i];
                }

            }
            xp1 = xMin;
            xp2 = xMax;
            yp1 = yMin;
            yp2 = yMax;
            if (stroke != null) {
                gC.setStroke(stroke);
                gC.strokePolygon(xCPoints, yCPoints, xCPoints.length);
            }
            if (fill != null) {
                gC.setFill(fill);
                gC.fillPolygon(xCPoints, yCPoints, xCPoints.length);
            }

            if (isSelected()) {
                drawHandles(gC);
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void drawHandles(GraphicsContextInterface gC) {
        for (int i = 0; i < xCPoints.length; i++) {
            drawHandle(gC, xCPoints[i], yCPoints[i], Pos.CENTER);
        }
    }

    @Override
    public int hitHandle(double x, double y) {
        activeHandle = -1;
        for (int i = 0; i < xCPoints.length; i++) {
            if (hitHandle(x, y, Pos.CENTER, xCPoints[i], yCPoints[i])) {
                activeHandle = i;
            }
        }
        return activeHandle;
    }

    @Override
    public void move(double[][] bounds, double[][] world, double[] start, double[] pos) {
        double dx = pos[0] - start[0];
        double dy = pos[1] - start[1];
        if (activeHandle < 0) {
            for (int i = 0; i < startX.length; i++) {
                xPoints[i] = xPosType.move(startX[i], dx, bounds[0], world[0]);
                yPoints[i] = yPosType.move(startY[i], dy, bounds[1], world[1]);
            }
        } else {
            int i = activeHandle;
            xPoints[i] = xPosType.move(startX[i], dx, bounds[0], world[0]);
            yPoints[i] = xPosType.move(startY[i], dy, bounds[1], world[1]);
        }
    }

    public void updateXPosType(POSTYPE newType, double[] bounds, double[] world) {
        for (int i = 0; i < xPoints.length; i++) {
            double xPix = xPosType.transform(xPoints[i], bounds, world);
            xPoints[i] = newType.itransform(xPix, bounds, world);
        }
        xPosType = newType;
    }

    public void updateYPosType(POSTYPE newType, double[] bounds, double[] world) {
        for (int i = 0; i < yPoints.length; i++) {
            double yPix = yPosType.transform(yPoints[i], bounds, world);
            yPoints[i] = newType.itransform(yPix, bounds, world);
        }
        yPosType = newType;
    }
}
