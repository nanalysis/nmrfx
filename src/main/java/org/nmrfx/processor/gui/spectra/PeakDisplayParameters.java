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
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui.spectra;

import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.utilities.NvUtil;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 *
 * @author brucejohnson
 */
public class PeakDisplayParameters {

    public static final int DISPLAY_PEAK = 0;
    public static final int DISPLAY_SIMULATED = 1;
    public static final int DISPLAY_LABEL = 2;
    public static final int DISPLAY_ELLIPSE = 3;
    public static final int DISPLAY_FILL_ELLIPSE = 4;
    public static final int DISPLAY_NONE = 5;
    public static final int COLOR_BY_PLANE = 0;
    public static final int COLOR_BY_ASSIGNED = 1;
    public static final int COLOR_BY_ERROR = 2;
    public static final int COLOR_BY_STATUS = 3;
    public static final int COLOR_BY_INTENSITY = 4;
    public static final int LABEL_NUMBER = 0;
    public static final int LABEL_LABEL = 1;
    public static final int LABEL_RESIDUE = 2;
    public static final int LABEL_RESIDUE1 = 3;
    public static final int LABEL_ATOM = 4;
    public static final int LABEL_CLUSTER = 5;
    public static final int LABEL_USER = 6;
    public static final int LABEL_COMMENT = 7;
    public static final int LABEL_SUMMARY = 8;
    public static final int LABEL_PPM = 9;
    public static final int LABEL_NONE = 10;
    public static final int MULTIPLET_LABEL_NONE = 0;
    public static final int MULTIPLET_LABEL_NUMBER = 1;
    public static final int MULTIPLET_LABEL_ATOM = 2;
    public static final int MULTIPLET_LABEL_SUMMARY = 3;
    public static final int MULTIPLET_LABEL_PPM = 4;

    /**
     * @return the colorOn
     */
    public Color getColorOn() {
        return colorOn;
    }

    /**
     * @return the colorOff
     */
    public Color getColorOff() {
        return colorOff;
    }

    /**
     * @return the peakColorType
     */
    public int getPeakColorType() {
        return peakColorType;
    }

    /**
     * @return the peakLabelType
     */
    public int getPeakLabelType() {
        return peakLabelType;
    }

    /**
     * @return the peakDisType
     */
    public int getPeakDisType() {
        return peakDisType;
    }

    /**
     * @return the peakOff
     */
    public int getPeakOff() {
        return peakOff;
    }

    /**
     * @return the peakTypes
     */
    public int getPeakTypes() {
        return peakTypes;
    }

    /**
     * @return the treeOn
     */
    public boolean isTreeOn() {
        return treeOn;
    }

    /**
     * @return the treeMode
     */
    public int getPeakTreeLabelType() {
        return multipletLabelType;
    }

    /**
     * @return whether or not to draw tree diagram
     */
    public boolean drawMultiplet() {
        return (treeOn || (multipletLabelType != MULTIPLET_LABEL_NONE));
    }

    /**
     * @return whether or not to draw tree label
     */
    public static boolean drawMultipletLabel(int multipletLabelType) {
        return (multipletLabelType > 0);
    }

    /**
     * @return the peakDisOn
     */
    public boolean isPeakDisOn() {
        return peakDisOn;
    }

    /**
     * @return the peakDisOff
     */
    public boolean isPeakDisOff() {
        return peakDisOff;
    }

    /**
     * @return the jmode
     */
    public int getJmode() {
        return jmode;
    }

    /**
     * @return the peak1DStroke
     */
    public double getPeak1DStroke() {
        return peak1DStroke;
    }

    public enum Params {

