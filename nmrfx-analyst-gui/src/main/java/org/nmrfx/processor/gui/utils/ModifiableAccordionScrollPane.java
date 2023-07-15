package org.nmrfx.processor.gui.utils;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.skin.TitledPaneSkin;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import org.nmrfx.processor.gui.ProcessingOperation;
import org.nmrfx.processor.gui.ProcessorController;

import java.util.List;

public class ModifiableAccordionScrollPane extends ScrollPane {
    private final ModifiableAccordion accordion = new ModifiableAccordion();

    public ModifiableAccordionScrollPane() {
        this.setFitToHeight(true);
        this.setFitToWidth(true);
        this.setHbarPolicy(ScrollBarPolicy.NEVER);
        this.setContent(accordion);
    }

    public List<TitledPane> getPanes() {
        return accordion.getPanes();
    }

    public int size() {
        return accordion.getPanes().size();
    }

    public void remove(int i) {
        accordion.getPanes().remove(i);
    }

    public void remove(int i1, int i2) {
        accordion.getPanes().remove(i1, i2);
    }

    public ModifiableAccordionScrollPane.ModifiableTitlePane get(int i) {
       return  (ModifiableAccordionScrollPane.ModifiableTitlePane)  accordion.getPanes().get(i);
    }

    public void add(ModifiableTitlePane titledPane) {
        accordion.add(titledPane);
    }

    public static class ModifiableTitlePane extends TitledPane {
        ContextMenu contextMenu = new ContextMenu();
        ProcessingOperation processingOperation;
        ProcessorController processorController;
        CheckBox checkBox;
        int index = -1;
        public ModifiableTitlePane(ProcessorController processorController, ProcessingOperation processingOperation) {
            this.processorController = processorController;
            this.processingOperation = processingOperation;

            // Formatting for titledPane
            // Create Container with title + buttons
            HBox titleBox = createContents(this);
            setGraphicTextGap(0);
            // It is easier to align the buttons if the title is a label within the graphic instead of showing the
            // title in the default way
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            setGraphic(titleBox);
            setSkin(new ButtonTitlePaneSkin(this));
            MenuItem deleteItem = new MenuItem("Delete");
            deleteItem.setOnAction(e -> deleteItem());
            contextMenu.getItems().add(deleteItem);
        }

        public void setProcessingOperation(ProcessingOperation op) {
            this.processingOperation = op;
        }

        public BooleanProperty getCheckBoxSelectedProperty() {
            return checkBox.selectedProperty();
        }

        public void setDetailedTitle(boolean state) {
            setText(processingOperation.getTitle(state));
        }

        public boolean isActive() {
            return checkBox.isSelected();
        }

        public void setActive(boolean state) {
            checkBox.setSelected(state);
        }

        public void setIndex(int i) {
            index = i;
        }

        public int getIndex() {
            return index;
        }

        private void deleteItem() {
            processorController.deleteItem(index);
        }

        /**
         * Create a HBox and add the title as a label on the left, a central spacer and control buttons on the right.
         *
         * @param titledPane The titledPane of to use.
         * @return A populated HBox.
         */
        private HBox createContents(TitledPane titledPane) {
            HBox titleBox = new HBox();
            titledPane.setOnContextMenuRequested(e -> showContextMenu(e, titledPane));
            titleBox.setSpacing(5);
            titleBox.setPadding(new Insets(0, 5, 0, 5));
            titleBox.setAlignment(Pos.CENTER);
            HBox.setHgrow(titleBox, Priority.ALWAYS);
            // Create Title
            Label label = new Label(titledPane.getText());
            label.textProperty().bind(titledPane.textProperty());
            label.textFillProperty().bind(titledPane.textFillProperty());

            titleBox.getChildren().add(label);
            // Create spacer to separate label and buttons
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            titleBox.getChildren().add(spacer);
            // Create Right Aligned Buttons
            checkBox = new CheckBox();
            Text moveIcon = GlyphsDude.createIcon(FontAwesomeIcon.ARROWS_V, "14");
            moveIcon.setOnMouseReleased(Event::consume);
            titleBox.getChildren().addAll(checkBox, moveIcon);
            titledPane.textFillProperty().bind(Bindings.when(checkBox.selectedProperty()).then(Color.BLUE).otherwise(Color.GRAY));
            return titleBox;
        }

        private void showContextMenu(ContextMenuEvent e, TitledPane titledPane) {
            contextMenu.show(titledPane, e.getScreenX(), e.getScreenY());
        }

    }

    private static class ModifiableAccordion extends Accordion {

        public void add(ModifiableTitlePane titledPane) {
            this.getPanes().add(titledPane);
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
