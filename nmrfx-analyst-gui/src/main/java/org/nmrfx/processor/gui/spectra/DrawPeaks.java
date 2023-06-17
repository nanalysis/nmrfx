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

/*
 * PeakRenderer.java
 *
 * Created on December 12, 2004, 10:00 AM
 */
package org.nmrfx.processor.gui.spectra;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Affine;
import org.apache.commons.collections4.list.TreeList;
import org.nmrfx.chart.Axis;
import org.nmrfx.graphicsio.GraphicsContextInterface;
import org.nmrfx.graphicsio.GraphicsIOException;
import org.nmrfx.peaks.*;
import org.nmrfx.peaks.Peak.Corner;
import org.nmrfx.processor.datasets.peaks.LineShapes;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.PeakDisplayParameters.DisplayTypes;
import org.nmrfx.processor.gui.spectra.PeakDisplayParameters.LabelTypes;
import org.nmrfx.processor.gui.utils.GUIColorUtils;
import org.nmrfx.utilities.Format;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.nmrfx.processor.gui.spectra.PeakDisplayParameters.LabelTypes.PPM;

/**
 * @author brucejohnson
 */
public class DrawPeaks {
    private static final Logger log = LoggerFactory.getLogger(DrawPeaks.class);

    public static final int MIN_HIT_SIZE = 5;
    private static final int N_REGIONS = 32;
    private static final double WIDTH_LIMIT = 0.0001;
    private static final double PEAK_2_D_STROKE = 1.0;

    double frOffset = 0.05;

    Axis xAxis;
    Axis yAxis;

    int jmode = 0;
    int disDim = 0;
    int peakDisType = 0;
    int multipletLabelType = PeakDisplayParameters.MULTIPLET_LABEL_SUMMARY;
    boolean treeOn = false;
    boolean peakDisOn = true;
    boolean peakDisOff = true;
    double peak1DStroke = PEAK_2_D_STROKE;
    double deltaY = 12.0;

    HashSet[] regions = null;
    Color selectFill = new Color(1.0f, 1.0f, 0.0f, 0.4f);
    private boolean multipletMode = false;
    List<PeakBox> lastTextBoxes = new TreeList<>();
    GraphicsContext g2;

    public DrawPeaks(PolyChart chart, GraphicsContext graphics) {
        this.g2 = graphics;
        regions = new HashSet[N_REGIONS];

        for (int i = 0; i < regions.length; i++) {
            regions[i] = new HashSet();
        }
        xAxis = chart.getAxes().getX();
        yAxis = chart.getAxes().getY();
    }

    class PeakBox {
        Bounds bounds;
        Peak peak;

        PeakBox(Bounds bounds, Peak peak) {
            this.bounds = bounds;
            this.peak = peak;
        }

        boolean intersects(Bounds testBounds) {
            return this.bounds.intersects(testBounds);
        }

        boolean contains(double x, double y) {
            return this.bounds.contains(x, y);
        }

        Peak getPeak() {
            return peak;
        }

        Bounds getBounds() {
            return bounds;
        }

        Multiplet getMultiplet() {
            return peak.getPeakDim(0).getMultiplet();
        }
    }

    public void resetDrawList() {
        for (int i = 0; i < regions.length; i++) {
            regions[i].clear();
        }
    }

    public void clear1DBounds() {
        lastTextBoxes.clear();
    }

    public void setMultipletMode(boolean state) {
        multipletMode = state;
    }

    public synchronized boolean pickPeak(PeakListAttributes peakAttr, DatasetAttributes dataAttr, GraphicsContextInterface g2, Peak peak, int[] dim,
                                         double[] offset, double x, double y) throws GraphicsIOException {
        int nPeakDim = peak.peakList.nDim;
        boolean result = false;
        if ((nPeakDim > 1)) {
            result = pick2DPeak(peakAttr, dataAttr, g2, dim, peak, x, y);
        } else {
            return pick1DPeak(peakAttr, peak, dim, x, y);
        }
        return result;
    }


    public void drawSimSum(GraphicsContextInterface g2, ArrayList peaks, int[] dim) throws GraphicsIOException {
        Peak1DRep peakRep = new Peak1DRep(g2, dim[0], peaks);
    }

    public synchronized void drawPeak(PeakListAttributes peakAttr, GraphicsContextInterface g2, Peak peak, int[] dim,
                                      double[] offset, boolean selected) throws GraphicsIOException {
        int nPeakDim = peak.peakList.nDim;
        int colorMode = setColor(peakAttr, g2, peak, offset);
        if (((colorMode == 0) && !peakDisOn) || ((colorMode != 0) && !peakDisOff)) {
            return;
        }

        if ((nPeakDim > 1) && !peakAttr.chart.is1D()) {
            draw2DPeak(peakAttr, g2, dim, peak, false, selected);
        } else {
            draw1DPeak(peakAttr, g2, dim, peak, colorMode, selected);
        }
    }

    public synchronized void drawMultiplet(PeakListAttributes peakAttr, GraphicsContextInterface g2, Multiplet multiplet, int[] dim,
                                           double[] offset, boolean selected, int line) throws GraphicsIOException {
        if ((multiplet != null)) {
            PeakDim peakDim = multiplet.getPeakDim();
            if (peakDim != null) {
                Peak peak = multiplet.getPeakDim().getPeak();
                int nPeakDim = peak.peakList.nDim;
                int colorMode = setColor(peakAttr, g2, peak, offset);
                if (((colorMode == 0) && !peakDisOn) || ((colorMode != 0) && !peakDisOff)) {
                    return;
                }
                if ((disDim == 0) || (nPeakDim <= 1)) {
                    draw1DMultiplet(peakAttr, g2, dim, multiplet, colorMode, selected, line);
                }
            }
        }
    }

    String getResidueStringFromLabel(String label) {
        String res = "";
        int periodPos = label.indexOf(".");
        if (periodPos >= 0) {
            int colonPos = label.indexOf(':');
            res = label.substring(colonPos + 1, periodPos);
        }

        return res;
    }

    int getResidueFromLabel(String label) {
        int periodPos = label.indexOf(".");
        int res = Integer.MAX_VALUE;

        if (periodPos >= 0) {
            int colonPos = label.indexOf(':');

            try {
                if (!Character.isDigit(label.charAt(colonPos + 1))) {
                    colonPos++;
                }
                if ((colonPos + 1) < periodPos) {
                    res = Integer.parseInt(label.substring(colonPos + 1, periodPos));
                }
            } catch (NumberFormatException nfE) {
                log.warn("Unable to parse residue.", nfE);
            }
        }

        return res;
    }