        PEAK_LABEL_TYPE("PEAK_LABEL_TYPE", Integer.valueOf(0)) {
            public void setValue(PeakDisplayParameters pdPar, Object object) {
                if (object instanceof Integer) {
                    pdPar.peakLabelType = ((Integer) object).intValue();
                } else {
                    int i = NvUtil.getStringPars(PeakDisplayParameters.peakLabelTypes, object.toString().toLowerCase(), 3);
                    if (i != -1) {
                        pdPar.peakLabelType = i;
                    }

                }
            }

            public Integer getValue(PeakDisplayParameters pdPar) {
                return Integer.valueOf(pdPar.peakLabelType);
            }

            public String getValueForTcl(PeakDisplayParameters pdPar) {
                String result = peakLabelTypes[pdPar.peakLabelType];
                return result;
            }
        },
        PEAK_COLOR_TYPE("PEAK_COLOR_TYPE", Integer.valueOf(0)) {
            public void setValue(PeakDisplayParameters pdPar, Object object) {
                if (object instanceof Integer) {
                    pdPar.peakColorType = ((Integer) object).intValue();
                } else {
                    int i = NvUtil.getStringPars(PeakDisplayParameters.peakColorTypes, object.toString().toLowerCase(), 3);
                    if (i != -1) {
                        pdPar.peakColorType = i;
                    }

                }
            }

            public Integer getValue(PeakDisplayParameters pdPar) {
                return Integer.valueOf(pdPar.peakColorType);
            }

            public String getValueForTcl(PeakDisplayParameters pdPar) {
                String result = peakColorTypes[pdPar.peakColorType];
                return result;
            }
        },
        PEAK_DIS_TYPE("PEAK_DIS_TYPE", Integer.valueOf(0)) {
            public void setValue(PeakDisplayParameters pdPar, Object object) {
                if (object instanceof Integer) {
                    pdPar.peakDisType = ((Integer) object).intValue();
                } else {
                    int i = NvUtil.getStringPars(PeakDisplayParameters.peakDisTypes, object.toString().toLowerCase(), 3);
                    if (i != -1) {
                        pdPar.peakDisType = i;
                    }

                }
            }

            public Integer getValue(PeakDisplayParameters pdPar) {
                return Integer.valueOf(pdPar.peakDisType);
            }

            public String getValueForTcl(PeakDisplayParameters pdPar) {
                String result = peakDisTypes[pdPar.peakDisType];
                return result;
            }
        },
        PEAK_OFF("PEAK_OFF", Integer.valueOf(0)) {
            public void setValue(PeakDisplayParameters pdPar, Object object) {
                pdPar.peakOff = ((Integer) object).intValue();
            }

            public Integer getValue(PeakDisplayParameters pdPar) {
                return Integer.valueOf(pdPar.peakOff);
            }

            public Integer getValueForTcl(PeakDisplayParameters pdPar) {
                return getValue(pdPar);
            }
        },
        PEAK_TYPES("PEAK_TYPES", Integer.valueOf(0)) {
            public void setValue(PeakDisplayParameters pdPar, Object object) {
                pdPar.peakTypes = ((Integer) object).intValue();
            }

            public Integer getValue(PeakDisplayParameters pdPar) {
                return Integer.valueOf(pdPar.peakTypes);
            }

            public Object getValueForTcl(PeakDisplayParameters pdPar) {
                String result = "all";
                if (pdPar.peakTypes != 0) {
                    result = Peak.typesToString(pdPar.peakTypes);
                }
                return result;
            }
        },
        JMODE("JMODE", Integer.valueOf(0)) {
            public void setValue(PeakDisplayParameters pdPar, Object object) {
                pdPar.jmode = ((Integer) object).intValue();
            }

            public Integer getValue(PeakDisplayParameters pdPar) {
                return Integer.valueOf(pdPar.jmode);
            }

            public Integer getValueForTcl(PeakDisplayParameters pdPar) {
                return getValue(pdPar);
            }
        },
        PEAK_TREE("PEAK_TREE", Boolean.valueOf(false)) {
            public void setValue(PeakDisplayParameters pdPar, Object object) {
                pdPar.treeOn = ((Boolean) object).booleanValue();
            }

            public Boolean getValue(PeakDisplayParameters pdPar) {
                return Boolean.valueOf(pdPar.treeOn);
            }

            public String getValueForTcl(PeakDisplayParameters pdPar) {
                String result = pdPar.treeOn ? "1" : "0";
                return result;
            }
        },
        MULTIPLET_LABEL_TYPE("MULTIPLET_LABEL_TYPE", Integer.valueOf(0)) {
            public void setValue(PeakDisplayParameters pdPar, Object object) {
                if (object instanceof Integer) {
                    pdPar.multipletLabelType = ((Integer) object).intValue();
                } else {
                    int i = NvUtil.getStringPars(PeakDisplayParameters.multipletLabelTypes, object.toString().toLowerCase(), 3);
                    if (i != -1) {
                        pdPar.multipletLabelType = i;
                    }

                }
            }

            public Integer getValue(PeakDisplayParameters pdPar) {
                return Integer.valueOf(pdPar.multipletLabelType);
            }

            public String getValueForTcl(PeakDisplayParameters pdPar) {
                String result = multipletLabelTypes[pdPar.multipletLabelType];
                return result;
            }
        },
        PEAK_DIS_ON("PEAK_DIS_ON", Boolean.valueOf(false)) {
            public void setValue(PeakDisplayParameters pdPar, Object object) {
                pdPar.peakDisOn = ((Boolean) object).booleanValue();
            }

            public Boolean getValue(PeakDisplayParameters pdPar) {
                return Boolean.valueOf(pdPar.peakDisOn);
            }

            public String getValueForTcl(PeakDisplayParameters pdPar) {
                String result = pdPar.peakDisOn ? "1" : "0";
                return result;
            }
        },
        PEAK_DIS_OFF("PEAK_DIS_OFF", Boolean.valueOf(false)) {
            public void setValue(PeakDisplayParameters pdPar, Object object) {
                pdPar.peakDisOff = ((Boolean) object).booleanValue();
            }

            public Boolean getValue(PeakDisplayParameters pdPar) {
                return Boolean.valueOf(pdPar.peakDisOff);
            }

            public String getValueForTcl(PeakDisplayParameters pdPar) {
                String result = pdPar.peakDisOff ? "1" : "0";
                return result;
            }
        },
        COLOR_ON("COLOR_ON", Color.BLACK) {
            public void setValue(PeakDisplayParameters pdPar, Object object) {
                pdPar.colorOn = (Color) object;
            }

            public Color getValue(PeakDisplayParameters pdPar) {
                return pdPar.colorOn;
            }

            public String getValueForTcl(PeakDisplayParameters pdPar) {
                String result = pdPar.colorOn.toString();
                return result;
            }
        },
        COLOR_OFF("COLOR_OFF", Color.RED) {
            public void setValue(PeakDisplayParameters pdPar, Object object) {
                pdPar.colorOff = (Color) object;
            }

            public Color getValue(PeakDisplayParameters pdPar) {
                return pdPar.colorOff;
            }

            public String getValueForTcl(PeakDisplayParameters pdPar) {
                String result = pdPar.colorOff.toString();
                return result;
            }
        },;
        private String description;
        private Object defaultValue;
        private boolean isEditable = false;

