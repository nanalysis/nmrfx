package org.nmrfx.processor.gui;

import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.nd4j.linalg.api.ops.impl.reduce.same.Min;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SideBar extends HBox {
    public enum SideBarOrientation {
        RIGHT(90.0, -1, 0),
        LEFT(-90.0, 0, 1),
        TOP(0, -1, 1),
        BOTTOM(0, -1, 0);

        private final double rotate;
        private final int spacerIndex;
        private final int contentIndex;

        SideBarOrientation(double rotate, int spacerIndex, int contentIndex) {
            this.rotate = rotate;
            this.spacerIndex = spacerIndex;
            this.contentIndex = contentIndex;
        }
    }

    private static final int MIN_CONTENT_SIZE = 50;
    private final SplitPane splitPane = new SplitPane();
    private final SplitPane contentSplitPane = new SplitPane();
    private final ToolBar toolbar = new ToolBar();
    private final LinkedHashMap<String, SideBarContent> contents = new LinkedHashMap<>();
    private final SideBarOrientation orientation;
    private final MouseDragBindings mouseDragBindings;
    private double contentSize;

    public SideBar (SideBarOrientation orientation, LinkedHashMap<String, Node> contents, double contentSize) {
        this.orientation = orientation;
        this.contentSize = contentSize;
        VBox.setVgrow(this, Priority.ALWAYS);
        HBox.setHgrow(this, Priority.ALWAYS);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        HBox.setHgrow(splitPane, Priority.ALWAYS);
        this.getChildren().add(splitPane);
        splitPane.getStyleClass().add("side-bar-split-pane");
        StackPane sp = new StackPane(toolbar);
        sp.setRotate(orientation.rotate);
        sp.maxWidthProperty().bind(Bindings.createDoubleBinding(() -> this.getMaxHeight() - 2, this.maxHeightProperty()));
        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        addAllContentsToSidePanel(contents);
        // Must add the spacer in the correct index
        int spacerIndex = orientation.spacerIndex == -1 ? toolbar.getItems().size() : orientation.spacerIndex;
        toolbar.getItems().add(spacerIndex, spacer);
        if (isVertical()) {
            Group group = new Group();
            group.getChildren().add(sp);
            splitPane.getItems().add(group);
        } else {
            splitPane.getItems().add(toolbar);
        }
        Orientation splitPaneOrientation = isVertical() ? Orientation.HORIZONTAL : Orientation.VERTICAL;
        splitPane.setOrientation(splitPaneOrientation);
        VBox.setVgrow(contentSplitPane, Priority.ALWAYS);
        Orientation contentSplitPaneOrientation = isVertical() ? Orientation.VERTICAL : Orientation.HORIZONTAL;
        HBox.setHgrow(contentSplitPane, Priority.ALWAYS);
        contentSplitPane.setOrientation(contentSplitPaneOrientation);
        contentSplitPane.setPrefWidth(0);
        contentSplitPane.getItems().addListener(this::contentSplitPaneItemListener);
        splitPane.getItems().add(orientation.contentIndex, contentSplitPane);
        if (isVertical()) {
            splitPane.heightProperty().addListener((observable, oldValue, newValue) -> toolbar.setPrefWidth(newValue.doubleValue() - 2));
        }
        mouseDragBindings = new MouseDragBindings(this);
    }

    private void contentSplitPaneItemListener(ListChangeListener.Change<? extends Node> c) {
        if (c.getList().isEmpty()) {
            if (isVertical()) {
                contentSplitPane.setPrefWidth(0);
                contentSplitPane.setMinWidth(0);
            } else {
                contentSplitPane.setPrefHeight(0);
                contentSplitPane.setMinHeight(0);
            }
        } else {
            if (isVertical()) {
                contentSplitPane.setPrefWidth(this.contentSize);
                contentSplitPane.setMinWidth(MIN_CONTENT_SIZE);
            } else {
                contentSplitPane.setPrefHeight(this.contentSize);
                contentSplitPane.setMinHeight(MIN_CONTENT_SIZE);
            }
        }
    }

    private boolean isVertical() {
        return orientation == SideBarOrientation.LEFT || orientation == SideBarOrientation.RIGHT;
    }

    public void addAllContentsToSidePanel(LinkedHashMap<String, Node> newContents) {
        contents.clear();
        toolbar.getItems().clear();
        contentSplitPane.getItems().clear();
        List<Map.Entry<String, Node>> orderedEntries = new ArrayList<>(newContents.entrySet());
        // Because of the orientation of the bar, the collection must be reversed for the first added entry to
        // appear at the top of the left bar
        if (orientation == SideBarOrientation.LEFT) {
            Collections.reverse(orderedEntries);
        }
        for (Map.Entry<String, Node> entry: orderedEntries) {
            addContentToSidePanel(entry.getKey(), entry.getValue());
        }

    }
    private void addContentToSidePanel(String key, Node nodeContent) {
        ToggleButton button = new ToggleButton(key);
        button.setOnMouseClicked(this::mouseButtonClickedHandler);
        toolbar.getItems().add(button);
        SideBarContent newContent = new SideBarContent(nodeContent);
        newContent.getStage().setOnCloseRequest(event -> {
            Stage stage = (Stage) event.getSource();
            SideBarContent content = (SideBarContent) stage.getScene().getRoot();
            List<String> keyList = contents.entrySet().stream().filter(e -> e.getValue() == content).map(Map.Entry::getKey).toList();
            if (!keyList.isEmpty()) {
                int index = new ArrayList<String>(contents.keySet()).indexOf(keyList.get(0));
                if (orientation.spacerIndex == 0) {
                    index++;
                }
                ((ToggleButton) toolbar.getItems().get(index)).setSelected(false);
            }
        });
        contents.put(key, newContent);
    }

    public void addSideBarContent(String name, Node nodeContent) {
        // remove the spacer,
        int currentSpacerIndex = orientation.spacerIndex == -1 ? toolbar.getItems().size() - 1 : orientation.spacerIndex;
        Node spacer = toolbar.getItems().remove(currentSpacerIndex);
        //add the sidebar content
        addContentToSidePanel(name, nodeContent);
        // readd the spacer, adjust spacer
        int newSpacerIndex = orientation.spacerIndex == -1 ? toolbar.getItems().size() : orientation.spacerIndex;
        toolbar.getItems().add(newSpacerIndex, spacer);
    }

    private void mouseButtonClickedHandler(MouseEvent event) {
        ToggleButton button = (ToggleButton) event.getSource();
        SideBarContent content = contents.get(button.getText());
        // The button focus affects the sizing of the toolbar, so must remove the focus from the buttons before
        // proceeding
        toolbar.requestFocus();
        if (event.getButton() == MouseButton.PRIMARY) {
            if (contentSplitPane.getItems().contains(content)) {
                contentSplitPane.getItems().remove(content);
                resetDividerPositions();
            } else {
                if (content.isShowing()) {
                    content.hide();
                } else {
                    int index = getIndex(content);
                    contentSplitPane.getItems().add(index, content);
                    resetDividerPositions();
                }
            }
        } else if (event.getButton() == MouseButton.SECONDARY) {
            if (content.isShowing() && !contentSplitPane.getItems().contains(content)) {
                content.hide();
            } else {
                contentSplitPane.getItems().remove(content);
                content.show();

            }
            button.setSelected(content.isShowing());
        }
    }

    private void resetDividerPositions() {
        ObservableList<SplitPane.Divider> dividers = contentSplitPane.getDividers();
        for (int i = 0; i < dividers.size(); i++) {
            dividers.get(i).setPosition((i + 1.0) / contentSplitPane.getItems().size());
        }
    }

    private int getIndex(Node toBeAdded) {
        List<Node> values = new ArrayList<>(contents.values());
        if (orientation == SideBarOrientation.LEFT) {
            Collections.reverse(values);
        }
        List<Integer> indices = new ArrayList<>();
        for (Node node: contentSplitPane.getItems()) {
            indices.add(values.indexOf(node));
        }
        int wantedIndices = values.indexOf(toBeAdded);
        indices.add(wantedIndices);
        Collections.sort(indices);
        return indices.indexOf(wantedIndices);
    }

    private class MouseDragBindings {
        SideBar sideBar;
        private static final int RESIZE_MARGIN = 10;
        boolean dragging = false;
        double[] dragStart = new double[2];
        double startingDragWidth = 0;
        double startingDragHeight = 0;

        public MouseDragBindings(SideBar sideBar) {
            this.sideBar = sideBar;

            this.sideBar.contentSplitPane.setOnMousePressed(this::mousePressed);
            this.sideBar.contentSplitPane.setOnMouseDragged(this::mouseDragged);
            this.sideBar.contentSplitPane.setOnMouseMoved(this::mouseOver);
            this.sideBar.contentSplitPane.setOnMouseReleased(event -> mouseReleased());
        }
        private void mouseOver(MouseEvent event) {
            if (isDraggable(event) || dragging) {
                Cursor cursor = isVertical() ? Cursor.H_RESIZE : Cursor.V_RESIZE;
                contentSplitPane.setCursor(cursor);
            } else {
                contentSplitPane.setCursor(Cursor.DEFAULT);
            }
        }

        private boolean isDraggable(MouseEvent event) {
            boolean isDraggable = false;
            if (orientation == SideBarOrientation.LEFT) {
                isDraggable = event.getX() > (contentSplitPane.getWidth() - RESIZE_MARGIN);
            } else if (orientation == SideBarOrientation.RIGHT) {
                isDraggable = event.getX() < RESIZE_MARGIN;
            } else if (orientation == SideBarOrientation.BOTTOM) {
                isDraggable = event.getY() < RESIZE_MARGIN;
            } else if (orientation == SideBarOrientation.TOP) {
                isDraggable = event.getY() > (contentSplitPane.getHeight() - RESIZE_MARGIN);
            }
            return isDraggable;
        }

        private void mouseReleased() {
            dragging = false;
            contentSplitPane.setCursor(Cursor.DEFAULT);
        }

        private void mousePressed(MouseEvent mouseEvent) {
            dragStart[0] = mouseEvent.getSceneX();
            dragStart[1] = mouseEvent.getSceneY();
            startingDragWidth = contentSplitPane.getWidth();
            startingDragHeight = contentSplitPane.getHeight();
            dragging = isDraggable(mouseEvent);
        }

        private void mouseDragged(MouseEvent mouseEvent) {
            double x = mouseEvent.getSceneX();
            double y = mouseEvent.getSceneY();
            double deltaX = x - dragStart[0];
            double deltaY = y - dragStart[1];
            if (isVertical()) {
                double prefWidth;
                double windowWidth = sideBar.getParent().getScene().getWindow().getWidth();
                if (orientation == SideBarOrientation.LEFT) {
                    prefWidth = Math.min(startingDragWidth + deltaX, windowWidth - (4 * MIN_CONTENT_SIZE));
                } else {
                    prefWidth = Math.min(startingDragWidth - deltaX, windowWidth - (4 * MIN_CONTENT_SIZE));
                }
                contentSplitPane.setPrefWidth(prefWidth);
                sideBar.contentSize = prefWidth;
                return;
            }
            double prefHeight;
            double windowHeight = sideBar.getParent().getScene().getWindow().getHeight();
            if (orientation == SideBarOrientation.BOTTOM) {
                prefHeight = Math.min(startingDragHeight - deltaY, windowHeight - (4 * MIN_CONTENT_SIZE));
            } else {
                prefHeight = Math.min(startingDragHeight + deltaY, windowHeight - (4 * MIN_CONTENT_SIZE));
            }
            contentSplitPane.setPrefHeight(prefHeight);
            sideBar.contentSize = prefHeight;
        }
    }
}

