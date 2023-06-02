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

public class OverlappingLines {

    public static final double EPSILON = 0.000001;

    static class Point {

        double x;
        double y;

        Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    static class LineSegment {

        Point p1;
        Point p2;

        public Point[] getBoundingBox() {
            Point[] result = new Point[2];
            result[0] = new Point(Math.min(p1.x, p2.x), Math.min(p1.y,
                    p2.y));
            result[1] = new Point(Math.max(p1.x, p2.x), Math.max(p1.y,
                    p2.y));
            return result;
        }

        LineSegment(Point p1, Point p2) {
            this.p1 = p1;
            this.p2 = p2;
        }
    }

    /**
     * Calculate the cross product of two points.
     *
     * @param a first point
     * @param b second point
     * @return the value of the cross product
     */
    public static double crossProduct(Point a, Point b) {
        return a.x * b.y - b.x * a.y;
    }

    /**
     * Check if bounding boxes do intersect. If one bounding box touches the other, they do intersect.
     *
     * @param a first bounding box
     * @param b second bounding box
     * @return <code>true</code> if they intersect, <code>false</code> otherwise.
     */
    public static boolean doBoundingBoxesIntersect(Point[] a, Point[] b) {
        return a[0].x <= b[1].x && a[1].x >= b[0].x && a[0].y <= b[1].y
                && a[1].y >= b[0].y;
    }

    /**
     * Checks if a Point is on a line
     *
     * @param a line (interpreted as line, although given as line segment)
     * @param b point
     * @return <code>true</code> if point is on line, otherwise <code>false</code>
     */
    public static boolean isPointOnLine(LineSegment a, Point b) {
        // Move the image, so that a.first is on (0|0)
        LineSegment aTmp = new LineSegment(new Point(0, 0), new Point(
                a.p2.x - a.p1.x, a.p2.y - a.p1.y));
        Point bTmp = new Point(b.x - a.p1.x, b.y - a.p1.y);
        double r = crossProduct(aTmp.p2, bTmp);
        return Math.abs(r) < EPSILON;
    }

    /**
     * Checks if a point is right of a line. If the point is on the line, it is not right of the line.
     *
     * @param a line segment interpreted as a line
     * @param b the point
     * @return <code>true</code> if the point is right of the line, <code>false</code> otherwise
     */
    public static boolean isPointRightOfLine(LineSegment a, Point b) {
        // Move the image, so that a.first is on (0|0)
        LineSegment aTmp = new LineSegment(new Point(0, 0), new Point(
                a.p2.x - a.p1.x, a.p2.y - a.p1.y));
        Point bTmp = new Point(b.x - a.p1.x, b.y - a.p1.y);
        return crossProduct(aTmp.p2, bTmp) < 0;
    }

    /**
     * Check if line segment first touches or crosses the line that is defined by line segment second.
     *
     * @param first  line segment interpreted as line
     * @param second line segment
     * @return <code>true</code> if line segment first touches or crosses line second, <code>false</code> otherwise.
     */
    public static boolean lineSegmentTouchesOrCrossesLine(LineSegment a,
                                                          LineSegment b) {
        return isPointOnLine(a, b.p1)
                || isPointOnLine(a, b.p2)
                || (isPointRightOfLine(a, b.p1) ^ isPointRightOfLine(a,
                b.p2));
    }

    /**
     * Check if line segments intersect
     *
     * @param a first line segment
     * @param b second line segment
     * @return <code>true</code> if lines do intersect, <code>false</code> otherwise
     */
    public static boolean doLinesIntersect(LineSegment a, LineSegment b) {
        Point[] box1 = a.getBoundingBox();
        Point[] box2 = b.getBoundingBox();
        return doBoundingBoxesIntersect(box1, box2)
                && lineSegmentTouchesOrCrossesLine(a, b)
                && lineSegmentTouchesOrCrossesLine(b, a);
    }

    public static boolean doLinesIntersect(double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4) {
        LineSegment l1 = new LineSegment(new Point(x1, y1), new Point(x2, y2));
        LineSegment l2 = new LineSegment(new Point(x3, y3), new Point(x4, y4));
        return doLinesIntersect(l1, l2);
    }
}