        Params(String description, Object defaultValue) {
            this.description = description;
            this.defaultValue = defaultValue;
        }

        public String getDescription() {
            return description;
        }

        public void setValue(PeakDisplayParameters pdPar, Object object) {
        }

        public Object getValue(PeakDisplayParameters pdPar) {
            return null;
        }

        public Object getValueForTcl(PeakDisplayParameters pdPar) {
            return "";
        }

        public Object defaultValue() {
            return defaultValue;
        }
    }

    public enum PeakLabelTypes {
        Number(),
        Label(),
        Residue(),
        SglResidue(),
        Atom(),
        Cluster(),
        User(),
        Comment(),
        Summary(),
        PPM(),
        None();

    }

    public enum PeakDisTypes {
        Peak(),
        Cross(),
        Label(),
        Ellipse(),
        FillEllipse(),
        None();
    }

    static final String[] peakLabelTypes = {
        "Number", "Label", "Residue", "1Residue", "Atom", "Cluster", "User", "Comment",
        "Summary", "PPM", "None"
    };
    static final String[] peakDisTypes = {"Peak", "Simulated", "Label", "Ellipse", "FillEllipse", "None"};
    static final String[] peakColorTypes = {
        "Plane", "Assigned", "Error", "Status", "Intensity"
    };
    static final String[] multipletLabelTypes = {
        "None", "Number", "Atom", "Summary", "PPM"
    };
    static Font defaultPeakFont = new Font("SansSerif", 10);
    Color colorOn = Color.BLACK;
    Color colorOff = Color.RED;
    int peakColorType = COLOR_BY_PLANE;
    int peakLabelType = LABEL_NUMBER;
    int peakDisType = DISPLAY_PEAK;
    int peakOff = 0;
    int peakTypes = 0;
    boolean treeOn = false;
    int multipletLabelType = MULTIPLET_LABEL_NUMBER;
    boolean peakDisOn = true;
    boolean peakDisOff = true;
    int jmode = 0;
    private String peakListName = "";
    double peak1DStroke = 0.5;

    PeakDisplayParameters(final String peakListName) {
        this.peakListName = peakListName;
    }

    public static PeakDisplayParameters newInstance(PeakDisplayParameters peakPar) {
        PeakDisplayParameters newPeakPar = new PeakDisplayParameters(peakPar.peakListName);

        newPeakPar.colorOn = peakPar.colorOn;
        newPeakPar.colorOff = peakPar.colorOff;
        newPeakPar.peakColorType = peakPar.peakColorType;
        newPeakPar.peakLabelType = peakPar.peakLabelType;
        newPeakPar.peakDisType = peakPar.peakDisType;
        newPeakPar.peakOff = peakPar.peakOff;
        newPeakPar.peakTypes = peakPar.peakTypes;
        newPeakPar.treeOn = peakPar.treeOn;
        newPeakPar.multipletLabelType = peakPar.multipletLabelType;
        newPeakPar.peakDisOn = peakPar.peakDisOn;
        newPeakPar.peakDisOff = peakPar.peakDisOff;
        newPeakPar.jmode = peakPar.jmode;
        newPeakPar.peak1DStroke = peakPar.peak1DStroke;

        return newPeakPar;
    }

    public PeakList getPeakList() {
        if ((peakListName == null) || peakListName.equals("")) {
            return null;
        } else {
            return PeakList.get(peakListName);
        }
    }

    public String getPeakListName() {
        return peakListName;
    }

    public void setPeakListName(final String name) {
        peakListName = name;
    }

    public static String[] getPeakLabelTypes() {
        return peakLabelTypes.clone();
    }

    public static String[] getPeakDisTypes() {
        return peakDisTypes.clone();
    }

    public static String[] getPeakColorTypes() {
        return peakColorTypes.clone();
    }

}
