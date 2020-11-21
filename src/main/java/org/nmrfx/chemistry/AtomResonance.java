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
import org.nmrfx.structure.utilities.NvUtil;
import java.util.List;
import java.util.Map;

import org.nmrfx.peaks.PeakListBase;
import org.nmrfx.peaks.SimpleResonance;
import org.nmrfx.star.Loop;
import org.nmrfx.star.Saveframe;
import org.nmrfx.star.ParseException;

/**
 *
 * @author Bruce Johnson
 */
public class AtomResonance extends SimpleResonance {

    Atom atom = null;
    static String[] resonanceLoopStrings = {
        "_Resonance.ID",
        "_Resonance.Name",
        "_Resonance.Resonance_set_ID",
        "_Resonance.Spin_system_ID ",
        "_Resonance.Resonance_linker_list_ID ",};
    static String[] resonanceCovalentLinkStrings = {
        "_Resonance_covalent_link.Resonance_ID_1",
        "_Resonance_covalent_link.Resonance_ID_2",};

    Object resonanceSet = null;
    Object ssID = null;

    public AtomResonance(long id) {
        super(id);
    }

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
        AtomResonanceFactory resFactory = (AtomResonanceFactory) PeakListBase.resFactory();
        for (int i = 0, n = idColumn.size(); i < n; i++) {
            String value = null;
            long idNum = 0;
            if ((value = NvUtil.getColumnValue(idColumn, i)) != null) {
                idNum = NvUtil.toLong(value);
            } else {
                //throw new TclException(interp,"Invalid id \""+value+"\"");
                continue;
            }

            AtomResonance resonance = (AtomResonance) resFactory.get(idNum);
            if (resonance == null) {
                resonance = (AtomResonance) resFactory.build(idNum);
            }
            if ((value = NvUtil.getColumnValue(nameColumn, i)) != null) {
                resonance.setName(value);
            }
//            if ((value = NvUtil.getColumnValue(resSetColumn, i)) != null) {
//                long resSet = NvUtil.toLong(value);
//                ResonanceSet resonanceSet = ResonanceSet.get(resSet);
//                if (resonanceSet == null) {
//                    resonanceSet = ResonanceSet.newInstance(resSet);
//                }
//                resonanceSet.addResonance(resonance);
//            }
            /* FIXME handle spinSystem
             if ((value = NvUtil.getColumnValue(ssColumn,i)) != null) {
             long spinSystem = NvUtil.toLong(interp,value);
             resonance.setSpinSystem(spinSystem);
             }
             */
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
                String value = null;
                long idNum = 0;
                if ((value = NvUtil.getColumnValue(resSetIDColumn, i)) != null) {
                    idNum = NvUtil.toLong(value);
                } else {
                    //throw new TclException("Invalid peak id value at row \""+i+"\"");
                    continue;
                }
//                ResonanceSet resonanceSet = ResonanceSet.get(idNum);
//                if (resonanceSet == null) {
//                    System.out.println("Resonance set " + idNum + " doesn't exist");
//                    continue;
//                }
                String atomName = "";
                String entityName = "";
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
                    //throw new TclException("invalid compound in assignments saveframe \""+mapID+"\"");
                    System.err.println("invalid compound in assignments saveframe \"" + mapID + "\"");
                    continue;
                }
                Atom atom = compound.getAtomLoose(atomName);
                if (atom == null) {
                    if (atomName.equals("H")) {
                        atom = compound.getAtom(atomName + "1");
                    }
                }
                if (atom == null) {
                    System.err.println("invalid atom in assignments saveframe \"" + mapID + "." + atomName + "\"");
                } else {
//                    resonance.setAtom(atom);
                }
            }
        }
    }

    String toSTARResonanceString() {
        StringBuffer result = new StringBuffer();
        String sep = " ";
        char stringQuote = '"';
        result.append(String.valueOf(getID()) + sep);
        result.append(stringQuote);
        result.append(getName());
        result.append(stringQuote);
        result.append(sep);
        if (resonanceSet == null) {
            result.append(".");
        } else {
            // result.append(String.valueOf(resonanceSet.getID()));
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
