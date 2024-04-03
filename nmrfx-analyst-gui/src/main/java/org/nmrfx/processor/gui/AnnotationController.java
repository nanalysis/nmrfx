package org.nmrfx.processor.gui;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;
import org.nmrfx.processor.gui.annotations.*;
import org.nmrfx.processor.gui.spectra.crosshair.CrossHairs;
import org.nmrfx.utils.GUIUtils;

import java.util.Arrays;
import java.util.List;

public class AnnotationController {
    private final ColorPicker strokeColorPicker = new ColorPicker();
    private final ColorPicker fillColorPicker = new ColorPicker();
    private final ChoiceBox<CanvasAnnotation.POSTYPE> xPosTypeChoiceBox = new ChoiceBox<>();
    private final ChoiceBox<CanvasAnnotation.POSTYPE> yPosTypeChoiceBox = new ChoiceBox<>();
    private final CheckBox strokeColorCheckBox = new CheckBox();
    private final CheckBox fillColorCheckBox = new CheckBox();
    private final CheckBox arrowFirstCheckBox = new CheckBox();
    private final CheckBox arrowLastCheckBox = new CheckBox();
    private final CheckBox lockAnnotationsCheckBox = new CheckBox();
    private final TextField textField = new TextField();
    private final TextArea textArea = new TextArea();
    private final Pane textPane = new Pane();
    private final Slider fontSizeSlider = new Slider();
    private final Slider lineWidthSlider = new Slider();
    PolyChart chart;
    CanvasAnnotation selectedAnno;
    FXMLController fxmlController;
    StrokeColorListener strokeColorListener = new StrokeColorListener();
    FillColorListener fillColorListener = new FillColorListener();

    public void setup(FXMLController fxmlController, TitledPane annoPane) {
        this.fxmlController = fxmlController;
        this.chart = fxmlController.getActiveChart();
        VBox vBox = new VBox();
        annoPane.setContent(vBox);
        ToolBar toolBar = new ToolBar();
        vBox.getChildren().add(toolBar);

        int iconSize = 18;
        FontIcon arrowFontIcon = new FontIcon();
        arrowFontIcon.setIconLiteral("mdi2a-arrow-top-left-bottom-right");
        arrowFontIcon.setIconSize(iconSize);
        Button arrowButton = new Button("Arrow", arrowFontIcon);
        arrowButton.setContentDisplay(ContentDisplay.TOP);
        arrowButton.setOnAction(e -> createArrow());
        toolBar.getItems().add(arrowButton);

        FontIcon rectangleFontIcon = new FontIcon();
        rectangleFontIcon.setIconLiteral("mdi2r-rectangle-outline");
        rectangleFontIcon.setIconSize(iconSize);
        Button rectangleButton = new Button("Rectangle", rectangleFontIcon);
        rectangleButton.setContentDisplay(ContentDisplay.TOP);
        rectangleButton.setOnAction(e -> createRectangle());
        toolBar.getItems().add(rectangleButton);

        FontIcon ovalFontIcon = new FontIcon();
        ovalFontIcon.setIconLiteral("mdi2c-circle-outline");
        ovalFontIcon.setIconSize(iconSize);
        Button ovalButton = new Button("Oval", ovalFontIcon);
        ovalButton.setContentDisplay(ContentDisplay.TOP);
        ovalButton.setOnAction(e -> createOval());
        toolBar.getItems().add(ovalButton);

        FontIcon lineFontIcon = new FontIcon();
        lineFontIcon.setIconLiteral("mdi2f-format-text-rotation-angle-up");
        lineFontIcon.setIconSize(iconSize);
        Button lineButton = new Button("Annotate", lineFontIcon);
        lineButton.setContentDisplay(ContentDisplay.TOP);
        lineButton.setOnAction(e -> createLine());
        toolBar.getItems().add(lineButton);

        FontIcon textFontIcon = new FontIcon();
        textFontIcon.setIconLiteral("mdi2f-format-size");
        textFontIcon.setIconSize(iconSize);
        Button textButton = new Button("Text Box", textFontIcon);
        textButton.setContentDisplay(ContentDisplay.TOP);
        textButton.setOnAction(e -> createText());
        toolBar.getItems().add(textButton);
        arrangeControls(vBox);
        configureControls();
    }

