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

import org.nmrfx.processor.datasets.peaks.Multiplet;
import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.gui.PolyChart;
import org.nmrfx.processor.gui.spectra.PeakDisplayParameters.DisplayTypes;
import org.nmrfx.processor.gui.spectra.PeakDisplayParameters.LabelTypes;
import static org.nmrfx.processor.gui.spectra.PeakDisplayParameters.LabelTypes.PPM;
import org.nmrfx.processor.utilities.Format;
import java.awt.geom.Line2D;
import java.util.*;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Affine;
import org.nmrfx.processor.datasets.peaks.Peak.Corner;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.gui.chart.Axis;
import org.nmrfx.processor.gui.graphicsio.GraphicsContextInterface;
import org.nmrfx.processor.gui.graphicsio.GraphicsContextProxy;
import org.nmrfx.processor.gui.graphicsio.GraphicsIOException;

/**
 *
 * @author brucejohnson
 */
public class DrawPeaks {

    static int nRegions = 32;
    static int minHitSize = 5;
    static double widthLimit = 0.0001;
    char[] anchorS = {'s', ' '};
    char[] anchorW = {' ', 'w'};
    /**
     * Creates a new instance of PeakRenderer
     */
    static Path g1DPath = new Path();
    static Path g2DPath = new Path();

    NMRAxis xAxis;
    NMRAxis yAxis;

    static int nSim = 19;
    static double[] bpCoords = new double[nSim * 2];

    static {
        int nSide = (nSim - 1) / 2;
        int j = 0;

        /* double w=1.66;
         for (int i = -nSide;i<=nSide;i++) {
         bpCoords[j] = i/4.0;
         bpCoords[j+1] = Math.exp(-bpCoords[j]*bpCoords[j]*w*w);
         j += 2;
         }
         BezierPath.makeBezierCurve(bpCoords, 1, g1DPath,1.0);
         */
        double w = 0.5;

        for (int i = -nSide; i <= nSide; i++) {
            double f = i / 4.0;
            bpCoords[j] = f;
            bpCoords[j + 1] = (w * w) / ((w * w) + (f * f));
            j += 2;
        }
    }
//    DatasetAttributes specPar = null;
    int jmode = 0;
    int disDim = 0;
    PolyChart chart = null;
    int peakDisType = 0;
//    int labelType = PeakDisplayParameters.LABEL_PPM;
    int peakLabelType = 0;
    int multipletLabelType = PeakDisplayParameters.MULTIPLET_LABEL_SUMMARY;
    boolean treeOn = false;
    int peakColorType = 0;
    boolean peakDisOn = true;
    boolean peakDisOff = true;
    static double peak2DStroke = 1.0;
    double peak1DStroke = peak2DStroke;
    static double peakOvalStroke = 2.0;
    int iPeakList = 0;
    float dY = 0;
    float dXRegion = 0.0f;
    HashSet[] regions = null;
    Color selectFill = new Color(1.0f, 1.0f, 0.0f, 0.4f);
    private boolean multipletMode = false;
    Bounds lastTextBox = null;
    GraphicsContextInterface g2;

    public DrawPeaks(PolyChart chart, Canvas peakCanvas) {
        this.chart = chart;
        this.g2 = new GraphicsContextProxy(peakCanvas.getGraphicsContext2D());
        //   setParameters();
        regions = new HashSet[nRegions];

        for (int i = 0; i < regions.length; i++) {
            regions[i] = new HashSet();
        }
        xAxis = (NMRAxis) chart.getXAxis();
        yAxis = (NMRAxis) chart.getYAxis();
    }

    public void resetDrawList() {
        for (int i = 0; i < regions.length; i++) {
            regions[i].clear();
        }
    }

//    protected void setParameters(PeakDisplayParameters pdPar) {
////        disDim = specPar.disDim;
//        colorOn = pdPar.getColorOn();
//        colorOff = pdPar.getColorOff();
//        jmode = pdPar.getJmode();
//        displayType = pdPar.getDisplayType();
//        oneDStroke = pdPar.getOneDStroke();
//        if (oneDStroke == 0.0) {
//            oneDStroke = peak2DStroke;
//        }
//        labelType = pdPar.getLabelType();
//        colorType = pdPar.getColorType();
//        multipletLabelType = pdPar.getTreeLabelType();
//        treeOn = pdPar.isTreeOn();
//        displayOn = pdPar.isDisplayOn();
//        displayOff = pdPar.isDisplayOff();
////        iPeakList = specPar.viewPar.iPeakList;
////        lastTextBox = null;
////        dY = (float) ((24 * Math.abs(chart.activeView[1][0]
////                - chart.activeView[1][1])) / Math.abs(chart.corner[1][0]
////                        - chart.corner[1][1]));
//    }
    public void setMultipletMode(boolean state) {
        multipletMode = state;
    }

