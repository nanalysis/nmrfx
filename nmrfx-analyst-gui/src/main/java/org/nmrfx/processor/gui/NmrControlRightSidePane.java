package org.nmrfx.processor.gui;

import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

/**
 * Pane to appear on right side of NMRScene that is only meant to contain a single child at a time.
 */
public class NmrControlRightSidePane extends BorderPane {
    private static final double MINIMUM_WIDTH_OF_CHILDREN = 400;

    public NmrControlRightSidePane() {
        centerProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
               oldValue.setVisible(false);
            }
            if (newValue instanceof Region addedRegion) {
                addedRegion.setMinWidth(MINIMUM_WIDTH_OF_CHILDREN);
                addedRegion.setVisible(true);
            }
        });
    }

    public boolean isContentShowing(NmrControlRightSideContent content) {
        return content != null && getCenter() == content.getPane();
    }

    public void clear() {
        setCenter(null);
    }

    public void addContent(NmrControlRightSideContent content) {
        setCenter(content.getPane());
    }

    public void removeContent(NmrControlRightSideContent content) {
        getChildren().remove(content.getPane());
    }

    public boolean hasContent() {
        return getCenter() != null;
    }

    public Pane getContentPane() {
        return hasContent() ? (Pane) getCenter() : null;
    }

    public void addContentListener(ListChangeListener<Node> contentListener) {
        getChildren().addListener(contentListener);
    }
}