    void arrangeControls(VBox vBox) {
        Label lockLabel = new Label("Lock Annotations");
        HBox hBox = new HBox();
        hBox.setSpacing(20);
        hBox.getChildren().addAll(lockLabel, lockAnnotationsCheckBox);
        Separator separator = new Separator();

        GridPane gridPane = new GridPane();
        vBox.setSpacing(10);
        vBox.getChildren().addAll(hBox, separator, gridPane);

        ColumnConstraints col0Constraint = new ColumnConstraints();
        col0Constraint.setMinWidth(75);
        gridPane.getColumnConstraints().add(col0Constraint);
        int row = 0;
        gridPane.add(new Label("X Pos Type"), 0, row);
        gridPane.add(xPosTypeChoiceBox, 1, row);
        row++;
        gridPane.add(new Label("Y Pos Type"), 0, row);
        gridPane.add(yPosTypeChoiceBox, 1, row);
        row++;
        gridPane.add(new Label("Stroke"), 0, row);
        gridPane.add(strokeColorPicker, 1, row);
        gridPane.add(strokeColorCheckBox, 2, row);
        row++;
        gridPane.add(new Label("Fill"), 0, row);
        gridPane.add(fillColorPicker, 1, row);
        gridPane.add(fillColorCheckBox, 2, row);
        row++;
        gridPane.add(new Label("Line Width"), 0, row);
        gridPane.add(lineWidthSlider, 1, row);
        row++;
        gridPane.add(new Label("Text"), 0, row);
        gridPane.add(textPane, 1, row);
        GridPane.setColumnSpan(textPane, 2);
        textPane.getChildren().add(textArea);
        row++;
        gridPane.add(new Label("Font Size"), 0, row);
        gridPane.add(fontSizeSlider, 1, row);
        row++;

        gridPane.add(new Label("Arrow First"), 0, row);
        gridPane.add(arrowFirstCheckBox, 1, row);
        row++;
        gridPane.add(new Label("Arrow Last"), 0, row);
        gridPane.add(arrowLastCheckBox, 1, row);
        row++;

        textArea.setPrefWidth(200);
        textArea.setPrefHeight(50);
        textArea.setWrapText(true);
        textField.setPrefWidth(200);
    }

    void configureControls() {
        strokeColorPicker.disableProperty().bind(strokeColorCheckBox.selectedProperty().not());
        strokeColorPicker.valueProperty().addListener(strokeColorListener);
        strokeColorCheckBox.selectedProperty().addListener(e -> updateStrokeColor());
        strokeColorCheckBox.setDisable(true);
        fillColorPicker.disableProperty().bind(fillColorCheckBox.selectedProperty().not());
        fillColorPicker.valueProperty().addListener(fillColorListener);
        fillColorCheckBox.selectedProperty().addListener(e -> updateFillColor());
        fillColorCheckBox.setDisable(true);

        arrowFirstCheckBox.setOnAction(e -> updateArrowFirst());
        arrowFirstCheckBox.setDisable(true);
        arrowLastCheckBox.setOnAction(e -> updateArrowLast());
        arrowLastCheckBox.setDisable(true);
        lockAnnotationsCheckBox.setOnAction(e -> lockAnnotations());
        lockAnnotationsCheckBox.setDisable(true);

        xPosTypeChoiceBox.getItems().addAll(CanvasAnnotation.POSTYPE.values());
        xPosTypeChoiceBox.setOnAction(e -> updateXPosType());
        yPosTypeChoiceBox.getItems().addAll(CanvasAnnotation.POSTYPE.values());
        yPosTypeChoiceBox.setOnAction(e -> updateYPosType());

        textField.setOnKeyPressed(this::textKeyPressed);
        textField.setDisable(true);

        textArea.setOnKeyPressed(this::textAreaPressed);
        textArea.setDisable(true);

        fontSizeSlider.valueProperty().addListener(e -> updateFontSize());
        fontSizeSlider.setMax(50.0);
        fontSizeSlider.setMin(0.0);
        fontSizeSlider.setMajorTickUnit(10.0);
        fontSizeSlider.setMinorTickCount(1);
        fontSizeSlider.setShowTickMarks(true);
        fontSizeSlider.setShowTickLabels(true);
        fontSizeSlider.setDisable(true);

        lineWidthSlider.valueProperty().addListener(e -> updateLineWidth());
        lineWidthSlider.setMax(10.0);
        lineWidthSlider.setMin(0.0);
        lineWidthSlider.setMajorTickUnit(5.0);
        lineWidthSlider.setMinorTickCount(3);
        lineWidthSlider.setShowTickMarks(true);
        lineWidthSlider.setShowTickLabels(true);
        lineWidthSlider.setDisable(true);
    }