    public synchronized boolean pickPeak(PeakListAttributes peakAttr, DatasetAttributes dataAttr, GraphicsContextInterface g2, Peak peak, int[] dim,
            double[] offset, double x, double y) throws GraphicsIOException {
        int nPeakDim = peak.peakList.nDim;
        boolean result = false;
        //        if ((disDim != 0) && (nPeakDim > 1)) {
        if ((nPeakDim > 1)) {
            result = pick2DPeak(peakAttr, dataAttr, g2, dim, peak, x, y);
        } else {
            return pick1DPeak(peakAttr, peak, dim, x, y);
        }
        return result;
    }
//
//    synchronized boolean pickMultiplet(GraphicsContextInterface g2, Multiplet multiplet, int[] dim,
//            double[] offset, int x, int y) {
//        int nPeakDim = multiplet.getPeakDim().getPeak().peakList.nDim;
//
//        if ((disDim != 0) && (nPeakDim > 1)) {
//            return false;
//        } else {
//            return pick1DMultiplet(g2, dim, multiplet, x, y);
//        }
//    }

    public void drawSimSum(GraphicsContextInterface g2, ArrayList peaks, int[] dim) throws GraphicsIOException {
        //int colorMode = setColor(g2, peak, offset);
        Peak1DRep peakRep = new Peak1DRep(g2, dim[0], peaks);
    }

    public synchronized void drawPeak(PeakListAttributes peakAttr, GraphicsContextInterface g2, Peak peak, int[] dim,
            double[] offset, boolean selected) throws GraphicsIOException {
        int nPeakDim = peak.peakList.nDim;
        int colorMode = setColor(peakAttr, g2, peak, offset);
        if (((colorMode == 0) && !peakDisOn) || ((colorMode != 0) && !peakDisOff)) {
            return;
        }

//        if ((disDim != 0) && (nPeakDim > 1)) {
        if (nPeakDim > 1) {
            draw2DPeak(peakAttr, g2, dim, peak, false, selected);
        } else {
            draw1DPeak(peakAttr, g2, dim, peak, colorMode, selected);
        }
    }

