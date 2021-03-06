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

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.scene.paint.Color;
import org.apache.commons.beanutils.PropertyUtils;
import org.nmrfx.processor.gui.spectra.ColorProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;


/**
 *
 * @author brucejohnson
 */
public class ChartProperties {
    private static final Logger log = LoggerFactory.getLogger(ChartProperties.class);

    final private PolyChart polyChart;

    private IntegerProperty leftBorderSize;
    private IntegerProperty rightBorderSize;
    private IntegerProperty topBorderSize;
    private IntegerProperty bottomBorderSize;
    private BooleanProperty intensityAxis;
    private DoubleProperty labelFontSize;
    private DoubleProperty ticFontSize;
    private ColorProperty cross0Color;
    private ColorProperty cross1Color;
    private ColorProperty axesColor;
    private ColorProperty bgColor;
    private BooleanProperty grid;
    private BooleanProperty regions;
    private BooleanProperty integrals;
    private DoubleProperty integralLowPos;
    private DoubleProperty integralHighPos;
    private DoubleProperty aspectRatio;
    private BooleanProperty aspect;
    private BooleanProperty titles;

    public ChartProperties(PolyChart chart) {
        this.polyChart = chart;
    }

    public void copyTo(PolyChart destChart) {
        ChartProperties destProps = destChart.chartProps;
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
        destProps.setTitles(getTitles());
    }

    public int getLeftBorderSize() {
        return leftBorderSizeProperty().get();
    }

    public void setLeftBorderSize(int value) {
        leftBorderSizeProperty().set(value);
    }

    public IntegerProperty leftBorderSizeProperty() {
        if (leftBorderSize == null) {
            leftBorderSize = new SimpleIntegerProperty(polyChart, "leftBorderSize", 0);
        }
        return leftBorderSize;
    }

    public int getRightBorderSize() {
        return rightBorderSizeProperty().get();
    }

    public void setRightBorderSize(int value) {
        rightBorderSizeProperty().set(value);
    }

    public IntegerProperty rightBorderSizeProperty() {
        if (rightBorderSize == null) {
            rightBorderSize = new SimpleIntegerProperty(polyChart, "rightBorderSize", 7);
        }
        return rightBorderSize;
    }

    public int getTopBorderSize() {
        return topBorderSizeProperty().get();
    }

    public void setTopBorderSize(int value) {
        topBorderSizeProperty().set(value);
    }

    public IntegerProperty topBorderSizeProperty() {
        if (topBorderSize == null) {
            topBorderSize = new SimpleIntegerProperty(polyChart, "topBorderSize", 7);
        }
        return topBorderSize;
    }

    public int getBottomBorderSize() {
        return bottomBorderSizeProperty().get();
    }

    public void setBottomBorderSize(int value) {
        bottomBorderSizeProperty().set(value);
    }

    public IntegerProperty bottomBorderSizeProperty() {
        if (bottomBorderSize == null) {
            bottomBorderSize = new SimpleIntegerProperty(polyChart, "bottomBorderSize", 0);
        }
        return bottomBorderSize;
    }

    public BooleanProperty intensityAxisProperty() {
        if (intensityAxis == null) {
            intensityAxis = new SimpleBooleanProperty(polyChart, "onedAxis", false);
        }
        return intensityAxis;
    }

    public void setIntensityAxis(boolean value) {
        intensityAxisProperty().set(value);
    }

    public boolean getIntensityAxis() {
        return intensityAxisProperty().get();
    }
    public double getLabelFontSize() {
        return labelFontSizeProperty().get();
    }

    public void setLabelFontSize(double value) {
        labelFontSizeProperty().set(value);
    }

    public DoubleProperty labelFontSizeProperty() {
        if (labelFontSize == null) {
            labelFontSize = new SimpleDoubleProperty(polyChart, "labelFontSize", PreferencesController.getLabelFontSize());
        }
        return labelFontSize;
    }

    public double getTicFontSize() {
        return ticFontSizeProperty().get();
    }

    public void setTicFontSize(double value) {
        ticFontSizeProperty().set(value);
    }

    public DoubleProperty ticFontSizeProperty() {
        if (ticFontSize == null) {
            ticFontSize = new SimpleDoubleProperty(polyChart, "ticFontSize", PreferencesController.getTickFontSize());
        }
        return ticFontSize;
    }

    public Color getBgColor() {
        return bgColorProperty().get();
    }

    public void setCross0Color(Color value) {
        cross0ColorProperty().set(value);
    }

    public void setAxesColor(Color value) {
        axesColorProperty().set(value);
    }

    public ColorProperty cross1ColorProperty() {
        if (cross1Color == null) {
            cross1Color = new ColorProperty(polyChart, "cross1Color", null);
        }
        return cross1Color;
    }

    public ColorProperty bgColorProperty() {
        if (bgColor == null) {
            bgColor = new ColorProperty(polyChart, "bgColor", null);
        }
        return bgColor;
    }