    public PolyChart getChart() {
        return fxmlController.getActiveChart();
    }

    public void setChart(PolyChart activeChart) {
        this.chart = activeChart;
    }

    private Double[] getCrossHairs() {
        Double[] positions = new Double[4];
        CrossHairs crossHairs = chart.getCrossHairs();
        Orientation[] orientations = new Orientation[]{Orientation.VERTICAL, Orientation.HORIZONTAL};
        int j = 0;
        for (Orientation orientation : orientations) {
            for (int iCrossHair = 0; iCrossHair < 2; iCrossHair++) {
                boolean visible = crossHairs.isVisible(iCrossHair, orientation);
                if (crossHairs.getState(iCrossHair, orientation) && visible) {
                    double position = crossHairs.getPosition(iCrossHair, orientation);
                    positions[j] = position;
                } else {
                    positions[j] = null;
                }
                j++;
            }
        }
        return positions;
    }

    private BoundsRectangle getDefaultPosition() {
        double[][] world = getChart().getWorld();
        double width = (world[0][1] - world[0][0]) / 10.0;
        double height = (world[1][1] - world[1][0]) / 10.0;
        double x1 = world[0][0] + width;
        double x2 = x1 + width;
        double y1 = world[1][0] + height;
        double y2 = y1 + height;
        return new BoundsRectangle(x1, y1, x2, y2);
    }

    private Double[] getStartPositions(boolean primaryOnly) {
        Double[] positions = getCrossHairs();
        boolean useDefault = false;
        for (int i = 0; i < positions.length; i++) {
            if (primaryOnly) {
                if (i % 2 == 0 && positions[i] == null) {
                    useDefault = true;
                    break;
                }
            } else if (positions[i] == null) {
                useDefault = true;
                break;
            }
        }

        if (useDefault) {
            BoundsRectangle boundsRectangle = getDefaultPosition();
            positions[0] = boundsRectangle.x1();
            positions[1] = boundsRectangle.x2();
            positions[2] = boundsRectangle.y1();
            positions[3] = boundsRectangle.y2();
        }
        return positions;
    }

    private void createPentagon() {
        List<Double> x = Arrays.asList(10.1, 10.2, 10., 9.9, 9.8);
        List<Double> y = Arrays.asList(110.0, 107.0, 105.0, 107.0, 110.0);
        double lineWidth = 1.0;
        Color stroke = Color.BLACK;
        Color fill = GUIUtils.getColor("");
        AnnoShape shape = new AnnoPolygon(x, y,
                CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.WORLD);
        shape.setStroke(stroke);
        shape.setFill(fill);
        shape.setLineWidth(lineWidth);
        getChart().addAnnotation(shape);
        refresh();
    }

