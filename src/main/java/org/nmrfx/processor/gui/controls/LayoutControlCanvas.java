/*
 * NMRFx Processor : A Program for Processing NMR Data 
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
package org.nmrfx.processor.gui.controls;

import org.nmrfx.processor.gui.FXMLController;
import java.util.ArrayList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 *
 * @author Bruce Johnson
 */
public class LayoutControlCanvas extends Pane {

    final FractionCanvas fPane;
    Rectangle lastRect = null;
    StackPane movingRect = FXMLController.makeNewWinIcon();

    public LayoutControlCanvas(FractionCanvas fPane) {
        this.fPane = fPane;
        ArrayList<Node> children = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Rectangle rect = new Rectangle(200, 200);
            rect.setFill(Color.GRAY);
            rect.setOpacity(0.5);
            children.add(rect);
        }
        children.add(movingRect);
        getChildren().addAll(children);
    }

    public int getRectangle(Point2D pt) {
        int i = 0;
        movingRect.relocate(pt.getX(), pt.getY());
        for (Node node : getChildrenUnmodifiable()) {

            if ((node != movingRect) && node.getBoundsInParent().contains(pt)) {
                Rectangle rect = (Rectangle) node;
                rect.setFill(Color.GREEN);
                if (lastRect != null) {
                    if (lastRect != rect) {
                        lastRect.setFill(Color.GRAY);
                    }
                }
                lastRect = rect;
                return i;
            }
            i++;
        }
        if (lastRect != null) {
            lastRect.setFill(Color.GRAY);
            lastRect = null;
        }
        return -1;
    }

    @Override
    public void layoutChildren() {
        super.layoutChildren();
        double width = fPane.getWidth();
        double height = fPane.getHeight();
        Rectangle[] rects = new Rectangle[4];
        for (int i = 0; i < rects.length; i++) {
            rects[i] = (Rectangle) getChildren().get(i);
        }
        if ((fPane.getChildren().size() < 2) || (fPane.orient == null)) {
            rects[0].setVisible(true);
            rects[1].setVisible(true);
            rects[2].setVisible(true);
            rects[3].setVisible(true);
        } else if (fPane.orient == FractionCanvas.ORIENTATION.HORIZONTAL) {
            rects[0].setVisible(true);
            rects[1].setVisible(true);
            rects[2].setVisible(false);
            rects[3].setVisible(false);
        } else if (fPane.orient == FractionCanvas.ORIENTATION.VERTICAL) {
            rects[0].setVisible(false);
            rects[1].setVisible(false);
            rects[2].setVisible(true);
            rects[3].setVisible(true);
        } else {
        }
        rects[0].setWidth(50);
        rects[0].setHeight(height / 2);
        rects[0].relocate(10, height / 4);

        rects[1].setWidth(50);
        rects[1].setHeight(height / 2);
        rects[1].relocate(width - 60, height / 4);

        rects[2].setWidth(width / 2);
        rects[2].setHeight(50);
        rects[2].relocate(width / 4, 10);

        rects[3].setWidth(width / 2);
        rects[3].setHeight(50);
        rects[3].relocate(width / 4, height - 60);

    }

    final void updateLayoutIndicators() {
        int nChildren = fPane.getChildrenUnmodifiable().size();
        int nCurrent = getChildren().size();
        if (nChildren != nCurrent) {
            getChildren().clear();
            for (int i = 0; i < nChildren; i++) {
                Rectangle rect = new Rectangle(200, 200);
                getChildren().add(rect);
                rect.setFill(Color.ORANGE);
//                rect.setOpacity(0.5);
            }
        }
        for (Node node : getChildren()) {
            node.resizeRelocate(300, 300, 200, 200);
//            node.relocate(300, 400);
        }

    }

}
