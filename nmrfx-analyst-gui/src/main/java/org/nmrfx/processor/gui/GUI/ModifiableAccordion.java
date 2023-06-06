package org.nmrfx.processor.gui.GUI;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.nmrfx.analyst.gui.AnalystApp;

import java.util.Optional;

// Wrapper class for Accordion. Can not extend accordion class directly to prevent access to getPanes
public class ModifiableAccordion extends ScrollPane {
    private final Accordion accordion = new Accordion ();

    public ModifiableAccordion() {
        this.setFitToHeight(true);
        this.setFitToWidth(true);
        this.setHbarPolicy(ScrollBarPolicy.NEVER);
        this.setContent(accordion);
    }

    public void add(TitledPane titledPane) {
        // Create Container for title + buttons
        HBox titleBox = new HBox();
        titleBox.setSpacing(5);
        titleBox.setPadding(new Insets(0, 5, 0, 5));
        titleBox.setAlignment(Pos.CENTER);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        // Create Title
        titleBox.getChildren().add(new Label(titledPane.getText()));
        // Create spacer to separate label and buttons
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleBox.getChildren().add(spacer);
        // Create Right Aligned Buttons
        Button upButton = GlyphsDude.createIconButton(FontAwesomeIcon.ARROW_UP, "", AnalystApp.ICON_SIZE_STR, AnalystApp.REG_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        upButton.setOnAction(this::moveUp);
        Button downButton = GlyphsDude.createIconButton(FontAwesomeIcon.ARROW_DOWN, "", AnalystApp.ICON_SIZE_STR, AnalystApp.REG_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        downButton.setOnAction(this::moveDown);
        Button closeButton = GlyphsDude.createIconButton(FontAwesomeIcon.CLOSE, "", AnalystApp.ICON_SIZE_STR, AnalystApp.REG_FONT_SIZE_STR, ContentDisplay.GRAPHIC_ONLY);
        closeButton.setOnAction(this::removeTitledPane);
        titleBox.getChildren().addAll(downButton, upButton, closeButton);

        // Formatting for titledPane
        titledPane.setGraphic(titleBox);
        titledPane.setGraphicTextGap(0);
        titledPane.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        accordion.getPanes().add(titledPane);

        // Listener to set the minWidthProperty once the scene is displayed (FRAGILE!)
        titleBox.widthProperty().addListener((obs, oldWidth, newWidth) ->{
            if (!titleBox.minWidthProperty().isBound()) {
                // Need to calculate how much space the arrow icon takes up once the stage is showing, can only calculate
                // once the stage has been shown and the sizing has been calculated
                Region arrowRegion = (Region) titledPane.lookup(".arrow-button");
                double arrowWidth = arrowRegion.getWidth();
                Insets arrowPadding = arrowRegion.getPadding();
                double usedSpace = arrowWidth + arrowPadding.getLeft() + arrowPadding.getRight();
                titleBox.minWidthProperty().bind(titledPane.widthProperty().subtract(usedSpace));
            }

        });
    }


    private void moveUp(ActionEvent actionEvent) {
        int currentIndex = getIndexOfTitledPane((Button) actionEvent.getSource());
        int newIndex = Math.max(currentIndex - 1, 0);
        TitledPane tp = accordion.getPanes().remove(currentIndex);
        accordion.getPanes().add(newIndex, tp);

    }

    private void moveDown(ActionEvent actionEvent) {
        int currentIndex = getIndexOfTitledPane((Button) actionEvent.getSource());
        int newIndex = Math.min(currentIndex + 1, getSize() - 1);
        TitledPane tp = accordion.getPanes().remove(currentIndex);
        accordion.getPanes().add(newIndex, tp);
    }

    private void removeTitledPane(ActionEvent actionEvent) {
        accordion.getPanes().remove(getIndexOfTitledPane((Button) actionEvent.getSource()));
    }

    private int getIndexOfTitledPane(Button button) {
        Optional<TitledPane> paneToRemove = accordion.getPanes().stream().filter(titledPane -> ((HBox) titledPane.getGraphic()).getChildren().contains(button)).findFirst();
        return paneToRemove.map(titledPane -> accordion.getPanes().indexOf(titledPane)).orElse(-1);
    }

    private int getSize() {
        return accordion.getPanes().size();
    }

}