    private void createQuadrilateral() {
        List<Double> x = Arrays.asList(10.0, 9.5, 9.5, 10.0);
        List<Double> y = Arrays.asList(110.0, 110.0, 105.0, 105.0);
        double lineWidth = 1.0;
        Color stroke = Color.BLACK;
        Color fill = GUIUtils.getColor("");
        AnnoShape shape = new AnnoPolygon(x, y,
                CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.WORLD);
        shape.setStroke(stroke);
        shape.setFill(fill);
        shape.setLineWidth(lineWidth);
        getChart().addAnnotation(shape);
        refresh();
    }

    private void createTriangle() {
        List<Double> x = Arrays.asList(10.0, 9.5, 9.0);
        List<Double> y = Arrays.asList(110.0, 105.0, 110.0);
        double lineWidth = 1.0;
        Color stroke = Color.BLACK;
        Color fill = GUIUtils.getColor("");
        AnnoShape shape = new AnnoPolygon(x, y,
                CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.WORLD);
        shape.setStroke(stroke);
        shape.setFill(fill);
        shape.setLineWidth(lineWidth);
        getChart().addAnnotation(shape);
        refresh();
    }

    private void createLine() {
        Double[] positions = getStartPositions(false);
        double x1 = positions[0];
        double x2 = positions[1];
        double y1 = positions[2];
        double y2 = positions[3];
        double lineWidth = 1.0;
        Color fill = Color.BLACK;
        String text = "Hello";
        double fontSize = 12.0;
        AnnoShape shape = new AnnoLineText(x1, y1, x2, y2, text, fontSize, lineWidth,
                CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.WORLD);
        shape.setFill(fill);
        shape.setLineWidth(lineWidth);
        getChart().addAnnotation(shape);
        refresh();
    }

    private void createText() {
        Double[] positions = getStartPositions(true);
        double x1 = positions[0];
        double y1 = positions[2];
        Color fill = Color.BLACK;
        String text = "Hello";
        double fontSize = 12.0;
        double width = text.length() * fontSize;
        AnnoText annoText = new AnnoText(x1, y1, width, text, fontSize,
                CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.WORLD);
        annoText.setFill(fill);
        getChart().addAnnotation(annoText);
        refresh();
    }

    private void createOval() {
        Double[] positions = getStartPositions(false);
        double x1 = positions[0];
        double x2 = positions[1];
        double y1 = positions[2];
        double y2 = positions[3];
        double lineWidth = 1.0;
        Color stroke = Color.BLACK;
        Color fill = GUIUtils.getColor("");
        AnnoShape shape = new AnnoOval(x1, y1, x2, y2,
                CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.WORLD);
        shape.setStroke(stroke);
        shape.setFill(fill);
        shape.setLineWidth(lineWidth);
        getChart().addAnnotation(shape);
        refresh();
    }

    private void createArrow() {
        Double[] positions = getStartPositions(false);
        double x1 = positions[0];
        double x2 = positions[1];
        double y1 = positions[2];
        double y2 = positions[3];
        boolean arrowFirst = true;
        boolean arrowLast = false;
        double lineWidth = 1.0;
        Color stroke = Color.BLACK;
        AnnoShape shape = new AnnoLine(x1, y1, x2, y2, arrowFirst, arrowLast, lineWidth,
                CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.WORLD);
        shape.setStroke(stroke);
        shape.setFill(stroke);
        shape.setLineWidth(lineWidth);
        getChart().addAnnotation(shape);
        refresh();
    }

    private void createRectangle() {
        Double[] positions = getStartPositions(false);
        double x1 = positions[0];
        double x2 = positions[1];
        double y1 = positions[2];
        double y2 = positions[3];
        double lineWidth = 1.0;
        Color stroke = Color.BLACK;
        Color fill = GUIUtils.getColor("");
        AnnoShape shape = new AnnoRectangle(x1, y1, x2, y2,
                CanvasAnnotation.POSTYPE.WORLD, CanvasAnnotation.POSTYPE.WORLD);
        shape.setStroke(stroke);
        shape.setFill(fill);
        shape.setLineWidth(lineWidth);
        getChart().addAnnotation(shape);
        refresh();
    }

