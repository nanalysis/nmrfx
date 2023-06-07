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
package org.nmrfx.processor.datasets.vendor.varian;

import org.nmrfx.processor.datasets.vendor.NMRParException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

public class VNMRPar {
    private static final Logger log = LoggerFactory.getLogger(VNMRPar.class);

    private static final Map<String, Map> parGroups = new HashMap<>();
    private static long nParGroups = 0;
    public String name = null;
    String attrLine = null;
    String[] valueLines = null;
    String enumLine = null;
    String valueList = null;

    public VNMRPar(String handle, String newName, String value) {
        name = newName.intern();
        this.valueList = value;
        Map<String, VNMRPar> parMap = parGroups.get(handle);

        if (parMap == null) {
            parMap = new HashMap();
            parGroups.put(handle, parMap);
        }
        parMap.put(newName, this);
    }

    public VNMRPar(String handle, String newName, String newAttrLine,
                   String[] newValueLines, String newEnumLine) {
        name = newName.intern();
        attrLine = newAttrLine.intern();
        enumLine = newEnumLine.intern();
        valueLines = new String[newValueLines.length];

        for (int i = 0; i < valueLines.length; i++) {
            valueLines[i] = newValueLines[i].intern();
        }

        Map<String, VNMRPar> parMap = parGroups.get(handle);

        if (parMap == null) {
            parMap = new HashMap();
            parGroups.put(handle, parMap);
        }

        parMap.put(newName, this);
    }

    public static String getNextHandle() {
        nParGroups++;
        return "vpar" + nParGroups;
    }

    public static void removeParGroup(String handle) {
        Map parMap = (Map) parGroups.get(handle);
        if (parMap != null) {
            parMap.clear();
        }
        parGroups.remove(handle);
    }

    public static VNMRPar getPar(String handle, String name) {
        Map parMap = (Map) parGroups.get(handle);

        if (parMap == null) {
            return null;
        }

        // check if name in map before getting it
        return (VNMRPar) parMap.get(name);
    }

    public static ArrayList<String> getPars(String handle) {
        Map parMap = (Map) parGroups.get(handle);

        if (parMap == null) {
            return new ArrayList<>();
        }
        ArrayList<String> aList = new ArrayList<String>();
        aList.addAll(parMap.keySet());
        Collections.sort(aList);
        return aList;
    }

    /**
     * get parameters from a Varian procpar file entry point for VarianData
     *
     * @param fpath : full path to the procpar file
     * @return a HashMap of parameter names and values
     */
    public static LinkedHashMap<String, String> getParMap(String fpath) {
        LinkedHashMap<String, String> hmap = null;
        try {
            String handle = processVNMRParFile(fpath);
            ArrayList<String> parNames = getPars(handle);
            hmap = new LinkedHashMap<>();
            for (String parName : parNames) {
                VNMRPar vp = getPar(handle, parName);
                if (vp != null) {
                    hmap.put(parName, vp.getJValues());
                }
            }
            removeParGroup(handle);
        } catch (NMRParException ex) {
            log.warn(ex.getMessage(), ex);
        }
        return hmap;
    }

    /**
     * get parameters from a Varian procpar file entry point for VarianData
     *
     * @param fpath   : full path to the procpar file
     * @param parlist : list of space-separated parameters
     * @return a HashMap of parameter names and values
     */
    public static LinkedHashMap<String, String> getParMap(String fpath, String parlist) {
        LinkedHashMap<String, String> hmap = null;
        try {
            String handle = processVNMRParFile(fpath);
            String[] parNames = parlist.split(" ");
            hmap = new LinkedHashMap();
            for (String parName : parNames) {
                VNMRPar vp = getPar(handle, parName);
                if (vp != null) {
                    hmap.put(parName, vp.getJValues());
                }
            }
            removeParGroup(handle);
        } catch (NMRParException ex) {
            log.warn(ex.getMessage(), ex);
        }
        return hmap;
    }

    public String getAttributes() {
        return attrLine;
    }

    /* From VNMR User Programming Manual
     Line 1 contains the attributes of the parameter and has the following fields (given in 
     same order as they appear in the file): 
     name is the parameter name, which can be any valid string. 
     subtype is an integer value for the parameter type:
     0 (undefined), 1 (real), 2 (string), 3 (delay), 4 (flag), 5 (frequency), 6 (pulse), 7 (integer). 
     basictype is an integer value:
     0 (undefined), 1 (real), 2 (string). 
     maxvalue is a real number for the maximum value that the parameter can contain, or 
     an index to a maximum value in the parameter parmax (found in 
     /vnmr/conpar). Applies to both string and real types of parameters. 
     minvalue is a real number for the minimum value that the parameter can contain or 
     an index to a minimum value in the parameter parmin (found in 
     /vnmr/conpar). Applies to real types of parameters only.
     stepsize is a real number for the step size in which parameters can be entered or 
     index to a step size in the parameter parstep (found in /vnmr/conpar). If 
     stepsize is 0, it is ignored. Applies to real types only. 
     Ggroup is an integer value:
     0 (ALL), 1 (SAMPLE), 2 (ACQUISITION), 3 (PROCESSING), 4 (DISPLAY), 5 (SPIN). 
     Dgroup is an integer value. The specific application determines the usage of this integer. 
     protection is a 32-bit word made up of the following bit masks, which are summed 
     to form the full mask: 
     active is an integer value:
     0 (not active), 1 (active).
     intptr is not used (generally set to 64). 
     */
    public String getRawEntry() {
        StringBuilder result = new StringBuilder();
        result.append(attrLine);
        result.append('\n');
        for (String valueLine : valueLines) {
            result.append(valueLine);
            result.append('\n');
        }
        result.append(enumLine);
        return result.toString();
    }

