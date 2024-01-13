package org.nmrfx.processor.gui.annotations;

import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.utils.GUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnnoLineText extends AnnoShape {
    private static final Logger log = LoggerFactory.getLogger(AnnoLineText.class);
    private static final int PTS_IN_ARROW = 6;
    protected String text;
    double x1;
    double y1;
    double x2;
    double y2;
    double xp1;
    double yp1;
    double xp2;
    double yp2;
    double[] xCPoints;
    double[] yCPoints;
    double arrowShapeA = 8.0;
    double arrowShapeB = 10.0;
    double arrowShapeC = 3.0;
    double startX1;
    double startY1;
    double startX2;
    double startY2;
    double width;
    int activeHandle = -1;
    Font font = Font.font("Liberation Sans", 12);


    public AnnoLineText() {

    }

    public AnnoLineText(double x1, double y1, double x2, double y2, String text, double fontSize, double width,
                        POSTYPE xPosType, POSTYPE yPosType) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.xPosType = xPosType;
        this.yPosType = yPosType;
        this.text = text;
        this.width = width;
        this.setFontSize(fontSize);
    }

    public double getFontSize() {
        return font.getSize();
    }

    public void setFontSize(double size) {
        this.font = Font.font(font.getFamily(), size);
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public double getX1() {
        return x1;
    }

    public void setX1(double x1) {
        this.x1 = x1;
    }

    public double getY1() {
        return y1;
    }

    public void setY1(double y1) {
        this.y1 = y1;
    }

    public double getX2() {
        return x2;
    }

    public void setX2(double x2) {
        this.x2 = x2;
    }

    public double getY2() {
        return y2;
    }

    public void setY2(double y2) {
        this.y2 = y2;
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
            gC.setFont(font);
            gC.setTextAlign(TextAlignment.LEFT);
            gC.setTextBaseline(VPos.BASELINE);
            xp1 = xPosType.transform(x1, bounds[0], world[0]);
            yp1 = yPosType.transform(y1, bounds[1], world[1]);
            xp2 = xPosType.transform(x2, bounds[0], world[0]);
            yp2 = yPosType.transform(y2, bounds[1], world[1]);

            double xa1;
            double ya1;
            double xa2 = xp2;
            double ya2 = yp2;

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
                gC.setStroke(stroke);
                gC.setFill(stroke);
                gC.strokePolygon(xCPoints, yCPoints, xCPoints.length);
                gC.fillPolygon(xCPoints, yCPoints, xCPoints.length);
            }

            gC.strokeLine(xa1, ya1, xa2, ya2);
            double textWidth = GUIUtils.getTextWidth(text, font);
            double textX = xp2 - textWidth / 2.0;
            double textY;
            if (yp1 > yp2) {
                textY = yp2 - 2;
            } else {
                textY = yp2 + getFontSize();
            }
            if (fill != null) {
                gC.setFill(fill);
                gC.fillText(text, textX, textY);
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
        drawHandle(gC, xp1, yp1, Pos.CENTER);
        drawHandle(gC, xp2, yp2, Pos.CENTER);
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

    double[] addArrowFirst() {
        double[] poly = new double[PTS_IN_ARROW * 2];

        calcArrow(this.xp1, this.yp1, this.xp2, this.yp2, poly);

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

    public void updateXPosType(POSTYPE newType, double[] bounds, double[] world) {
        double x1Pix = xPosType.transform(x1, bounds, world);
        double x2Pix = xPosType.transform(x2, bounds, world);
        x1 = newType.itransform(x1Pix, bounds, world);
        x2 = newType.itransform(x2Pix, bounds, world);
        xPosType = newType;
    }

    public void updateYPosType(POSTYPE newType, double[] bounds, double[] world) {
        double y1Pix = yPosType.transform(y1, bounds, world);
        double y2Pix = yPosType.transform(y2, bounds, world);
        y1 = newType.itransform(y1Pix, bounds, world);
        y2 = newType.itransform(y2Pix, bounds, world);
        yPosType = newType;
    }
}