    public void annotationDeselected(CanvasAnnotation annotation) {
        selectedAnno = null;
        strokeColorCheckBox.setSelected(false);
        strokeColorCheckBox.setDisable(true);
        fillColorCheckBox.setSelected(false);
        fillColorCheckBox.setDisable(true);
        arrowFirstCheckBox.setDisable(true);
        arrowLastCheckBox.setDisable(true);
        textField.setDisable(true);
        textField.clear();
        textArea.setDisable(true);
        textArea.clear();
        fontSizeSlider.setDisable(true);
        lineWidthSlider.setDisable(true);
    }

    public void annotationSelected(CanvasAnnotation annotation) {
        selectedAnno = annotation;
        xPosTypeChoiceBox.setValue(annotation.getXPosType());
        yPosTypeChoiceBox.setValue(annotation.getYPosType());
        fillColorCheckBox.setDisable(false);
        textField.clear();
        textField.setDisable(true);
        arrowFirstCheckBox.setDisable(true);
        arrowLastCheckBox.setDisable(true);
        fontSizeSlider.setDisable(true);
        lockAnnotationsCheckBox.setDisable(false);

        if (annotation instanceof AnnoShape shape) {
            strokeColorCheckBox.setDisable(false);
            strokeColorPicker.setValue(shape.getStrokeColor());
            strokeColorCheckBox.setSelected(shape.getStrokeColor() != null);
            fillColorPicker.setValue(shape.getFillColor());
            fillColorCheckBox.setSelected(shape.getFillColor() != null);
            lineWidthSlider.setDisable(false);
            lineWidthSlider.setValue(shape.getLineWidth());
            if (annotation instanceof AnnoLine line) {
                fillColorCheckBox.setSelected(false);
                fillColorCheckBox.setDisable(true);
                arrowFirstCheckBox.setDisable(false);
                arrowFirstCheckBox.setSelected(line.isArrowFirst());
                arrowLastCheckBox.setDisable(false);
                arrowLastCheckBox.setSelected(line.isArrowLast());
                arrowFirstCheckBox.setSelected(line.isArrowFirst());
                arrowLastCheckBox.setSelected(line.isArrowLast());
            } else if (annotation instanceof AnnoLineText text) {
                textPane.getChildren().set(0, textField);
                textField.setDisable(false);
                textField.setText(text.getText());
                fontSizeSlider.setDisable(false);
                fontSizeSlider.setValue(text.getFontSize());
            }
        } else if (annotation instanceof AnnoText text) {
            textPane.getChildren().set(0, textArea);
            strokeColorCheckBox.setSelected(false);
            strokeColorCheckBox.setDisable(true);
            lineWidthSlider.setDisable(true);
            textArea.setDisable(false);
            textArea.setText(text.getText());
            fontSizeSlider.setDisable(false);
            fontSizeSlider.setValue(text.getFontSize());
            fillColorPicker.setValue(text.getFillColor());
            fillColorCheckBox.setSelected(text.getFillColor() != null);
        }
    }

    private void updateXPosType() {
        if (selectedAnno != null) {
            var newType = xPosTypeChoiceBox.getValue();
            var oldType = selectedAnno.getXPosType();
            var bounds = getChart().getBounds();
            var world = getChart().getWorld();
            if (newType != oldType) {
                selectedAnno.updateXPosType(newType, bounds[0], world[0]);
            }
        }
        refresh();
    }

    private void updateYPosType() {
        if (selectedAnno != null) {
            var newType = yPosTypeChoiceBox.getValue();
            var oldType = selectedAnno.getYPosType();
            var bounds = getChart().getBounds();
            var world = getChart().getWorld();
            if (newType != oldType) {
                selectedAnno.updateYPosType(newType, bounds[1], world[1]);
            }
        }
        refresh();
    }

