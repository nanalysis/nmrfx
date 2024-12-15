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
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import org.nmrfx.processor.processing.ProcessingOperation;
import org.nmrfx.processor.processing.ProcessingOperationInterface;

import java.util.ArrayList;
import java.util.List;

/**
 * @author brucejohnson
 */
public class OperationInfo {

    public final static String[] opOrders = {
            "Cascade-Simulate",
            "VECREF",
            "ZEROS",
            "ONES",
            "GEN",
            "RANDN",
            "RAND",
            "ADD",
            "Cascade-FID",
            "BZ",
            "TDCOMB",
            "EA",
            "DCFID",
            "FILTER",
            "SIGN",
            "Cascade-TD-Solvent",
            "SUPPRESS",
            "TDSS",
            "Cascade-Sampling",
            "EXTEND",
            "SCHEDULE",
            "ZFMAT",
            "PHASE_ID",
            "GRINS",
            "IST",
            "ISTMATRIX",
            "NESTA_L1_EXT",
            "NESTA_L0_EXT",
            "NESTA",
            "LP",
            "LPR",
            "Cascade-Apodization",
            "Apodization",
            "EXPD",
            "GM",
            "SB",
            "KAISER",
            "BLACKMAN",
            "TRI",
            "TM",
            "GMB",
            "APODIZE",
            "Cascade-Transform",
            "ZF",
            "FT",
            "IFT",
            "RFT",
            "REVERSE",
            "CSHIFT",
            "TILT45",
            "SHIFT",
            "TRIM",
            "Cascade-FD-Solvent",
            "FDSS",
            "Cascade-Phasing",
            "HFT",
            "AUTOPHASE",
            "PHASE",
            "COMB",
            "COMPLEX",
            "REAL",
            "IMAG",
            "MAG",
            "POWER",
            "ZIMAG",
            "ZREAL",
            "Cascade-Baseline",
            "Baseline Correction",
            "BC",
            "DC",
            "DCFID",
            "REGIONS",
            "AUTOREGIONS",
            "BCMED",
            "BCPOLY",
            "TDPOLY",
            "BCWHIT",
            "BCSINE",
            "GAPSMOOTH",
            "Cascade-Regions",
            "BUCKET",
            "EXTRACT",
            "EXTRACTP",
            "Cascade-Measure",
            "INTEGRATE",
            "MEASURE",
            "Cascade-Dataset",
            "DPHASE",
            "DEPT",
            "Cascade-New",
            "SCRIPT",
            "CWTD",
            "DX",
            "MULT", //"SCRIPT"
    };

    public final static ArrayList<String> opOrderList = new ArrayList<>();

    static {
        for (String op : opOrders) {
            opOrderList.add(op);
        }
    }

    public static String trimOp(String op) {
        op = op.trim();
        int end = op.indexOf(" ");
        if (end != -1) {
            op = op.substring(0, end);
        }
        end = op.indexOf("(");
        if (end != -1) {
            op = op.substring(0, end);
        }
        return op.toUpperCase();
    }

    public static String fixOp(String op) {
        int end = op.indexOf("(");
        if (end == -1) {
            op = op.toUpperCase() + "()";
        } else {
            op = op.substring(0, end).toUpperCase() + op.substring(end);

        }
        return op;
    }

    public static boolean isOp(String op) {
        return opOrderList.contains(trimOp(op));
    }

    public static List<String> getOps(String opFragment) {
        opFragment = trimOp(opFragment);
        ArrayList<String> result = new ArrayList<>();
        for (String op : opOrderList) {
            if (op.startsWith(opFragment)) {
                result.add(fixOp(op));
            }
        }
        return result;
    }

    public static int getCurrentPosition(List<ProcessingOperationInterface> current, ProcessingOperation newOp) {
        String newOpName = newOp.getName();
        return getCurrentPosition(current, newOpName);
    }

    public static int getCurrentPosition(List<ProcessingOperationInterface> current, String newOpName) {
        if (newOpName.indexOf("(") != -1) {
            newOpName = newOpName.substring(0, newOpName.indexOf("("));
        }
        int index = -1;
        int iPos = 0;
        for (ProcessingOperationInterface currentOp : current) {
            String currentOpName = currentOp.getName();
            if (newOpName.equalsIgnoreCase(currentOpName)) {
                index = iPos;
                break;
            }
            iPos++;
        }
        return index;
    }

    public static int getPosition(List<ProcessingOperationInterface> current, ProcessingOperationInterface newOp) {
        String newOpName = newOp.getName();
        return getPosition(current, newOpName);
    }

    public static int getPosition(List<ProcessingOperationInterface> current, String newOpName) {
        if (newOpName.indexOf("(") != -1) {
            newOpName = newOpName.substring(0, newOpName.indexOf("("));
        }
        int index = -1;
        int iPos = 0;
        for (ProcessingOperationInterface currentOp : current) {
            String currentOpName = currentOp.getName();
            if (newOpName.equalsIgnoreCase(currentOpName)) {
                index = iPos;
                break;
            }
            iPos++;
        }
        if (index == -1) {
            int orderIndex = opOrderList.indexOf(newOpName);
            if (orderIndex != -1) {
                int i = 0;
                for (ProcessingOperationInterface currentOp : current) {
                    String currentOpName = currentOp.getName();
                    int curIndex = opOrderList.indexOf(currentOpName);
                    if (curIndex >= orderIndex) {
                        index = i;
                        break;
                    }
                    i++;
                }
                if (index == -1) {
                    index = current.size();
                }

            }

        }
        return index;
    }
}
