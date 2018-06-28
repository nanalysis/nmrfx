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

import java.lang.reflect.InvocationTargetException;
import org.nmrfx.processor.datasets.peaks.Multiplet;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.gui.PolyChart;
import static org.nmrfx.processor.gui.spectra.DrawPeaks.minHitSize;
import org.nmrfx.processor.gui.spectra.PeakDisplayParameters.DisplayTypes;
import static org.nmrfx.processor.gui.spectra.PeakDisplayParameters.LabelTypes.Number;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.apache.commons.beanutils.PropertyUtils;
import org.nmrfx.processor.datasets.peaks.ComplexCoupling;
import org.nmrfx.processor.datasets.peaks.Coupling;
import org.nmrfx.processor.datasets.peaks.CouplingPattern;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.datasets.peaks.PeakEvent;
import org.nmrfx.processor.datasets.peaks.PeakListener;
import org.nmrfx.processor.gui.controls.ConsoleUtil;

/**
 *
 * @author Bruce Johnson
 */
public class PeakListAttributes implements PeakListener {

    PeakList peakList;
    final DatasetAttributes dataAttr;
    final PolyChart chart;
    Optional<List<Peak>> peaksInRegion = Optional.empty();
    Optional<List<Multiplet>> multipletsInRegion = Optional.empty();
    Set<Peak> selectedPeaks = new HashSet<>();
    Set<MultipletSelection> selectedMultiplets = new HashSet<>();
    NMRAxis xAxis = null;
    NMRAxis yAxis = null;

    private ColorProperty onColor;

    public ColorProperty onColorProperty() {
        if (onColor == null) {
            onColor = new ColorProperty(this, "+color", Color.BLACK);
        }
        return onColor;
    }

    public void setOnColor(Color value) {
        onColorProperty().set(value);
    }

    public Color getOnColor() {
        return onColorProperty().get();
    }
    private ColorProperty offColor;

    public ColorProperty offColorProperty() {
        if (offColor == null) {
            offColor = new ColorProperty(this, "-color", Color.RED);
        }
        return offColor;
    }

    public void setOffColor(Color value) {
        offColorProperty().set(value);
    }

    public Color getOffColor() {
        return offColorProperty().get();
    }

    private BooleanProperty drawPeaks;

    public BooleanProperty drawPeaksProperty() {
        if (drawPeaks == null) {
            drawPeaks = new SimpleBooleanProperty(this, "on", true);
        }
        return drawPeaks;
    }

    public void setDrawPeaks(boolean value) {
        drawPeaksProperty().set(value);
    }

    public boolean getDrawPeaks() {
        return drawPeaksProperty().get();
    }

    private BooleanProperty simPeaks;

    public BooleanProperty simPeaksProperty() {
        if (simPeaks == null) {
            simPeaks = new SimpleBooleanProperty(this, "sim", false);
        }
        return simPeaks;
    }

    public void setSimPeaks(boolean value) {
        simPeaksProperty().set(value);
    }

    public boolean getSimPeaks() {
        return simPeaksProperty().get();
    }

    private ObjectProperty<PeakDisplayParameters.LabelTypes> peakLabelType;

    public final ObjectProperty<PeakDisplayParameters.LabelTypes> labelTypeProperty() {
        if (peakLabelType == null) {
            peakLabelType = new SimpleObjectProperty<PeakDisplayParameters.LabelTypes>(Number);
        }
        return this.peakLabelType;
    }

    public final PeakDisplayParameters.LabelTypes getLabelType() {
        return this.labelTypeProperty().get();
    }

    public final void setLabelType(final PeakDisplayParameters.LabelTypes labelType) {
        this.labelTypeProperty().set(labelType);
    }

    public final void setLabelType(final String type) {
        this.labelTypeProperty().set(PeakDisplayParameters.LabelTypes.valueOf(type));
    }

    private ObjectProperty<PeakDisplayParameters.DisplayTypes> displayType;

    public final ObjectProperty<PeakDisplayParameters.DisplayTypes> displayTypeProperty() {
        if (displayType == null) {
            displayType = new SimpleObjectProperty<PeakDisplayParameters.DisplayTypes>(DisplayTypes.Peak);
        }
        return this.displayType;
    }

