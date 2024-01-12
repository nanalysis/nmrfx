package org.nmrfx.processor.gui.annotations;

import javafx.geometry.Pos;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnnoLine extends AnnoSimpleLine {
    private static final Logger log = LoggerFactory.getLogger(AnnoLine.class);
    private static final int PTS_IN_ARROW = 6;
    double[] xCPoints;
    double[] yCPoints;
    boolean arrowFirst = false;
    boolean arrowLast = false;
    double arrowShapeA = 8.0;
    double arrowShapeB = 10.0;
    double arrowShapeC = 3.0;
    double startX1;
    double startY1;
    double startX2;
    double startY2;
    double width;
    int activeHandle = -1;

    public AnnoLine() {

    }

    public AnnoLine(double x1, double y1, double x2, double y2) {
        super(x1, y1, x2, y2);
    }

    public AnnoLine(double x1, double y1, double x2, double y2, boolean arrowFirst, boolean arrowLast,
                    double lineWidth, POSTYPE xPosType, POSTYPE yPosType) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.xPosType = xPosType;
        this.yPosType = yPosType;
        this.arrowFirst = arrowFirst;
        this.arrowLast = arrowLast;
        this.width = lineWidth;
    }

    public boolean isArrowFirst() {
        return arrowFirst;
    }

    public void setArrowFirst(boolean arrowFirst) {
        this.arrowFirst = arrowFirst;
    }

    public boolean isArrowLast() {
        return arrowLast;
    }

    public void setArrowLast(boolean arrowLast) {
        this.arrowLast = arrowLast;
    }

    double getDistance(double x1, double x2, double y1, double y2) {
        double d1 = x2 - x1;
        double d2 = y2 - y1;
        return Math.hypot(d1, d2);
    }

    @Override
    public boolean hit(double x, double y, boolean selectMode) {
        double distance1 = getDistance(x, xp1, y, yp1);
        double distance2 = getDistance(x, xp2, y, yp2);
        double distance3 = getDistance(xp1, xp2, yp1, yp2);
        boolean hit = false;
        double tolerance = 3.0;
        if (distance1 + distance2 - distance3 < tolerance) {
            hit = true;
        }
        if (hit) {
            startX1 = x1;
            startX2 = x2;
            startY1 = y1;
            startY2 = y2;
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
            if (stroke != null) {
                gC.setStroke(stroke);
                gC.setFill(stroke);
            }

            xp1 = xPosType.transform(x1, bounds[0], world[0]);
            yp1 = yPosType.transform(y1, bounds[1], world[1]);
            xp2 = xPosType.transform(x2, bounds[0], world[0]);
            yp2 = yPosType.transform(y2, bounds[1], world[1]);

            double xa1 = xp1;
            double ya1 = yp1;
            double xa2 = xp2;
            double ya2 = yp2;

            if (arrowFirst) {
                xCPoints = new double[PTS_IN_ARROW];
                yCPoints = new double[PTS_IN_ARROW];
                double[] poly = addArrowFirst();
                for (int i = 0; i < poly.length / 2; i++) {
                    xCPoints[i] = poly[2 * i];
                    yCPoints[i] = poly[2 * i + 1];
                }
                xa1 = (xCPoints[2] + xCPoints[3]) / 2;
                ya1 = (yCPoints[2] + yCPoints[3]) / 2;
                if (stroke != null) {
                    gC.strokePolygon(xCPoints, yCPoints, xCPoints.length);
                    gC.fillPolygon(xCPoints, yCPoints, xCPoints.length);
                }
            }

            if (arrowLast) {
                xCPoints = new double[PTS_IN_ARROW];
                yCPoints = new double[PTS_IN_ARROW];
                double[] poly = addArrowLast();
                for (int i = 0; i < poly.length / 2; i++) {
                    xCPoints[i] = poly[2 * i];
                    yCPoints[i] = poly[2 * i + 1];
                }
                xa2 = (xCPoints[2] + xCPoints[3]) / 2;
                ya2 = (yCPoints[2] + yCPoints[3]) / 2;
                if (stroke != null) {
                    gC.strokePolygon(xCPoints, yCPoints, xCPoints.length);
                    gC.fillPolygon(xCPoints, yCPoints, xCPoints.length);
                }
            }

            if (stroke != null) {
                gC.strokeLine(xa1, ya1, xa2, ya2);
            }

            if (isSelected()) {
                drawHandles(gC);
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    double[] addArrowFirst() {
        double[] poly = new double[PTS_IN_ARROW * 2];

        calcArrow(this.xp1, this.yp1, this.xp2, this.yp2, poly);

        return poly;
    }

    double[] addArrowLast() {
        double[] poly = new double[PTS_IN_ARROW * 2];

        calcArrow(this.xp2, this.yp2, this.xp1, this.yp1, poly);

        return poly;
    }

    double[] calcArrow(double x1, double y1, double x2, double y2, double[] poly) {
        double shapeA = arrowShapeA + 0.001;
        double shapeB = arrowShapeB + 0.001;
        double shapeC = arrowShapeC + width / 2.0 + 0.001;
        poly[0] = poly[10] = x1;
        poly[1] = poly[11] = y1;
        double dx = poly[0] - x2;
        double dy = poly[1] - y2;
        double length = Math.hypot(dx, dy);
        double sinTheta = 0.0;
        double cosTheta = 0.0;
        if (length > 1.0e-9) {
            sinTheta = dy / length;
            cosTheta = dx / length;
        }
        double vertX = poly[0] - shapeA * cosTheta;
        double vertY = poly[1] - shapeA * sinTheta;
        double temp = shapeC * sinTheta;
        poly[2] = poly[0] - shapeB * cosTheta + temp;
        poly[8] = poly[2] - 2 * temp;
        temp = shapeC * cosTheta;
        poly[3] = poly[1] - shapeB * sinTheta - temp;
        poly[9] = poly[3] + 2 * temp;
        double fracHeight = (width / 2.0) / shapeC;
        poly[4] = poly[2] * fracHeight + vertX * (1.0 - fracHeight);
        poly[5] = poly[3] * fracHeight + vertY * (1.0 - fracHeight);
        poly[6] = poly[8] * fracHeight + vertX * (1.0 - fracHeight);
        poly[7] = poly[9] * fracHeight + vertY * (1.0 - fracHeight);
        double[] thetaTrig = new double[2];
        thetaTrig[0] = cosTheta;
        thetaTrig[1] = sinTheta;
        return thetaTrig;
    }

    @Override
    public int hitHandle(double x, double y) {
        if (hitHandle(x, y, Pos.CENTER, xp1, yp1)) {
            activeHandle = 0;
        } else if (hitHandle(x, y, Pos.CENTER, xp2, yp2)) {
            activeHandle = 1;
        } else {
            activeHandle = -1;
        }
        return activeHandle;
    }

    @Override
    public void move(double[][] bounds, double[][] world, double[] start, double[] pos) {
        double dx = pos[0] - start[0];
        double dy = pos[1] - start[1];
        if (activeHandle < 0) {
            x1 = xPosType.move(startX1, dx, bounds[0], world[0]);
            x2 = xPosType.move(startX2, dx, bounds[0], world[0]);
            y1 = yPosType.move(startY1, dy, bounds[1], world[1]);
            y2 = yPosType.move(startY2, dy, bounds[1], world[1]);
        } else if (activeHandle == 0) {
            x1 = xPosType.move(startX1, dx, bounds[0], world[0]);
            y1 = xPosType.move(startY1, dy, bounds[1], world[1]);
        } else if (activeHandle == 1) {
            x2 = xPosType.move(startX2, dx, bounds[0], world[0]);
            y2 = xPosType.move(startY2, dy, bounds[1], world[1]);
        }
    }
}
