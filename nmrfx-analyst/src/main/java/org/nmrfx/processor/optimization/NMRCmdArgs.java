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

/*UPDATES
 *
 * TODO 03/03/09
 * -Add enumerated switches, all except list and vect?
 * -Extend from Command for Tcl compatibility
 */
/*
 * Adding feature for bound parameters (04/23/09)
 *
 */

/*
 * Creating command arg class to not only make
 * the code a bit more modular but also so that
 * we can easily implement the variable number of
 * arguments strucutre while still being compatible
 * with the old style(ie. -xlist/vect, ylist/vect).
 */
package org.nmrfx.processor.optimization;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;

public class NMRCmdArgs {

    private Hashtable<String, Object> cmdArgs;
    private HashSet<String> validArgs;
    private DataType dataType;

    public NMRCmdArgs() {
        cmdArgs = new Hashtable<String, Object>();
        validArgs = new HashSet<String>();
    }

    //--------------------------------------------------------------
    public boolean validArgByPass(String str) {
        return DataType.isBound(str);
    }

    /*
     * Add element to run-timeCmdArg List
     */
    public ArgError addRTarg(String sw, Object obj) {
        if (validArgs.contains(sw)) {
            cmdArgs.put(sw, obj);
            return ArgError.NONE;
        } else if (DataType.isList(sw)) {
            if (dataType != null && dataType == DataType.VECTOR) {
                return ArgError.LIST_VECTOR_COMBO;
            }
            dataType = DataType.TCL_LIST;
            cmdArgs.put(sw, obj);
            return ArgError.NONE;
        } else if (DataType.isVector(sw)) {
            if (dataType != null && dataType == DataType.TCL_LIST) {
                return ArgError.LIST_VECTOR_COMBO;
            }
            dataType = DataType.VECTOR;
            cmdArgs.put(sw, obj);
            return ArgError.NONE;
        } else if (DataType.isGuess(sw)) {
            cmdArgs.put(sw, obj);
            return ArgError.NONE;
        } else if (DataType.isBound(sw)) {
            cmdArgs.put(sw, obj);
            return ArgError.NONE;
        } else {
            return ArgError.MISSING_ARG;
        }
    }


    /*
     * Minimal switch error checking. Performed before we
     * know what equation to use.
     */
    public boolean isValid(String sw) {
        return (validArgs.contains(sw)
                || DataType.isList(sw)
                || DataType.isVector(sw)
                || DataType.isGuess(sw)
                || DataType.isBound(sw));
    }

    private boolean isValidPostLoad(String sw) {
        return (validArgs.contains(sw));
    }
    //--------------------------------------------------------------

    /*
     * Add valid args
     */
    public void addValidArg(String sw) {
        validArgs.add(sw);
    }

    public void addValidArg(String[] sw) {
        for (int n = 0; n < sw.length; n++) {
            validArgs.add(sw[n]);
        }
    }

    /*
     * Used after probing equation class for valid function vars
     */
    public void addValidVars(VecID[] paramList) {
        String paramType = null;

        if (dataType == DataType.VECTOR) {
            paramType = DataType.VECTOR.toString();
        } else if (dataType == DataType.TCL_LIST) {
            paramType = DataType.TCL_LIST.toString();
        }

        for (int n = 0; n < paramList.length; n++) {
            String str = "-"
                    + paramList[n].toString()
                    + paramType;

            addValidArg(str);
        }
    }

    //----------------------------------------------------------------
    /*
     * Used after probing equation class for valid function vars
     */
    public void addValidParams(VecID[] paramList) {
        String paramType = DataType.GUESS.toString();
        String boundType = DataType.BOUND_PARAM.toString();

        for (int n = 0; n < paramList.length; n++) {
            String str = "-"
                    + paramList[n].toString()
                    + paramType;

            addValidArg(str);

            str = "-"
                    + paramList[n].toString()
                    + boundType;

            addValidArg(str);
        }
    }

    //----------------------------------------------------------------
    /*
     * Return a value from a single arg
     */
    public Object getArgVal(String sw) {
        return cmdArgs.get(sw);
    }

    /*
     * Used to retrieve serialized/tcllist data pointer and guess vals
     */
    public Object getData(VecID var) {
        String type = dataType.toString();

        return cmdArgs.get("-"
                + var.toString()
                + type);
    }

