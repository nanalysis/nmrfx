package org.nmrfx.analyst.netmatch;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author brucejohnson
 */
public class PeakSets {

    String type;
    // list of expected groups of atoms for this peak set
    List<AtomValue> valuesAtom = new ArrayList<>();
    // list of the measured peaks in this peak set
    List<PeakValue> valuesPeak = new ArrayList<>();
    // list of list of the possible peaks that could match atoms
    List<List<ItemMatch>> peakMatches = new ArrayList<>();
    // list of list of the possible atoms that could match peaks
    List<List<ItemMatch>> atomMatches = new ArrayList<>();
    // match of atoms to peaks obtained from bipartite matcher
    int[] bestMatching;
    // match of atoms to peaks obtained by modifiying the bestMatching values
    int[] trialMatching;

    PeakSets(String type) {
        this.type = type;
    }

    void setMatching(int[] matching) {
        this.bestMatching = matching.clone();
    }

    int[] getMatching(boolean useBest) {
        if (useBest) {
            return bestMatching;
        } else {
            return trialMatching;
        }
    }

    List<AtomValue> getAtoms() {
        return valuesAtom;
    }

    List<PeakValue> getPeaks() {
        return valuesPeak;
    }

    double getProbability(int iAtom, int jPeak) {
        List<ItemMatch> atomPeakMatch = peakMatches.get(iAtom);
        for (ItemMatch peakMatch : atomPeakMatch) {
            if (peakMatch.itemNum == jPeak) {
                return peakMatch.probability;
            }
        }
        return 0.0;
    }

    /*
     * Build a list of items that could be swapped
     *   to be used by a mutator that would randomly select one of the items and swap it
     */

    void findSwaps() {
    }

    void genTrial() {
//        for (ArrayList<PeakMatcher.ItemMatch> itemMatches : atomMatches) {
//            int nMatches = itemMatches.size();
//            System.out.println(nMatches);
//        }
        trialMatching = bestMatching.clone();
    }
}