    public final PeakDisplayParameters.DisplayTypes getDisplayType() {
        return this.displayTypeProperty().get();
    }

    public final void setDisplayType(final PeakDisplayParameters.DisplayTypes displayType) {
        this.displayTypeProperty().set(displayType);
    }

    public final void setDisplayType(final String type) {
        this.displayTypeProperty().set(PeakDisplayParameters.DisplayTypes.valueOf(type));
    }

    public PeakListAttributes(PolyChart chart, DatasetAttributes dataAttr, PeakList peakList) {
        this.chart = chart;
        this.dataAttr = dataAttr;
        this.peakList = peakList;
        setPeakListName(peakList.getName());
    }

    public DatasetAttributes getDatasetAttributes() {
        return dataAttr;
    }

    public PeakList getPeakList() {
        return peakList;
    }
    private StringProperty peakListName;

    public StringProperty peakListNameProperty() {
        if (peakListName == null) {
            peakListName = new SimpleStringProperty(this, "peakListName", "");
        }
        return peakListName;
    }

    public void setPeakList(PeakList peakList) {
        this.peakList = peakList;
        setPeakListName(peakList.getName());

    }

    public void setPeakListName(String value) {
        peakListNameProperty().set(value);
    }

    public String getPeakListName() {
        return peakListNameProperty().get();
    }

    double[][] getRegionLimits(DatasetAttributes dataAttr) {
        int nDataDim = dataAttr.nDim;
        double[][] limits = new double[nDataDim][2];
        for (int i = 0; i < nDataDim; i++) {
            NMRAxis axis = chart.getAxis(i);
            if (chart.getAxMode(i) == DatasetAttributes.AXMODE.PPM) {
                limits[i][0] = axis.getLowerBound();
                limits[i][1] = axis.getUpperBound();
            } else {
                double lb = axis.getLowerBound();
                double ub = axis.getUpperBound();
                if (Math.abs(lb - ub) < 0.5) {
                    lb = lb - 1.4;
                    ub = ub + 1.4;
                }
                limits[i][1] = DatasetAttributes.AXMODE.PPM.indexToValue(dataAttr, i, lb);
                limits[i][0] = DatasetAttributes.AXMODE.PPM.indexToValue(dataAttr, i, ub);
            }
        }
        return limits;

    }

    public int[] getPeakDim() {
        int nPeakDim = peakList.nDim;
        int nDataDim = dataAttr.nDim;
        int[] dim = new int[nDataDim];
        boolean[] used = new boolean[nPeakDim];
        int nMatch = 0;
        for (int i = 0; i < nDataDim; i++) {
            dim[i] = -1;
            for (int j = 0; j < nPeakDim; j++) {
                if (dataAttr.getLabel(i).equals(peakList.getSpectralDim(j).getDimName())) {
                    dim[i] = j;
                    used[j] = true;
                    nMatch++;
                    break;
                }
            }
        }
        for (int i = 0; i < nDataDim; i++) {
            if (dim[i] == -1) {
                for (int j = 0; j < nPeakDim; j++) {
                    if (!used[j]) {
                        String dNuc = dataAttr.getDataset().getNucleus(i).getNumberName();
                        String pNuc = peakList.getSpectralDim(j).getNucleus();
                        if (dNuc.equals(pNuc)) {
                            dim[i] = j;
                            used[j] = true;
                            nMatch++;
                            break;
                        }
                    }
                }
            }
        }
        return dim;
    }

    public void clearPeaksInRegion() {
        peaksInRegion = Optional.empty();
        multipletsInRegion = Optional.empty();
    }

    public List<Peak> getPeaksInRegion() {
        if (!peaksInRegion.isPresent()) {
            findPeaksInRegion();
        }
        return peaksInRegion.get();
    }

    public List<Multiplet> getMultipletsInRegion() {
        if (!multipletsInRegion.isPresent()) {
            findMultipletsInRegion();
        }
        return multipletsInRegion.get();
    }