    public Object getParamData(VecID param) {
        String type = DataType.GUESS.toString();
        return cmdArgs.get("-"
                + param.toString()
                + type);
    }

    public Object getWeightData() {
        return cmdArgs.get(DataType.WGT_VECTOR);
    }

    public boolean isBound(VecID param) {
        String type = DataType.BOUND_PARAM.toString();

        return checkSwitch("-"
                + param.toString()
                + type);
    }

    /*
     * Used to retrieve full name of variable or param as found on the
     * command line.
     */
    //----------------------------------------------------------------
    public String getListName(VecID key) {
        return "-" + key.toString() + DataType.TCL_LIST.toString();
    }

    //No hyphen returned at beginning of string
    public String getVecName(VecID key) {
        return (key.toString() + DataType.VECTOR.toString());
    }

    public boolean isList() {
        return (dataType == DataType.TCL_LIST);
    }

    /*
     * Check for existence of a run-time switch
     */
    public boolean checkSwitch(String sw) {
        return cmdArgs.containsKey(sw);
    }

    /*
     * Routine to verify that the pre-defined equation varList
     * matches the switches passed via the command line.
     */
    public boolean verifyVarList(VecID[] varList) {
        boolean ret = true;
        String paramType;

        if (dataType == DataType.VECTOR) {
            paramType = DataType.VECTOR.toString();
        } else if (dataType == DataType.TCL_LIST) {
            paramType = DataType.TCL_LIST.toString();
        } else {
            //Should never end up here
            return false;
        }

        for (int n = 0; n < varList.length; n++) {
            String str = "-"
                    + varList[n].toString()
                    + paramType;

            ret = ret && checkSwitch(str);
        }

        return ret;
    }

    /*
     * Parameter version of verifyVarList.
     *
     */
    //TODO 002 - 041609 - Implement new EstParam and exploit it internal pending state
    public boolean verifyParamList(VecID[] paramList,
                                   EstParamSet estParams) {
        boolean ret = true;
        Enumeration e = cmdArgs.keys();

        //
        //Check command line guesses against vaild possibilities
        //
        while (e.hasMoreElements()) {
            String val = e.nextElement().toString();

            if (DataType.isGuess(val)) {
                ret = ret & isValidPostLoad(val);
            }
        }

        //
        //Early eggzit
        //
        if (ret == false) {
            return ret;
        }

        //
        //Build list of params that require auto-guessing
        //
        for (int i = 0; i < paramList.length; i++) {
            String str = "-"
                    + paramList[i].toString()
                    + DataType.GUESS.toString();

            if (!checkSwitch(str)) {
                estParams.setPending(paramList[i], true);
            } else {
                estParams.setPending(paramList[i], false);
            }

        }

        return ret;
    }

    /*
     * Errors returned by addRTargs
     *
     */
    public enum ArgError {

        NONE, MISSING_ARG, INVALID_ARG, LIST_VECTOR_COMBO;
    }

    /*
     * For classification of whether data input is vector or tclList based
     * also handles guesses
     */
    //----------------------------------------------------------------
    public enum DataType {

        NONE("none"),
        VECTOR("vec"),
        TCL_LIST("list"),
        SDEV_LIST("sdevlist"),
        SDEV_VECTOR("sdevvec"),
        WGT_VECTOR("weight"),
        BOUND_PARAM("bound"),
        GUESS("guess");

        DataType(String typeStr) {
            this.type = typeStr;
        }

        private final String type;

        @Override
        public String toString() {
            return type;
        }

        public static boolean isList(String str) {
            return (str.indexOf(TCL_LIST.toString()) == 2);
        }

        public static boolean isVector(String str) {
            return (str.indexOf(VECTOR.toString()) == 2);
        }

        public static boolean isGuess(String str) {
            return (str.indexOf(GUESS.toString()) == 2);
        }

        public static boolean isBound(String str) {
            boolean t = str.indexOf(BOUND_PARAM.toString()) == 2;
            return (t);
        }

        public static boolean isSwitch(String str) {
            boolean ret = false;

            if (str.length() >= 2) {
                ret = Character.isLetter(str.charAt(1)) && (str.charAt(0) == '-');
            }

            return ret;
        }
    }
}
