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

package org.nmrfx.structure.chemistry.constraints;

import org.nmrfx.chemistry.Atom;
import org.nmrfx.processor.datasets.peaks.Peak;
import java.io.Serializable;
import java.util.Vector;

public class DistanceConstraint implements Serializable {

    public static final Vector constraints = new Vector(64);
    public Atom previous;
    public Atom next;
    public Atom[] atom1;
    public Atom[] atom2;
    public Peak peak = null;
    public double intensity = 0.0;
    public double volume = 0.0;
    public double scale = 0.0;
    public double target = 0.0;
    public double lower = 0.0;
    public double upper = 0.0;
    public int dcClass = 0;
    public double active = 1;

    public DistanceConstraint(Peak p, Atom a1, Atom a2, double newScale) {
        atom1 = new Atom[1];
        atom2 = new Atom[1];
        atom1[0] = a1;
        atom2[0] = a2;
        peak = p;
        scale = newScale;
        constraints.addElement(this);
    }

    public static DistanceConstraint get(int i) {
        if ((i < 0) || (i >= constraints.size())) {
            return null;
        }

        return ((DistanceConstraint) constraints.elementAt(i));
    }


    /*
     public String disConToString (Interp interp, int iDiscon) throws TclException
     {
     TclObject list = null;
     list = TclList.newInstance ();
     // _Distance_constraint_ID
     TclList.append (interp, list, TclInteger.newInstance (iDiscon));
     // _Atom_one_mol_system_component_name
     TclList.append (interp, list, TclString.newInstance (entityName1[0]));
     // _Atom_one_residue_seq_code
     TclList.append (interp, list, TclString.newInstance (resNum1[0]));
     // _Atom_one_atom_name
     TclList.append (interp, list, TclString.newInstance (atomName1[0]));
     // _Atom_two_mol_system_component_name
     TclList.append (interp, list, TclString.newInstance (entityName2[0]));
     // _Atom_two_residue_seq_code
     TclList.append (interp, list, TclString.newInstance (resNum2[0]));
     // _Atom_two_atom_name
     TclList.append (interp, list, TclString.newInstance (atomName2[0]));
     // _Distance_value
     TclList.append (interp, list, TclDouble.newInstance (target));
     // _Distance_lower_bound_value
     TclList.append (interp, list, TclDouble.newInstance (lower));
     // _Distance_upper_bound_value
     TclList.append (interp, list, TclDouble.newInstance (upper));
     return (list.toString ());
     }
     */
 /*

     public String disConToXMLString (Interp interp, int iDisCon) throws TclException
     {
     StringBuffer result = new StringBuffer ();
     result.append ("<distanceConstraint ");
     result.append (" _Distance_constraint_ID=\"" + iDisCon + "\"");
     result.append (" _Atom_one_mol_system_component_name=\"" + entityName1[0] + "\"");
     result.append (" _Atom_one_residue_seq_code=\"" + resNum1[0] + "\"");
     result.append (" _Atom_one_atom_name=\"" + atomName1[0] + "\"");
     result.append (" _Atom_one_mol_system_component_name=\"" + entityName2[0] + "\"");
     result.append (" _Atom_one_residue_seq_code=\"" + resNum2[0] + "\"");
     result.append (" _Atom_one_atom_name=\"" + atomName2[0] + "\"");
     result.append ("\n<_Distance_value> ");
     result.append (target);
     result.append ("</_Distance_value>");
     result.append ("\n<_Distance_lower_bound_value> ");
     result.append (lower);
     result.append ("</_Distance_lower_bound_value>");
     result.append ("\n<_Distance_upper_bound_value> ");
     result.append (upper);
     result.append ("</_Distance_upper_bound_value>");
     result.append ("\n</distanceConstraint>");
     return (result.toString ());
     }

     public static void writeDisCon (Interp interp, String chanName) throws TclException
     {
     FileChannel chan = (FileChannel) TclIO.getChannel (interp, chanName);
     if (chan == null)
     {
     throw new TclException (interp, "Couln't find channel " + chanName);
     }

     try
     {
     chan.write (interp, "loop_\n");
     chan.write (interp, "  _Distance_constraint_ID\n");
     chan.write (interp, "  _Atom_one_mol_system_component_name\n");
     chan.write (interp, "  _Atom_one_residue_seq_code\n");
     chan.write (interp, "  _Atom_one_atom_name\n");
     chan.write (interp, "  _Atom_two_mol_system_component_name\n");
     chan.write (interp, "  _Atom_two_residue_seq_code\n");
     chan.write (interp, "  _Atom_two_atom_name\n");
     chan.write (interp, "  _Distance_value\n");
     chan.write (interp, "  _Distance_lower_bound_value\n");
     chan.write (interp, "  _Distance_upper_bound_value\n");
     }
     catch (IOException ioE)
     {
     throw new TclException (interp, "Error writing data");
     }

     TclObject list = TclList.newInstance ();
     for (int i = 0; i < constraints.size (); i++)
     {
     try
     {
     chan.write (interp, ((DistanceConstraint) constraints.elementAt (i)).disConToString (interp, i) + "\n");
     }
     catch (IOException ioE)
     {
     throw new TclException (interp, "Error writing data");
     }
     }
     try
     {
     chan.write (interp, "\nstop_\n\n");
     }
     catch (IOException ioE)
     {
     throw new TclException (interp, "Error writing data");
     }
     }
     public static void writeDisConToXML (Interp interp, String chanName) throws TclException
     {
     FileChannel chan = (FileChannel) TclIO.getChannel (interp, chanName);
     if (chan == null)
     {
     throw new TclException (interp, "Couln't find channel " + chanName);
     }

     for (int i = 0; i < constraints.size (); i++)
     {
     try
     {
     chan.write (interp, ((DistanceConstraint) constraints.elementAt (i)).disConToXMLString (interp, i) + "\n");
     }
     catch (IOException ioE)
     {
     throw new TclException (interp, "Error writing data");
     }
     }
     }
     */
}
