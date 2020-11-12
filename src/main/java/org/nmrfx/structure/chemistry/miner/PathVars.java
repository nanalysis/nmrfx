package org.nmrfx.structure.chemistry.miner;

import org.nmrfx.chemistry.AtomContainer;
import org.nmrfx.chemistry.IAtom;

public class PathVars {

    String command = "";
    String varName = "p";
    AtomContainer atomContainer = null;
    boolean ringsFound = false;
    boolean aromaticityChecked = false;
    IAtom atom = null;

    // setup event listener on atomContainer existence
    public PathVars(AtomContainer atomContainer, String varName) {
        this.atomContainer = atomContainer;
        this.varName = varName;
    }

    public void setCurrentAtom(final IAtom atom) {
        this.atom = atom;
    }

    public void traceProc(String propName) {
        Object propValue = atom.getProperty(propName);

    }

    public int getIndex(String indexString) {
        int index;

        try {
            index = Integer.parseInt(indexString);
        } catch (NumberFormatException nfE) {
            index = 0;
        }

        index--;

        return index;
    }

    String getPropName(String string) {
        int commaPos = string.indexOf(",");
        String propName = null;

        if (commaPos != -1) {
            propName = string.substring(commaPos + 1);
        }

        return propName;
    }

    void setProperty(String propName, String propValue) {
    }
}
