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

import java.util.ArrayList;
import java.util.List;

/**
 *
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
        "GRINS",
        "IST",
        "ISTMATRIX",
        "NESTA_L1_EXT",
        "NESTA_L0_EXT",
        "NESTA",
        "LP",
        "LPR",
        "Cascade-Apodization",
        "APODIZE",
        "BLACKMAN",
        "EXPD",
        "KAISER",
        "SB",
        "TRI",
        "TM",
        "GM",
        "GMB",
        "Cascade-Transform",
        "ZF",
        "FT",
        "IFT",
        "RFT",
        "REVERSE",
        "CSHIFT",
        "SHIFT",
        "TRIM",
        "Cascade-FD-Solvent",
        "FDSS",
        "Cascade-Phasing",
        "HFT",
        "AUTOPHASE",
        "PHASE",
        "COMB",
        "REAL",
        "IMAG",
        "MAG",
        "POWER",
        "Cascade-Baseline",
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

    public static int getCurrentPosition(List<String> current, String newOp) {
        int index = -1;
        newOp = trimOp(newOp);
        int iPos = 0;
        for (String currentOp : current) {
            currentOp = trimOp(currentOp);
            if (newOp.equals(currentOp)) {
                index = iPos;
                break;
            }
            iPos++;
        }
        return index;
    }

    public static int getPosition(List<String> current, String newOp) {
        int index = -1;
        newOp = trimOp(newOp);
        int iPos = 0;
        for (String currentOp : current) {
            currentOp = trimOp(currentOp);
            if (newOp.equals(currentOp)) {
                index = iPos;
                break;
            }
            iPos++;
        }
        if (index == -1) {
            int orderIndex = opOrderList.indexOf(newOp);
            if (orderIndex != -1) {
                int i = 0;
                for (String curOp : current) {
                    curOp = trimOp(curOp);
                    int curIndex = opOrderList.indexOf(curOp);
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
