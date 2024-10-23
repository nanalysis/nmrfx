/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2018 One Moon Scientific, Inc., Westfield, N.J., USA
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

import javafx.beans.property.*;
import javafx.scene.paint.Color;
import org.nmrfx.utils.properties.ColorProperty;
import org.nmrfx.utils.properties.PublicPropertyContainer;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ChartProperties implements PublicPropertyContainer {
    public static final int PROJECTION_BORDER_DEFAULT_SIZE = 150;
    public static final int EMPTY_BORDER_DEFAULT_SIZE = 5;

    private final PolyChart polyChart;

    private final IntegerProperty leftBorderSize;
    private final IntegerProperty rightBorderSize;
    private final IntegerProperty topBorderSize;
    private final IntegerProperty bottomBorderSize;
    private final BooleanProperty intensityAxis;
    private final IntegerProperty labelFontSize;
    private final IntegerProperty ticFontSize;
    private final ColorProperty cross0Color;
    private final ColorProperty cross1Color;
    private final ColorProperty axesColor;
    private final ColorProperty bgColor;
    private final BooleanProperty grid;
    private final BooleanProperty regions;
    private final BooleanProperty integrals;
    private final BooleanProperty integralValues;
    private final DoubleProperty integralLowPos;
    private final DoubleProperty integralHighPos;
    private final DoubleProperty aspectRatio;
    private final BooleanProperty aspect;
    private final DoubleProperty stackX;
    private final DoubleProperty stackY;
    private final BooleanProperty titles;
    private final BooleanProperty parameters;

    private final Set<Property<?>> properties = new HashSet<>();

    public ChartProperties(PolyChart chart) {
        this.polyChart = chart;

        leftBorderSize = add(new SimpleIntegerProperty(polyChart, "leftBorderSize", 0));
        rightBorderSize = add(new SimpleIntegerProperty(polyChart, "rightBorderSize", EMPTY_BORDER_DEFAULT_SIZE));
        topBorderSize = add(new SimpleIntegerProperty(polyChart, "topBorderSize", EMPTY_BORDER_DEFAULT_SIZE));
        bottomBorderSize = add(new SimpleIntegerProperty(polyChart, "bottomBorderSize", 0));
        intensityAxis = add(new SimpleBooleanProperty(polyChart, "intensityAxis", false));
        labelFontSize = add(new SimpleIntegerProperty(polyChart, "labelFontSize", PreferencesController.getLabelFontSize()));
        ticFontSize = add(new SimpleIntegerProperty(polyChart, "ticFontSize", PreferencesController.getTickFontSize()));
        cross1Color = add(new ColorProperty(polyChart, "cross1Color", null));
        bgColor = add(new ColorProperty(polyChart, "bgColor", null));
        axesColor = add(new ColorProperty(polyChart, "axesColor", null));
        cross0Color = add(new ColorProperty(polyChart, "cross0Color", null));
        grid = add(new SimpleBooleanProperty(polyChart, "grid", false));
        regions = add(new SimpleBooleanProperty(polyChart, "regions", false));
        integrals = add(new SimpleBooleanProperty(polyChart, "integrals", false));
        integralValues = add(new SimpleBooleanProperty(polyChart, "integralValues", false));
        integralLowPos = add(new SimpleDoubleProperty(polyChart, "integralLowPos", 0.8));
        integralHighPos = add(new SimpleDoubleProperty(polyChart, "integralHighPos", 0.95));
        titles = add(new SimpleBooleanProperty(polyChart, "titles", true));
        parameters = add(new SimpleBooleanProperty(polyChart, "parameters", false));
        aspectRatio = add(new SimpleDoubleProperty(polyChart, "aspectRatio", 1.0));
        aspect = add(new SimpleBooleanProperty(polyChart, "aspect", false));
        stackX = add(new SimpleDoubleProperty(polyChart, "stackX", 0.0), false);
        stackY = add(new SimpleDoubleProperty(polyChart, "stackY", 0.0), false);
    }

    private <T extends Property<?>> T add(T property) {
        return add(property, true);
    }

    private <T extends Property<?>> T add(T property, boolean refresh) {
        if (refresh) {
            property.addListener(ignore -> polyChart.refresh());
        }
        properties.add(property);
        return property;
    }

    @Override
    public Collection<Property<?>> getPublicProperties() {
        return Collections.unmodifiableSet(properties);
    }

    public void copyTo(PolyChart destChart) {
        ChartProperties destProps = destChart.getChartProperties();
        destProps.setLeftBorderSize(getLeftBorderSize());
        destProps.setRightBorderSize(getRightBorderSize());
        destProps.setTopBorderSize(getTopBorderSize());
        destProps.setBottomBorderSize(getBottomBorderSize());
        destProps.setIntensityAxis(getIntensityAxis());

        destProps.setLabelFontSize(getLabelFontSize());
        destProps.setTicFontSize(getTicFontSize());

        destProps.setCross0Color(getCross0Color());
        destProps.setCross1Color(getCross1Color());
        destProps.setBgColor(getBgColor());
        destProps.setAxesColor(getAxesColor());

        destProps.setGrid(getGrid());
        destProps.setRegions(getRegions());
        destProps.setIntegralLowPos(getIntegralLowPos());
        destProps.setIntegralHighPos(getIntegralHighPos());
        destProps.setIntegrals(getIntegrals());
        destProps.setIntegralValues(getIntegralValues());
        destProps.setTitles(getTitles());
        destProps.setParameters(getParameters());
        destProps.setAspectRatio(getAspectRatio());
        destProps.setAspect(getAspect());
        destProps.setStackX(getStackX());
        destProps.setStackY(getStackY());
    }

    public int getLeftBorderSize() {
        return leftBorderSizeProperty().get();
    }

    public void setLeftBorderSize(int value) {
        leftBorderSizeProperty().set(value);
    }

    public IntegerProperty leftBorderSizeProperty() {
        return leftBorderSize;
    }

    public int getRightBorderSize() {
        return rightBorderSizeProperty().get();
    }

    public void setRightBorderSize(int value) {
        rightBorderSizeProperty().set(value);
    }

    public IntegerProperty rightBorderSizeProperty() {
        return rightBorderSize;
    }

    public int getTopBorderSize() {
        return topBorderSizeProperty().get();
    }

    public void setTopBorderSize(int value) {
        topBorderSizeProperty().set(value);
    }

    public IntegerProperty topBorderSizeProperty() {
        return topBorderSize;
    }

    public int getBottomBorderSize() {
        return bottomBorderSizeProperty().get();
    }

    public void setBottomBorderSize(int value) {
        bottomBorderSizeProperty().set(value);
    }

    public IntegerProperty bottomBorderSizeProperty() {
        return bottomBorderSize;
    }

    public BooleanProperty intensityAxisProperty() {
        return intensityAxis;
    }

    public boolean getIntensityAxis() {
        return intensityAxisProperty().get();
    }

    public void setIntensityAxis(boolean value) {
        intensityAxisProperty().set(value);
    }

    public double getLabelFontSize() {
        return labelFontSizeProperty().get();
    }

    public void setLabelFontSize(double value) {
        labelFontSizeProperty().set((int) value);
    }

    public IntegerProperty labelFontSizeProperty() {
        return labelFontSize;
    }

    public double getTicFontSize() {
        return ticFontSizeProperty().get();
    }

    public void setTicFontSize(double value) {
        ticFontSizeProperty().set((int) value);
    }

    public IntegerProperty ticFontSizeProperty() {
        return ticFontSize;
    }

    public Color getBgColor() {
        return bgColorProperty().get();
    }

    public void setBgColor(Color value) {
        bgColorProperty().set(value);
    }

    public ColorProperty cross1ColorProperty() {
        return cross1Color;
    }

    public ColorProperty bgColorProperty() {
        return bgColor;
    }

    public ColorProperty axesColorProperty() {
        return axesColor;
    }

    public Color getCross0Color() {
        return cross0ColorProperty().get();
    }

    public void setCross0Color(Color value) {
        cross0ColorProperty().set(value);
    }

    public ColorProperty cross0ColorProperty() {
        return cross0Color;
    }

    public Color getCross1Color() {
        return cross1ColorProperty().get();
    }

    public void setCross1Color(Color value) {
        cross1ColorProperty().set(value);
    }

    public Color getAxesColor() {
        return axesColorProperty().get();
    }

    public void setAxesColor(Color value) {
        axesColorProperty().set(value);
    }

    public BooleanProperty gridProperty() {
        return grid;
    }

    public boolean getGrid() {
        return gridProperty().get();
    }

    public void setGrid(boolean value) {
        gridProperty().set(value);
    }

    public BooleanProperty regionsProperty() {
        return regions;
    }

    public boolean getRegions() {
        return regionsProperty().get();
    }

    public void setRegions(boolean value) {
        regionsProperty().set(value);
    }

    public BooleanProperty integralsProperty() {
        return integrals;
    }

    public boolean getIntegrals() {
        return integralsProperty().get();
    }
    public void setIntegrals(boolean value) {
        integralsProperty().set(value);
    }
    public BooleanProperty integralValuesProperty() {
        return integralValues;
    }

    public void setIntegralValues(boolean value) {
        integralValuesProperty().set(value);
    }

    public boolean getIntegralValues() {
        return integralValuesProperty().get();
    }


    public double getIntegralLowPos() {
        return integralLowPosProperty().get();
    }

    public void setIntegralLowPos(double value) {
        integralLowPosProperty().set(value);
    }

    public DoubleProperty integralLowPosProperty() {
        return integralLowPos;
    }

    public double getIntegralHighPos() {
        return integralHighPosProperty().get();
    }

    public void setIntegralHighPos(double value) {
        integralHighPosProperty().set(value);
    }

    public DoubleProperty integralHighPosProperty() {
        return integralHighPos;
    }

    public BooleanProperty titlesProperty() {
        return titles;
    }

    public boolean getTitles() {
        return titlesProperty().get();
    }

    public void setTitles(boolean value) {
        titlesProperty().set(value);
    }

    public BooleanProperty parametersProperty() {
        return parameters;
    }

    public boolean getParameters() {
        return parametersProperty().get();
    }

    public void setParameters(boolean value) {
        parametersProperty().set(value);
    }

    public double getAspectRatio() {
        return aspectRatioProperty().get();
    }

    public void setAspectRatio(double value) {
        aspectRatioProperty().set(value);
    }

    public DoubleProperty aspectRatioProperty() {
        return aspectRatio;
    }

    public BooleanProperty aspectProperty() {
        return aspect;
    }

    public boolean getAspect() {
        return aspectProperty().get();
    }

    public void setAspect(boolean value) {
        aspectProperty().set(value);
    }

    public double getStackX() {
        return stackXProperty().get();
    }

    public void setStackX(double value) {
        value = Math.min(1.00, Math.max(0.0, value));
        stackXProperty().set(value);
    }

    public DoubleProperty stackXProperty() {
        return stackX;
    }

    public double getStackY() {
        return stackYProperty().get();
    }

    public void setStackY(double value) {
        value = Math.min(1.00, Math.max(0.0, value));
        stackYProperty().set(value);
    }

    public DoubleProperty stackYProperty() {
        return stackY;
    }
}