    String getAtomFromLabel(String label) {
        int periodPos = label.indexOf(".");
        String atomLabel = "";

        if (periodPos >= 0) {
            atomLabel = label.substring(periodPos + 1);
        } else {
            atomLabel = label.trim();
        }

        return atomLabel;
    }

    String getLabel(Peak peak, PeakListAttributes peakAttr) {
        StringBuilder labels = new StringBuilder("");
        String label = null;
        int nPeakDim = peak.peakList.nDim;
        String plab = null;

        switch (peakAttr.getLabelType()) {
            case Number:
                switch (peak.getType()) {
                    case Peak.COMPOUND:
                        label = (String.valueOf(peak.getIdNum()));
                        break;
                    case Peak.SOLVENT:
                        label = "S";
                        break;
                    case Peak.ARTIFACT:
                        label = "A";
                        break;
                    case Peak.MINOR:
                        label = "M";
                        break;
                    case Peak.IMPURITY:
                        label = "I";
                        break;
                    case Peak.CHEMSHIFT_REF:
                        label = "R";
                        break;
                    case Peak.QUANTITY_REF:
                        label = "Q";
                        break;
                    case Peak.COMBO_REF:
                        label = "R";
                        break;
                    case Peak.WATER:
                        label = "W";
                        break;
                    default:
                        label = (String.valueOf(peak.getIdNum()));
                        break;
                }

                break;

            case Label:

                boolean iAsg = false;
                int i;

                for (i = 0; i < nPeakDim; i++) {
                    if (i > 0) {
                        labels.append(" ");
                    }

                    if ((peak.peakDims[i].getLabel().length() == 0)
                            || peak.peakDims[i].getLabel().equals("?")) {
                        labels.append("?").append(String.valueOf(peak.getIdNum()));
                    } else {
                        iAsg = true;
                        labels.append(peak.peakDims[i].getLabel());
                    }
                }

                if (!iAsg) {
                    label = "#" + String.valueOf(peak.getIdNum());
                } else {
                    label = labels.toString();
                }

                break;

            case Residue:

                int lastres = -1;
                int residue = 0;

                for (i = 0; i < nPeakDim; i++) {
                    plab = peak.peakDims[i].getLabel().trim();

                    if ((plab.length() != 0) && !plab.startsWith("?")) {
                        residue = getResidueFromLabel(plab);

                        if (residue != Integer.MAX_VALUE) {
                            if (residue != lastres) {
                                lastres = residue;

                                if (i != 0) {
                                    labels.append("-");
                                }

                                labels.append(String.valueOf(residue));
                            }
                        } else {
                            labels.append("?");
                        }
                    }
                }

                label = labels.toString().trim();

                if (label.length() == 0) {
                    label = "#" + String.valueOf(peak.getIdNum());
                }

                break;
            case SglResidue:

                for (i = 0; i < nPeakDim; i++) {
                    plab = peak.peakDims[0].getLabel().trim();
                    if ((plab.length() != 0) && !plab.startsWith("?")) {
                        label = getResidueStringFromLabel(plab);
                        if ((label != null) && (label.length() != 0)) {
                            break;
                        }
                    }
                }

                if ((label == null) || (label.length() == 0)) {
                    label = "#" + String.valueOf(peak.getIdNum());
                }

                break;

            case Atom:

                for (i = 0; i < nPeakDim; i++) {
                    if (i > 0) {
                        labels.append(" ");
                    }

                    plab = peak.peakDims[i].getLabel().trim();

                    if ((plab.length() != 0) && !plab.startsWith("?")) {
                        labels.append(getAtomFromLabel(plab));
                    }
                }

                label = labels.toString().trim();

                if (label.length() == 0) {
                    label = "#" + (String.valueOf(peak.getIdNum()));
                }

                break;

            case Cluster:

                label = "c" + peak.getClusterOriginPeakID(-1);
                break;

            case User:

                for (i = 0; i < nPeakDim; i++) {
                    labels.append(peak.peakDims[i].getUser()).append(" ");
                }

                label = labels.toString().trim();

                break;

            case Comment:
                label = String.valueOf(peak.getComment());

                break;

            case Summary:
                double normVal = 0;

                // FIXME  make precision in ctr a function of dig resolution  sw/sfrq/size
                if (multipletMode && peak.peakDims[0].hasMultiplet()) {
                    String couplings = peak.peakDims[0].getMultiplet().getCouplingsAsSimpleString();
                    if (peak.peakList.scale > 0.0) {
                        normVal = peak.peakDims[0].getMultiplet().getVolume() / peak.peakList.scale;
                    }
                    label = Format.format2(normVal) + " " + peak.peakDims[0].getMultiplet().getMultiplicity() + " " + couplings
                            + "\n" + Format.format4(peak.peakDims[0].getMultiplet().getCenter());
                } else {
                    double ppm = peak.peakDims[0].getChemShiftValue();
                    if (ppm > 20.0) {
                        label = Format.format2(ppm);
                    } else {
                        label = Format.format3(ppm);
                    }
                }

                break;
            case PPM:
                double ppm = peak.peakDims[0].getChemShiftValue();
                if (ppm > 20.0) {
                    label = Format.format2(ppm);
                } else {
                    label = Format.format3(ppm);
                }
                break;
            case None:
                label = "";

                break;
            default:
                label = "";
        }

        return label;
    }

