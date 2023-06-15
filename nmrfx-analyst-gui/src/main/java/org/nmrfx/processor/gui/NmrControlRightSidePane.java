package org.nmrfx.processor.gui;

import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;


public class NmrControlRightSidePane extends StackPane {
    private static final double MINIMUM_WIDTH_OF_CHILDREN = 400;

    public NmrControlRightSidePane() {
        // Adjust sizing and visibility when contents of the pane change
        getChildren().addListener((ListChangeListener<Node>) c -> {
            while(c.next()) {
                if (!c.getRemoved().isEmpty()) {
                    c.getRemoved().get(0).setVisible(false);
                }
                if (!c.getAddedSubList().isEmpty()) {
                    Region addedRegion = (Region) c.getAddedSubList().get(0);
                    addedRegion.setMinWidth(MINIMUM_WIDTH_OF_CHILDREN);
                    addedRegion.setVisible(true);
                }
            }
        });

    }

    public boolean isContentShowing(NmrControlRightSideContent content) {
        return content != null && getChildren().contains(content.getPane());
    }
}
