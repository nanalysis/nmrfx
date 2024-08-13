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

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.nmrfx.chart.Axis;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.peaks.*;
import org.nmrfx.peaks.events.PeakEvent;
import org.nmrfx.peaks.events.PeakListener;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.PreferencesController;
import org.nmrfx.processor.gui.spectra.PeakDisplayParameters.ColorTypes;
import org.nmrfx.processor.gui.spectra.PeakDisplayParameters.DisplayTypes;
import org.nmrfx.utils.properties.ColorProperty;
import org.nmrfx.utils.properties.PublicPropertyContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static org.nmrfx.processor.gui.spectra.DrawPeaks.MIN_HIT_SIZE;

/**
 * @author Bruce Johnson
 */
public class PeakListAttributes implements PeakListener, PublicPropertyContainer {
    private static final Logger log = LoggerFactory.getLogger(PeakListAttributes.class);
    public static final String LABEL_TYPE = "labelType";
    public static final String DISPLAY_TYPE = "displayType";
    public static final String SIM_PEAKS = "simPeaks";
    public static final String NPLANES = "nplanes";
    public static final String FONT_SIZE = "font size";
    public static final String DRAW_PEAKS = "drawPeaks";
    public static final String ON_COLOR = "onColor";
    public static final String OFF_COLOR = "offColor";

    private final IntegerProperty nplanes = new SimpleIntegerProperty(this, NPLANES, 0);
    private final IntegerProperty fontSize = new SimpleIntegerProperty(this, FONT_SIZE, PreferencesController.getPeakFontSize());
    private final ColorProperty onColor = new ColorProperty(this, ON_COLOR, Color.BLACK);
    private final ColorProperty offColor = new ColorProperty(this, OFF_COLOR, Color.RED);
    private final BooleanProperty drawPeaks = new SimpleBooleanProperty(this, DRAW_PEAKS, true);
    private final BooleanProperty simPeaks = new SimpleBooleanProperty(this, SIM_PEAKS, false);
    private final BooleanProperty drawLinks = new SimpleBooleanProperty(this, "drawLinks", false);
    private final ObjectProperty<PeakDisplayParameters.LabelTypes> labelType = new SimpleObjectProperty<>(this, LABEL_TYPE, PeakDisplayParameters.LabelTypes.Label);
    private final ObjectProperty<PeakDisplayParameters.DisplayTypes> displayType = new SimpleObjectProperty<>(this, DISPLAY_TYPE, DisplayTypes.Peak);
    private final ObjectProperty<PeakDisplayParameters.ColorTypes> colorType = new SimpleObjectProperty<>(this, "colorType", ColorTypes.Plane);
    private final StringProperty peakListName = new SimpleStringProperty(this, "peakListName", "");

    PeakList peakList;
    final DatasetAttributes dataAttr;
    final PolyChart chart;
    Optional<List<Peak>> peaksInRegion = Optional.empty();
    Optional<List<Multiplet>> multipletsInRegion = Optional.empty();
    Set<Peak> selectedPeaks = new HashSet<>();
    Set<MultipletSelection> selectedMultiplets = FXCollections.observableSet();
    Axis xAxis = null;
    Axis yAxis = null;
    double[][] foldLimits = null;

    public IntegerProperty nplanesProperty() {
        return nplanes;
    }

    public void setNplanes(Integer value) {
        nplanesProperty().set(value);
    }

    public Integer getNplanes() {
        return nplanesProperty().get();
    }

    public IntegerProperty fontSizeProperty() {
        return fontSize;
    }
    public void setFontSize(Integer value) {
        fontSizeProperty().set(value);
    }
    public Integer getFontSize() {
        return fontSizeProperty().get();
    }

    public ColorProperty onColorProperty() {
        return onColor;
    }

    public void setOnColor(Color value) {
        onColorProperty().set(value);
    }

    public Color getOnColor() {
        return onColorProperty().get();
    }

    public ColorProperty offColorProperty() {
        return offColor;
    }

    public void setOffColor(Color value) {
        offColorProperty().set(value);
    }

    public Color getOffColor() {
        return offColorProperty().get();
    }

    public BooleanProperty drawPeaksProperty() {
        return drawPeaks;
    }

    public void setDrawPeaks(boolean value) {
        drawPeaksProperty().set(value);
    }

    public boolean getDrawPeaks() {
        return drawPeaksProperty().get();
    }

    public BooleanProperty simPeaksProperty() {
        return simPeaks;
    }

    public void setSimPeaks(boolean value) {
        simPeaksProperty().set(value);
    }

    public boolean getSimPeaks() {
        return simPeaksProperty().get();
    }

