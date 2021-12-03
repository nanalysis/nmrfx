/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.chemistry.relax;

import java.util.Arrays;
import java.util.Objects;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.peaks.Peak;

/**
 *
 * @author brucejohnson
 */
public class ResonanceSource implements Comparable<ResonanceSource> {

    final Peak peak;
    final Atom[] atoms;

    public ResonanceSource(Peak peak, Atom[] atoms) {
        this.atoms = atoms;
        this.peak = peak;
    }

    public ResonanceSource(Atom... atoms) {
        this.atoms = atoms;
        this.peak = null;
    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append("peak ").append(peak).append(" atoms:");
        for (Atom atom : atoms) {
            sBuilder.append(" ").append(atom.getFullName());
        }
        return sBuilder.toString();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 13 * hash + Objects.hashCode(this.peak);
        for (var atom : atoms) {
            hash = 13 * hash + Objects.hash(atom);
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ResonanceSource other = (ResonanceSource) obj;
        if (!Objects.equals(this.peak, other.peak)) {
            return false;
        }
        return Arrays.deepEquals(this.atoms, other.atoms);
    }

    public Atom getAtom() {
        return atoms[0];
    }

    public Atom[] getAtoms() {
        return atoms;
    }

    public Peak getPeak() {
        return peak;
    }

    @Override
    public int compareTo(ResonanceSource other) {
        int result = 0;
        if (this.equals(other)) {
            return result;
        } else {
            for (int i = 0; i < this.atoms.length; i++) {
                if (i >= other.atoms.length) {
                    result = 1;
                    break;
                }
                result = this.atoms[i].compareTo(other.atoms[i]);
                if (result != 0) {
                    break;
                }
            }
        }
        return result;
    }
}
