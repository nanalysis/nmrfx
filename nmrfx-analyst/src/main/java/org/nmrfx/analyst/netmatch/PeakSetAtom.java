package org.nmrfx.analyst.netmatch;

/**
 *
 * @author brucejohnson
 */
class PeakSetAtom {

    final PeakSets peakSets;
    final int atom;

    PeakSetAtom(PeakSets peakSets, int atom) {
        this.peakSets = peakSets;
        this.atom = atom;
    }

}