    public BooleanProperty drawLinksProperty() {
        return drawLinks;
    }

    public void setDrawLinks(boolean value) {
        drawLinksProperty().set(value);
    }

    public boolean getDrawLinks() {
        return drawLinksProperty().get();
    }


    public final ObjectProperty<PeakDisplayParameters.LabelTypes> labelTypeProperty() {
        return this.labelType;
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


    public final ObjectProperty<PeakDisplayParameters.DisplayTypes> displayTypeProperty() {
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

    public final ObjectProperty<PeakDisplayParameters.ColorTypes> colorTypeProperty() {
        return this.colorType;
    }

    public final PeakDisplayParameters.ColorTypes getColorType() {
        return this.colorTypeProperty().get();
    }

    public final void setColorType(final PeakDisplayParameters.ColorTypes colorType) {
        this.colorTypeProperty().set(colorType);
    }

    public final void setColorType(final String type) {
        this.colorTypeProperty().set(PeakDisplayParameters.ColorTypes.valueOf(type));
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

    public StringProperty peakListNameProperty() {
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

    @Override
    public String toString() {
        return getPeakListName();
    }

    void updateFoldingLimits(DatasetAttributes dataAttr) {
        int nDataDim = dataAttr.nDim;
        foldLimits = new double[nDataDim][2];
        for (int i = 0; i < nDataDim; i++) {
            DatasetBase dataset = dataAttr.getDataset();
            foldLimits[i] = dataset.getLimits(dataAttr.getDim(i));
        }
    }

    double[][] getRegionLimits(DatasetAttributes dataAttr) {
        int nDataDim = dataAttr.nDim;
        double[][] limits = new double[nDataDim][2];
        for (int i = 0; i < nDataDim; i++) {
            Axis axis = chart.getAxes().get(i);
            if (chart.getAxes().getMode(i) == DatasetAttributes.AXMODE.PPM) {
                limits[i][0] = axis.getLowerBound();
                limits[i][1] = axis.getUpperBound();
                double lb = chart.getAxes().getMode(i).getIndexD(dataAttr, i, limits[i][0]);
                double ub = chart.getAxes().getMode(i).getIndexD(dataAttr, i, limits[i][1]);
                if (Math.abs(lb - ub) < 0.5) {
                    lb = lb - ((double) getNplanes()) - 0.5;
                    ub = ub + ((double) getNplanes()) + 0.5;
                    limits[i][1] = DatasetAttributes.AXMODE.PPM.indexToValue(dataAttr, i, lb);
                    limits[i][0] = DatasetAttributes.AXMODE.PPM.indexToValue(dataAttr, i, ub);
                }
            } else {
                double lb = axis.getLowerBound();
                double ub = axis.getUpperBound();
                if (Math.abs(lb - ub) < 0.5) {
                    lb = lb - ((double) getNplanes()) - 0.5;
                    ub = ub + ((double) getNplanes()) + 0.5;
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
        updateFoldingLimits(dataAttr);
        if ((peakList != null) && (peakList.peaks() != null)) {
            double[][] limits = getRegionLimits(dataAttr);
            int[] peakDim = getPeakDim();
            List<Peak> peaks = peakList.peaks()
                    .stream()
                    .parallel()
                    .filter(peak -> peak.inRegion(limits, foldLimits, peakDim))
                    .collect(Collectors.toList());
            peaksInRegion = Optional.of(peaks);
        }
    }

    public void findPeaksInRegion(double[][] crossLimits) {
        peaksInRegion = Optional.empty();
        updateFoldingLimits(dataAttr);
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
                    .filter(peak -> peak.inRegion(limits, foldLimits, peakDim))
                    .collect(Collectors.toList());
            peaksInRegion = Optional.of(peaks);
        }
    }

    public List<Peak> selectPeaksInRegion(double[][] crossLimits) {
        if ((peakList != null) && (peakList.peaks() != null)) {
            updateFoldingLimits(dataAttr);
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
                    .filter(peak -> peak.inRegion(limits, foldLimits, peakDim))
                    .collect(Collectors.toList());
            selectedPeaks.addAll(peaks);
            return (peaks);
        }
        return new ArrayList<Peak>();
    }

    public double foldShift(int iDim, double shift) {
        if (foldLimits != null) {
            shift = Dataset.foldPPM(shift, foldLimits[iDim]);
        }
        return shift;
    }

    public Optional<Peak> hitPeak(DrawPeaks drawPeaks, double pickX, double pickY) {
        Optional<Peak> hit = Optional.empty();
        if (peaksInRegion.isPresent()) {
            int[] peakDim = getPeakDim();
            xAxis = chart.getAxes().getX();
            yAxis = chart.getAxes().getY();
            if ((peakList.nDim > 1) && !chart.is1D()) {
                hit = peaksInRegion.get().stream().parallel().filter(peak -> peak.getStatus() >= 0)
                        .filter((peak) -> pick2DPeak(peak, pickX, pickY)).findFirst();
            } else {
                hit = peaksInRegion.get().stream().parallel().filter(peak -> peak.getStatus() >= 0)
                        .filter((peak) -> drawPeaks.pick1DPeak(this, peak, peakDim, pickX, pickY)).findFirst();
            }
        }
        return hit;
    }

    public Optional<MultipletSelection> hitMultiplet(DrawPeaks drawPeaks, double pickX, double pickY) {
        if (peakList.getNDim() > 1) {
            return Optional.empty();
        }
        Optional<MultipletSelection> hit = drawPeaks.hitMultipletLabel(pickX, pickY);
        if (hit.isPresent()) {
            return hit;
        } else {
            return hitMultipletLine(drawPeaks, pickX, pickY);
        }
    }

    public Optional<MultipletSelection> hitMultipletLine(DrawPeaks drawPeaks, double pickX, double pickY) {
        Optional<MultipletSelection> hit = Optional.empty();
        if (peaksInRegion.isPresent()) {
            int[] peakDim = getPeakDim();
            xAxis = chart.getAxes().getX();
            yAxis = chart.getAxes().getY();
            if (peakList.nDim == 1) {
                var pickResult = peaksInRegion.get().stream().filter(peak -> peak.getStatus() >= 0).
                        map(peak -> peak.getPeakDim(0).getMultiplet())
                        .map((multiplet) -> drawPeaks.pick1DMultiplet(this, peakDim, multiplet, pickX, pickY)).filter(hitMulti -> hitMulti.isPresent()).findFirst();
                if (pickResult.isPresent()) {
                    hit = pickResult.get();
                }
            }
        }
        return hit;
    }

    public void selectPeak(DrawPeaks drawPeaks, double pickX, double pickY, boolean append) {
        Optional<Peak> hit = Optional.empty();
        if (peaksInRegion.isPresent()) {
            int[] peakDim = getPeakDim();
            xAxis = chart.getAxes().getX();
            yAxis = chart.getAxes().getY();
            if ((peakList.nDim > 1) && !chart.is1D()) {
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
                if (true) {  // fixme only if multiplet display on
                    for (Peak peak : peaksInRegion.get()) {
                        Multiplet multiplet = peak.getPeakDim(0).getMultiplet();
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
                    chart.updateSelectedMultiplets();
                }
            }

        }
    }

    public void selectPeaks(DrawPeaks drawPeaks, double pickX, double pickY, boolean append) {
        if (peaksInRegion.isPresent()) {
            int[] peakDim = getPeakDim();
            xAxis = chart.getAxes().getX();
            yAxis = chart.getAxes().getY();
            if (!append) {
                selectedPeaks.clear();
            }
            if ((peakList.nDim > 1) && !chart.is1D()) {
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
            if (peakDims[i] >= 0) {
                PeakDim peakDim = peak.peakDims[peakDims[i]];
                if (!peakDim.isFrozen()) {
                    double oldAxisValue = getAxisValue(i, oldValue[i]);
                    double newAxisValue = getAxisValue(i, newValue[i]);
                    double delta = newAxisValue - oldAxisValue;
                    double shift = peakDim.getChemShiftValue();
                    peakDim.setChemShiftValue((float) (shift + delta));
                }
            }
        }
    }

    public void resizePeak(Peak peak, double[] oldValue, double[] newValue) {
        int[] peakDims = getPeakDim();
        int nDim = Math.min(peakDims.length, oldValue.length);
        for (int i = 0; i < nDim; i++) {
            if (peakDims[i] >= 0) {
                double newAxisValue = getAxisValue(i, newValue[i]);
                double bound = peak.peakDims[peakDims[i]].getBoundsValue();
                double shift = peak.peakDims[peakDims[i]].getChemShiftValue();
                double newWidth = 2 * Math.abs(newAxisValue - shift);

                peak.peakDims[peakDims[i]].setBoundsValue((float) newWidth);
                double scale = newWidth / bound;
                double width = peak.peakDims[peakDims[i]].getLineWidthValue();
                peak.peakDims[peakDims[i]].setLineWidthValue((float) (width * scale));
            }
        }
    }

    public void moveMultipletCoupling(MultipletSelection mSel, double[] oldValue, double[] newValue) {
        int[] peakDims = getPeakDim();
        int nDim = Math.min(peakDims.length, oldValue.length);
        Multiplet multiplet = mSel.getMultiplet();
        int iDim = 0;
        int mLine = mSel.getLine();
        if (mLine < 0) {
            return;
        }
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
            }
        } else if (multiplet.isGenericMultiplet()) {
            Coupling coupling = multiplet.getCoupling();
            ComplexCoupling cPat = (ComplexCoupling) coupling;
            List<AbsMultipletComponent> comps = cPat.getAbsComponentList();
            AbsMultipletComponent activeComp = comps.get(mLine);
            activeComp.setOffset(activeComp.getOffset() + delta);
            comps.set(mLine, activeComp);
            multiplet.updateCoupling(comps);
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
        double bou = peak.peakDims[0].getBoundsValue();
        double ctr = peak.peakDims[0].getChemShiftValue();
        Rectangle box = getBox(ctr, bou, 20);
        boolean result = box.contains(x, y);
        return result;
    }

    private boolean pick2DPeak(Peak peak, double x,
                               double y) {
        double[] ctr = {0.0, 0.0};
        double[] bou = {0.0, 0.0};
        int[] peakDim = getPeakDim();
        boolean ok = true;
        for (int i=0;i<2;i++) {
            int pDim = peakDim[i];
            if ((pDim >= 0) && (pDim < peak.peakDims.length)) {
                bou[i] = peak.peakDims[pDim].getBoundsValue();
                ctr[i] = peak.peakDims[pDim].getChemShiftValue();
                ctr[i] = foldShift(i, ctr[i]);
            } else {
                ok = false;
                break;
            }
        }
        if (!ok) {
            return false;
        }

        Rectangle box = getBox(ctr, bou);
        boolean result = box.contains(x, y);

        if (!result) {
            int growWidth = 0;
            int growHeight = 0;
            int width = (int) box.getWidth();
            if (width < MIN_HIT_SIZE) {
                growWidth = MIN_HIT_SIZE - width;
            }
            int height = (int) box.getHeight();
            if (height < MIN_HIT_SIZE) {
                growHeight = MIN_HIT_SIZE - height;
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

    public void copyTo(PeakListAttributes peakAttr) {
        peakAttr.setLabelType(getLabelType());
        peakAttr.setDisplayType(getDisplayType());
        peakAttr.setSimPeaks(getSimPeaks());
        peakAttr.setNplanes(getNplanes());
        peakAttr.setDrawPeaks(getDrawPeaks());
        peakAttr.setColorType(getColorType());
        peakAttr.setDrawLinks(getDrawLinks());
        peakAttr.setOffColor(getOffColor());
        peakAttr.setOnColor(getOnColor());
        peakAttr.setFontSize(getFontSize());
    }

    @Override
    public Collection<Property<?>> getPublicProperties() {
        return Set.of(onColor, offColor, drawPeaks, nplanes, simPeaks, labelType, displayType, fontSize);
    }

    /**
     * Set a property from its name and value.
     * The default implementation can't be used here: some strings are converted to specific enumerations
     *
     * @param name  the property name
     * @param value the new value
     */
    @Override
    public void setPublicPropertyValue(String name, Object value) {
        Fx.runOnFxThread(() -> {
            switch (name) {
                case LABEL_TYPE -> setLabelType(value.toString());
                case DISPLAY_TYPE -> setDisplayType(value.toString());
                case SIM_PEAKS -> setSimPeaks(Boolean.parseBoolean(value.toString()));
                case NPLANES -> setNplanes(Integer.valueOf(value.toString()));
                case FONT_SIZE -> setFontSize(Integer.valueOf(value.toString()));
                case DRAW_PEAKS -> setDrawPeaks(Boolean.parseBoolean(value.toString()));
                case ON_COLOR -> onColorProperty().set((Color) value);
                case OFF_COLOR -> offColorProperty().set((Color) value);
                default -> log.warn("Trying to set {} to {} but property isn't publicly exposed", name, value);
            }
        });
    }

    /**
     * Get a map of all public properties name and their current value.
     * The default implementation can't be used here: enumeration properties are converted to string
     *
     * @return public properties and their values
     */
    @Override
    public Map<String, Object> getPublicPropertiesValues() {
        Map<String, Object> map = new HashMap<>();
        for (var property : getPublicProperties()) {
            if (property instanceof ColorProperty colorProperty) {
                // special conversion for colors
                String colorName = colorProperty.getColorAsRGB();
                if (colorName != null) {
                    map.put(property.getName(), colorName);
                }
            } else {
                // This differs from the default implementation: data is converted to string.
                // Not sure if this actually makes any difference.
                Object obj = property.getValue();
                if (obj == null) {
                    map.put(property.getName(), null);
                } else {
                    map.put(property.getName(), obj.toString());
                }
            }
        }
        return map;
    }
}