    String getMultipletLabel(Multiplet multiplet) {
        StringBuilder labels = new StringBuilder("");
        String label = null;
        Peak peak = multiplet.getPeakDim().getPeak();

        int nPeakDim = peak.peakList.nDim;
        String plab = null;

        switch (multipletLabelType) {
            case PeakDisplayParameters.MULTIPLET_LABEL_NUMBER:

                switch (peak.getType()) {
                    case Peak.COMPOUND:
                        label = (String.valueOf(peak.getIdNum()));
                        break;
                    case Peak.SOLVENT:
                        label = "S";
                        break;
                    case Peak.ARTIFACT:
                        label = "A";
                        break;
                    case Peak.MINOR:
                        label = "M";
                        break;
                    case Peak.IMPURITY:
                        label = "I";
                        break;
                    case Peak.CHEMSHIFT_REF:
                        label = "R";
                        break;
                    case Peak.QUANTITY_REF:
                        label = "Q";
                        break;
                    case Peak.COMBO_REF:
                        label = "R";
                        break;
                    case Peak.WATER:
                        label = "W";
                        break;
                    default:
                        label = (String.valueOf(peak.getIdNum()));
                        break;
                }

                break;

            case PeakDisplayParameters.MULTIPLET_LABEL_ATOM: //FIXME

                for (int i = 0; i < nPeakDim; i++) {
                    if (i > 0) {
                        labels.append(" ");
                    }

                    plab = peak.peakDims[i].getLabel().trim();

                    if ((plab.length() != 0) && !plab.startsWith("?")) {
                        labels.append(getAtomFromLabel(plab));
                    }
                }

                label = labels.toString().trim();

                if (label.length() == 0) {
                    label = "#" + (String.valueOf(peak.getIdNum()));
                }

                break;

            case PeakDisplayParameters.MULTIPLET_LABEL_SUMMARY:
                double normVal = 0;

                // FIXME  make precision in ctr a function of dig resolution  sw/sfrq/size
                String couplings = multiplet.getCouplingsAsSimpleString();
                if (peak.peakList.scale > 0.0) {
                    normVal = multiplet.getVolume() / peak.peakList.scale;
                }
                StringBuilder sBuilder = new StringBuilder();
                sBuilder.append(Format.format2(normVal)).append(' ').
                        append(multiplet.getMultiplicity()).append('\n');
                if ((couplings != null) && (couplings.length() > 0)) {
                    sBuilder.append(couplings).append('\n');
                }
                sBuilder.append((Format.format3(multiplet.getCenter())));
                label = sBuilder.toString();

                break;
            case PeakDisplayParameters.MULTIPLET_LABEL_PPM:
                double ppm = multiplet.getCenter();
                if (ppm > 20.0) {
                    label = Format.format2(ppm);
                } else {
                    label = Format.format3(ppm);
                }
                break;
            case PeakDisplayParameters.MULTIPLET_LABEL_NONE:
                label = "";

                break;
            default:
                label = "";
        }

        return label;
    }

    int setColor(PeakListAttributes peakAttr, GraphicsContextInterface g2, Peak peak, double[] offset) throws GraphicsIOException {
        int nPeakDim = peak.peakList.nDim;
        int colorMode = 0;
        Color color = peakAttr.getOnColor();

        switch (peakAttr.getColorType()) {
            case Plane:

                for (int i = 2; i < offset.length; i++) {
                    if (Math.abs(offset[i]) > 0.5) {
                        colorMode = 1;

                        break;
                    }
                }

                break;

            case Assigned:

                boolean iAsg = true;

                for (int i = 0; i < nPeakDim; i++) {
                    if ((peak.peakDims[i].getLabel().length() == 0)
                            || peak.peakDims[i].getLabel().equals("?")) {
                        iAsg = false;

                        break;
                    }
                }

                if (!iAsg) {
                    colorMode = 1;
                }

                break;

            case Error:

                boolean isOK = true;

                for (int i = 0; i < nPeakDim; i++) {
                    if ((peak.peakDims[i].getError()[0] != '+')
                            || (peak.peakDims[i].getError()[1] != '+')) {
                        isOK = false;

                        break;
                    }
                }

                if (!isOK) {
                    colorMode = 1;
                }

                break;

            case Status:

                if (peak.getStatus() != 0) {
                    colorMode = 1;
                }

                break;

            case Intensity:

                if (peak.getIntensity() < 0) {
                    colorMode = 1;
                }

                break;
            default:
                colorMode = 0;
        }

        if (colorMode == 1) {
            color = peakAttr.getOffColor();
        }
        int[] peakColor = peak.getColor();
        if (peakColor != null) {
            color = GUIColorUtils.toColor(peakColor);
        }
        g2.setStroke(color);
        g2.setFill(color);
        return colorMode;
    }

    protected void drawSelectionIndicator(GraphicsContextInterface g2, String label, double angle, double x1, double y1) throws GraphicsIOException {
        Bounds bounds = measureText(label, g2.getFont(), angle, x1, y1);
        double border = 2;
        Paint current = g2.getFill();
        g2.setFill(selectFill);
        g2.fillRect(bounds.getMinX() - border, bounds.getMinY() - border, bounds.getWidth() + 2 * border, bounds.getHeight() + 2 * border);
        g2.setFill(current);
        g2.strokeRect(bounds.getMinX() - border, bounds.getMinY() - border, bounds.getWidth() + 2 * border, bounds.getHeight() + 2 * border);
    }

    protected boolean pick1DPeak(PeakListAttributes peakAttr, Peak peak, int[] dim, double hitX, double hitY) {
        if ((dim[0] < 0) || (dim[0] >= peak.peakDims.length)) {
            return false;
        }
        String label = getLabel(peak, peakAttr);
        double x = peak.peakDims[dim[0]].getChemShiftValue();
        double x1 = xAxis.getDisplayPosition(x);
        float intensity = peak.getIntensity();

        double textY = xAxis.getYOrigin() - g2.getFont().getSize() - 5;
        double y1 = textY;
        Bounds bounds;
        if (peakAttr.getLabelType() == PPM) {
            y1 = yAxis.getDisplayPosition(intensity);
            bounds = measureText(label, g2.getFont(), -90, x1, y1 - 35);
        } else {
            bounds = measureText(label, g2.getFont(), 0.0, x1, y1);
        }
        return bounds.contains(hitX, hitY);

    }