    public void setJValue(String value) {
        valueList = value;
    } // end setJValue

    public String getJValues() {
        if (valueList != null) {
            return valueList;
        } else {
            String[] fields = attrLine.split("\\s");
            String type = fields[2];
            String active = fields[9];

            if (type.equals("1")) {
                String[] values = valueLines[0].split("\\s");
                if (values.length < 2) {
                    return "";
                }
                if (active.equals("0")) {
                    return "n";
                }
                if (values.length == 2) {
                    return values[1];
                } else {
                    StringBuilder result = new StringBuilder();
                    for (int i = 1; i < values.length; i++) {
                        result.append(values[i]).append("\n");
                    }
                    return result.toString();
                }
            } else if (valueLines.length == 1) {
                int quotePos1 = valueLines[0].indexOf('"');
                int quotePos2 = valueLines[0].lastIndexOf('"');
                return valueLines[0].substring(quotePos1 + 1, quotePos2);
            } else {
                StringBuilder result = new StringBuilder();
                int quotePos1 = valueLines[0].indexOf('"');
                int quotePos2 = valueLines[0].lastIndexOf('"');
                result.append(valueLines[0].substring(quotePos1 + 1, quotePos2)).append("\n");
                for (int i = 1; i < valueLines.length; i++) {
                    quotePos1 = valueLines[i].indexOf('"');
                    quotePos2 = valueLines[i].lastIndexOf('"');
                    result.append(valueLines[i].substring(quotePos1 + 1, quotePos2)).append("\n");
                }
                return result.toString();
            }
        }
    } // end getJValues

    static public String processVNMRParFile(String fileName)
            throws NMRParException {
        String handle;
        LineNumberReader lineReader = null;
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(fileName);
            lineReader = new LineNumberReader(fileReader);
        } catch (FileNotFoundException fnfE) {
            throw new NMRParException(fnfE.getMessage());
        }
        handle = processVNMRPar(lineReader);
        try {
            fileReader.close();
            lineReader.close();
        } catch (IOException ioE) {
            throw new NMRParException(ioE.getMessage());
        }
        return handle;
    }

    static String processVNMRPar(String data)
            throws NMRParException {
        String handle;
        LineNumberReader lineReader = new LineNumberReader(new StringReader(data));
        handle = processVNMRPar(lineReader);
        try {
            lineReader.close();
        } catch (IOException ioE) {
            throw new NMRParException(ioE.getMessage());
        }
        return handle;
    }

    static String processVNMRPar(LineNumberReader lineReader)
            throws NMRParException {

        String handle = VNMRPar.getNextHandle();

        try {
            while (lineReader.ready()) {
                String attrLine = lineReader.readLine();

                if (attrLine == null) {
                    break;
                }

                String[] fields = attrLine.split("\\s");
                if (fields.length < 3) {
                    throw new NMRParException("error reading data from procpar string " + attrLine);
                }
                String parName = fields[0];
                String type = fields[2];
                String s1 = getValueLine(lineReader);
                String[] valueLines;

                if (type.equals("2")) {
                    String[] values = s1.split("\\s");
                    int nLines = Integer.parseInt(values[0]);
                    valueLines = new String[nLines];
                    valueLines[0] = s1.intern();

                    for (int i = 1; i < nLines; i++) {
                        valueLines[i] = getValueLine(lineReader).intern();
                    }
                } else {
                    valueLines = new String[1];
                    valueLines[0] = s1.intern();
                }

                String enumLine = getValueLine(lineReader).intern();
                new VNMRPar(handle, parName, attrLine,
                        valueLines, enumLine);
            }
        } catch (IOException ioE) {
            throw new NMRParException("error reading data from procpar string");
        }

        return handle;
    }

    static String getValueLine(LineNumberReader lineReader)
            throws NMRParException {
        try {
            String s1 = lineReader.readLine();

            if (s1 == null) {
                throw new NMRParException(
                        "Unexpected end of data while parsing VNMR procpar");
            }

            int firstQuote = s1.indexOf('"');
            int lastQuote = s1.lastIndexOf('"');

            if ((firstQuote != -1) && (firstQuote == lastQuote)) {
                StringBuilder sbuf = new StringBuilder();
                sbuf.append(s1);

                while (true) {
                    int len = s1.length();

                    if ((len > 0) && (s1.charAt(len - 1) == '"')) {
                        break;
                    }

                    s1 = lineReader.readLine();

                    if (s1 == null) {
                        throw new NMRParException(
                                "Unexpected end of data while parsing VNMR procpar");
                    }
                    sbuf.append('\n');
                    sbuf.append(s1);
                }

                s1 = sbuf.toString();
            }

            return s1;
        } catch (IOException ioE) {
            throw new NMRParException(
                    "Unexpected end of data while parsing VNMR procpar");
        }
    }

}