    public boolean clearSelectedPeaks() {
        boolean hadPeaks = !selectedPeaks.isEmpty() || !selectedMultiplets.isEmpty();
        selectedPeaks.clear();
        selectedMultiplets.clear();
        return hadPeaks;
    }

    public Set<Peak> getSelectedPeaks() {
        return selectedPeaks;
    }

    public Set<MultipletSelection> getSelectedMultiplets() {
        return selectedMultiplets;
    }

    public void findPeaksInRegion() {
        peaksInRegion = Optional.empty();
        if ((peakList != null) && (peakList.peaks() != null)) {
            double[][] limits = getRegionLimits(dataAttr);
            int[] peakDim = getPeakDim();
            List<Peak> peaks = peakList.peaks()
                    .stream()
                    .parallel()
                    .filter(peak -> peak.inRegion(limits, null, peakDim))
                    .collect(Collectors.toList());
            peaksInRegion = Optional.of(peaks);
        }
    }

    public void findPeaksInRegion(double[][] crossLimits) {
        peaksInRegion = Optional.empty();
        if ((peakList != null) && (peakList.peaks() != null)) {
            double[][] limits = getRegionLimits(dataAttr);
            limits[0][0] = crossLimits[0][0];
            limits[0][1] = crossLimits[0][1];
            if (limits.length > 1) {
                limits[1][0] = crossLimits[1][0];
                limits[1][1] = crossLimits[1][1];
            }
            int[] peakDim = getPeakDim();
            List<Peak> peaks = peakList.peaks()
                    .stream()
                    .parallel()
                    .filter(peak -> peak.inRegion(limits, null, peakDim))
                    .collect(Collectors.toList());
            peaksInRegion = Optional.of(peaks);
        }
    }

    public List<Peak> selectPeaksInRegion(double[][] crossLimits) {
        if ((peakList != null) && (peakList.peaks() != null)) {
            double[][] limits = getRegionLimits(dataAttr);
            limits[0][0] = crossLimits[0][0];
            limits[0][1] = crossLimits[0][1];
            if (limits.length > 1) {
                limits[1][0] = crossLimits[1][0];
                limits[1][1] = crossLimits[1][1];
            }
            int[] peakDim = getPeakDim();
            List<Peak> peaks = peakList.peaks()
                    .stream()
                    .parallel()
                    .filter(peak -> peak.inRegion(limits, null, peakDim))
                    .collect(Collectors.toList());
            selectedPeaks.addAll(peaks);
            return (peaks);
        }
        return new ArrayList<Peak>();
    }

    public void findMultipletsInRegion() {
        double[][] limits = getRegionLimits(dataAttr);
        int[] peakDim = getPeakDim();
        List<Multiplet> multiplets = peakList.getMultiplets()
                .stream()
                .parallel()
                .filter(multiplet -> multiplet.inRegion(limits, null, peakDim))
                .collect(Collectors.toList());
        multipletsInRegion = Optional.of(multiplets);
    }

    public Optional<Peak> hitPeak(DrawPeaks drawPeaks, double pickX, double pickY) {
        Optional<Peak> hit = Optional.empty();
        if (peaksInRegion.isPresent()) {
            int[] peakDim = getPeakDim();
            xAxis = (NMRAxis) chart.getXAxis();
            yAxis = (NMRAxis) chart.getYAxis();
            if (peakList.nDim > 1) {
                hit = peaksInRegion.get().stream().parallel().filter(peak -> peak.getStatus() >= 0)
                        .filter((peak) -> pick2DPeak(peak, pickX, pickY)).findFirst();
            } else {
                hit = peaksInRegion.get().stream().parallel().filter(peak -> peak.getStatus() >= 0)
                        .filter((peak) -> drawPeaks.pick1DPeak(this, peak, peakDim, pickX, pickY)).findFirst();
            }
        }
        return hit;
    }

