/*
 * NMRFx Structure : A Program for Calculating Structures
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
package org.nmrfx.chemistry.io;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AtomParser {
    public static final Map<String, String> pdbToIUPAC = new HashMap<>();
    private static final Map<String, String> iupacToPDB = new HashMap<>();
    private static final Map<String, String> xplorToIUPAC = new HashMap<>();
    private static final Map<String, String> iupacToXPLOR = new HashMap<>();
    private static final Map<String, String> map1To3 = new HashMap<>();
    private static final Map<String, String> map3To1 = new HashMap<>();

    public String resNum = "";
    public String resName = "";
    public String atomName = "";
    public String elemName = "";
    public String chainID = "";
    public String segment = "";
    public String loc = "";
    public String insertCode = "";
    public double x;
    public double y;
    public double z;
    public double occupancy = 1.0;
    public double bfactor = 1.0;
    public double charge = 0.0;
    public String temp = "";

    static {
        map1To3.put("A", "ALA");
        map1To3.put("D", "ASP");
        map1To3.put("N", "ASN");
        map1To3.put("R", "ARG");
        map1To3.put("C", "CYS");
        map1To3.put("E", "GLU");
        map1To3.put("Q", "GLN");
        map1To3.put("I", "ILE");
        map1To3.put("V", "VAL");
        map1To3.put("L", "LEU");
        map1To3.put("P", "PRO");
        map1To3.put("F", "PHE");
        map1To3.put("Y", "TYR");
        map1To3.put("W", "TRP");
        map1To3.put("K", "LYS");
        map1To3.put("M", "MET");
        map1To3.put("H", "HIS");
        map1To3.put("G", "GLY");
        map1To3.put("S", "SER");
        map1To3.put("T", "THR");
        for (String key : map1To3.keySet()) {
            map3To1.put(map1To3.get(key), key);
        }

        pdbToIUPAC.put("CYS,HB1", "HB2");
        pdbToIUPAC.put("CYS,HB2", "HB3");
        pdbToIUPAC.put("CYS,HN", "H");
        pdbToIUPAC.put("ASP,HB1", "HB2");
        pdbToIUPAC.put("ASP,HB2", "HB3");
        pdbToIUPAC.put("ASP,HN", "H");
        pdbToIUPAC.put("GLU,HB1", "HB2");
        pdbToIUPAC.put("GLU,HB2", "HB3");
        pdbToIUPAC.put("GLU,HG1", "HG2");
        pdbToIUPAC.put("GLU,HG2", "HG3");
        pdbToIUPAC.put("GLU,HN", "H");
        pdbToIUPAC.put("PHE,HB1", "HB2");
        pdbToIUPAC.put("PHE,HB2", "HB3");
        pdbToIUPAC.put("PHE,HN", "H");
        pdbToIUPAC.put("GLY,HA1", "HA2");
        pdbToIUPAC.put("GLY,HA2", "HA3");
        pdbToIUPAC.put("GLY,HN", "H");
        pdbToIUPAC.put("HIS,HB1", "HB2");
        pdbToIUPAC.put("HIS,HB2", "HB3");
        pdbToIUPAC.put("HIS,HN", "H");
        pdbToIUPAC.put("ILE,HG11", "HG12");
        pdbToIUPAC.put("ILE,HG12", "HG13");
        pdbToIUPAC.put("ILE,CD", "CD1");
        pdbToIUPAC.put("ILE,HD1", "HD11");
        pdbToIUPAC.put("ILE,HD2", "HD12");
        pdbToIUPAC.put("ILE,HD3", "HD13");
        pdbToIUPAC.put("ILE,HN", "H");
        pdbToIUPAC.put("LYS,HB1", "HB2");
        pdbToIUPAC.put("LYS,HB2", "HB3");
        pdbToIUPAC.put("LYS,HD1", "HD2");
        pdbToIUPAC.put("LYS,HD2", "HD3");
        pdbToIUPAC.put("LYS,HE1", "HE2");
        pdbToIUPAC.put("LYS,HE2", "HE3");
        pdbToIUPAC.put("LYS,HG1", "HG2");
        pdbToIUPAC.put("LYS,HG2", "HG3");
        pdbToIUPAC.put("LYS,HN", "H");
        pdbToIUPAC.put("LEU,HB1", "HB2");
        pdbToIUPAC.put("LEU,HB2", "HB3");
        pdbToIUPAC.put("LEU,HB2", "HB3");
        pdbToIUPAC.put("LEU,HN", "H");
        pdbToIUPAC.put("MET,HB1", "HB2");
        pdbToIUPAC.put("MET,HB2", "HB3");
        pdbToIUPAC.put("MET,HG1", "HG2");
        pdbToIUPAC.put("MET,HG2", "HG3");
        pdbToIUPAC.put("MET,HN", "H");
        pdbToIUPAC.put("ASN,HB1", "HB2");
        pdbToIUPAC.put("ASN,HB2", "HB3");
        pdbToIUPAC.put("ASN,HN", "H");
        pdbToIUPAC.put("PRO,HB1", "HB2");
        pdbToIUPAC.put("PRO,HB2", "HB3");
        pdbToIUPAC.put("PRO,HD1", "HD2");
        pdbToIUPAC.put("PRO,HD2", "HD3");
        pdbToIUPAC.put("PRO,HG1", "HG2");
        pdbToIUPAC.put("PRO,HG2", "HG3");
        pdbToIUPAC.put("PRO,HT1", "H2");
        pdbToIUPAC.put("PRO,HT2", "H3");
        pdbToIUPAC.put("GLN,HB1", "HB2");
        pdbToIUPAC.put("GLN,HB2", "HB3");
        pdbToIUPAC.put("GLN,HG1", "HG2");
        pdbToIUPAC.put("GLN,HG2", "HG3");
        pdbToIUPAC.put("GLN,HN", "H");
        pdbToIUPAC.put("ARG,HB1", "HB2");
        pdbToIUPAC.put("ARG,HB2", "HB3");
        pdbToIUPAC.put("ARG,HD1", "HD2");
        pdbToIUPAC.put("ARG,HD2", "HD3");
        pdbToIUPAC.put("ARG,HG1", "HG2");
        pdbToIUPAC.put("ARG,HG2", "HG3");
        pdbToIUPAC.put("ARG,HN", "H");
        pdbToIUPAC.put("SER,HB1", "HB2");
        pdbToIUPAC.put("SER,HB2", "HB3");
        pdbToIUPAC.put("SER,HN", "H");
        pdbToIUPAC.put("TRP,HB1", "HB2");
        pdbToIUPAC.put("TRP,HB2", "HB3");
        pdbToIUPAC.put("TRP,HN", "H");
        pdbToIUPAC.put("X,O", "O1");
        pdbToIUPAC.put("X,OXT", "O2");
        pdbToIUPAC.put("TYR,HB1", "HB2");
        pdbToIUPAC.put("TYR,HB2", "HB3");
        pdbToIUPAC.put("TYR,HN", "H");
        pdbToIUPAC.put("ALA,HN", "H");
        pdbToIUPAC.put("THR,HN", "H");
        pdbToIUPAC.put("VAL,HN", "H");

        xplorToIUPAC.put("CYS,HB1", "HB3");
        xplorToIUPAC.put("CYS,HN", "H");
        xplorToIUPAC.put("ASP,HB1", "HB3");
        xplorToIUPAC.put("ASP,HN", "H");
        xplorToIUPAC.put("GLU,HB1", "HB3");
        xplorToIUPAC.put("GLU,HG1", "HG3");
        xplorToIUPAC.put("GLU,HN", "H");
        xplorToIUPAC.put("PHE,HB1", "HB3");
        xplorToIUPAC.put("PHE,HN", "H");
        xplorToIUPAC.put("GLY,HA1", "HA3");
        xplorToIUPAC.put("GLY,HN", "H");
        xplorToIUPAC.put("HIS,HB1", "HB3");
        xplorToIUPAC.put("HIS,HN", "H");
        xplorToIUPAC.put("ILE,HG11", "HG13");
        xplorToIUPAC.put("ILE,CD", "CD1");
        xplorToIUPAC.put("ILE,HD1", "HD11");
        xplorToIUPAC.put("ILE,HD2", "HD12");
        xplorToIUPAC.put("ILE,HD3", "HD13");
        xplorToIUPAC.put("ILE,HN", "H");
        xplorToIUPAC.put("LYS,HB1", "HB3");
        xplorToIUPAC.put("LYS,HD1", "HD3");
        xplorToIUPAC.put("LYS,HE1", "HE3");
        xplorToIUPAC.put("LYS,HG1", "HG3");
        xplorToIUPAC.put("LYS,HN", "H");
        xplorToIUPAC.put("LEU,HB1", "HB3");
        xplorToIUPAC.put("LEU,HN", "H");
        xplorToIUPAC.put("MET,HB1", "HB3");
        xplorToIUPAC.put("MET,HG1", "HG3");
        xplorToIUPAC.put("MET,HN", "H");
        xplorToIUPAC.put("ASN,HB1", "HB3");
        xplorToIUPAC.put("ASN,HN", "H");
        xplorToIUPAC.put("PRO,HB1", "HB3");
        xplorToIUPAC.put("PRO,HD1", "HD3");
        xplorToIUPAC.put("PRO,HG1", "HG3");
        xplorToIUPAC.put("PRO,HT1", "H2");
        xplorToIUPAC.put("PRO,HT2", "H3");
        xplorToIUPAC.put("GLN,HB1", "HB3");
        xplorToIUPAC.put("GLN,HG1", "HG3");
        xplorToIUPAC.put("GLN,HN", "H");
        xplorToIUPAC.put("ARG,HB1", "HB3");
        xplorToIUPAC.put("ARG,HD1", "HD3");
        xplorToIUPAC.put("ARG,HG1", "HG3");
        xplorToIUPAC.put("ARG,HN", "H");
        xplorToIUPAC.put("SER,HB1", "HB3");
        xplorToIUPAC.put("SER,HN", "H");
        xplorToIUPAC.put("TRP,HB1", "HB3");
        xplorToIUPAC.put("TRP,HN", "H");
        xplorToIUPAC.put("X,O", "O1");
        xplorToIUPAC.put("X,OXT", "O2");
        xplorToIUPAC.put("TYR,HB1", "HB3");
        xplorToIUPAC.put("TYR,HN", "H");
        xplorToIUPAC.put("ALA,HN", "H");
        xplorToIUPAC.put("THR,HN", "H");
        xplorToIUPAC.put("VAL,HN", "H");

        pdbToIUPAC.put("DADE", "DA");
        pdbToIUPAC.put("RADE", "A");
        pdbToIUPAC.put("DA", "DA");
        pdbToIUPAC.put("RA", "A");
        pdbToIUPAC.put("A", "A");

        pdbToIUPAC.put("DCYT", "DC");
        pdbToIUPAC.put("RCYT", "C");
        pdbToIUPAC.put("DC", "DC");
        pdbToIUPAC.put("RC", "C");
        pdbToIUPAC.put("C", "C");

        pdbToIUPAC.put("DGUA", "DG");
        pdbToIUPAC.put("RGUA", "G");
        pdbToIUPAC.put("DG", "DG");
        pdbToIUPAC.put("RG", "G");
        pdbToIUPAC.put("G", "G");

        pdbToIUPAC.put("DTHY", "DT");
        pdbToIUPAC.put("T", "DT");
        pdbToIUPAC.put("RURA", "U");
        pdbToIUPAC.put("URA", "U");
        pdbToIUPAC.put("RU", "U");

        pdbToIUPAC.put("DT,O1P", "OP1");
        pdbToIUPAC.put("U,O1P", "OP1");
        pdbToIUPAC.put("DT,O2P", "OP2");
        pdbToIUPAC.put("U,O2P", "OP2");
        pdbToIUPAC.put("DT,O5*", "O5'");
        pdbToIUPAC.put("U,O5*", "O5'");
        pdbToIUPAC.put("DT,C5*", "C5'");
        pdbToIUPAC.put("U,C5*", "C5'");
        pdbToIUPAC.put("DT,H5*1", "H5'");
        pdbToIUPAC.put("U,H5*1", "H5'");
        pdbToIUPAC.put("DT,H5*2", "H5''");
        pdbToIUPAC.put("DT,H5\"", "H5''");
        pdbToIUPAC.put("U,H5*2", "H5''");
        pdbToIUPAC.put("U,H5\"", "H5''");
        pdbToIUPAC.put("DT,C4*", "C4'");
        pdbToIUPAC.put("U,C4*", "C4'");
        pdbToIUPAC.put("DT,H4*", "H4'");
        pdbToIUPAC.put("U,H4*", "H4'");
        pdbToIUPAC.put("DT,O4*", "O4'");
        pdbToIUPAC.put("U,O4*", "O4'");
        pdbToIUPAC.put("DT,C1*", "C1'");
        pdbToIUPAC.put("U,C1*", "C1'");
        pdbToIUPAC.put("DT,H1*", "H1'");
        pdbToIUPAC.put("U,H1*", "H1'");
        pdbToIUPAC.put("DT,C3*", "C3'");
        pdbToIUPAC.put("U,C3*", "C3'");
        pdbToIUPAC.put("DT,H3*", "H3'");
        pdbToIUPAC.put("U,H3*", "H3'");
        pdbToIUPAC.put("DT,C2*", "C2'");
        pdbToIUPAC.put("U,C2*", "C2'");
        pdbToIUPAC.put("DT,H2*1", "H2'");
        pdbToIUPAC.put("U,H2*1", "H2'");
        pdbToIUPAC.put("U,O2*", "O2'");
        pdbToIUPAC.put("DT,H*2", "H2''");
        pdbToIUPAC.put("DT,H\"", "H2''");
        pdbToIUPAC.put("U,HO*2", "HO2'");
        pdbToIUPAC.put("DT,O3*", "O3'");
        pdbToIUPAC.put("U,O3*", "O3'");
        pdbToIUPAC.put("DA,O1P", "OP1");
        pdbToIUPAC.put("A,O1P", "OP1");
        pdbToIUPAC.put("DA,O2P", "OP2");
        pdbToIUPAC.put("A,O2P", "OP2");
        pdbToIUPAC.put("DA,O5*", "O5'");
        pdbToIUPAC.put("A,O5*", "O5'");
        pdbToIUPAC.put("DA,C5*", "C5'");
        pdbToIUPAC.put("A,C5*", "C5'");
        pdbToIUPAC.put("DA,H5*1", "H5'");
        pdbToIUPAC.put("A,H5*1", "H5'");
        pdbToIUPAC.put("DA,H5*2", "H5''");
        pdbToIUPAC.put("DA,H5\"", "H5''");
        pdbToIUPAC.put("A,H5*2", "H5''");
        pdbToIUPAC.put("A,H5\"", "H5''");
        pdbToIUPAC.put("DA,C4*", "C4'");
        pdbToIUPAC.put("A,C4*", "C4'");
        pdbToIUPAC.put("DA,H4*", "H4'");
        pdbToIUPAC.put("A,H4*", "H4'");
        pdbToIUPAC.put("DA,O4*", "O4'");
        pdbToIUPAC.put("A,O4*", "O4'");
        pdbToIUPAC.put("DA,C1*", "C1'");
        pdbToIUPAC.put("A,C1*", "C1'");
        pdbToIUPAC.put("DA,H1*", "H1'");
        pdbToIUPAC.put("A,H1*", "H1'");
        pdbToIUPAC.put("DA,C3*", "C3'");
        pdbToIUPAC.put("A,C3*", "C3'");
        pdbToIUPAC.put("DA,H3*", "H3'");
        pdbToIUPAC.put("A,H3*", "H3'");
        pdbToIUPAC.put("DA,C2*", "C2'");
        pdbToIUPAC.put("A,C2*", "C2'");
        pdbToIUPAC.put("DA,H2*1", "H2'");
        pdbToIUPAC.put("A,H2*1", "H2'");
        pdbToIUPAC.put("A,O2*", "O2'");
        pdbToIUPAC.put("DA,H*2", "H2''");
        pdbToIUPAC.put("DA,H2\"", "H2''");
        pdbToIUPAC.put("A,HO*2", "HO2'");
        pdbToIUPAC.put("DA,O3*", "O3'");
        pdbToIUPAC.put("A,O3*", "O3'");
        pdbToIUPAC.put("DG,O1P", "OP1");
        pdbToIUPAC.put("G,O1P", "OP1");
        pdbToIUPAC.put("DG,O2P", "OP2");
        pdbToIUPAC.put("G,O2P", "OP2");
        pdbToIUPAC.put("DG,O5*", "O5'");
        pdbToIUPAC.put("G,O5*", "O5'");
        pdbToIUPAC.put("DG,C5*", "C5'");
        pdbToIUPAC.put("G,C5*", "C5'");
        pdbToIUPAC.put("DG,H5*1", "H5'");
        pdbToIUPAC.put("G,H5*1", "H5'");
        pdbToIUPAC.put("DG,H5*2", "H5''");
        pdbToIUPAC.put("DG,H5\"", "H5''");
        pdbToIUPAC.put("G,H5*2", "H5''");
        pdbToIUPAC.put("G,H5\"", "H5''");
        pdbToIUPAC.put("DG,C4*", "C4'");
        pdbToIUPAC.put("G,C4*", "C4'");
        pdbToIUPAC.put("DG,H4*", "H4'");
        pdbToIUPAC.put("G,H4*", "H4'");
        pdbToIUPAC.put("DG,O4*", "O4'");
        pdbToIUPAC.put("G,O4*", "O4'");
        pdbToIUPAC.put("DG,C1*", "C1'");
        pdbToIUPAC.put("G,C1*", "C1'");
        pdbToIUPAC.put("DG,H1*", "H1'");
        pdbToIUPAC.put("G,H1*", "H1'");
        pdbToIUPAC.put("DG,C3*", "C3'");
        pdbToIUPAC.put("G,C3*", "C3'");
        pdbToIUPAC.put("DG,H3*", "H3'");
        pdbToIUPAC.put("G,H3*", "H3'");
        pdbToIUPAC.put("DG,C2*", "C2'");
        pdbToIUPAC.put("G,C2*", "C2'");
        pdbToIUPAC.put("DG,H2*1", "H2'");
        pdbToIUPAC.put("G,H2*1", "H2'");
        pdbToIUPAC.put("G,O2*", "O2'");
        pdbToIUPAC.put("DG,H2\"", "H2''");
        pdbToIUPAC.put("G,HO*2", "HO2'");
        pdbToIUPAC.put("DG,O3*", "O3'");
        pdbToIUPAC.put("G,O3*", "O3'");
        pdbToIUPAC.put("DC,O1P", "OP1");
        pdbToIUPAC.put("C,O1P", "OP1");
        pdbToIUPAC.put("DC,O2P", "OP2");
        pdbToIUPAC.put("C,O2P", "OP2");
        pdbToIUPAC.put("DC,O5*", "O5'");
        pdbToIUPAC.put("C,O5*", "O5'");
        pdbToIUPAC.put("DC,C5*", "C5'");
        pdbToIUPAC.put("C,C5*", "C5'");
        pdbToIUPAC.put("DC,H5*1", "H5'");
        pdbToIUPAC.put("C,H5*1", "H5'");
        pdbToIUPAC.put("DC,H5\"", "H5''");
        pdbToIUPAC.put("C,H5*2", "H5''");
        pdbToIUPAC.put("C,H5\"", "H5''");
        pdbToIUPAC.put("DC,C4*", "C4'");
        pdbToIUPAC.put("C,C4*", "C4'");
        pdbToIUPAC.put("DC,H4*", "H4'");
        pdbToIUPAC.put("C,H4*", "H4'");
        pdbToIUPAC.put("DC,O4*", "O4'");
        pdbToIUPAC.put("C,O4*", "O4'");
        pdbToIUPAC.put("DC,C1*", "C1'");
        pdbToIUPAC.put("C,C1*", "C1'");
        pdbToIUPAC.put("DC,H1*", "H1'");
        pdbToIUPAC.put("C,H1*", "H1'");
        pdbToIUPAC.put("DC,C3*", "C3'");
        pdbToIUPAC.put("C,C3*", "C3'");
        pdbToIUPAC.put("DC,H3*", "H3'");
        pdbToIUPAC.put("C,H3*", "H3'");
        pdbToIUPAC.put("DC,C2*", "C2'");
        pdbToIUPAC.put("C,C2*", "C2'");
        pdbToIUPAC.put("DC,H2*1", "H2'");
        pdbToIUPAC.put("C,H2*1", "H2'");
        pdbToIUPAC.put("C,O2*", "O2'");
        pdbToIUPAC.put("DC,H2\"", "H2''");
        pdbToIUPAC.put("C,HO*2", "HO2'");
        pdbToIUPAC.put("DC,O3*", "O3'");
        pdbToIUPAC.put("C,O3*", "O3'");

        iupacToPDB.put("DA", "DA");
        iupacToPDB.put("A", "RA");
        iupacToPDB.put("DC", "DC");
        iupacToPDB.put("C", "RC");
        iupacToPDB.put("DG", "DG");
        iupacToPDB.put("G", "RG");
        iupacToPDB.put("DT", "DT");
        iupacToPDB.put("U", "RU");

        iupacToPDB.put("CYS,HB2", "HB1");
        iupacToPDB.put("CYS,HB3", "HB2");
        iupacToPDB.put("ASP,HB2", "HB1");
        iupacToPDB.put("ASP,HB3", "HB2");
        iupacToPDB.put("GLU,HB2", "HB1");
        iupacToPDB.put("GLU,HB3", "HB2");
        iupacToPDB.put("GLU,HG2", "HG1");
        iupacToPDB.put("GLU,HG3", "HG2");
        iupacToPDB.put("PHE,HB2", "HB1");
        iupacToPDB.put("PHE,HB3", "HB2");
        iupacToPDB.put("GLY,HA2", "HA1");
        iupacToPDB.put("GLY,HA3", "HA2");
        iupacToPDB.put("HIS,HB2", "HB1");
        iupacToPDB.put("HIS,HB3", "HB2");
        iupacToPDB.put("ILE,HG12", "HG11");
        iupacToPDB.put("ILE,HG13", "HG12");
        iupacToPDB.put("ILE,HD11", "HD1");
        iupacToPDB.put("ILE,HD12", "HD2");
        iupacToPDB.put("ILE,HD13", "HD3");
        iupacToPDB.put("LYS,HB2", "HB1");
        iupacToPDB.put("LYS,HB3", "HB2");
        iupacToPDB.put("LYS,HD2", "HD1");
        iupacToPDB.put("LYS,HD3", "HD2");
        iupacToPDB.put("LYS,HE2", "HE1");
        iupacToPDB.put("LYS,HE3", "HE2");
        iupacToPDB.put("LYS,HG2", "HG1");
        iupacToPDB.put("LYS,HG3", "HG2");
        iupacToPDB.put("LEU,HB2", "HB1");
        iupacToPDB.put("LEU,HB3", "HB2");
        iupacToPDB.put("MET,HB2", "HB1");
        iupacToPDB.put("MET,HB3", "HB2");
        iupacToPDB.put("MET,HG2", "HG1");
        iupacToPDB.put("MET,HG3", "HG2");
        iupacToPDB.put("ASN,HB2", "HB1");
        iupacToPDB.put("ASN,HB3", "HB2");
        iupacToPDB.put("PRO,H2", "HT1");
        iupacToPDB.put("PRO,H3", "HT2");
        iupacToPDB.put("PRO,HB2", "HB1");
        iupacToPDB.put("PRO,HB3", "HB2");
        iupacToPDB.put("PRO,HD2", "HD1");
        iupacToPDB.put("PRO,HD3", "HD2");
        iupacToPDB.put("PRO,HG2", "HG1");
        iupacToPDB.put("PRO,HG3", "HG2");
        iupacToPDB.put("GLN,HB2", "HB1");
        iupacToPDB.put("GLN,HB3", "HB2");
        iupacToPDB.put("GLN,HG2", "HG1");
        iupacToPDB.put("GLN,HG3", "HG2");
        iupacToPDB.put("ARG,HB2", "HB1");
        iupacToPDB.put("ARG,HB3", "HB2");
        iupacToPDB.put("ARG,HD2", "HD1");
        iupacToPDB.put("ARG,HD3", "HD2");
        iupacToPDB.put("ARG,HG2", "HG1");
        iupacToPDB.put("ARG,HG3", "HG2");
        iupacToPDB.put("SER,HB2", "HB1");
        iupacToPDB.put("SER,HB3", "HB2");
        iupacToPDB.put("TRP,HB2", "HB1");
        iupacToPDB.put("TRP,HB3", "HB2");
        iupacToPDB.put("X,O1", "O");
        iupacToPDB.put("X,O2", "OXT");
        iupacToPDB.put("TYR,HB2", "HB1");
        iupacToPDB.put("TYR,HB3", "HB2");

        iupacToXPLOR.put("CYS,HB3", "HB1");
        iupacToXPLOR.put("ASP,HB3", "HB1");
        iupacToXPLOR.put("GLU,HB3", "HB1");
        iupacToXPLOR.put("GLU,HG3", "HG1");
        iupacToXPLOR.put("PHE,HB3", "HB1");
        iupacToXPLOR.put("GLY,HA3", "HA1");
        iupacToXPLOR.put("HIS,HB3", "HB1");
        iupacToXPLOR.put("ILE,HG13", "HG11");
        iupacToXPLOR.put("LYS,HB3", "HB1");
        iupacToXPLOR.put("LYS,HD3", "HD1");
        iupacToXPLOR.put("LYS,HE3", "HE1");
        iupacToXPLOR.put("LYS,HG3", "HG1");
        iupacToXPLOR.put("LEU,HB3", "HB1");
        iupacToXPLOR.put("MET,HB3", "HB1");
        iupacToXPLOR.put("MET,HG3", "HG1");
        iupacToXPLOR.put("ASN,HB3", "HB1");
        iupacToXPLOR.put("PRO,H2", "HT1");
        iupacToXPLOR.put("PRO,HB3", "HB1");
        iupacToXPLOR.put("PRO,HD3", "HD1");
        iupacToXPLOR.put("PRO,HG3", "HG1");
        iupacToXPLOR.put("GLN,HB3", "HB1");
        iupacToXPLOR.put("GLN,HG3", "HG1");
        iupacToXPLOR.put("ARG,HB3", "HB1");
        iupacToXPLOR.put("ARG,HD3", "HD1");
        iupacToXPLOR.put("ARG,HG3", "HG1");
        iupacToXPLOR.put("SER,HB3", "HB1");
        iupacToXPLOR.put("TRP,HB3", "HB1");
        iupacToXPLOR.put("X,O1", "O");
        iupacToXPLOR.put("X,O2", "OXT");
        iupacToXPLOR.put("TYR,HB3", "HB1");

    }

    public static String iupacToCurrent(String resName, String atomName) {
        if (!org.nmrfx.chemistry.io.PDBFile.isIUPACMode()) {
            String newName = (String) iupacToPDB.get(resName.toUpperCase() + "," + atomName.toUpperCase());
            if (newName != null) {
                atomName = newName.toLowerCase();
            }
        }
        return atomName;

    }

    public static String pdbToIUPAC(String resName, String atomName) {
        String newName = (String) pdbToIUPAC.get(resName.toUpperCase() + "," + atomName.toUpperCase());
        if (newName != null) {
            atomName = newName.toLowerCase();
        }
        return atomName;
    }

    public static String iupacToPDB(String resName, String atomName) {
        String newName = (String) iupacToPDB.get(resName.toUpperCase() + "," + atomName.toUpperCase());
        if (newName != null) {
            atomName = newName.toLowerCase();
        }
        return atomName;
    }

    public static String xplorToIUPAC(String resName, String atomName) {
        String newName = (String) xplorToIUPAC.get(resName.toUpperCase() + "," + atomName.toUpperCase());
        if (newName != null) {
            atomName = newName.toLowerCase();
        }
        return atomName;
    }

    public static String iupacToXPLOR(String resName, String atomName) {
        String newName = (String) iupacToXPLOR.get(resName.toUpperCase() + "," + atomName.toUpperCase());
        if (newName != null) {
            atomName = newName.toLowerCase();
        }
        return atomName;
    }

    public static String currentToIUPAC(String resName, String atomName) {
        if (!org.nmrfx.chemistry.io.PDBFile.isIUPACMode()) {
            String newName = (String) pdbToIUPAC.get(resName.toUpperCase() + "," + atomName.toUpperCase());
            if (newName != null) {
                atomName = newName.toLowerCase();
            }
        }
        return atomName;

    }

    public static boolean isResNameConsistant(String currentName, String testName) {
        boolean ok = false;
        if (currentName.equalsIgnoreCase(testName)) {
            ok = true;
        } else {
            String mappedName = pdbToIUPAC.get(currentName);
            if ((mappedName != null) && mappedName.equalsIgnoreCase(testName)) {
                ok = true;
            }
        }
        return ok;
    }

    public static String pdbResToPRFName(String resName, char RD) {
        if (resName.toUpperCase().equals("URA")) {
            resName = "u";
        } else if (resName.length() < 3) {
            String newName = (String) pdbToIUPAC.get(resName.toUpperCase());
            if (newName != null) {
                resName = newName.toLowerCase();
                if (((RD == 'r') || (RD == 'd')) && (resName.length() == 3)) {
                    resName = RD + resName;
                }
            }
        }
        return resName;
    }

    public static Set<String> getAANames() {
        return map3To1.keySet();
    }

    public static String convert1To3(String s) {
        return map1To3.get(s);
    }

    public static String convert3To1(String s) {
        return map3To1.get(s);
    }
}