    private void updateStrokeColor() {
        boolean selected = strokeColorCheckBox.isSelected();
        Color color = strokeColorPicker.getValue();
        if (selectedAnno instanceof AnnoShape shape) {
            if (selected) {
                shape.setStroke(color);
            } else {
                if (selectedAnno instanceof AnnoLine line) {
                    line.setStroke(Color.BLACK);
                } else if (selectedAnno instanceof AnnoLineText line) {
                    line.setStroke(Color.BLACK);
                } else if (shape.getFill().isBlank()) {
                    shape.setStroke(Color.BLACK);
                } else {
                    shape.setStroke("");
                }
            }
        }
        refresh();
    }

    private void updateFillColor() {
        boolean selected = fillColorCheckBox.isSelected();
        Color color = fillColorPicker.getValue();
        if (selectedAnno instanceof AnnoShape shape) {
            if (selected) {
                shape.setFill(color);
            } else {
                if (selectedAnno instanceof AnnoLineText line) {
                    line.setFill(Color.BLACK);
                } else if (shape.getStroke().isBlank()) {
                    shape.setStroke(Color.BLACK);
                    shape.setFill("");
                } else {
                    shape.setFill("");
                }
            }
        } else if (selectedAnno instanceof AnnoText text) {
            if (selected) {
                text.setFill(color);
            } else {
                text.setFill(Color.BLACK);
            }
        }
        refresh();
    }

    private void updateLineWidth() {
        double newLineWidth = lineWidthSlider.getValue();
        if (selectedAnno instanceof AnnoShape shape) {
            shape.setLineWidth(newLineWidth);
        }
        refresh();
    }

    private void updateArrowFirst() {
        if (selectedAnno instanceof AnnoLine anno) {
            boolean checked = arrowFirstCheckBox.isSelected();
            anno.setArrowFirst(checked);
        }
        refresh();
    }

    private void updateArrowLast() {
        if (selectedAnno instanceof AnnoLine anno) {
            boolean checked = arrowLastCheckBox.isSelected();
            anno.setArrowLast(checked);
        }
        refresh();
    }

    private void textKeyPressed(KeyEvent keyEvent) {
        if (keyEvent.getCode() == KeyCode.ENTER) {
            String text = textField.getText();
            if (selectedAnno instanceof AnnoLineText anno) {
                anno.setText(text);
            }
            refresh();
        }
    }

    private void textAreaPressed(KeyEvent keyEvent) {
        if (keyEvent.getCode() == KeyCode.ENTER) {
            String text = textArea.getText();
            if (selectedAnno instanceof AnnoText anno) {
                anno.setText(text);
            }
            refresh();
        }
    }

    private void updateFontSize() {
        double newFontSize = fontSizeSlider.getValue();
        if (selectedAnno instanceof AnnoLineText anno) {
            anno.setFontSize(newFontSize);
        } else if (selectedAnno instanceof AnnoText anno) {
            anno.setFontSize(newFontSize);
        }
        refresh();
    }

    private void refresh() {
        getChart().drawPeakLists(true);
    }

    private void lockAnnotations() {
        if (lockAnnotationsCheckBox.isSelected()) {
            chart.setLockAnno(true);
        } else {
            chart.setLockAnno(false);
        }
        refresh();
    }

    record BoundsRectangle(double x1, double y1, double x2, double y2) {
    }

    abstract class ColorListener implements ChangeListener<Color> {

        boolean active = true;

        abstract void update(CanvasAnnotation anno, Color value);

        @Override
        public void changed(ObservableValue<? extends Color> observable, Color oldValue, Color newValue) {
            if (active) {
                update(selectedAnno, newValue);
            }
            refresh();
        }
    }

    private class StrokeColorListener extends ColorListener {
        void update(CanvasAnnotation anno, Color value) {
            if (anno instanceof AnnoShape shape) {
                shape.setStroke(value);
            }
        }
    }

    private class FillColorListener extends ColorListener {
        void update(CanvasAnnotation anno, Color value) {
            if (anno instanceof AnnoShape shape) {
                shape.setFill(value);
            } else if (anno instanceof AnnoText text) {
                text.setFill(value);
            }
        }
    }

}

