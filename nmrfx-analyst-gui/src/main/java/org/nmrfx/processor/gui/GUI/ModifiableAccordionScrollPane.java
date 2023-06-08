package org.nmrfx.processor.gui.GUI;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.skin.TitledPaneSkin;
import javafx.scene.layout.*;
import org.nmrfx.analyst.gui.AnalystApp;

import java.util.Optional;

public class ModifiableAccordionScrollPane extends ScrollPane {
    private final ModifiableAccordion accordion = new ModifiableAccordion();

    public ModifiableAccordionScrollPane() {
        this.setFitToHeight(true);
        this.setFitToWidth(true);
        this.setHbarPolicy(ScrollBarPolicy.NEVER);
        this.setContent(accordion);
    }

    public void add(TitledPane titledPane) {
        accordion.add(titledPane);
    }


    private static class ModifiableAccordion extends Accordion {

        public void add(TitledPane titledPane) {
            // Create Container with title + buttons
            HBox titleBox = createContents(titledPane.getText());

            // Formatting for titledPane
            titledPane.setGraphicTextGap(0);
            // It is easier to align the buttons if the title is a label within the graphic instead of showing the
            // title in the default way
            titledPane.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            titledPane.setGraphic(titleBox);
            titledPane.setSkin(new ButtonTitlePaneSkin(titledPane));

            this.getPanes().add(titledPane);
        }

        /**
         * Create a HBox and add the title as a label on the left, a central spacer and control buttons on the right.
         *
         * @param title The title of to use.
         * @return A populated HBox.
         */
        private HBox createContents(String title) {
            HBox titleBox = new HBox();
            titleBox.setSpacing(5);
            titleBox.setPadding(new Insets(0, 5, 0, 5));
            titleBox.setAlignment(Pos.CENTER);
            HBox.setHgrow(titleBox, Priority.ALWAYS);
            // Create Title
            titleBox.getChildren().add(new Label(title));
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
            return titleBox;
        }

        private void moveUp(ActionEvent actionEvent) {
            int currentIndex = getIndexOfTitledPane((Button) actionEvent.getSource());
            int newIndex = Math.max(currentIndex - 1, 0);
            TitledPane tp = getPanes().remove(currentIndex);
            getPanes().add(newIndex, tp);
        }

        private void moveDown(ActionEvent actionEvent) {
            int currentIndex = getIndexOfTitledPane((Button) actionEvent.getSource());
            int newIndex = Math.min(currentIndex + 1, getPanes().size() - 1);
            TitledPane tp = getPanes().remove(currentIndex);
            getPanes().add(newIndex, tp);
        }

        private void removeTitledPane(ActionEvent actionEvent) {
            getPanes().remove(getIndexOfTitledPane((Button) actionEvent.getSource()));
        }

        private int getIndexOfTitledPane(Button button) {
            Optional<TitledPane> paneToRemove = getPanes().stream().filter(titledPane -> ((HBox) titledPane.getGraphic()).getChildren().contains(button)).findFirst();
            return paneToRemove.map(titledPane -> getPanes().indexOf(titledPane)).orElse(-1);
        }
    }

    private static class ButtonTitlePaneSkin extends TitledPaneSkin {
        final Region arrow;

        ButtonTitlePaneSkin(final TitledPane titledPane) {
            super(titledPane);
            arrow = (Region) getSkinnable().lookup(".arrow-button");

        }

        @Override
        protected void layoutChildren(final double x, final double y, final double w, final double h) {
            super.layoutChildren(x, y, w, h);
            double arrowWidth = arrow.getLayoutBounds().getWidth();
            double arrowPadding = arrow.getPadding().getLeft() + arrow.getPadding().getRight();

            ((Region) getSkinnable().getGraphic()).setMinWidth(w - (arrowWidth + arrowPadding));
        }
    }
}
