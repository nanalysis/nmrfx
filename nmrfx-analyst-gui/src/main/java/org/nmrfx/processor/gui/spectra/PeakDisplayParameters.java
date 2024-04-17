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

import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.nmrfx.peaks.Peak;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.utilities.NvUtil;

/**
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
     * @return the colorType
     */
    public int getColorType() {
        return colorType;
    }

    /**
     * @return the labelType
     */
    public int getLabelType() {
        return labelType;
    }

    /**
     * @return the displayType
     */
    public int getDisplayType() {
        return displayType;
    }

    /**
     * @return the peakOff
     */
    public int getPeakOff() {
        return peakOff;
    }

    /**
     * @return the types
     */
    public int getPeakTypes() {
        return types;
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
    public int getTreeLabelType() {
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
     * @return the displayOn
     */
    public boolean isDisplayOn() {
        return displayOn;
    }

    /**
     * @return the displayOff
     */
    public boolean isDisplayOff() {
        return displayOff;
    }

    /**
     * @return the jmode
     */
    public int getJmode() {
        return jmode;
    }

    /**
     * @return the oneDStroke
     */
    public double getOneDStroke() {
        return oneDStroke;
    }

    public enum Params {

        LABEL_TYPE("LABEL_TYPE", Integer.valueOf(0)) {
            public void setValue(PeakDisplayParameters pdPar, Object object) {
                if (object instanceof Integer) {
                    pdPar.labelType = ((Integer) object).intValue();
                } else {
                    int i = NvUtil.getStringPars(PeakDisplayParameters.labelTypes, object.toString().toLowerCase(), 3);
                    if (i != -1) {
                        pdPar.labelType = i;
                    }

                }
            }

            public Integer getValue(PeakDisplayParameters pdPar) {
                return Integer.valueOf(pdPar.labelType);
            }

            public String getValueForTcl(PeakDisplayParameters pdPar) {
                String result = labelTypes[pdPar.labelType];
                return result;
            }
        },
        COLOR_TYPE("COLOR_TYPE", Integer.valueOf(0)) {
            public void setValue(PeakDisplayParameters pdPar, Object object) {
                if (object instanceof Integer) {
                    pdPar.colorType = ((Integer) object).intValue();
                } else {
                    int i = NvUtil.getStringPars(PeakDisplayParameters.colorTypes, object.toString().toLowerCase(), 3);
                    if (i != -1) {
                        pdPar.colorType = i;
                    }

                }
            }

            public Integer getValue(PeakDisplayParameters pdPar) {
                return Integer.valueOf(pdPar.colorType);
            }

            public String getValueForTcl(PeakDisplayParameters pdPar) {
                String result = colorTypes[pdPar.colorType];
                return result;
            }
        },
        DISPLAY_TYPE("DISPLAY_TYPE", Integer.valueOf(0)) {
            public void setValue(PeakDisplayParameters pdPar, Object object) {
                if (object instanceof Integer) {
                    pdPar.displayType = ((Integer) object).intValue();
                } else {
                    int i = NvUtil.getStringPars(PeakDisplayParameters.displayTypes, object.toString().toLowerCase(), 3);
                    if (i != -1) {
                        pdPar.displayType = i;
                    }

                }
            }

            public Integer getValue(PeakDisplayParameters pdPar) {
                return Integer.valueOf(pdPar.displayType);
            }

            public String getValueForTcl(PeakDisplayParameters pdPar) {
                String result = displayTypes[pdPar.displayType];
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
        TYPES("PEAK_TYPES", Integer.valueOf(0)) {
            public void setValue(PeakDisplayParameters pdPar, Object object) {
                pdPar.types = ((Integer) object).intValue();
            }

            public Integer getValue(PeakDisplayParameters pdPar) {
                return Integer.valueOf(pdPar.types);
            }

            public Object getValueForTcl(PeakDisplayParameters pdPar) {
                String result = "all";
                if (pdPar.types != 0) {
                    result = Peak.typesToString(pdPar.types);
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
                pdPar.displayOn = ((Boolean) object).booleanValue();
            }

            public Boolean getValue(PeakDisplayParameters pdPar) {
                return Boolean.valueOf(pdPar.displayOn);
            }

            public String getValueForTcl(PeakDisplayParameters pdPar) {
                String result = pdPar.displayOn ? "1" : "0";
                return result;
            }
        },
        PEAK_DIS_OFF("PEAK_DIS_OFF", Boolean.valueOf(false)) {
            public void setValue(PeakDisplayParameters pdPar, Object object) {
                pdPar.displayOff = ((Boolean) object).booleanValue();
            }

            public Boolean getValue(PeakDisplayParameters pdPar) {
                return Boolean.valueOf(pdPar.displayOff);
            }

            public String getValueForTcl(PeakDisplayParameters pdPar) {
                String result = pdPar.displayOff ? "1" : "0";
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
        },
        ;
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

    public enum LabelTypes {
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

    public enum DisplayTypes {
        Peak(),
        Cross(),
        Label(),
        Ellipse(),
        FillEllipse(),
        None();
    }

    public enum ColorTypes {
        Plane(),
        Assigned(),
        Error(),
        Status(),
        Intensity();
    }

    static final String[] labelTypes = {
            "Number", "Label", "Residue", "1Residue", "Atom", "Cluster", "User", "Comment",
            "Summary", "PPM", "None"
    };
    static final String[] displayTypes = {"Peak", "Simulated", "Label", "Ellipse", "FillEllipse", "None"};
    static final String[] colorTypes = {
            "Plane", "Assigned", "Error", "Status", "Intensity"
    };
    static final String[] multipletLabelTypes = {
            "None", "Number", "Atom", "Summary", "PPM"
    };
    Color colorOn = Color.BLACK;
    Color colorOff = Color.RED;
    int colorType = COLOR_BY_PLANE;
    int labelType = LABEL_NUMBER;
    int displayType = DISPLAY_PEAK;
    int peakOff = 0;
    int types = 0;
    boolean treeOn = false;
    int multipletLabelType = MULTIPLET_LABEL_NUMBER;
    boolean displayOn = true;
    boolean displayOff = true;
    int jmode = 0;
    private String peakListName = "";
    double oneDStroke = 0.5;

    PeakDisplayParameters(final String peakListName) {
        this.peakListName = peakListName;
    }

    public static PeakDisplayParameters newInstance(PeakDisplayParameters peakPar) {
        PeakDisplayParameters newPeakPar = new PeakDisplayParameters(peakPar.peakListName);

        newPeakPar.colorOn = peakPar.colorOn;
        newPeakPar.colorOff = peakPar.colorOff;
        newPeakPar.colorType = peakPar.colorType;
        newPeakPar.labelType = peakPar.labelType;
        newPeakPar.displayType = peakPar.displayType;
        newPeakPar.peakOff = peakPar.peakOff;
        newPeakPar.types = peakPar.types;
        newPeakPar.treeOn = peakPar.treeOn;
        newPeakPar.multipletLabelType = peakPar.multipletLabelType;
        newPeakPar.displayOn = peakPar.displayOn;
        newPeakPar.displayOff = peakPar.displayOff;
        newPeakPar.jmode = peakPar.jmode;
        newPeakPar.oneDStroke = peakPar.oneDStroke;

        return newPeakPar;
    }

    public PeakList getPeakList() {
        if ((peakListName == null) || peakListName.equals("")) {
            return null;
        } else {
            return (PeakList) PeakList.get(peakListName);
        }
    }

    public String getPeakListName() {
        return peakListName;
    }

    public void setPeakListName(final String name) {
        peakListName = name;
    }

    public static String[] getLabelTypes() {
        return labelTypes.clone();
    }

    public static String[] getDisplayTypes() {
        return displayTypes.clone();
    }

    public static String[] getColorTypes() {
        return colorTypes.clone();
    }

}
