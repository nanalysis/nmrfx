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
package org.nmrfx.chemistry;

import org.nmrfx.chemistry.io.NMRStarReader;
import org.nmrfx.chemistry.utilities.NvUtil;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.peaks.ResonanceFactory;
import org.nmrfx.peaks.SimpleResonance;
import org.nmrfx.star.Loop;
import org.nmrfx.star.ParseException;
import org.nmrfx.star.Saveframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * @author Bruce Johnson
 */
public class AtomResonance extends SimpleResonance {

    private static final Logger log = LoggerFactory.getLogger(AtomResonance.class);

    Atom atom = null;
    public final static String[] resonanceLoopStrings = {
            "_Resonance.ID",
            "_Resonance.Name",
            "_Resonance.Resonance_set_ID",
            "_Resonance.Spin_system_ID ",
            "_Resonance.Resonance_linker_list_ID ",};
    public final static String[] resonanceCovalentLinkStrings = {
            "_Resonance_covalent_link.Resonance_ID_1",
            "_Resonance_covalent_link.Resonance_ID_2",};

    Object resonanceSet = null;
    Object ssID = null;
    boolean labelValid = true;

    public AtomResonance(long id) {
        super(id);
    }

    public AtomResonance copy() {
        AtomResonance copy = new AtomResonance(getID());
        copy.getPeakDims().addAll(getPeakDims());
        if (getNames() != null) {
            copy.setName(getNames());
            copy.setName(getName());
        }
        copy.setAtomName(getAtomName());
        copy.atom = atom;
        return copy;
    }

    @Override
    public void setName(List<String> newNames) {
        super.setName(newNames);
        boolean valid = true;
        for (var name : newNames) {
            if (!isLabelValid(name)) {
                valid = false;
                break;
            }
        }
        labelValid = valid;
    }

    private boolean isLabelValid(String name) {
        MoleculeBase molBase = MoleculeFactory.getActive();
        boolean result = true;
        if (!name.isBlank() && (molBase != null)) {
            Atom testAtom = molBase.findAtom(name);
            result = testAtom != null;
        }
        return result;
    }

    @Override
    public boolean isLabelValid() {
        return labelValid;
    }

    @Override
    public String getAtomName() {
        if (atom != null) {
            return atom.getFullName();
        } else {
            return super.getAtomName();
        }
    }

    public void setAtom(Atom atom) {
        this.atom = atom;
    }

    public Atom getAtom() {
        return atom;
    }

    public Atom getPossibleAtom() {
        if (atom != null) {
            return atom;
        } else {
            Atom possibleAtom = null;
            MoleculeBase molBase = MoleculeFactory.getActive();
            String name = getName();
            if (!name.isBlank() && (molBase != null)) {
                possibleAtom = molBase.findAtom(name);
            }
            return possibleAtom;
        }
    }

    public static void processSTAR3ResonanceList(final NMRStarReader nmrStar,
                                                 Saveframe saveframe, Map<String, Compound> compoundMap) throws ParseException {
        // fixme unused String listName = saveframe.getValue(interp,"_Resonance_linker_list","Sf_framecode");
        // FIXME String details = saveframe.getValue(interp,"_Resonance_linker_list","Details");

        //  FIXME Should have Resonance lists PeakList peakList = new PeakList(listName,nDim);
        Loop loop = saveframe.getLoop("_Resonance");
        if (loop == null) {
            throw new ParseException("No \"_Resonance\" loop");
        }
        List<String> idColumn = loop.getColumnAsList("ID");
        List<String> nameColumn = loop.getColumnAsList("Name");
        List<String> resSetColumn = loop.getColumnAsList("Resonance_set_ID");
        // fixme unused ArrayList ssColumn = loop.getColumnAsList("Spin_system_ID");
        ResonanceFactory resFactory = PeakList.resFactory();
        for (int i = 0, n = idColumn.size(); i < n; i++) {
            String value;
            long idNum;
            if ((value = NvUtil.getColumnValue(idColumn, i)) != null) {
                idNum = NvUtil.toLong(value);
            } else {
                continue;
            }

            AtomResonance resonance = (AtomResonance) resFactory.get(idNum);
            if (resonance == null) {
                resonance = (AtomResonance) resFactory.build(idNum);
            }
            if ((value = NvUtil.getColumnValue(nameColumn, i)) != null) {
                resonance.setName(value);
            }
        }

        loop = saveframe.getLoop("_Resonance_assignment");
        if (loop != null) {
            List<String> resSetIDColumn = loop.getColumnAsList("Resonance_set_ID");
            List<String> entityAssemblyIDColumn = loop.getColumnAsList("Entity_assembly_ID");
            List<String> entityIDColumn = loop.getColumnAsList("Entity_ID");
            List<String> compIdxIDColumn = loop.getColumnAsList("Comp_index_ID");
            List<String> compIDColumn = loop.getColumnAsList("Comp_ID");
            List<String> atomIDColumn = loop.getColumnAsList("Atom_ID");
            // fixme unused ArrayList atomSetIDColumn = loop.getColumnAsList("Atom_set_ID");
            for (int i = 0, n = resSetIDColumn.size(); i < n; i++) {
                String value;
                long idNum = 0;
                if ((value = NvUtil.getColumnValue(resSetIDColumn, i)) != null) {
                    idNum = NvUtil.toLong(value);
                } else {
                    continue;
                }
                String atomName = "";
                String iRes = "";
                String entityAssemblyID = "";
                String entityID = "";
                if ((value = NvUtil.getColumnValue(entityAssemblyIDColumn, i)) != null) {
                    entityAssemblyID = value;
                }
                if (entityAssemblyID.equals("")) {
                    entityAssemblyID = "1";
                }
                if ((value = NvUtil.getColumnValue(entityIDColumn, i)) != null) {
                    entityID = value;
                } else {
                    throw new ParseException("No entity ID");
                }
                if ((value = NvUtil.getColumnValue(compIdxIDColumn, i)) != null) {
                    iRes = value;
                } else {
                    throw new ParseException("No compound ID");
                }
                if ((value = NvUtil.getColumnValue(atomIDColumn, i)) != null) {
                    atomName = value;
                } else {
                    throw new ParseException("No atom ID");
                }
                // fixme if ((value = NvUtil.getColumnValue(atomSetIDColumn,i)) != null) {
                // fixme unused atomSetNum = NvUtil.toLong(interp,value);
                //}

                String mapID = entityAssemblyID + "." + entityID + "." + iRes;
                Compound compound = compoundMap.get(mapID);
                if (compound == null) {
                    log.warn("invalid compound in assignments saveframe \"{}\"", mapID);
                    continue;
                }
                Atom atom = compound.getAtomLoose(atomName);
                if (atom == null) {
                    if (atomName.equals("H")) {
                        atom = compound.getAtom(atomName + "1");
                    }
                }
                if (atom == null) {
                    log.warn("invalid atom in assignments saveframe \"{}.{}\"", mapID, atomName);
                } else {
                }
            }
        }
    }

    public String toSTARResonanceString() {
        StringBuilder result = new StringBuilder();
        String sep = " ";
        char stringQuote = '"';
        result.append(String.valueOf(getID())).append(sep);
        result.append(stringQuote);
        result.append(getName());
        result.append(stringQuote);
        result.append(sep);
        if (resonanceSet == null) {
            result.append(".");
        }
        result.append(sep);
        if (ssID == null) {
            result.append(".");
        } else {
            result.append(ssID);
        }
        result.append(sep);
        result.append("1");
        return result.toString();
    }

}