    public ColorProperty axesColorProperty() {
        if (axesColor == null) {
            axesColor = new ColorProperty(polyChart, "axesColor", null);
        }
        return axesColor;
    }

    public Color getCross0Color() {
        return cross0ColorProperty().get();
    }

    public void setCross1Color(Color value) {
        cross1ColorProperty().set(value);
    }

    public ColorProperty cross0ColorProperty() {
        if (cross0Color == null) {
            cross0Color = new ColorProperty(polyChart, "cross0Color", null);
        }
        return cross0Color;
    }

    public void setBgColor(Color value) {
        bgColorProperty().set(value);
    }

    public Color getCross1Color() {
        return cross1ColorProperty().get();
    }

    public Color getAxesColor() {
        return axesColorProperty().get();
    }

    public BooleanProperty gridProperty() {
        if (grid == null) {
            grid = new SimpleBooleanProperty(polyChart, "grid", false);
        }
        return grid;
    }

    public void setGrid(boolean value) {
        gridProperty().set(value);
    }

    public boolean getGrid() {
        return gridProperty().get();
    }

    public BooleanProperty regionsProperty() {
        if (regions == null) {
            regions = new SimpleBooleanProperty(polyChart, "regions", false);
        }
        return regions;
    }

    public void setRegions(boolean value) {
        regionsProperty().set(value);
    }

    public boolean getRegions() {
        return regionsProperty().get();
    }

    public BooleanProperty integralsProperty() {
        if (integrals == null) {
            integrals = new SimpleBooleanProperty(polyChart, "integrals", false);
        }
        return integrals;
    }

    public void setIntegrals(boolean value) {
        integralsProperty().set(value);
    }

    public boolean getIntegrals() {
        return integralsProperty().get();
    }

    public double getIntegralLowPos() {
        return integralLowPosProperty().get();
    }

    public void setIntegralLowPos(double value) {
        integralLowPosProperty().set(value);
    }

    public DoubleProperty integralLowPosProperty() {
        if (integralLowPos == null) {
            integralLowPos = new SimpleDoubleProperty(polyChart, "integralLowPos", 0.8);
        }
        return integralLowPos;
    }

    public double getIntegralHighPos() {
        return integralHighPosProperty().get();
    }

    public void setIntegralHighPos(double value) {
        integralHighPosProperty().set(value);
    }

    public DoubleProperty integralHighPosProperty() {
        if (integralHighPos == null) {
            integralHighPos = new SimpleDoubleProperty(polyChart, "integralHighPos", 0.95);
        }
        return integralHighPos;
    }

    public BooleanProperty titlesProperty() {
        if (titles == null) {
            titles = new SimpleBooleanProperty(polyChart, "titles", false);
        }
        return titles;
    }

    public void setTitles(boolean value) {
        titlesProperty().set(value);
    }

    public boolean getTitles() {
        return titlesProperty().get();
    }

    public double getAspectRatio() {
        return aspectRatioProperty().get();
    }

    public void setAspectRatio(double value) {
        aspectRatioProperty().set(value);
    }

    public DoubleProperty aspectRatioProperty() {
        if (aspectRatio == null) {
            aspectRatio = new SimpleDoubleProperty(polyChart, "aspectRatio", 1.0);
        }
        return aspectRatio;
    }

    public BooleanProperty aspectProperty() {
        if (aspect == null) {
            aspect = new SimpleBooleanProperty(polyChart, "aspect", false);
        }
        return aspect;
    }

    public void setAspect(boolean value) {
        aspectProperty().set(value);
    }

    public boolean getAspect() {
        return aspectProperty().get();
    }

    public void config(String name, Object value) {
        if (Platform.isFxApplicationThread()) {
            try {
                PropertyUtils.setSimpleProperty(this, name, value);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
                log.error(ex.getMessage(), ex);
            }
        } else {
            Platform.runLater(() -> {
                        try {
                            PropertyUtils.setProperty(this, name, value);
                        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
                            log.error(ex.getMessage(), ex);
                        }
                    }
            );
        }
    }

    public Map<String, Object> config() {
        Map<String, Object> data = new HashMap<>();
        String[] beanNames = {"ticFontSize", "labelFontSize", "bgColor",
            "axesColor", "cross0Color", "cross1Color", "grid", "intensityAxis",
            "leftBorderSize", "rightBorderSize",
            "topBorderSize", "bottomBorderSize", "regions", "integrals",
            "integralLowPos", "integralHighPos", "titles", "aspect", "aspectRatio"};
        for (String beanName : beanNames) {
            try {
                if (beanName.contains("Color")) {
                    Object colObj = PropertyUtils.getSimpleProperty(this, beanName);
                    if (colObj instanceof Color) {
                        String colorName = GUIScripter.toRGBCode((Color) colObj);
                        data.put(beanName, colorName);
                    }
                } else {
                    data.put(beanName, PropertyUtils.getSimpleProperty(this, beanName));
                }
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
                log.error(ex.getMessage(), ex);
            }
        }
        return data;
    }

}
