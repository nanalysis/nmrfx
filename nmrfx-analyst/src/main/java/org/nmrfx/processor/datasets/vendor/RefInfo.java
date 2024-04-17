/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.datasets.vendor;

import org.nmrfx.annotations.PythonAPI;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Bruce Johnson
 */
@PythonAPI("autoscript")
public class RefInfo {
    String directRef = "";

    static public final String[] PROP_NAMES = {"skip", "label", "acqarray", "acqsize", "tdsize", "sf", "sw", "ref"};
    Map<String, Object> refMap = new HashMap<>();

    String getArraySizes(NMRData nmrData) {
        String arraySizes = "";

        if (nmrData != null) {
            int nDim = nmrData.getNDim();
            StringBuilder sBuilder = new StringBuilder();
            for (int i = 0; i < nDim; i++) {
                if (i != 0) {
                    sBuilder.append(',');
                }
                sBuilder.append(nmrData.getArraySize(i));
            }
            arraySizes = sBuilder.toString();

        }
        return arraySizes;
    }

    public void setDirectRef(String value) {
        directRef = value;
    }

    String getAcqOrder(NMRData nmrData, boolean useQuotes) {
        String acqOrder = "";

        if (nmrData != null) {
            acqOrder = nmrData.getAcqOrderShort();
            if (!acqOrder.equals("")) {
                if (useQuotes) {
                    acqOrder = "'" + acqOrder + "'";
                }
            } else {
                String[] acqOrderArray = nmrData.getAcqOrder();
                if (acqOrderArray != null) {
                    StringBuilder sBuilder = new StringBuilder();
                    for (int i = 0; i < acqOrderArray.length; i++) {
                        if (i != 0) {
                            sBuilder.append(',');
                        }
                        if (useQuotes) {
                            sBuilder.append("'");
                        }
                        sBuilder.append(acqOrderArray[i]);
                        if (useQuotes) {
                            sBuilder.append("'");
                        }
                    }
                    acqOrder = sBuilder.toString();
                }
            }
        }
        return acqOrder;
    }

    public String getParString(NMRData nmrData, int nDim, String indent) {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(indent);
        sBuilder.append("acqOrder(");
        sBuilder.append(getAcqOrder(nmrData, true));
        sBuilder.append(")");
        sBuilder.append(System.lineSeparator());
        sBuilder.append(indent);
        sBuilder.append("acqarray(");
        sBuilder.append(getArraySizes(nmrData));
        sBuilder.append(")");
        sBuilder.append(System.lineSeparator());
        for (String propName : PROP_NAMES) {
            if (propName.equals("acqarray")) {
                continue;
            }
            sBuilder.append(indent);
            sBuilder.append(propName);
            sBuilder.append("(");
            for (int dim = 0; dim < nDim; dim++) {
                if (dim > 0) {
                    sBuilder.append(",");
                }
                String value = getPropValue(nmrData, dim, propName, false);
                boolean useString = true;
                // Ending with F or D allows a string to be parsed as a number
                if ((value.length() > 0) && !Character.isLetter(value.charAt(value.length() - 1))) {
                    try {
                        Double.parseDouble(value);
                        useString = false;
                    } catch (NumberFormatException nFE) {
                        useString = true;

                    }
                }
                if (propName.equals("label")) {
                    useString = true;
                }
                if (useString) {
                    sBuilder.append("'");
                    sBuilder.append(value);
                    sBuilder.append("'");
                } else {
                    sBuilder.append(value);
                }
            }
            sBuilder.append(")");
            sBuilder.append(System.lineSeparator());
        }
        return sBuilder.toString();

    }

    String getPropValue(NMRData nmrData, int dim, String propName, boolean getDefault) {
        String dimName = "" + (dim + 1);
        String nameWithDim = propName + dimName;
        if (nmrData == null) {
            return "";
        }
        String value;
        if (!getDefault && refMap.containsKey(nameWithDim)) {
            value = refMap.get(nameWithDim).toString();
        } else {
            switch (propName) {
                case "sw":
                    value = nmrData.getSWNames()[dim];
                    break;
                case "skip":
                    if (nmrData.getSize(dim) > 1) {
                        value = "0";
                    } else {
                        value = "1";
                    }
                    break;
                case "tdsize":
                    if (getDefault) {
                        value = String.valueOf(nmrData.getSize(dim));
                    } else {
                        value = "0";
                    }
                    break;
                case "acqsize":
                    if (getDefault) {
                        value = String.valueOf(nmrData.getSize(dim));
                    } else {
                        value = "0";
                    }
                    break;
                case "acqarray":
                    value = "0";
                    break;
                case "sf":
                    value = nmrData.getSFNames()[dim];
                    break;
                case "label":
                    value = nmrData.getLabelNames()[dim];
                    break;
                case "ref":
                    if (dim == 0) {
                        value = directRef;
                    } else {
                        String tn = nmrData.getTN(dim);
                        value = "";
                        for (int i = 0; i < tn.length(); i++) {
                            if (Character.isLetter(tn.charAt(i))) {
                                value = String.valueOf(tn.charAt(i));
                            }
                        }
                    }
                    break;
                default:
                    value = "";
            }

        }
        return value;
    }

}