    public void selectPeak(DrawPeaks drawPeaks, double pickX, double pickY, boolean append) {
        Optional<Peak> hit = Optional.empty();
        if (peaksInRegion.isPresent()) {
            int[] peakDim = getPeakDim();
            xAxis = (NMRAxis) chart.getXAxis();
            yAxis = (NMRAxis) chart.getYAxis();
            if (peakList.nDim > 1) {
                hit = peaksInRegion.get().stream().parallel().filter(peak -> peak.getStatus() >= 0)
                        .filter((peak) -> pick2DPeak(peak, pickX, pickY)).findFirst();
                if (hit.isPresent()) {
                    if (!selectedPeaks.contains(hit.get()) && !append) {
                        selectedPeaks.clear();
                    }
                    selectedPeaks.add(hit.get());
                } else {
                    if (!append) {
                        selectedPeaks.clear();
                    }
                }
            } else {
                //    protected boolean pick1DPeak(PeakListAttributes peakAttr, Peak peak, int[] dim, double hitX, double hitY) {

                hit = peaksInRegion.get().stream().parallel().filter(peak -> peak.getStatus() >= 0)
                        // .filter((peak) -> pick1DPeak(peak, pickX, pickY)).findFirst();
                        .filter((peak) -> drawPeaks.pick1DPeak(this, peak, peakDim, pickX, pickY)).findFirst();
                if (hit.isPresent()) {
                    if (!selectedPeaks.contains(hit.get()) && !append) {
                        selectedPeaks.clear();
                    }
                    selectedPeaks.add(hit.get());
                } else {
                    if (!append) {
                        selectedPeaks.clear();
                    }
                }
                if (multipletsInRegion.isPresent()) {
                    for (Multiplet multiplet : multipletsInRegion.get()) {
                        Optional<MultipletSelection> lineHit = drawPeaks.pick1DMultiplet(this, peakDim, multiplet, pickX, pickY);
                        if (lineHit.isPresent()) {
                            MultipletSelection mSel = lineHit.get();
                            if (!selectedMultiplets.contains(mSel) && !append) {
                                selectedMultiplets.clear();
                            }
                            selectedMultiplets.add(mSel);
                            break;
                        } else {
                            if (!append) {
                                selectedMultiplets.clear();
                            }
                        }

                    }
                }
            }

        }
    }

    public void selectPeaks(DrawPeaks drawPeaks, double pickX, double pickY, boolean append) {
        if (peaksInRegion.isPresent()) {
            int[] peakDim = getPeakDim();
            xAxis = (NMRAxis) chart.getXAxis();
            yAxis = (NMRAxis) chart.getYAxis();
            if (!append) {
                selectedPeaks.clear();
            }
            if (peakList.nDim > 1) {
                List<Peak> peaks = peaksInRegion.get().stream().parallel()
                        .filter((peak) -> pick2DPeak(peak, pickX, pickY))
                        .filter((peak) -> !selectedPeaks.contains(peak))
                        .collect(Collectors.toList());
                selectedPeaks.addAll(peaks);
            } else {
                List<Peak> peaks = peaksInRegion.get().stream().parallel()
                        .filter((peak) -> drawPeaks.pick1DPeak(this, peak, peakDim, pickX, pickY))
                        .filter((peak) -> !selectedPeaks.contains(peak))
                        .collect(Collectors.toList());
                selectedPeaks.addAll(peaks);

            }

        }
    }

    public void movePeak(Peak peak, double[] oldValue, double[] newValue) {
        int[] peakDims = getPeakDim();
        int nDim = Math.min(peakDims.length, oldValue.length);
        for (int i = 0; i < nDim; i++) {
            PeakDim peakDim = peak.peakDim[peakDims[i]];
            if (!peakDim.isFrozen()) {
                // check if this is peakDim is coupled in pattern.  If so don't move it
                //   you have to move the multiplet tree items to change coupling values
                if (peakDim.hasMultiplet()) {
                    Multiplet multiplet = peakDim.getMultiplet();
                    if (multiplet.isCoupled()) {
                        Coupling coupling = multiplet.getCoupling();
                        if (coupling instanceof CouplingPattern) {
                            continue;
                        }
                    }
                }
                double oldAxisValue = getAxisValue(i, oldValue[i]);
                double newAxisValue = getAxisValue(i, newValue[i]);
                double delta = newAxisValue - oldAxisValue;
                double shift = peakDim.getChemShiftValue();
                peakDim.setChemShiftValue((float) (shift + delta));
            }
        }
    }

