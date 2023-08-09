package org.nmrfx.processor.gui.utils;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.skin.TitledPaneSkin;
import javafx.scene.effect.InnerShadow;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import org.nmrfx.processor.processing.ProcessingOperation;
import org.nmrfx.processor.gui.ProcessorController;
import org.nmrfx.processor.processing.ProcessingOperationGroup;
import org.nmrfx.processor.processing.ProcessingOperationInterface;

import java.util.List;

public class ModifiableAccordionScrollPane extends ScrollPane {
    private final ModifiableAccordion accordion = new ModifiableAccordion();
    private  ModifiableTitlePane source;
    private  ModifiableTitlePane target;

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
        return (ModifiableAccordionScrollPane.ModifiableTitlePane) accordion.getPanes().get(i);
    }

    public void add(ModifiableTitlePane titledPane) {
        accordion.add(titledPane);
    }

    public ModifiableTitlePane makeNewTitlePane(ProcessorController processorController, ProcessingOperation processingOperation) {
        return new ModifiableTitlePane(this, processorController, processingOperation);
    }

    public ModifiableTitlePane makeNewTitlePane(ProcessorController processorController, ProcessingOperationGroup processingOperation) {
        return new ModifiableTitlePane(this, processorController, processingOperation);
    }

    public class ModifiableTitlePane extends TitledPane {
        ModifiableAccordionScrollPane accordionScrollPane;
        ContextMenu contextMenu = new ContextMenu();
        ProcessingOperationInterface processingOperation;
        ProcessorController processorController;
        CheckBox checkBox;
        int index = -1;

        public ModifiableTitlePane(ModifiableAccordionScrollPane accordionScrollPane, ProcessorController processorController, ProcessingOperation processingOperation) {
            this.accordionScrollPane = accordionScrollPane;
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

        public ModifiableTitlePane(ModifiableAccordionScrollPane accordionScrollPane, ProcessorController processorController, ProcessingOperationGroup processingOperation) {
            this.accordionScrollPane = accordionScrollPane;
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
        private HBox createContents(ModifiableTitlePane titledPane) {
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
            checkBox.setSelected(!processingOperation.isDisabled());
            checkBox.setOnAction(e -> updateTitle());
            Color fillColor = processingOperation.isDisabled() ? Color.BLUE : Color.GRAY;
            titledPane.setTextFill(fillColor);
            Text moveIcon = GlyphsDude.createIcon(FontAwesomeIcon.ARROWS_V, "14");
            moveIcon.setOnMouseReleased(Event::consume);
            titleBox.getChildren().addAll(checkBox, moveIcon);
            titledPane.textFillProperty().bind(Bindings.when(checkBox.selectedProperty()).then(Color.BLUE).otherwise(Color.GRAY));
            setupDragHandling(titledPane, moveIcon);
            return titleBox;
        }

        private void updateTitle() {
            setDetailedTitle(processorController.detailSelected());
        }

        private void showContextMenu(ContextMenuEvent e, TitledPane titledPane) {
            contextMenu.show(titledPane, e.getScreenX(), e.getScreenY());
        }

        void setupDragHandling(ModifiableTitlePane titledPane, Node node) {
            node.setOnDragDetected(event -> {
                source = titledPane;
                target = null;
                /* drag was detected, start a drag-and-drop gesture*/
                /* allow any transfer mode */
                Dragboard db = source.startDragAndDrop(TransferMode.MOVE);

                /* Put a string on a dragboard */
                ClipboardContent content = new ClipboardContent();
                content.putString(source.getText());
                db.setContent(content);
                event.consume();
            });

            titledPane.setOnDragOver(event -> {
                /* data is dragged over the target */
                /* accept it only if it is not dragged from the same node
                 * and if it has a string data */
                if (target != null) {
                    target.setEffect(null);
                }
                target = titledPane;
                if (event.getGestureSource() != target && event.getDragboard().hasString()) {
                    /* allow for both copying and moving, whatever user chooses */
                    event.acceptTransferModes(TransferMode.MOVE);
                    InnerShadow is = new InnerShadow();
                    is.setOffsetX(1.0);
                    is.setColor(Color.web("#666666"));
                    is.setOffsetY(1.0);
                    target.setEffect(is);
                }

                event.consume();
            });

            titledPane.setOnDragEntered(event -> {
                /* the drag-and-drop gesture entered the target */
                /* show to the user that it is an actual gesture target */
                if (event.getGestureSource() != target && event.getDragboard().hasString()) {
                    target = titledPane;
                    InnerShadow is = new InnerShadow();
                    is.setOffsetX(1.0);
                    is.setColor(Color.web("#FFFF00"));
                    is.setOffsetY(1.0);
                    target.setEffect(is);
                }
                event.consume();
            });

            titledPane.setOnDragExited(event -> {
                if (target != null) {
                    target.setEffect(null);
                }
                event.consume();
            });

            titledPane.setOnDragDropped(event -> {
                /* data dropped */
                /* if there is a string data on dragboard, read it and use it */
                if (target != null) {
                    target.setEffect(null);
                }
                Dragboard db = event.getDragboard();
                boolean success = db.hasString();
                /* let the source know whether the string was successfully
                 * transferred and used */
                event.setDropCompleted(success);

                event.consume();
            });

            titledPane.setOnDragDone(event -> {
                List<ProcessingOperationInterface> listItems = processorController.getOperationList();

                /* the drag and drop gesture ended */
                /* if the data was successfully moved, clear it */
                if (event != null && target != null && target.getIndex() >= 0) {
                    if (event.getTransferMode() == TransferMode.MOVE) {
                        int sourceIndex = Math.max(0, source.getIndex());
                        if (sourceIndex > ProcessorController.GROUP_SCALE) {
                            int groupIndex = sourceIndex / ProcessorController.GROUP_SCALE;
                            sourceIndex = sourceIndex % ProcessorController.GROUP_SCALE;
                            int targetIndex = target.getIndex();
                            targetIndex = targetIndex % ProcessorController.GROUP_SCALE;
                            ProcessingOperationInterface procOpI = listItems.get(groupIndex);
                            if (procOpI instanceof  ProcessingOperationGroup procGroup) {
                                procGroup.adjust(sourceIndex, targetIndex);
                                processorController.updateGroupAccordion(target.accordionScrollPane, procGroup, groupIndex);
                            }
                        } else {
                            int targetIndex = Math.min(target.getIndex(), listItems.size() - 1);
                            ProcessingOperationInterface swap = listItems.remove(sourceIndex);
                            listItems.add(targetIndex, swap);
                            processorController.updateAfterOperationListChanged();
                        }
                    }
                    target = null;
                    event.consume();
                }
            });
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
