package org.nmrfx.processor.gui;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

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
    // Whether to rotate, what index to add the toolbar and splitpane on

    private static final int MIN_CONTENT_SIZE = 50;
    private final SplitPane splitPane = new SplitPane();

    private final SplitPane contentSplitPane = new SplitPane();

    private final ToolBar toolbar = new ToolBar();

    private final LinkedHashMap<String, SideBarContent> contents = new LinkedHashMap<>();
    private final SideBarOrientation orientation;
    private double contentSize;
    public SideBar (SideBarOrientation orientation, LinkedHashMap<String, Node> contents, double contentSize) {
        this.orientation = orientation;
        this.contentSize = contentSize;
        VBox.setVgrow(this, Priority.ALWAYS);
        HBox.setHgrow(this, Priority.ALWAYS);
        this.getChildren().add(splitPane);
//        this.setMaxHeight(Region.USE_PREF_SIZE);
        splitPane.getStyleClass().add("side-bar-split-pane");
        toolbar.setRotate(orientation.rotate);
        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        addAllContentsToSidePanel(contents);
        // Must add the spacer in the correct index
        int spacerIndex = orientation.spacerIndex == -1 ? toolbar.getItems().size() : orientation.spacerIndex;
        toolbar.getItems().add(spacerIndex, spacer);
        Group group = new Group();
        group.getChildren().add(toolbar);

        splitPane.getItems().add(group);

        Orientation splitPaneOrientation = isVertical() ? Orientation.HORIZONTAL : Orientation.VERTICAL;
        splitPane.setOrientation(splitPaneOrientation);
        VBox.setVgrow(contentSplitPane, Priority.ALWAYS);
        Orientation contentSplitPaneOrientation = isVertical() ? Orientation.VERTICAL : Orientation.HORIZONTAL;
        HBox.setHgrow(contentSplitPane, Priority.ALWAYS);
        contentSplitPane.setOrientation(contentSplitPaneOrientation);
        contentSplitPane.setPrefWidth(0);
        contentSplitPane.getItems().addListener((ListChangeListener<Node>) c -> {
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
                    contentSplitPane.setPrefWidth(200);
                    contentSplitPane.setMinWidth(MIN_CONTENT_SIZE);
                } else {
                    contentSplitPane.setPrefHeight(200);
                    contentSplitPane.setMinHeight(MIN_CONTENT_SIZE);
                }
            }
        });

        this.heightProperty().addListener(((observable, oldValue, newValue) -> {
            System.out.println(orientation);
            System.out.println("pane old:" + oldValue);
            System.out.println("pane new:" + newValue);

        }));

        toolbar.widthProperty().addListener((observable, oldValue, newValue) -> {
            System.out.println(orientation);
            System.out.println("toolbar - old: " + oldValue);
            System.out.println("toolbar - new: " + newValue);
            System.out.println("splitpane: " + splitPane.getHeight());
            System.out.println("height: "  + this.getHeight());
            System.out.println("height Prop: "  + this.heightProperty().get());
        });

        splitPane.getItems().add(orientation.contentIndex, contentSplitPane);
        if (isVertical()) {
            toolbar.minWidthProperty().bind(Bindings.createDoubleBinding(() -> this.getHeight() - 5, this.heightProperty()));
//            toolbar.prefWidthProperty().bind(Bindings.createDoubleBinding(() -> this.getHeight() - 3, this.heightProperty()));
//            splitPane.prefHeightProperty().bind(Bindings.createDoubleBinding(() -> this.getHeight() - 5, this.heightProperty()));
        } else {
            toolbar.minWidthProperty().bind(Bindings.createDoubleBinding(() -> this.getWidth() - 5, this.widthProperty()));
//            toolbar.prefWidthProperty().bind(Bindings.createDoubleBinding(() -> this.getWidth() - 3, this.widthProperty()));
//            splitPane.prefWidthProperty().bind(Bindings.createDoubleBinding(() -> this.getWidth() - 5, this.widthProperty()));
        }
        setContentSplitPaneResizing();
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

    private void mouseButtonClickedHandler(MouseEvent event) {
        ToggleButton button = (ToggleButton) event.getSource();
        SideBarContent content = contents.get(button.getText());
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

    private void setContentSplitPaneResizing() {
        contentSplitPane.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                mousePressed(event);
            }});
        contentSplitPane.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                mouseDragged(event);
            }});
        contentSplitPane.setOnMouseMoved(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                mouseOver(event);
            }});
        contentSplitPane.setOnMouseReleased(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                mouseReleased(event);
            }});
    }
    int RESIZE_MARGIN = 10;
    boolean dragging = false;
    double[] dragStart = new double[2];
    double startingDragWidth = 0;
    double startingDragHeight = 0;



    protected void mouseOver(MouseEvent event) {
        if(isInDraggableZone(event) || dragging ) {
            Cursor cursor = isVertical() ? Cursor.H_RESIZE : Cursor.V_RESIZE;
            contentSplitPane.setCursor(cursor);
        }
        else {
            contentSplitPane.setCursor(Cursor.DEFAULT);
        }
    }

    protected boolean isInDraggableZone(MouseEvent event) {
        boolean isDraggable = false;
        if (orientation == SideBarOrientation.LEFT) {
            isDraggable = event.getX() > (contentSplitPane.getWidth() - RESIZE_MARGIN);
        } else if (orientation == SideBarOrientation.RIGHT) {
            isDraggable = event.getX() < RESIZE_MARGIN;
        } else if (orientation == SideBarOrientation.BOTTOM) {
            isDraggable = event.getY() < RESIZE_MARGIN;
        }
        return isDraggable;
    }

    protected void mouseReleased(MouseEvent event) {
        dragging = false;
        contentSplitPane.setCursor(Cursor.DEFAULT);
    }

    protected void mousePressed(MouseEvent mouseEvent) {

        dragStart[0] = mouseEvent.getX();
        dragStart[1] = mouseEvent.getY();
        startingDragWidth = contentSplitPane.getWidth();
        startingDragHeight = contentSplitPane.getHeight();
        // ignore clicks outside of the draggable margin
        if(!isInDraggableZone(mouseEvent)) {
            return;
        }

        dragging = true;


    }

    protected void mouseDragged(MouseEvent mouseEvent) {
        double x = mouseEvent.getX();
        double y = mouseEvent.getY();
        double deltaX = x - dragStart[0];
        double deltaY = y - dragStart[1];
        double windoWidth = this.getParent().getScene().getWindow().getWidth();
        if (isVertical()) {
            double prefWidth = 0.0;
            if (orientation == SideBarOrientation.LEFT) {
                prefWidth = Math.min(startingDragWidth + deltaX, windoWidth - (4 * MIN_CONTENT_SIZE));
            } else {
                prefWidth = Math.min(startingDragWidth - deltaX, windoWidth - (4 * MIN_CONTENT_SIZE));
            }
            contentSplitPane.setPrefWidth(prefWidth);
            return;
        }
        double prefHeight = 0.0;
        double windowHeight = this.getParent().getScene().getWindow().getHeight();

        if (orientation == SideBarOrientation.BOTTOM) {
            prefHeight = Math.min(startingDragHeight - deltaY, windowHeight - (4 * MIN_CONTENT_SIZE));
        }
        contentSplitPane.setPrefHeight(prefHeight);
    }
}