    public void resizePeak(Peak peak, double[] oldValue, double[] newValue) {
        int[] peakDim = getPeakDim();
        int nDim = Math.min(peakDim.length, oldValue.length);
        for (int i = 0; i < nDim; i++) {
            double newAxisValue = getAxisValue(i, newValue[i]);
            double bound = peak.peakDim[peakDim[i]].getBoundsValue();
            double shift = peak.peakDim[peakDim[i]].getChemShiftValue();
            double newWidth = 2 * Math.abs(newAxisValue - shift);

            peak.peakDim[peakDim[i]].setBoundsValue((float) newWidth);
            double scale = newWidth / bound;
            double width = peak.peakDim[peakDim[i]].getLineWidthValue();
            peak.peakDim[peakDim[i]].setLineWidthValue((float) (width * scale));
        }
    }

    public void moveMultipletCoupling(MultipletSelection mSel, double[] oldValue, double[] newValue) {
        int[] peakDims = getPeakDim();
        int nDim = Math.min(peakDims.length, oldValue.length);
        Multiplet multiplet = mSel.getMultiplet();
        int iDim = 0;
        int mLine = mSel.getLine();
        double oldAxisValue = getAxisValue(iDim, oldValue[iDim]);
        double newAxisValue = getAxisValue(iDim, newValue[iDim]);
        double delta = newAxisValue - oldAxisValue;
        if (multiplet.isCoupled()) {
            Coupling coupling = multiplet.getCoupling();
            if (coupling instanceof CouplingPattern) {
                CouplingPattern cPat = (CouplingPattern) coupling;
                double mCenter = mSel.getCenter();
                double[] values = cPat.getValues();
                double deltaHz;
                if (mSel.getEdge() > mCenter) {
                    deltaHz = delta * multiplet.getPeakDim().getSpectralDimObj().getSf();
                } else {
                    deltaHz = -delta * multiplet.getPeakDim().getSpectralDimObj().getSf();
                }

                values[mLine] += deltaHz;
                cPat.adjustCouplings(mLine, values[mLine]);
                multiplet.setMultipletComponentValues();
            }
        } else if (multiplet.isGenericMultiplet()) {
            Coupling coupling = multiplet.getCoupling();
            ComplexCoupling cPat = (ComplexCoupling) coupling;
            PeakDim peakDim = multiplet.getPeakDims().get(mLine);
            double shift = peakDim.getChemShiftValue();
            peakDim.setChemShiftValue((float) (shift + delta));
            multiplet.updateCenter();
        } else {
            PeakDim peakDim = multiplet.getPeakDim();
            double shift = peakDim.getChemShiftValue();
            peakDim.setChemShiftValue((float) (shift + delta));
        }
    }

    protected boolean pick1DPeak(Peak peak, double x,
            double y) {
        double height = yAxis.getHeight();
        y = height - y;
        int[] peakDim = getPeakDim();
        double bou = peak.peakDim[0].getBoundsValue();
        double ctr = peak.peakDim[0].getChemShiftValue();
        Rectangle box = getBox(ctr, bou, 20);
        boolean result = box.contains(x, y);
        return result;
    }

    private boolean pick2DPeak(Peak peak, double x,
            double y) {
        double[] ctr = {0.0, 0.0};
        double[] bou = {0.0, 0.0};
        int[] peakDim = getPeakDim();

        bou[0] = peak.peakDim[peakDim[0]].getBoundsValue();
        bou[1] = peak.peakDim[peakDim[1]].getBoundsValue();
        ctr[0] = peak.peakDim[peakDim[0]].getChemShiftValue();
        ctr[1] = peak.peakDim[peakDim[1]].getChemShiftValue();
        Rectangle box = getBox(ctr, bou);
        boolean result = box.contains(x, y);
//        System.out.println(box.toString() + " " + x + " " + y + " " + result);

        if (!result) {
            int growWidth = 0;
            int growHeight = 0;
            int width = (int) box.getWidth();
            if (width < minHitSize) {
                growWidth = minHitSize - width;
            }
            int height = (int) box.getHeight();
            if (height < minHitSize) {
                growHeight = minHitSize - height;
            }
            // fixme why are we doing this (from old code) and should it grow symmetrically
            // gues we try to hit small rect for selectivity, then expand if no hit
            if ((growWidth > 0) || (growHeight > 0)) {
                box.setWidth(growWidth);
                box.setX(box.getX() - growWidth / 2);
                box.setHeight(growHeight);
                box.setY(box.getY() - growHeight / 2);

                result = box.contains(x, y);
            }
        }
        return result;
    }

