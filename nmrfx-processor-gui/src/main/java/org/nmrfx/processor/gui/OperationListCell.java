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
package org.nmrfx.processor.gui;

import java.util.ArrayList;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.effect.InnerShadow;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.paint.Color;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 * ListCell for scriptView in FXMLController. Allows Operations to be draggable.
 *
 * @author johnsonb
 */
public class OperationListCell<T> extends ListCell<T> implements ChangeListener<Number> {

    private static OperationListCell source;
    private static OperationListCell target;
    private static ArrayList<OperationListCell> cells = new ArrayList<>(20);
    private ListView scriptView;

    private static int failedIndex = -1;

    private OperationListCell temp;

    private static Integer moveIndex = null;

    public OperationListCell(ListView scriptView) {
        cells.add(this);
        this.scriptView = scriptView;

        this.indexProperty().addListener(this);
        this.getStyleClass().add("op-list-cell");

        temp = this;

        this.setOnDragDetected(new EventHandler<MouseEvent>() {
            public void handle(MouseEvent event) {
                moveIndex = temp.indexProperty().getValue();
                OperationListCell.source = temp;
                /* drag was detected, start a drag-and-drop gesture*/
 /* allow any transfer mode */
                Dragboard db = source.startDragAndDrop(TransferMode.COPY);

                /* Put a string on a dragboard */
                ClipboardContent content = new ClipboardContent();
                content.putString(source.getText());
                db.setContent(content);

                event.consume();
            }
        });

        this.setOnDragOver(new EventHandler<DragEvent>() {
            public void handle(DragEvent event) {
                /* data is dragged over the target */
 /* accept it only if it is not dragged from the same node 
                 * and if it has a string data */
                if (target != null) {
                    target.setEffect(null);
                }
                OperationListCell.target = temp;
                if (event.getGestureSource() != target
                        && event.getDragboard().hasString()) {
                    /* allow for both copying and moving, whatever user chooses */
                    event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                    InnerShadow is = new InnerShadow();
                    is.setOffsetX(1.0);
                    is.setColor(Color.web("#666666"));
                    is.setOffsetY(1.0);
                    target.setEffect(is);
                }

                event.consume();
            }
        });

        this.setOnDragEntered(new EventHandler<DragEvent>() {
            public void handle(DragEvent event) {
                /* the drag-and-drop gesture entered the target */
 /* show to the user that it is an actual gesture target */
                if (event.getGestureSource() != target
                        && event.getDragboard().hasString()) {
                    InnerShadow is = new InnerShadow();
                    is.setOffsetX(1.0);
                    is.setColor(Color.web("#FFFF00"));
                    is.setOffsetY(1.0);
                    target.setEffect(is);
                }

                event.consume();
            }
        });

        this.setOnDragExited(new EventHandler<DragEvent>() {
            public void handle(DragEvent event) {
                OperationListCell.target = temp;

                event.consume();
            }
        });

        this.setOnDragDropped(new EventHandler<DragEvent>() {
            public void handle(DragEvent event) {
                /* data dropped */
 /* if there is a string data on dragboard, read it and use it */
                if (target != null) {
                    target.setEffect(null);
                }
                OperationListCell.target = temp;
                scriptView.getSelectionModel().select(temp.getIndex());
                target.setEffect(null);
                Dragboard db = event.getDragboard();
                boolean success = false;
                if (db.hasString()) {
                    success = true;
                }
                /* let the source know whether the string was successfully 
                 * transferred and used */
                event.setDropCompleted(success);

                event.consume();
            }
        });

        this.setOnDragDone(new EventHandler<DragEvent>() {
            public void handle(DragEvent event) {
                ObservableList<String> listItems = scriptView.getItems();

                /* the drag and drop gesture ended */
 /* if the data was successfully moved, clear it */
                if (event == null || event.getTransferMode() == null) {
                    ;
                } else if (target.getIndex() < 0 || target == null) {
                    ;
                } else if (event.getTransferMode() == TransferMode.COPY) {
                    int sourceIndex = Math.max(0, source.getIndex());
                    int targetIndex = Math.min(target.getIndex(), listItems.size() - 1);
                    //System.out.println("drag done source " + sourceIndex + " to " + targetIndex);
                    String swap;
                    //ObservableList<String> listItems = MainApp.mainController.listItems;

                    swap = listItems.remove(sourceIndex);
                    listItems.add(targetIndex, swap);
                    scriptView.getSelectionModel().select(targetIndex);
// fixme
//                    processorController.chartProcessor.execScriptList();
//                    processorController.propertyManager.chartProcessor.getChart().layoutPlotChildren();
//                    processorController.propertyManager.popOver.hide();
                }
                event.consume();
            }
        });

    }

    @Override
    public void updateItem(T item, boolean empty) {
//        if (item != null) {
        super.updateItem(item, empty);
//            setText(item);
//        }
    }

    @Override
    public void changed(ObservableValue<? extends Number> ov, Number t, Number t1) {
        setOnDragEntered(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent t) {
                ObservableList<String> listItems = scriptView.getItems();
                if (t.getTransferMode() == TransferMode.MOVE) {
                    String selected = (String) t.getDragboard()
                            .getContent(DataFormat.PLAIN_TEXT);
                    Object obj = t.getDragboard().getContent(DataFormat.PLAIN_TEXT);

                    listItems.remove(getIndex());
                    listItems.add(getIndex() + 1, selected);
                }
            }
        });
    }

    private void updateCell() {
        ObservableList<String> listItems = scriptView.getItems();

        if (this.getIndex() != -1 && this.getIndex() < listItems.size()) {
            this.setText(listItems.get(this.getIndex()));
            if (this.getIndex() == OperationListCell.failedIndex) {
                setOperationFailed();
            } else {
                resetEffects();
            }
        }
    }

    /**
     * When ListItems changes, update all the cells
     */
    public static void updateCells() {
        for (OperationListCell cell : cells) {
            cell.updateCell();
        }
    }

    private void setOperationFailed() {
        InnerShadow is = new InnerShadow();

        is.setColor(Color.web("#FF6C6C"));
        this.setEffect(is);
    }

    private void resetEffects() {
        this.setEffect(null);
    }

    private static int cellSize() {
        return cells.size();
    }

    public static void setFailedIndex(int index) {
        failedIndex = index;
    }

    /**
     * Remove a failed Operation Cell's effects
     */
    public static void resetCells() {
        failedIndex = -1;
        updateCells();
    }

    public static void failedOperation(int index) {
        setFailedIndex(index);
        updateCells();
    }
}