    public synchronized void drawMultiplet(PeakListAttributes peakAttr, GraphicsContextInterface g2, Multiplet multiplet, int[] dim,
            double[] offset, boolean selected, int line) throws GraphicsIOException {
        Peak peak = multiplet.getPeakDim().getPeak();
        int nPeakDim = peak.peakList.nDim;
        int colorMode = setColor(peakAttr, g2, peak, offset);
        if (((colorMode == 0) && !peakDisOn) || ((colorMode != 0) && !peakDisOff)) {
            return;
        }
        if ((disDim != 0) && (nPeakDim > 1)) {
//            draw2DPeak(peakAttr, g2, dim, peak, false, false);
        } else {
            draw1DMultiplet(peakAttr, g2, dim, multiplet, colorMode, selected, line);
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

                    if ((peak.peakDim[i].getLabel().length() == 0)
                            || peak.peakDim[i].getLabel().equals("?")) {
                        labels.append("?").append(String.valueOf(peak.getIdNum()));
                    } else {
                        iAsg = true;
                        labels.append(peak.peakDim[i].getLabel());
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
                    plab = peak.peakDim[i].getLabel().trim();

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
                    plab = peak.peakDim[0].getLabel().trim();
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

            case Atom: //FIXME

                for (i = 0; i < nPeakDim; i++) {
                    if (i > 0) {
                        labels.append(" ");
                    }

                    plab = peak.peakDim[i].getLabel().trim();

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
                    labels.append(peak.peakDim[i].getUser()).append(" ");
                }

                label = labels.toString().trim();

                break;

            case Comment:
                label = String.valueOf(peak.getComment());

                break;

            case Summary:
                double normVal = 0;

                // FIXME  make precision in ctr a function of dig resolution  sw/sfrq/size
                if (multipletMode && peak.peakDim[0].hasMultiplet()) {
                    String couplings = peak.peakDim[0].getMultiplet().getCouplingsAsSimpleString();
                    if (peak.peakList.scale > 0.0) {
                        normVal = peak.peakDim[0].getMultiplet().getVolume() / peak.peakList.scale;
                    }
                    label = Format.format2(normVal) + " " + peak.peakDim[0].getMultiplet().getMultiplicity() + " " + couplings
                            + "\n" + Format.format4(peak.peakDim[0].getMultiplet().getCenter());
                } else {
                    //label = (String.valueOf(peak.getIdNum()));
                    double ppm = peak.peakDim[0].getChemShiftValue();
                    if (ppm > 20.0) {
                        label = Format.format2(ppm);
                    } else {
                        label = Format.format3(ppm);
                    }
                }

                break;
            case PPM:
                double ppm = peak.peakDim[0].getChemShiftValue();
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
        float xM = (float) multiplet.getCenter();

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

                    plab = peak.peakDim[i].getLabel().trim();

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
                label = Format.format2(normVal) + " " + multiplet.getMultiplicity() + " " + couplings
                        + "\n" + Format.format3(multiplet.getCenter());

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

        // g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        switch (peakColorType) {
            case PeakDisplayParameters.COLOR_BY_PLANE:

                for (int i = 2; i < offset.length; i++) {
                    if (Math.abs(offset[i]) > 0.5) {
                        colorMode = 1;

                        break;
                    }
                }

                break;

            case PeakDisplayParameters.COLOR_BY_ASSIGNED:

                boolean iAsg = true;

                for (int i = 0; i < nPeakDim; i++) {
                    if ((peak.peakDim[i].getLabel().length() == 0)
                            || peak.peakDim[i].getLabel().equals("?")) {
                        iAsg = false;

                        break;
                    }
                }

                if (!iAsg) {
                    colorMode = 1;
                }

                break;

            case PeakDisplayParameters.COLOR_BY_ERROR:

                boolean isOK = true;

                for (int i = 0; i < nPeakDim; i++) {
                    if ((peak.peakDim[i].getError()[0] != '+')
                            || (peak.peakDim[i].getError()[1] != '+')) {
                        isOK = false;

                        break;
                    }
                }

                if (!isOK) {
                    colorMode = 1;
                }

                break;

            case PeakDisplayParameters.COLOR_BY_STATUS:

                if (peak.getStatus() != 0) {
                    colorMode = 1;
                }

                break;

            case PeakDisplayParameters.COLOR_BY_INTENSITY:

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
        Color peakColor = peak.getColor();
        if (peakColor != null) {
            color = peakColor;
        }
        g2.setStroke(color);
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
        if ((dim[0] < 0) || (dim[0] >= peak.peakDim.length)) {
            return false;
        }
        String label = getLabel(peak, peakAttr);
        double x = peak.peakDim[dim[0]].getChemShiftValue();
        double x1 = xAxis.getDisplayPosition(x);
        float intensity = peak.getIntensity();

        double height = yAxis.getHeight();
        double textY = height - g2.getFont().getSize() - 5;
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

//    protected boolean pick1DMultiplet(GraphicsContextInterface g2, int[] dim, Multiplet multiplet, int x,
//            int y) {
//        if (!multiplet.isValid()) {
//            System.out.println("invalid mult");
//            return false;
//        }
//        String label = getMultipletLabel(multiplet);
//        float xM = (float) multiplet.getCenter();
//        float yM = (float) multiplet.getIntensity();
//        Rectangle textBox;
//        if (multipletLabelType == PeakDisplayParameters.MULTIPLET_LABEL_SUMMARY) {
//            double max = 0.0;
//            if (treeOn) {
//                ArrayList<Line2D> lines = multiplet.getSplittingGraph();
//                for (Line2D line : lines) {
//                    if (line.getY1() > max) {
//                        max = line.getY1();
//                    }
//                }
//            }
//            float textY = (float) (yM + dY * ((1.0 + max + 1.5)));
//            textBox = chart.getTextBox(g2, xM, textY, label, anchorS, 0, 0);
//        } else {
//            float textY;
//            double max = 0.0;
//            if (displayType == PeakDisplayParameters.DISPLAY_NONE) {
//                textY = (float) (chart.activeView[1][0]
//                        + (iPeakList * dY) + (dY / 6));
//            } else {
//                if (treeOn) {
//                    ArrayList<Line2D> lines = multiplet.getSplittingGraph();
//                    for (Line2D line : lines) {
//                        if (line.getY1() > max) {
//                            max = line.getY1();
//                        }
//                    }
//                }
//                textY = (float) (yM + dY * ((1.0 + max + 1.5)));
//            }
//            textBox = chart.getTextBox(g2, xM, textY, label, anchorW, 0, -90);
//
//        }
//        return textBox.contains(x, y);
//    }
    private boolean pick2DPeak(PeakListAttributes peakAttr, DatasetAttributes dataAttr, GraphicsContextInterface g2, int[] dim, Peak peak, double x,
            double y) {
        if ((dim[0] < 0) || (dim[0] >= peak.peakDim.length)) {
            return false;
        }
        double[] ctr = {0.0, 0.0};
        double[] bou = {0.0, 0.0};

        bou[0] = peak.peakDim[dim[0]].getBoundsValue();
        bou[1] = peak.peakDim[dim[1]].getBoundsValue();
        ctr[0] = peak.peakDim[dim[0]].getChemShiftValue();
        ctr[1] = peak.peakDim[dim[1]].getChemShiftValue();
        Rectangle box = getBox(ctr, bou);
        boolean result = box.contains(x, y);

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
        // fixme add hitting label
//        if (!result) {
//            double x1 = ctr0 + (bou0 / 2.0);
//            double y1 = ctr1 + (bou1 / 2.0);
//            double x2 = ctr0 - (bou0 / 2.0);
//            double y2 = ctr1 - (bou1 / 2.0);
//            String label = getLabel(peak);
//            double[] position = peak.getCorner().getPosition(x1, y1, x2, y2);
//            char[] anchor = peak.getCorner().getAnchor(x1, y1, x2, y2);
//
//            Rectangle textBox = chart.getTextBox(g2, position[0], position[1], label, anchor, 0);
//            result = textBox.contains(x, y);
//
//        }
        return result;
    }
//    void draw1DPeakSelection(GraphicsContextInterface g2, int[] dim, Peak peak, boolean erase) {
//        if (!peak.isValid() || (peak.getStatus() < 0)) {
//            return;
//        }
//
//        String label = getLabel(peak);
//        float x = peak.peakDim[dim[0]].getChemShiftValue();
//        float textY = (float) (chart.activeView[1][0]
//                + (iPeakList * dY) + (dY / 6));
//        int nLines = label.split("\n").length;
//        float height = (float) (chart.activeView[1][0]
//                + (iPeakList * dY) + (((nLines - 1) * dY) / 2) + dY);
//        float lineY2 = (float) (height - (dY / 2) + (dY / 6));
//
//        Peak1DRep peakRep = new Peak1DRep(dim[0], x, textY, height, lineY2,
//                label, 0, peak);
//        peakRep.renderSelection(g2, erase);
//    }
//    void draw1DMultipletSelection(GraphicsContextInterface g2, int[] dim, Multiplet multiplet, boolean erase) {
//        if (!multiplet.isValid() || (multiplet.getStatus() < 0)) {
//            return;
//        }
//        String label = getMultipletLabel(multiplet);
//        float xM = (float) multiplet.getCenter();
//        float yM = (float) multiplet.getIntensity();
//        if (multipletLabelType == PeakDisplayParameters.MULTIPLET_LABEL_SUMMARY) {
//            double max = 0.0;
//            if (treeOn) {
//                ArrayList<Line2D> lines = multiplet.getSplittingGraph();
//                for (Line2D line : lines) {
//                    if (line.getY1() > max) {
//                        max = line.getY1();
//                    }
//                }
//            }
//            float textY = (float) (yM + dY * ((1.0 + max + 1.5)));
//            drawSelectionIndicator(g2, xM, textY, label, anchorS, 0);
//        } else {
//            float textY;
//            double max = 0.0;
//            if (displayType == PeakDisplayParameters.DISPLAY_NONE) {
//                textY = (float) (chart.activeView[1][0]
//                        + (iPeakList * dY) + (dY / 6));
//            } else {
//                if (treeOn) {
//                    ArrayList<Line2D> lines = multiplet.getSplittingGraph();
//                    for (Line2D line : lines) {
//                        if (line.getY1() > max) {
//                            max = line.getY1();
//                        }
//                    }
//                }
//                textY = (float) (yM + dY * ((1.0 + max + 1.5)));
//            }
//
//            drawSelectionIndicator(g2, xM, textY, label, anchorW, -90);
//        }
//
//        //         addTo1DRegionHash(x,peakRep);
//    }

    void draw1DPeak(PeakListAttributes peakAttr, GraphicsContextInterface g2, int[] dim, Peak peak, int colorMode, boolean selected) throws GraphicsIOException {
        if ((dim[0] < 0) || (dim[0] >= peak.peakDim.length)) {
            return;
        }
        String label = getLabel(peak, peakAttr);
        float x = peak.peakDim[dim[0]].getChemShiftValue();
        float intensity = peak.getIntensity();

        double textY = xAxis.getYOrigin() - g2.getFont().getSize() - 5;

        Peak1DRep peakRep = new Peak1DRep(peakAttr, dim[0], x, intensity, textY, label, colorMode, peak);

        //         addTo1DRegionHash(x,peakRep);
        if (selected) {
            peakRep.renderSelection(g2, false);
        } else {
            peakRep.render(g2, false);
        }
    }

    void draw1DMultiplet(PeakListAttributes peakAttr, GraphicsContextInterface g2, int[] dim, Multiplet multiplet,
            int colorMode, boolean selected, int iLine) throws GraphicsIOException {
        if (!multiplet.isValid()) {
            System.out.println("invalid mult");
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
        float yM = (float) multiplet.getIntensity();
        ArrayList<Line2D> lines = multiplet.getSplittingGraph();
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

            for (Line2D line : lines) {
                if (line.getY1() > max) {
                    max = line.getY1();
                }
            }
            int i = 0;
            if (lines.size() == 1) {
                max = -1.0;
            }
            for (Line2D line : lines) {
                double xC = xM + line.getX1();
                double xE = xM - line.getX2();
                int index = generic ? i : (int) Math.round(line.getY1());

                boolean selMode = selected && (index == iLine);
                renderToMulti(g2, strokeColor, xC, xE, yM, max, line.getY1(), selMode);
                i++;
            }
            if (!selected) {
                renderMultipletLabel(g2, label, strokeColor, xM, yM, max);
            }
//            chart.myDrawLine(g2, xM, (yM + dY * (1.0 + max + 1.2)), xM, (yM + dY * (1.0 + max + 0.5)));
        }
    }

    void renderToMulti(GraphicsContextInterface g2, Color color, double xC, double xE, double y, double max, double nY, boolean selMode) throws GraphicsIOException {
        double deltaY = 25.0;
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
            System.out.println("invalid mult");
            return result;
        }
        Peak peak = multiplet.getOrigin();
        if ((peak.getStatus() < 0) || !peak.isValid()) {
            return result;
        }

        float xM = (float) multiplet.getCenter();
        float yM = (float) multiplet.getIntensity();
        ArrayList<Line2D> lines = multiplet.getSplittingGraph();
        double max = 0.0;
        treeOn = true;
        boolean generic = multiplet.isGenericMultiplet();
        if (treeOn) {
            for (Line2D line : lines) {
                if (line.getY1() > max) {
                    max = line.getY1();
                }
            }
            int i = 0;
            for (Line2D line : lines) {
                double xC = xM + line.getX1();
                double xE = xM - line.getX2();
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

    boolean hitMultipletLine(double xE, double y, double max, double nY, double hitX, double hitY) {
        double deltaY = 25.0;
        double y1 = yAxis.getDisplayPosition(y);
        double x2 = xAxis.getDisplayPosition(xE);

        y1 -= (1 + max - nY) * 2 * deltaY;
        double y2 = y1 + deltaY;
        Rectangle rect = new Rectangle(x2 - 5, y2, 10, deltaY);
        return rect.contains(hitX, hitY);
    }

    void renderMultipletLabel(GraphicsContextInterface g2, String label, Color color, double xC, double y, double max) throws GraphicsIOException {
        double deltaY = 25.0;
        double x1 = xAxis.getDisplayPosition(xC);
        double y1 = yAxis.getDisplayPosition(y);
        if (max < 0.0) {
            y1 -= deltaY;
        } else {
            y1 -= (1 + max) * 2 * deltaY;

        }
        double yText = y1 - 5;
        g2.setTextAlign(TextAlignment.CENTER);
        Bounds bounds = measureText(label, g2.getFont(), 0, x1, yText);
        int nTries = 5;
        if (lastTextBox != null) {
            for (int i = 0; i < nTries; i++) {
                bounds = measureText(label, g2.getFont(), 0, x1, yText);
                if (!lastTextBox.intersects(bounds)) {
                    break;
                }
                yText -= bounds.getHeight();
            }
        }
        if ((lastTextBox == null) || (!lastTextBox.intersects(bounds))) {
            lastTextBox = bounds;
            g2.setTextBaseline(VPos.BOTTOM);
            String[] segments = label.split("\n");
            double lineIncr = g2.getFont().getSize();
            double lineOffset = lineIncr * (segments.length - 1);
            for (String segment : segments) {
                g2.fillText(segment, x1, yText - lineOffset);
                g2.setStroke(color);
                lineOffset -= lineIncr;
            }
            g2.strokeLine(x1, y1, x1, yText);
        }

    }

//        if (PeakDisplayParameters.drawMultipletLabel(multipletLabelType)) {
//            if (multipletLabelType == PeakDisplayParameters.MULTIPLET_LABEL_SUMMARY) {
//                float textY = (float) (yM + dY * ((1.0 + max + 1.5)));
//                Rectangle textBox = chart.getTextBox(g2, xM, textY, label, anchorS, 0);
//                if ((lastTextBox != null) && (lastTextBox.intersects(textBox))) {
//                    textY += dY; // fixme not correct calc
//                }
//                textBox = chart.getTextBox(g2, xM, textY, label, anchorS, 0);
//                chart.myDrawText(g2, xM, textY, label, anchorS, 0);
//                chart.myDrawLine(g2, xM, textY - 0.3 * dY, xM, (yM + dY * (1.0 + max + 0.5)));
//
//                lastTextBox = textBox;
//            } else {
//                float textY;
//                if (displayType == PeakDisplayParameters.DISPLAY_NONE) {
//                    textY = (float) (chart.activeView[1][0]
//                            + (iPeakList * dY) + (dY / 6));
//                } else {
//                    textY = (float) (yM + dY * ((1.0 + max + 1.5)));
//                }
//                Rectangle textBox = chart.getTextBox(g2, xM, textY, label, anchorW, 0, -90);
//                chart.myDrawRotatedText(g2, xM, textY, label, anchorW, 0, -90);
//                int bx = (int) ((textBox.getMaxX() + textBox.getMinX()) / 2);
//                int by = (int) (textBox.getMinY());
//                if (displayType == PeakDisplayParameters.DISPLAY_NONE) {
//                    chart.myDrawLinePts(g2, bx, by, bx, by - 10);
//                } else {
//                    chart.myDrawLine(g2, xM, textY - dY * 0.2, xM, (yM + dY * (1.0 + max + 0.5)));
//                }
//                lastTextBox = textBox;
//            }
//        }
    //         addTo1DRegionHash(x,peakRep);
//    void draw2DPeakSelection(GraphicsContextInterface g2, int[] dim, Peak peak, boolean erase) {
//        if (!peak.isValid() || (peak.getStatus() < 0)) {
//            return;
//        }
//
//        draw2DPeak(g2, dim, peak, erase, true);
//    }
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
        float ctr0;
        float ctr1;
        int jx = 1;
        int jy = 1;
        float j0 = 0.0f;
        float j1 = 0.0f;
        int ax = 0;
        double[] ctr = {0.0, 0.0};
        double[] bou = {0.0, 0.0};
        double[] wid = {0.0, 0.0};

        if ((jmode == 0) && peak.peakDim[dim[0]].hasMultiplet() && (peak.peakDim[dim[0]].getMultiplet().isCoupled())) {
            jx = 2;
        }

        if ((dim.length > 1)
                && ((jmode == 1) && peak.peakDim[dim[1]].hasMultiplet() && (peak.peakDim[dim[1]].getMultiplet().isCoupled()))) {
            jy = 2;
        }
        bou[0] = peak.peakDim[dim[0]].getBoundsValue();
        bou[1] = peak.peakDim[dim[1]].getBoundsValue();
        wid[0] = peak.peakDim[dim[0]].getLineWidth();
        wid[1] = peak.peakDim[dim[1]].getLineWidth();
        /* fixme draw twoD coupling
         if ((dim.length > 1) && ((jx > 1) || (jy > 1))) {
         ax = dim[0];

         double coupling = peak.peakDim[dim[0]].getMultiplet().getCouplingValue(0);
         j0 = (float) (coupling / peak.peakList.getSpectralDim(ax).getSf());
         ax = dim[1];
         coupling = peak.peakDim[dim[1]].getMultiplet().getCouplingValue(0);
         j1 = (float) (coupling / peak.peakList.getSpectralDim(ax).getSf());
         bou[0] = j0;
         bou[1] = j1;
         }

         if ((jmode == 0) && (peak.peakDim[dim[0]].getMultiplet().isCoupled())) {
         ax = dim[0];

         double coupling = peak.peakDim[dim[0]].getMultiplet().getCouplingValue(0);
         bou[0] = Math.abs(peak.peakDim[dim[0]].getBoundsValue()) + Math.abs(coupling / peak.peakList.getSpectralDim(ax).getSf());
         }

         if ((dim.length > 1) && (jmode == 0) &&
         (peak.peakDim[dim[1]].getMultiplet().isCoupled())) {
         ax = dim[1];

         double coupling = peak.peakDim[dim[1]].getMultiplet().getCouplingValue(0);
         bou[1] = Math.abs(peak.peakDim[dim[1]].getBoundsValue()) + Math.abs(coupling / peak.peakList.getSpectralDim(ax).getSf());
         }
         */
        ctr0 = peak.peakDim[dim[0]].getChemShiftValue();
        ctr1 = peak.peakDim[dim[1]].getChemShiftValue();
        // fixme fold
//        ctr0 = (float) specPar.foldPPM(ctr0, 0);
//        ctr1 = (float) specPar.foldPPM(ctr1, 1);

        for (int kx = 0; kx < jx; kx++) {
            for (int ky = 0; ky < jy; ky++) {
                ctr[0] = ctr0 + ((j0 * ((jx * (kx - 1)) + 1)) / 2.0);
                ctr[1] = ctr1 + ((j1 * ((jy * (ky - 1)) + 1)) / 2.0);

                //Nv_FoldPPM(&(peak.d[pkdim[0]].ctr), fFldLim, 0);
                //Nv_FoldPPM(&(peak.d[pkdim[1]].ctr), fFldLim, 1);
                for (int j = 0; j < 2; j++) {
                    //ctr[j] = peak.peakDim[dim[j]].getCtr();
                    //bou[j] = peak.peakDim[dim[j]].getBou();
                }
                g2.setLineWidth(peak2DStroke);

                if (erase) {
//                    Rectangle box = chart.getBox(g2, ctr[0], ctr[1], bou[0],
//                            bou[1]);
// FIXME
//                    g2.drawImage(specPar.bufOffscreen, box.x - 2, box.y - 2,
                    //                           box.x + box.width + 4, box.y + box.height + 4,
                    //                          box.x - 2, box.y - 2, box.x + box.width + 4,
                    //                         box.y + box.height + 4, null);
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
                    if (null == disType) {
//                        g2.setStroke(peakOvalStroke);
//                        chart.myDrawPointer(g2, position[0], position[1], xc, yc);
                    } else {
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
                                x1 = xAxis.getDisplayPosition(ctr[0] + (wid[0] * 0.68 / 2.0));
                                x2 = xAxis.getDisplayPosition(ctr[0] - (wid[0] * 0.68 / 2.0));
                                y2 = yAxis.getDisplayPosition(ctr[1] + (wid[1] * 0.68 / 2.0));
                                y1 = yAxis.getDisplayPosition(ctr[1] - (wid[1] * 0.68 / 2.0));
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
                                x1 = xAxis.getDisplayPosition(ctr[0] + (wid[0] * 0.68 / 2.0));
                                x2 = xAxis.getDisplayPosition(ctr[0] - (wid[0] * 0.68 / 2.0));
                                y2 = yAxis.getDisplayPosition(ctr[1] + (wid[1] * 0.68 / 2.0));
                                y1 = yAxis.getDisplayPosition(ctr[1] - (wid[1] * 0.68 / 2.0));
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
                        g2.setFill(peakAttr.getOnColor());
                        g2.fillText(label, position[0], position[1]);
                    }
                }
            }
        }
    }

    public void drawLinkLines(PeakListAttributes peakAttr, GraphicsContextInterface g2, Peak peak, int[] dim) throws GraphicsIOException {
        if (g2 == null) {
            return;
        }
        PeakDim peakDim0 = peak.peakDim[dim[0]];
        PeakDim peakDim1 = peak.peakDim[dim[1]];
        double ppmX = peakDim0.getChemShift();
        double ppmY = peakDim1.getChemShift();
        double posX = xAxis.getDisplayPosition(ppmX);
        double posY = yAxis.getDisplayPosition(ppmY);

        PeakList peakList = peakDim0.getPeak().getPeakList();
        List<PeakDim> linkedPeakDims = peakDim1.getLinkedPeakDims();
        if (linkedPeakDims.size() > 1) {
            double minX = Double.MAX_VALUE;
            double maxX = Double.NEGATIVE_INFINITY;
            double edge1 = xAxis.getLowerBound();
            double edge2 = xAxis.getUpperBound();
            for (PeakDim peakDim : linkedPeakDims) {
                Peak peak0 = peakDim.getPeak();
                if (peak0.getPeakList() == peakList) {
                    double shift = peak0.peakDim[0].getChemShift();
                    if ((shift > edge1) && (shift < edge2)) {
                        minX = Math.min(shift, minX);
                        maxX = Math.max(shift, maxX);
                    }
                }
            }
            double x1 = xAxis.getDisplayPosition(minX);
            double x2 = xAxis.getDisplayPosition(maxX);
            if (peakDim0.isFrozen()) {
                g2.setStroke(Peak.FREEZE_COLORS[0]);
            } else {
                g2.setStroke(Color.BLACK);

            }
            g2.beginPath();
            g2.moveTo(x1, posY);
            g2.lineTo(x2, posY);
            g2.stroke();
        }
        linkedPeakDims = peakDim0.getLinkedPeakDims();
        if (linkedPeakDims.size() > 1) {
            double minY = Double.MAX_VALUE;
            double maxY = Double.NEGATIVE_INFINITY;
            double edge1 = yAxis.getLowerBound();
            double edge2 = yAxis.getUpperBound();
            for (PeakDim peakDim : linkedPeakDims) {
                Peak peak1 = peakDim.getPeak();
                if (peak1.getPeakList() == peakList) {
                    double shift = peak1.peakDim[1].getChemShift();
                    if ((shift > edge1) && (shift < edge2)) {
                        minY = Math.min(shift, minY);
                        maxY = Math.max(shift, maxY);
                    }
                }
            }
            double y1 = yAxis.getDisplayPosition(minY);
            double y2 = yAxis.getDisplayPosition(maxY);
            if (peakDim1.isFrozen()) {
                g2.setStroke(Peak.FREEZE_COLORS[1]);
            } else {
                g2.setStroke(Color.BLACK);
            }
            g2.beginPath();
            g2.moveTo(posX, y1);
            g2.lineTo(posX, y2);
            g2.stroke();
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
//        text.setTextAlignment(TextAlignment.CENTER);
//        text.setTextOrigin(VPos.TOP);
        Bounds textBounds = text.getBoundsInLocal();
        Rectangle stencil = new Rectangle(
                textBounds.getMinX(), textBounds.getMinY(), textBounds.getWidth(), textBounds.getHeight()
        );

        Shape intersection = Shape.intersect(text, stencil);
        Affine aT = new Affine();
        aT.appendTranslation(x, y);
        aT.appendRotation(angle);

        Bounds trimBounds = intersection.getBoundsInLocal();
        Bounds useBounds;
        if (false) {
            useBounds = textBounds;
        } else {
            useBounds = trimBounds;
        }
        double xOffset = useBounds.getWidth() / 2.0;
        double yOffset = textBounds.getHeight() - 3;
        if (angle != 0.0) {
            xOffset = -useBounds.getHeight() / 2.0;
            yOffset = 0.0;
        }

        Bounds trBds = aT.transform(useBounds);

        Bounds ab = new BoundingBox(trBds.getMinX() - xOffset, trBds.getMinY() + yOffset, trBds.getWidth(), trBds.getHeight());

        return ab;
    }

    class Peak1DRep {

        double x = 0;
        double textY = 0;
        double height = 0;
        int dim = 0;
        String label = "";
        Peak peak = null;
        ArrayList peaks = null;
        int colorMode = 0;
        Path gDerived1DPath = null;
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

        Peak1DRep(GraphicsContextInterface g2, int dim, ArrayList peaks) throws GraphicsIOException {
            this.peaks = peaks;
            this.dim = dim;
            generateDerivedPath(g2);
        }

        void generateDerivedPath(GraphicsContextInterface g2) throws GraphicsIOException {
            double min = Double.MAX_VALUE;
            double max = Double.NEGATIVE_INFINITY;
            double minWid = Double.MAX_VALUE;
            int maxSize = 1000;
            int nPeaks = 0;
            for (int k = 0, nRootPeaks = peaks.size(); k < nRootPeaks; k++) {
                Peak kPeak = (Peak) peaks.get(k);
                List<Peak> lPeaks = PeakList.getLinks(kPeak, true);

                for (Peak lPeak : lPeaks) {
                    double wid = Math.abs(lPeak.peakDim[0].getLineWidthValue());
                    if (wid < widthLimit) {
                        wid = widthLimit;
                    }

                    if (wid < minWid) {
                        minWid = wid;
                    }

                    double v = lPeak.peakDim[0].getChemShiftValue() - (3 * Math.abs(lPeak.peakDim[0].getLineWidthValue()));

                    if (v < min) {
                        min = v;
                    }

                    v = lPeak.peakDim[0].getChemShiftValue() + (3 * Math.abs(lPeak.peakDim[0].getLineWidthValue()));

                    if (v > max) {
                        max = v;
                    }

                    nPeaks++;
                }
            }
            if (nPeaks == 0) {
                return;
            }
            double delta = minWid / 11;
            min -= delta;
            max += delta;
            gDerived1DPath = new Path();
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
                for (int k = 0, nRootPeaks = peaks.size(); k < nRootPeaks; k++) {
                    Peak kPeak = (Peak) peaks.get(k);
                    List<Peak> lPeaks = PeakList.getLinks(kPeak, true);

                    for (Peak lPeak : lPeaks) {
                        double c = lPeak.peakDim[0].getChemShiftValue();
                        double w = Math.abs(lPeak.peakDim[0].getLineWidthValue());
                        if (w < widthLimit) {
                            w = widthLimit;
                        }
                        double a = lPeak.getIntensity();
                        bpCoords[iCoord + 1] += ((a * ((w * w) / 4)) / (((w * w) / 4)
                                + ((f - c) * (f - c))));
                    }

                }
                iCoord += 2;
                f += delta;
            }
            g2.beginPath();
            BezierPath.makeBezierCurve(bpCoords, 1, g2, 1.0, x, 0.0, 1.0, 0.0, xAxis, yAxis);
            g2.stroke();

        }
//
//        void erase(GraphicsContextInterface g2) {
//            //  Rectangle textBox = chart.getTextBox(g2,x,textY, label , anchorS,0);
//            //   g2.drawImage(spectrum.bufOffscreen,textBox.x-1,textBox.y-14,textBox.x+textBox.width+2,textBox.y+textBox.height+14,
//            //           textBox.x-1,textBox.y-14,textBox.x+textBox.width+2,textBox.y+textBox.height+14,null);
//        }
//

        void renderToMulti(GraphicsContextInterface g2, boolean eraseFirst, float mX) throws GraphicsIOException {
            if (eraseFirst) {
                // erase(g2);
            }

            if ((peak.getStatus() < 0) || !peak.isValid()) {
                return;
            }

            if (colorMode == 0) {
                g2.setStroke(peakAttr.getOnColor());
            } else {
                g2.setStroke(peakAttr.getOffColor());
            }
            double x1 = xAxis.getDisplayPosition(x);
            double y1 = yAxis.getDisplayPosition(height);
            double x2 = xAxis.getDisplayPosition(mX);
            double y2 = y1 + 25;

            g2.setLineWidth(peak1DStroke);
            g2.beginPath();
            g2.moveTo(x1, y1);
            g2.lineTo(x2, y2);
            g2.lineTo(x2, y2 - (y1 - y2));
            g2.stroke();

        }

        void render(GraphicsContextInterface g2, boolean eraseFirst) throws GraphicsIOException {
            if (eraseFirst) {
//                erase(g2);
            }

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

            if (peakAttr.getLabelType() == PPM) {
                Bounds bounds = measureText(label, g2.getFont(), -90, x1, y1 + 35);
                if ((lastTextBox == null) || (!lastTextBox.intersects(bounds))) {
                    lastTextBox = bounds;

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
                if ((lastTextBox == null) || (!lastTextBox.intersects(bounds))) {
                    lastTextBox = bounds;

                    g2.setTextBaseline(VPos.TOP);
                    g2.fillText(label, x1, textY);
                }
                if (peakDisType <= PeakDisplayParameters.DISPLAY_SIMULATED) {
                    g2.beginPath();
                    double yLine = bounds.getMinY();
                    g2.moveTo(x1, textY);
                    g2.lineTo(x1, textY - 20);
                    g2.stroke();
                }
            }
            g2.setTextAlign(TextAlignment.LEFT);

            if (peakAttr.getSimPeaks()) {
                renderSimulated(g2, eraseFirst);
            }
        }
//

        void renderSimulated(GraphicsContextInterface g2, boolean eraseFirst) throws GraphicsIOException {
            double w = peak.peakDim[dim].getLineWidthValue();
            if (w < widthLimit) {
                w = widthLimit;
            }

            double intensity = peak.getIntensity();
            g2.setStroke(peakAttr.getOnColor());
            g2.setLineWidth(peak1DStroke);

            g2.beginPath();
            BezierPath.makeBezierCurve(bpCoords, 1, g2, 1.0, x, 0.0, w, intensity, xAxis, yAxis);
            g2.stroke();

        }
//
//        void renderSimSum(GraphicsContextInterface g2, boolean eraseFirst) {
//            if (gDerived1DPath != null) {
//                g2.setColor(colorOff);
//                chart.drawShape(g2, gDerived1DPath);
//            }
//        }
//

        void renderSelection(GraphicsContextInterface g2, boolean erase) throws GraphicsIOException {
            double x1 = xAxis.getDisplayPosition(x);
            double y1 = yAxis.getDisplayPosition(height);
            if (erase) {
                //erase(g2);
            } else {
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
}