    double getAxisValue(int iDim, double x) {
        if (iDim == 0) {
            x = xAxis.getValueForDisplay(x).doubleValue();
        } else {
            x = yAxis.getValueForDisplay(x).doubleValue();

        }
        return x;
    }

    double getYAxisValue(double y) {
        y = yAxis.getValueForDisplay(y).doubleValue();
        return y;
    }

    Rectangle getBox(double[] ctr, double[] bou) {

        double x1 = xAxis.getDisplayPosition(ctr[0] + (bou[0] / 2.0));
        double x2 = xAxis.getDisplayPosition(ctr[0] - (bou[0] / 2.0));
        double y1 = yAxis.getDisplayPosition(ctr[1] + (bou[1] / 2.0));
        double y2 = yAxis.getDisplayPosition(ctr[1] - (bou[1] / 2.0));
        double xMin;
        double xWidth;
        if (x1 < x2) {
            xMin = x1;
            xWidth = x2 - x1;
        } else {
            xMin = x2;
            xWidth = x1 - x2;
        }
        double yMin;
        double yHeight;
        if (y1 < y2) {
            yMin = x1;
            yHeight = y2 - y1;
        } else {
            yMin = y2;
            yHeight = y1 - y2;
        }
        return new Rectangle(xMin, yMin, xWidth, yHeight);
    }

    Rectangle getBox(double ctr, double bou, double height) {

        double x1 = xAxis.getDisplayPosition(ctr + (bou / 2.0));
        double x2 = xAxis.getDisplayPosition(ctr - (bou / 2.0));
        double y1 = 0.0;
        double y2 = height;
        double xMin;
        double xWidth;
        if (x1 < x2) {
            xMin = x1;
            xWidth = x2 - x1;
        } else {
            xMin = x2;
            xWidth = x1 - x2;
        }
        double yMin;
        double yHeight;
        if (y1 < y2) {
            yMin = y1;
            yHeight = y2 - y1;
        } else {
            yMin = y2;
            yHeight = y1 - y2;
        }
        return new Rectangle(xMin, yMin, xWidth, yHeight);
    }

    @Override
    public void peakListChanged(PeakEvent peakEvent) {
        peaksInRegion = Optional.empty();
    }

    public void config(String name, Object value) {
        ConsoleUtil.runOnFxThread(() -> {
            try {
                switch (name) {
                    case "labelType":
                        setLabelType(value.toString());
                        break;
                    case "displayType":
                        setDisplayType(value.toString());
                        break;
                    case "simPeaks":
                        setSimPeaks(Boolean.valueOf(value.toString()));
                        break;
                    case "drawPeaks":
                        setDrawPeaks(Boolean.valueOf(value.toString()));
                        break;
                    default:
                        PropertyUtils.setSimpleProperty(this, name, value);
                        break;
                }
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
                Logger.getLogger(PeakListAttributes.class.getName()).log(Level.SEVERE, null, ex);
            }
        });

    }

    public Map<String, Object> config() {
        Map<String, Object> data = new HashMap<>();
        String[] beanNames = {"onColor", "offColor", "drawPeaks", "simPeaks", "labelType", "displayType"};
        for (String beanName : beanNames) {
            try {
                if (beanName.contains("Color")) {
                    Object colObj = PropertyUtils.getSimpleProperty(this, beanName);
                    if (colObj instanceof Color) {
                        String colorName = colObj.toString();
                        data.put(beanName, colorName);
                    }
                } else {
                    Object obj = PropertyUtils.getSimpleProperty(this, beanName);
                    if (obj == null) {
                        data.put(beanName, null);
                    } else {
                        data.put(beanName, obj.toString());
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
                Logger.getLogger(PeakListAttributes.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return data;
    }

}