    private boolean pick2DPeak(PeakListAttributes peakAttr, DatasetAttributes dataAttr, GraphicsContextInterface g2, int[] dim, Peak peak, double x,
                               double y) {
        if ((dim[0] < 0) || (dim[0] >= peak.peakDims.length)) {
            return false;
        }
        double[] ctr = {0.0, 0.0};
        double[] bou = {0.0, 0.0};

        bou[0] = peak.peakDims[dim[0]].getBoundsValue();
        bou[1] = peak.peakDims[dim[1]].getBoundsValue();
        ctr[0] = peak.peakDims[dim[0]].getChemShiftValue();
        ctr[1] = peak.peakDims[dim[1]].getChemShiftValue();
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

    void draw1DPeak(PeakListAttributes peakAttr, GraphicsContextInterface g2, int[] dim, Peak peak, int colorMode, boolean selected) throws GraphicsIOException {
        if ((dim[0] < 0) || (dim[0] >= peak.peakDims.length)) {
            return;
        }
        String label = getLabel(peak, peakAttr);
        float x = peak.peakDims[dim[0]].getChemShiftValue();
        float intensity = peak.getIntensity();

        double textY = xAxis.getYOrigin() - g2.getFont().getSize() - 5;

        Peak1DRep peakRep = new Peak1DRep(peakAttr, dim[0], x, intensity, textY, label, colorMode, peak);

        //         addTo1DRegionHash(x,peakRep);
        if (selected) {
            peakRep.renderSelection(g2);
        } else {
            peakRep.render(g2);
        }
    }

    void draw1DMultiplet(PeakListAttributes peakAttr, GraphicsContextInterface g2, int[] dim, Multiplet multiplet,
                         int colorMode, boolean selected, int iLine) throws GraphicsIOException {
        if (!multiplet.isValid()) {
            return;
        }
        Peak peak = multiplet.getOrigin();
        if ((peak.getStatus() < 0) || !peak.isValid()) {
            return;
        }

        String label;
        if (multipletLabelType == PeakDisplayParameters.MULTIPLET_LABEL_NUMBER) {
            label = String.valueOf(multiplet.getIDNum());
        } else {
            label = getMultipletLabel(multiplet);
        }
        float xM = (float) multiplet.getCenter();
        float yM = (float) multiplet.getMax();
        double range = yAxis.getRange();
        yM += range * frOffset;
        ArrayList<TreeLine> lines = multiplet.getSplittingGraph();
        double max = 0.0;
        treeOn = true;
        Color strokeColor;
        boolean generic = multiplet.isGenericMultiplet();
        if (treeOn) {
            if (colorMode == 0) {
                strokeColor = peakAttr.getOnColor();
            } else {
                strokeColor = peakAttr.getOffColor();
            }

            for (TreeLine line : lines) {
                if (line.getY1() > max) {
                    max = line.getY1();
                }
            }
            int i = 0;
            if (lines.size() == 1) {
                max = -1.0;
            }
            for (TreeLine line : lines) {
                double xC = xM - line.getX1();
                double xE = xM + line.getX2();
                int index = generic ? i : (int) Math.round(line.getY1());

                boolean selMode = selected && (index == iLine);
                renderToMulti(g2, strokeColor, xC, xE, yM, max, line.getY1(), selMode);
                i++;
            }
            if (!selected) {
                try {
                    renderMultipletLabel(g2, multiplet, label, strokeColor, xM, yM, max);
                } catch (Exception e) {
                    log.warn(e.getMessage(), e);
                }
            }
        }
    }

    void renderToMulti(GraphicsContextInterface g2, Color color, double xC, double xE, double y, double max, double nY, boolean selMode) throws GraphicsIOException {
        double x1 = xAxis.getDisplayPosition(xC);
        double y1 = yAxis.getDisplayPosition(y);
        double x2 = xAxis.getDisplayPosition(xE);
        double y2, y3;
        if (max < 0.0) {
            y1 -= deltaY;
            y2 = y1;
            y3 = y2 + deltaY;
        } else {
            y1 -= (1 + max - nY) * 2 * deltaY;
            y2 = y1 + deltaY;
            y3 = y2 + deltaY;
        }
        if (selMode) {
            g2.setLineWidth(peak1DStroke * 4);
            g2.setStroke(Color.ORANGE);
            g2.beginPath();
            g2.moveTo(x2, y2);
            g2.lineTo(x2, y3);
            g2.stroke();
        }

        g2.setLineWidth(peak1DStroke);
        g2.setStroke(color);
        g2.beginPath();
        if (max < 0.0) {
            g2.moveTo(x2, y2);
            g2.lineTo(x2, y3);
        } else {
            g2.moveTo(x1, y1);
            g2.lineTo(x2, y2);
            g2.lineTo(x2, y3);
        }
        g2.stroke();
    }

    Optional<MultipletSelection> pick1DMultiplet(PeakListAttributes peakAttr, int[] dim, Multiplet multiplet, double hitX, double hitY) {
        Optional<MultipletSelection> result = Optional.empty();
        if (!multiplet.isValid()) {
            return result;
        }
        Peak peak = multiplet.getOrigin();
        if ((peak.getStatus() < 0) || !peak.isValid()) {
            return result;
        }

        float xM = (float) multiplet.getCenter();
        float yM = (float) multiplet.getMax();
        ArrayList<TreeLine> lines = multiplet.getSplittingGraph();
        double range = yAxis.getRange();
        yM += range * frOffset;
        double max = 0.0;
        treeOn = true;
        boolean generic = multiplet.isGenericMultiplet();
        if (treeOn) {
            for (TreeLine line : lines) {
                if (line.getY1() > max) {
                    max = line.getY1();
                }
            }
            int i = 0;
            for (TreeLine line : lines) {
                double xC = xM + line.getX1();
                double xE = xM + line.getX2();
                if (hitMultipletLine(xE, yM, max, line.getY1(), hitX, hitY)) {
                    int index = generic ? i : (int) Math.round(line.getY1());
                    MultipletSelection mSel = new MultipletSelection(multiplet, xC, xE, index);
                    result = Optional.of(mSel);
                    break;
                }
                i++;
            }

        }
        return result;
    }

    Optional<MultipletSelection> hitMultipletLabel(double hitX, double hitY) {
        MultipletSelection multipletSelection = null;
        for (var peakBox : lastTextBoxes) {
            if (peakBox.contains(hitX, hitY)) {
                Multiplet multiplet = peakBox.getMultiplet();
                multipletSelection = new MultipletSelection(multiplet, peakBox.getBounds());
                break;
            }
        }
        return Optional.ofNullable(multipletSelection);
    }

    boolean hitMultipletLine(double xE, double y, double max, double nY, double hitX, double hitY) {
        double y1 = yAxis.getDisplayPosition(y);
        double x2 = xAxis.getDisplayPosition(xE);

        y1 -= (1 + max - nY) * 2 * deltaY;
        double y2 = y1 + deltaY;
        Rectangle rect = new Rectangle(x2 - 5, y2, 10, deltaY);
        return rect.contains(hitX, hitY);
    }

    void renderMultipletLabel(GraphicsContextInterface g2, Multiplet multiplet, String label, Color color, double xC, double y, double max) throws GraphicsIOException {
        double x1 = xAxis.getDisplayPosition(xC);
        double y1 = yAxis.getDisplayPosition(y);
        if (max < 0.0) {
            y1 -= deltaY;
        } else {
            y1 -= (1 + max) * 2 * deltaY;

        }
        double yText = y1 - deltaY;
        g2.setTextAlign(TextAlignment.CENTER);
        Bounds bounds = measureText(label, g2.getFont(), 0, x1, yText);
        bounds = new BoundingBox(bounds.getMinX(), bounds.getMinY() - bounds.getHeight(), bounds.getWidth(), bounds.getHeight());
        int nTries = 10;
        boolean noOverlap = true;
        if (!lastTextBoxes.isEmpty()) {
            noOverlap = false;
            int nBoxes = lastTextBoxes.size();
            for (int i = 0; i < nTries; i++) {
                boolean ok = true;
                for (int iBox = nBoxes - 1; iBox >= 0; iBox--) {
                    Bounds lastTextBox = lastTextBoxes.get(iBox).getBounds();
                    if (bounds.getMinX() > lastTextBox.getMaxX()) {
                        break;
                    }

                    bounds = measureText(label, g2.getFont(), 0, x1, yText);
                    bounds = new BoundingBox(bounds.getMinX(), bounds.getMinY() - bounds.getHeight(), bounds.getWidth(), bounds.getHeight());
                    if (lastTextBox.intersects(bounds)) {
                        ok = false;
                    }
                }
                if (!ok) {
                    yText -= (1.5 * deltaY);
                } else {
                    noOverlap = true;
                    break;
                }

            }
        }
        if (noOverlap) {
            lastTextBoxes.add(new PeakBox(bounds, multiplet.getPeakDim().getPeak()));
            g2.setTextBaseline(VPos.BOTTOM);
            String[] segments = label.split("\n");
            if (segments.length > 0) {
                double lineIncr = bounds.getHeight() / segments.length;
                double lineOffset = lineIncr * (segments.length - 1);
                for (String segment : segments) {
                    g2.fillText(segment, x1, yText - lineOffset);
                    g2.setStroke(color);
                    lineOffset -= lineIncr;
                }
            }
            g2.strokeLine(x1, y1, x1, yText);
        }

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

    public void draw2DPeak(PeakListAttributes peakAttr, GraphicsContextInterface g2, int[] dim, Peak peak, boolean erase,
                           boolean selected) throws GraphicsIOException {
        if (g2 == null) {
            return;
        }
        String label = getLabel(peak, peakAttr);
        double ctr0;
        double ctr1;
        int jx = 1;
        int jy = 1;
        float j0 = 0.0f;
        float j1 = 0.0f;
        double[] ctr = {0.0, 0.0};
        double[] bou = {0.0, 0.0};
        double[] wid = {0.0, 0.0};
        double widthScale = 1.37 / 2.0;  // optimal integration region size.  divide by 2.0 to get width of one side

        if ((jmode == 0) && peak.peakDims[dim[0]].hasMultiplet() && (peak.peakDims[dim[0]].getMultiplet().isCoupled())) {
            jx = 2;
        }

        if ((dim.length > 1)
                && ((jmode == 1) && peak.peakDims[dim[1]].hasMultiplet() && (peak.peakDims[dim[1]].getMultiplet().isCoupled()))) {
            jy = 2;
        }
        bou[0] = peak.peakDims[dim[0]].getBoundsValue();
        bou[1] = peak.peakDims[dim[1]].getBoundsValue();
        wid[0] = peak.peakDims[dim[0]].getLineWidth();
        wid[1] = peak.peakDims[dim[1]].getLineWidth();

        ctr0 = peak.peakDims[dim[0]].getChemShiftValue();
        ctr1 = peak.peakDims[dim[1]].getChemShiftValue();
        ctr0 = peakAttr.foldShift(0, ctr0);
        ctr1 = peakAttr.foldShift(1, ctr1);

        for (int kx = 0; kx < jx; kx++) {
            for (int ky = 0; ky < jy; ky++) {
                ctr[0] = ctr0 + ((j0 * ((jx * (kx - 1)) + 1)) / 2.0);
                ctr[1] = ctr1 + ((j1 * ((jy * (ky - 1)) + 1)) / 2.0);

                g2.setLineWidth(PEAK_2_D_STROKE);

                if (erase) {
                    // FIXME
                } else if (selected) {
                    Rectangle box = getBox(ctr, bou);
                    Paint currentPaint = g2.getFill();
                    g2.setFill(selectFill);
                    g2.fillRect(box.getX(), box.getY(), box.getWidth(), box.getHeight());
                    g2.setFill(currentPaint);
                } else {
                    double x1 = xAxis.getDisplayPosition(ctr[0] + (bou[0] / 2.0));
                    double x2 = xAxis.getDisplayPosition(ctr[0] - (bou[0] / 2.0));
                    double y1 = yAxis.getDisplayPosition(ctr[1] + (bou[1] / 2.0));
                    double y2 = yAxis.getDisplayPosition(ctr[1] - (bou[1] / 2.0));

                    double xc = xAxis.getDisplayPosition(ctr[0]);
                    double yc = yAxis.getDisplayPosition(ctr[1]);

                    double[] position = peak.getCorner().getPosition(x1, y1, x2, y2);
                    DisplayTypes disType = peakAttr.getDisplayType();
                    if (disType != null) {
                        switch (disType) {
                            case Peak:
                                g2.beginPath();
                                g2.moveTo(x1, y1);
                                g2.lineTo(x1, y2);
                                g2.lineTo(x2, y2);
                                g2.lineTo(x2, y1);
                                g2.lineTo(x1, y1);
                                g2.moveTo(x1, yc);
                                g2.lineTo(x2, yc);
                                g2.moveTo(xc, y1);
                                g2.lineTo(xc, y2);
                                g2.stroke();
                                break;
                            case Cross:
                                x1 = xAxis.getDisplayPosition(ctr[0] + (wid[0] * widthScale));
                                x2 = xAxis.getDisplayPosition(ctr[0] - (wid[0] * widthScale));
                                y2 = yAxis.getDisplayPosition(ctr[1] + (wid[1] * widthScale));
                                y1 = yAxis.getDisplayPosition(ctr[1] - (wid[1] * widthScale));
                                g2.beginPath();
                                g2.moveTo(x1, yc);
                                g2.lineTo(x2, yc);
                                g2.moveTo(xc, y1);
                                g2.lineTo(xc, y2);
                                g2.stroke();
                                if (peakAttr.getLabelType() != LabelTypes.None) {
                                    g2.beginPath();
                                    g2.moveTo(xc, yc);
                                    g2.lineTo(position[0], position[1]);
                                    g2.stroke();
                                }
                                break;
                            case Ellipse:
                            case FillEllipse:
                                x1 = xAxis.getDisplayPosition(ctr[0] + (wid[0] * widthScale));
                                x2 = xAxis.getDisplayPosition(ctr[0] - (wid[0] * widthScale));
                                y2 = yAxis.getDisplayPosition(ctr[1] + (wid[1] * widthScale));
                                y1 = yAxis.getDisplayPosition(ctr[1] - (wid[1] * widthScale));
                                if (disType == DisplayTypes.Ellipse) {
                                    g2.strokeOval(x1, y1, x2 - x1, y2 - y1);
                                } else {
                                    g2.fillOval(x1, y1, x2 - x1, y2 - y1);
                                }
                                if (peakAttr.getLabelType() != LabelTypes.None) {
                                    g2.beginPath();
                                    g2.moveTo(xc, yc);
                                    g2.lineTo(position[0], position[1]);
                                    g2.stroke();
                                }
                                break;
                            case None:
                                break;
                            default:
                                if (peakAttr.getLabelType() != LabelTypes.None) {
                                    g2.beginPath();
                                    g2.moveTo(xc, yc);
                                    g2.lineTo(position[0], position[1]);
                                    g2.stroke();
                                }
                                break;
                        }
                    }
                    if (peakAttr.getLabelType() != LabelTypes.None) {
                        setLabelAlignment(g2, peak.getCorner());
                        g2.fillText(label, position[0], position[1]);
                    }
                }
            }
        }
    }

    public void drawPeakConnection(ConnectPeakAttributes connPeaks, GraphicsContextInterface g2, int[] dim) {
        if (g2 == null) {
            return;
        }
        if (dim.length != 2) {
            return;
        }
        /**
         * FIXME: Lines are drawn outside the chart bounds when connecting peaks
         * are not within the visible chart size.
         *
         */

        for (int i = 0, limit = connPeaks.getPeaks().size(); i < limit; i++) {
            int j = i + 1;
            if (j < limit) {
                Peak p1 = connPeaks.getPeaks().get(i);
                Peak p2 = connPeaks.getPeaks().get(j);
                if (p1 != null && p2 != null) {
                    PeakDim p1x = p1.peakDims[dim[0]];
                    PeakDim p1y = p1.peakDims[dim[1]];
                    PeakDim p2x = p2.peakDims[dim[0]];
                    PeakDim p2y = p2.peakDims[dim[1]];
                    double x1 = p1x.getChemShift();
                    double y1 = p1y.getChemShift();
                    double x2 = p2x.getChemShift();
                    double y2 = p2y.getChemShift();
                    x1 = xAxis.getDisplayPosition(x1);
                    x2 = xAxis.getDisplayPosition(x2);
                    y1 = yAxis.getDisplayPosition(y1);
                    y2 = yAxis.getDisplayPosition(y2);

                    g2.setStroke(connPeaks.getColor());
                    g2.setLineWidth(connPeaks.getWidth());
                    g2.beginPath();
                    g2.moveTo(x1, y1);
                    g2.lineTo(x2, y2);
                    g2.stroke();
                }
            }
        }
    }

    public void drawLinkLines(PeakListAttributes peakAttr, GraphicsContextInterface g2, Peak peak, int[] dim, boolean ignoreLinkDrawn) throws GraphicsIOException {
        if ((g2 == null) || (peak.peakDims.length < 2)) {
            return;
        }
        PeakList peakList = peak.getPeakList();
        PeakDim peakDim0 = peak.peakDims[dim[0]];
        PeakDim peakDim1 = peak.peakDims[dim[1]];

        double edge1x = xAxis.getLowerBound();
        double edge2x = xAxis.getUpperBound();
        double edge1y = yAxis.getLowerBound();
        double edge2y = yAxis.getUpperBound();

        if (ignoreLinkDrawn || !peakDim1.isLinkDrawn()) {
            List<PeakDim> linkedPeakDims = peakDim1.getLinkedPeakDims();
            if (linkedPeakDims.size() > 1) {
                double minX = Double.MAX_VALUE;
                double maxX = Double.NEGATIVE_INFINITY;
                double sumY = 0.0;
                int nY = 0;
                double minYdiag = Double.MAX_VALUE;
                double maxYdiag = Double.NEGATIVE_INFINITY;
                double sumXdiag = 0.0;
                int nXdiag = 0;
                for (PeakDim peakDim : linkedPeakDims) {
                    Peak peak0 = peakDim.getPeak();
                    if (peak0.getPeakList() == peakList) { // FIXME: use equal method for comparison
                        peakDim.setLinkDrawn(true);
                        double shiftX = peak0.peakDims[0].getChemShift();
                        double shiftY = peak0.peakDims[1].getChemShift();
                        if ((shiftX > edge1x) && (shiftX < edge2x) && (shiftY > edge1y) && (shiftY < edge2y)) {
                            minX = Math.min(shiftX, minX);
                            maxX = Math.max(shiftX, maxX);
                            minYdiag = Math.min(shiftY, minYdiag);
                            maxYdiag = Math.max(shiftY, maxYdiag);
                            if (peak0.getPeakDim(1) == peakDim) {
                                sumY += shiftY;
                                nY++;
                            } else {
                                sumXdiag += shiftX;
                                nXdiag++;
                            }
                        }
                    }
                }
                double posY = yAxis.getDisplayPosition(sumY / nY);

                double x1 = xAxis.getDisplayPosition(minX);
                double x2 = xAxis.getDisplayPosition(maxX);

                if (peakDim0.isFrozen()) {
                    g2.setStroke(GUIColorUtils.toColor(Peak.FREEZE_COLORS[0]));
                } else {
                    g2.setStroke(peakAttr.getOnColor());

                }
                g2.beginPath();
                g2.moveTo(x1, posY);
                g2.lineTo(x2, posY);
                g2.stroke();

                if (nXdiag > 0) {
                    double posXdiag = xAxis.getDisplayPosition(sumXdiag / nXdiag);
                    double y1diag = yAxis.getDisplayPosition(minYdiag);
                    double y2diag = yAxis.getDisplayPosition(maxYdiag);

                    g2.beginPath();
                    g2.moveTo(posXdiag, y1diag);
                    g2.lineTo(posXdiag, y2diag);
                    g2.stroke();
                }
            }
        }
        if (ignoreLinkDrawn || !peakDim0.isLinkDrawn()) {
            List<PeakDim> linkedPeakDims = peakDim0.getLinkedPeakDims();
            if (linkedPeakDims.size() > 1) {
                double minY = Double.MAX_VALUE;
                double maxY = Double.NEGATIVE_INFINITY;
                double sumX = 0.0;
                int nX = 0;
                double minXdiag = Double.MAX_VALUE;
                double maxXdiag = Double.NEGATIVE_INFINITY;
                double sumYdiag = 0.0;
                int nYdiag = 0;
                for (PeakDim peakDim : linkedPeakDims) {
                    Peak peak1 = peakDim.getPeak();
                    if (peak1.getPeakList() == peakList) {
                        peakDim.setLinkDrawn(true);
                        double shiftY = peak1.peakDims[1].getChemShift();
                        double shiftX = peak1.peakDims[0].getChemShift();
                        if ((shiftX > edge1x) && (shiftX < edge2x) && (shiftY > edge1y) && (shiftY < edge2y)) {
                            minY = Math.min(shiftY, minY);
                            maxY = Math.max(shiftY, maxY);
                            minXdiag = Math.min(shiftX, minXdiag);
                            maxXdiag = Math.max(shiftX, maxXdiag);
                            if (peak1.getPeakDim(0) == peakDim) {
                                sumX += shiftX;
                                nX++;
                            } else {
                                sumYdiag += shiftY;
                                nYdiag++;
                            }
                        }
                    }
                }
                double y1 = yAxis.getDisplayPosition(minY);
                double y2 = yAxis.getDisplayPosition(maxY);
                double posX = xAxis.getDisplayPosition(sumX / nX);

                if (peakDim1.isFrozen()) {
                    g2.setStroke(GUIColorUtils.toColor(Peak.FREEZE_COLORS[1]));
                } else {
                    g2.setStroke(peakAttr.getOnColor());
                }
                g2.beginPath();
                g2.moveTo(posX, y1);
                g2.lineTo(posX, y2);
                g2.stroke();
                if (nYdiag > 0) {
                    double x1diag = xAxis.getDisplayPosition(minXdiag);
                    double x2diag = xAxis.getDisplayPosition(maxXdiag);
                    double posYdiag = yAxis.getDisplayPosition(sumYdiag / nYdiag);

                    g2.beginPath();
                    g2.moveTo(x1diag, posYdiag);
                    g2.lineTo(x2diag, posYdiag);
                    g2.stroke();
                }
            }
        }
    }

    private void setLabelAlignment(GraphicsContextInterface g2, Corner corner) throws GraphicsIOException {
        double[] position = corner.getPosition();
        if (position[0] < -0.1) {
            g2.setTextAlign(TextAlignment.RIGHT);
        } else if (position[0] > 0.1) {
            g2.setTextAlign(TextAlignment.LEFT);
        } else {
            g2.setTextAlign(TextAlignment.CENTER);
        }
        if (position[1] < -0.25) {
            g2.setTextBaseline(VPos.BOTTOM);
        } else if (position[1] > 0.25) {
            g2.setTextBaseline(VPos.TOP);
        } else {
            g2.setTextBaseline(VPos.CENTER);
        }

    }

    public static Bounds measureText(String s, Font font, double angle, double x, double y) {
        Text text = new Text(s);
        text.setFont(font);
        text.setTextAlignment(TextAlignment.CENTER);
        text.setTextOrigin(VPos.TOP);
        Bounds useBounds = text.getBoundsInLocal();

        double xOffset = useBounds.getWidth() / 2.0;
        double yOffset = useBounds.getHeight() - 3;
        Bounds ab;
        if (angle != 0.0) {
            Affine aT = new Affine();
            aT.appendTranslation(x, y);
            aT.appendRotation(angle);
            xOffset = -useBounds.getHeight() / 2.0;
            yOffset = 0.0;
            Bounds trBds = aT.transform(useBounds);
            ab = new BoundingBox(trBds.getMinX() - xOffset, trBds.getMinY() + yOffset, trBds.getWidth(), trBds.getHeight());
        } else {
            ab = new BoundingBox(x + useBounds.getMinX() - xOffset,
                    y + useBounds.getMinY() - useBounds.getHeight() + yOffset,
                    useBounds.getWidth(),
                    useBounds.getHeight());
        }

        return ab;
    }

    static double[] genPeakShape(double w, double shapeFactor) {
        int nSim = 19;

        int nSide = (nSim - 1) / 2;

        double[] coords = new double[nSim * 2];

        int j = 0;
        for (int i = -nSide; i <= nSide; i++) {
            double f = i / 4.0;
            coords[j] = f;
            coords[j + 1] = LineShapes.G_LORENTZIAN.calculate(f, 1.0, 0.0, w, shapeFactor);
            j += 2;
        }
        return coords;
    }

    class Peak1DRep {

        double x = 0;
        double textY = 0;
        double height = 0;
        int dim = 0;
        String label = "";
        Peak peak = null;
        List<Peak> peaks = null;
        int colorMode = 0;
        PeakListAttributes peakAttr;

        Peak1DRep(PeakListAttributes peakAttr, int dim, double x, double height, double textY,
                  String label, int colorMode, Peak peak) {
            this.x = x;
            this.dim = dim;
            this.textY = textY;
            this.height = height;
            this.label = label;
            this.peak = peak;
            this.colorMode = colorMode;
            this.peakAttr = peakAttr;
        }

        Peak1DRep(GraphicsContextInterface g2, int dim, List<Peak> peaks) throws GraphicsIOException {
            this.peaks = peaks;
            this.dim = dim;
            generateDerivedPath(g2);
        }

        void generateDerivedPath(GraphicsContextInterface g2) throws GraphicsIOException {
            double min = Double.MAX_VALUE;
            double max = Double.NEGATIVE_INFINITY;
            double minWid = Double.MAX_VALUE;
            int maxSize = 1000;
            if (peaks.isEmpty()) {
                return;
            }
            for (Peak peak : peaks) {
                PeakDim peakDim = peak.peakDims[0];
                Multiplet multiplet = peakDim.getMultiplet();
                List<AbsMultipletComponent> comps = multiplet.getAbsComponentList();
                for (AbsMultipletComponent comp : comps) {
                    double wid = comp.getLineWidth();
                    if (wid < WIDTH_LIMIT) {
                        wid = WIDTH_LIMIT;
                    }

                    if (wid < minWid) {
                        minWid = wid;
                    }

                    double v = comp.getOffset() - (3 * Math.abs(wid));

                    if (v < min) {
                        min = v;
                    }

                    v = comp.getOffset() + (3 * Math.abs(wid));

                    if (v > max) {
                        max = v;
                    }

                }
            }
            double delta = minWid / 11;
            min -= delta;
            max += delta;
            int m = (int) Math.round((max - min) / delta);
            if (m > maxSize) {
                m = maxSize;
                delta = (max - min) / m;
            } else if (m == 0) {
                return;
            }
            double f = min;
            double[] bpCoords = new double[m * 2];
            int iCoord = 0;

            for (int i = 0; i < m; i++) {
                bpCoords[iCoord] = f;
                for (Peak peak : peaks) {
                    PeakDim peakDim = peak.peakDims[0];
                    Multiplet multiplet = peakDim.getMultiplet();
                    List<AbsMultipletComponent> comps = multiplet.getAbsComponentList();
                    for (AbsMultipletComponent comp : comps) {
                        double c = comp.getOffset();
                        double w = comp.getLineWidth();
                        if (w < WIDTH_LIMIT) {
                            w = WIDTH_LIMIT;
                        }
                        double shapeFactor = peakDim.getShapeFactorValue();
                        double a = comp.getIntensity();
                        double height = LineShapes.G_LORENTZIAN.calculate(f, a, c, w, shapeFactor);
                        bpCoords[iCoord + 1] += height;
                    }
                }
                iCoord += 2;
                f += delta;
            }
            g2.beginPath();
            BezierPath.makeBezierCurve(bpCoords, 1, g2, 1.0, x, 0.0, 1.0, 1.0, xAxis, yAxis);

            g2.stroke();
        }

        void render(GraphicsContextInterface g2) throws GraphicsIOException {
            if ((peak.getStatus() < 0) || !peak.isValid()) {
                return;
            }

            if (colorMode == 0) {
                g2.setStroke(peakAttr.getOnColor());
            } else {
                g2.setStroke(peakAttr.getOffColor());
            }

            g2.setLineWidth(peak1DStroke);
            double x1 = xAxis.getDisplayPosition(x);
            double y1 = yAxis.getDisplayPosition(height);
            int lastBox = lastTextBoxes.size() - 1;

            if (peakAttr.getLabelType() == PPM) {
                Bounds bounds = measureText(label, g2.getFont(), -90, x1, y1 + 35);
                if (lastTextBoxes.isEmpty() || (!lastTextBoxes.get(lastBox).intersects(bounds))) {
                    lastTextBoxes.add(new PeakBox(bounds, peak));

                    g2.save();
                    g2.setTextAlign(TextAlignment.LEFT);
                    g2.setTextBaseline(VPos.CENTER);
                    g2.translate(x1, y1);

                    g2.rotate(-90.0);
                    g2.fillText(label, 35, 0); // fixme use var for 35 and account for it in intersect test
                    g2.restore();
                }
                if (peakDisType <= PeakDisplayParameters.DISPLAY_SIMULATED) {
                    g2.beginPath();
                    g2.moveTo(x1, y1 - 10);
                    g2.lineTo(x1, y1 - 30);
                    g2.stroke();

                }
            } else {
                g2.setTextAlign(TextAlignment.CENTER);
                Bounds bounds = measureText(label, g2.getFont(), 0, x1, textY);
                if (lastTextBoxes.isEmpty() || (!lastTextBoxes.get(lastBox).intersects(bounds))) {
                    lastTextBoxes.add(new PeakBox(bounds, peak));

                    g2.setTextBaseline(VPos.TOP);
                    g2.fillText(label, x1, textY);
                }
                if (peakDisType <= PeakDisplayParameters.DISPLAY_SIMULATED) {
                    g2.beginPath();
                    g2.moveTo(x1, textY);
                    g2.lineTo(x1, textY - 20);
                    g2.stroke();
                }
            }
            g2.setTextAlign(TextAlignment.LEFT);

            if (peakAttr.getSimPeaks()) {
                renderSimulated(g2);
            }
        }
//

        void renderSimulated(GraphicsContextInterface g2) throws GraphicsIOException {
            Multiplet multiplet = peak.peakDims[dim].getMultiplet();
            if (multiplet.isCoupled() || multiplet.isGenericMultiplet()) {
                renderSimulatedMultiplet(g2);
            } else {
                double w = peak.peakDims[dim].getLineWidthValue();
                if (w < WIDTH_LIMIT) {
                    w = WIDTH_LIMIT;
                }
                double shapeFactor = peak.peakDims[dim].getShapeFactorValue();
                double[] coords = genPeakShape(1.0, shapeFactor);

                double intensity = peak.getIntensity();
                g2.setStroke(peakAttr.getOnColor());
                g2.setLineWidth(peak1DStroke);

                g2.beginPath();
                BezierPath.makeBezierCurve(coords, 1, g2, 1.0, x, 0.0, w, intensity, xAxis, yAxis);
                g2.stroke();
            }

        }

        void renderSimulatedMultiplet(GraphicsContextInterface g2) throws GraphicsIOException {
            double shapeFactor = peak.peakDims[dim].getShapeFactorValue();
            g2.setStroke(peakAttr.getOnColor());
            g2.setLineWidth(peak1DStroke);
            List<AbsMultipletComponent> comps = peak.peakDims[dim].getMultiplet().getAbsComponentList();
            for (AbsMultipletComponent comp : comps) {
                double w = comp.getLineWidth();
                if (w < WIDTH_LIMIT) {
                    w = WIDTH_LIMIT;
                }

                double intensity = comp.getIntensity();
                double pos = comp.getOffset();
                double[] coords = genPeakShape(1.0, shapeFactor);

                g2.beginPath();
                BezierPath.makeBezierCurve(coords, 1, g2, 1.0, pos, 0.0, w, intensity, xAxis, yAxis);
                g2.stroke();
            }

        }

        void renderSelection(GraphicsContextInterface g2) throws GraphicsIOException {
            double x1 = xAxis.getDisplayPosition(x);
            double y1 = yAxis.getDisplayPosition(height);
            if (colorMode == 0) {
                g2.setStroke(peakAttr.getOnColor());
            } else {
                g2.setStroke(peakAttr.getOffColor());
            }

            g2.setLineWidth(peak1DStroke);
            if (peakAttr.getLabelType() == PPM) {
                drawSelectionIndicator(g2, label, -90, x1, y1 - 35);
            } else {
                drawSelectionIndicator(g2, label, 0, x1, textY);
            }
        }
    }
}
