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
package org.nmrfx.processor.gui.spectra;

import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.input.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.nmrfx.analyst.gui.AnalystApp;
import org.nmrfx.processor.gui.CanvasCursor;
import org.nmrfx.processor.gui.KeyMonitor;
import org.nmrfx.processor.gui.PeakPicking;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.events.DataFormatEventHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Bruce Johnson
 */
public class KeyBindings {
    private static final Map<DataFormat, DataFormatEventHandler> dataFormatHandlers = new HashMap<>();
    private static final Map<String, BiConsumer<String, PolyChart>> globalKeyActionMap = new HashMap<>();

    KeyMonitor keyMonitor = new KeyMonitor();
    PolyChart chart;
    Map<String, Consumer> keyActionMap = new HashMap<>();
    private final KeyCodeCombination pasteKeyCodeCombination = new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN);

    public KeyBindings(PolyChart chart) {
        this.chart = chart;
    }

    /**
     * Adds the provided DataFormat event handler to the dataFormatHandler map.
     *
     * @param dataFormat The DataFormat.
     * @param handler    The DataFormat handler.
     */
    public static void registerCanvasDataFormatHandler(DataFormat dataFormat, DataFormatEventHandler handler) {
        dataFormatHandlers.put(dataFormat, handler);
    }

    public static void registerGlobalKeyAction(String keyString, BiConsumer<String, PolyChart> action) {
        // add firstchar so that key processing doesn't clear keyMonitor before a two key string is typed
        String firstChar = keyString.substring(0, 1);
        if (!globalKeyActionMap.containsKey(firstChar)) {
            globalKeyActionMap.put(firstChar, null);
        }
        globalKeyActionMap.put(keyString, action);
    }

    public void registerKeyAction(String keyString, Consumer<PolyChart> action) {
        // add firstchar so that key processing doesn't clear keyMonitor before a two key string is typed
        String firstChar = keyString.substring(0, 1);
        if (!keyActionMap.containsKey(firstChar)) {
            keyActionMap.put(firstChar, null);
        }
        keyActionMap.put(keyString, action);
    }

    public void deregisterKeyAction(String keyString) {
        keyActionMap.remove(keyString);
    }

    public void keyPressed(KeyEvent keyEvent) {
        KeyCode code = keyEvent.getCode();
        if (null != code) {
            switch (code) {
                case DOWN -> {
                    handleDownAction(keyEvent);
                    keyEvent.consume();
                }
                case UP -> {
                    handleUpAction(keyEvent);
                    keyEvent.consume();
                }
                case RIGHT -> {
                    chart.incrementPlane(3, 1);
                    keyEvent.consume();
                }
                case LEFT -> {
                    chart.incrementPlane(3, -1);
                    keyEvent.consume();
                }
                case ENTER, ESCAPE -> {
                    keyMonitor.complete();
                    keyEvent.consume();
                }
                case DELETE, BACK_SPACE -> {
                    keyMonitor.complete();
                    keyEvent.consume();
                    chart.deleteSelectedItems();
                    chart.refresh();
                }
                case V -> {
                    // Paste command is shortcut + V, so make sure the KeyEvent matches that combination
                    if (pasteKeyCodeCombination.match(keyEvent)) {
                        handlePasteAction();
                    }
                    keyEvent.consume();
                }
                default -> {
                }
            }
        }
    }

    public void keyReleased(KeyEvent keyEvent) {
    }

    public void keyTyped(KeyEvent keyEvent) {
        Pattern pattern = Pattern.compile("jz([0-9]+)");
        String keyChar = keyEvent.getCharacter();
        if (keyChar.equals(" ")) {
            String keyString = keyMonitor.getKeyString();
            if (keyString.isEmpty()) {
                keyMonitor.clear();
                chart.focus();
                chart.showHitPeak(chart.getMouseX(), chart.getMouseY());
                return;
            }
        }
        keyMonitor.storeKey(keyChar);
        String keyString = keyMonitor.getKeyString();
        String shortString = keyString.substring(0, Math.min(2, keyString.length()));
        keyString = keyString.trim();
        // note always break on a single character that is used in a two character sequence
        // otherwise the keystring will be cleared and the multiple key event will never be processed
        if (keyActionMap.containsKey(shortString)) {
            Consumer action = keyActionMap.get(shortString);
            if (action != null) {
                action.accept(chart);
                keyMonitor.clear();
            }
            return;
        } else if (globalKeyActionMap.containsKey(shortString)) {
            BiConsumer<String, PolyChart> action = globalKeyActionMap.get(shortString);
            if (action != null) {
                action.accept(shortString, chart);
                keyMonitor.clear();
            }
            return;
        }
        switch (shortString) {
            case "a" -> {
            }
            case "aa", "as" -> {
                List<DatasetAttributes> activeData = chart.getActiveDatasetAttributes();
                if (activeData.size() == 1) {
                    DatasetAttributes datasetAttr = activeData.get(0);
                    double pickX = chart.getAxes().getX().getValueForDisplay(chart.getMouseX()).doubleValue();
                    double pickY = chart.getAxes().getY().getValueForDisplay(chart.getMouseY()).doubleValue();
                    PeakPicking.pickAtPosition(chart, datasetAttr, pickX, pickY, shortString.equals("as"), false);
                    keyMonitor.clear();
                    chart.drawPeakLists(true);
                } else {
                    keyMonitor.clear();
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setContentText("Must have one dataset displayed");
                    alert.showAndWait();
                }
            }
            case "c" -> {
            }
            case "c1" -> keyMonitor.clear();
            case "c3" -> keyMonitor.clear();
            case "cc" -> {
                chart.getFXMLController().setCursor(CanvasCursor.CROSSHAIR.getCursor());
                keyMonitor.clear();
            }
            case "cs" -> {
                chart.getFXMLController().setCursor(CanvasCursor.SELECTOR.getCursor());
                keyMonitor.clear();
            }
            case "ca" -> {
                chart.getFXMLController().setCursor(CanvasCursor.PEAK.getCursor());
                keyMonitor.clear();
            }
            case "cr" -> {
                chart.getFXMLController().setCursor(CanvasCursor.REGION.getCursor());
                keyMonitor.clear();
            }
            case "p" -> {
            }
            case "pf" -> {
                chart.fitPeakLists(-1);
                keyMonitor.clear();
            }
            case "pt" -> {
                chart.tweakPeakLists();
                keyMonitor.clear();
            }
            case "u" -> {
            }
            case "uu" -> {
                chart.getFXMLController().undo();
                keyMonitor.clear();
            }
            case "ur" -> {
                chart.getFXMLController().redo();
                keyMonitor.clear();
            }
            case "s" -> {
            }
            case "sc" -> {
                chart.setToBuffer();
                keyMonitor.clear();
            }
            case "sd" -> {
                chart.getFXMLController().removeSelectedChart();
                keyMonitor.clear();
            }
            case "sg" -> {
                chart.getFXMLController().exportGraphics();
                keyMonitor.clear();
            }
            case "sn" -> {
                Stage stage = new Stage(StageStyle.DECORATED);
                AnalystApp.getFXMLControllerManager().newController(stage);
                keyMonitor.clear();
            }
            case "sv" -> {
                chart.pasteFromBuffer();
                keyMonitor.clear();
            }
            case "sx" -> {
                chart.sliceView(Orientation.VERTICAL);
                keyMonitor.clear();
            }
            case "sy" -> {
                chart.sliceView(Orientation.HORIZONTAL);
                keyMonitor.clear();
            }
            case "v" -> {
            }
            case "vc" -> {
                chart.center();
                keyMonitor.clear();
            }
            case "vd" -> {
                chart.copyChartLimits();
                keyMonitor.clear();
            }
            case "vv" -> {
                chart.pasteChartLimits();
                keyMonitor.clear();
            }
            case "ve" -> {
                chart.expand();
                keyMonitor.clear();
            }
            case "vf" -> {
                chart.full();
                keyMonitor.clear();
            }
            case "vi" -> {
                chart.zoom(1.2);
                keyMonitor.clear();
            }
            case "vo" -> {
                chart.zoom(0.8);
                keyMonitor.clear();
            }
            case "vr" -> {
                chart.copyLimits();
                keyMonitor.clear();
            }
            case "vs" -> {
                chart.swapView();
                keyMonitor.clear();
            }
            case "vp" -> {
                chart.popView();
                keyMonitor.clear();
            }
            case "vu" -> {
                chart.unifyLimits();
                keyMonitor.clear();
            }
            case "vw" -> {
                chart.pasteLimits();
                keyMonitor.clear();
            }
            case "vx", "vy", "vz", "va" -> {
                if (keyString.length() > 2) {
                    String axisName = shortString.substring(1, 2).toUpperCase();
                    String dimNum = keyString.substring(2, 3);
                    if (Character.isDigit(dimNum.charAt(0))) {
                        int iDim = Integer.parseInt(dimNum) - 1;
                        if (iDim < chart.getDataset().getNDim()) {
                            String dimName = chart.getDataset().getLabel(iDim);
                            chart.getFXMLController().setDim(axisName, dimName);
                            chart.refresh();
                        }
                    }
                    keyMonitor.clear();
                }
            }
            case "j" -> {
            }
            case "jx", "jy", "jz" -> {
                // fixme what about a,b,c..
                int iDim = keyString.charAt(1) - 'x';
                switch (keyString.substring(2)) {
                    case "f" -> {
                        chart.full(iDim);
                        chart.refresh();
                        keyMonitor.clear();
                    }
                    case "m" -> {
                        if (iDim > 1) {
                            chart.gotoMaxPlane();
                            chart.refresh();
                        }
                        keyMonitor.clear();
                    }
                    case "c" -> {
                        chart.center(iDim);
                        chart.refresh();
                        keyMonitor.clear();
                    }
                    case "b" -> {
                        if (iDim > 1) {
                            chart.firstPlane(2);
                            chart.refresh();
                        }
                        keyMonitor.clear();
                    }
                    case "t" -> {
                        if (iDim > 1) {
                            chart.lastPlane(2);
                            chart.refresh();
                        }
                        keyMonitor.clear();
                    }
                    default -> {
                        if (keyString.length() > 2) {
                            if (keyMonitor.isComplete()) {
                                if (iDim > 1) {
                                    Matcher matcher = pattern.matcher(keyString);
                                    if (matcher.matches()) {
                                        String group = matcher.group(1);
                                        int plane = Integer.parseInt(group);
                                        chart.getAxes().setMinMax(2, plane, plane);
                                        chart.refresh();
                                    }
                                }
                                keyMonitor.clear();
                            }
                        }
                    }
                }
            }
            default -> keyMonitor.clear();
        }

    }

    private void handleUpAction(KeyEvent keyEvent) {
        if (keyEvent.isShiftDown()) {
            List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
            dataAttrs.forEach(d -> d.rotateDim(1, 1));
            chart.getFXMLController().updateAttrDims();
            chart.full();
            chart.focus();
        } else {
            if (chart.is1D()) {
                chart.incrementRow(1);
            } else {
                chart.incrementPlane(2, 1);
            }
        }
    }

    private void handleDownAction(KeyEvent keyEvent) {
        if (keyEvent.isShiftDown()) {
            List<DatasetAttributes> dataAttrs = chart.getDatasetAttributes();
            dataAttrs.forEach(d -> d.rotateDim(1, -1));
            chart.getFXMLController().updateAttrDims();
            chart.full();
            chart.focus();
        } else {
            if (chart.is1D()) {
                chart.incrementRow(-1);
            } else {
                chart.incrementPlane(2, -1);
            }
        }
    }

    /**
     * Checks the clipboard and calls the appropriate DataFormatHandler.
     */
    private void handlePasteAction() {
        final Clipboard clipboard = Clipboard.getSystemClipboard();
        // Handlers should be arranged in highest to lowest priority as the clipboard may contain multiple DataFormats
        DataFormat dfMDCLT = getDataFormat("MDLCT");
        if (clipboard.getContentTypes().contains(getDataFormat("MDLCT")) && dataFormatHandlers.containsKey(dfMDCLT)) {
            dataFormatHandlers.get(dfMDCLT).handlePaste(clipboard.getContent(dfMDCLT), chart);
        } else if (clipboard.getContentTypes().contains(DataFormat.PLAIN_TEXT) && dataFormatHandlers.containsKey(DataFormat.PLAIN_TEXT)) {
            dataFormatHandlers.get(DataFormat.PLAIN_TEXT).handlePaste(clipboard.getContent(DataFormat.PLAIN_TEXT), chart);
        }
    }

    private DataFormat getDataFormat(String format) {
        DataFormat dataFormat = DataFormat.lookupMimeType(format);
        if (dataFormat == null) {
            dataFormat = new DataFormat(format);
        }
        return dataFormat;
    }
}
