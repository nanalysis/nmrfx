package org.nmrfx.processor.gui;

import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * Pane to appear on right side of NMRScene that is only meant to contain a single child at a time.
 */
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

    public void clear() {
        getChildren().clear();
    }

    public void addContent(NmrControlRightSideContent content) {
        clear();
        getChildren().add(content.getPane());
    }

    public void removeContent(NmrControlRightSideContent content) {
        getChildren().remove(content.getPane());
    }

    public int size() {
        return getChildren().size();
    }

    public boolean hasContent() {
        return !getChildren().isEmpty();
    }

    public Pane getContentPane() {
        return hasContent() ? (Pane) getChildren().get(0) : null;
    }

    public void addContentListener(ListChangeListener<Node> contentListener) {
        getChildren().addListener(contentListener);
    }
}
