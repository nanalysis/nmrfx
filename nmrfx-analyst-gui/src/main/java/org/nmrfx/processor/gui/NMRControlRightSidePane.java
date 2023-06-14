package org.nmrfx.processor.gui;

import javafx.collections.ListChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.nmrfx.fxutil.Fxml;

import java.io.IOException;

public class NMRControlRightSidePane extends StackPane {
    private static final double MINIMUM_WIDTH_OF_CHILDREN = 400;
    private static final String FXML_FILENAME = "NMRControlRightSidePane.fxml";

    public NMRControlRightSidePane() {
        FXMLLoader fxmlLoader = new FXMLLoader(
                getClass().getResource(Fxml.FXML_RESOURCES_BASE + FXML_FILENAME));

        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException e) {
        throw new IllegalStateException("Unable to load fxml file: " + FXML_FILENAME, e);
    }

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

    public boolean isContentShowing(NMRControlRightSideContent content) {
        return content != null && getChildren().contains(content.getPane());
    }
}
