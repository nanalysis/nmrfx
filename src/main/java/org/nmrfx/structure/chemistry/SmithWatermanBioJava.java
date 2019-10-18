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

package org.nmrfx.structure.chemistry;

import org.biojava.nbio.alignment.SimpleGapPenalty;
import org.biojava.nbio.alignment.SimpleSubstitutionMatrix;
import org.biojava.nbio.alignment.SmithWaterman;

import org.biojava.nbio.alignment.template.GapPenalty;
import org.biojava.nbio.alignment.template.SequencePair;
import org.biojava.nbio.alignment.template.SubstitutionMatrix;
import org.biojava.nbio.core.sequence.ProteinSequence;
import org.biojava.nbio.core.sequence.compound.AminoAcidCompound;
import org.biojava.nbio.core.sequence.template.Sequence;
import org.biojava.nbio.core.exceptions.CompoundNotFoundException;
import java.util.*;

public class SmithWatermanBioJava {

    private static final double MISMATCH_PENALTY = -0.5333;
    private final String aString;
    private final String bString;
    private final int nRows;
    private final int nCols;
    private final ArrayList<Integer> indexA = new ArrayList<Integer>();
    private final ArrayList<Integer> indexB = new ArrayList<Integer>();

    public SmithWatermanBioJava(String aString, String bString) {
        int n = aString.length();
        int m = bString.length();
        nRows = n + 1;
        nCols = m + 1;
        this.aString = aString;
        this.bString = bString;
    }

    public ArrayList<Integer> getA() {
        return indexA;
    }

    public ArrayList<Integer> getB() {
        return indexB;
    }

    public SequencePair doAlignment() throws IllegalArgumentException {
        ProteinSequence sequence1;
        ProteinSequence sequence2;
        try {
            sequence1 = new ProteinSequence(aString);
            sequence2 = new ProteinSequence(bString);
        } catch (CompoundNotFoundException cnfE) {
            throw new IllegalArgumentException(cnfE.getMessage());
        }
        GapPenalty gapPenalty = new SimpleGapPenalty();
        SubstitutionMatrix<AminoAcidCompound> matrix = SimpleSubstitutionMatrix.getBlosum62();
        SmithWaterman sm = new SmithWaterman(sequence1, sequence2, gapPenalty, matrix);
        Sequence q = sm.getQuery();
        Sequence t = sm.getTarget();
        SequencePair pair = sm.getPair();

        //System.out.printf("%s\n%d identical %d\n", pair, pair.getNumIdenticals(), pair.getLength());
        indexA.clear();
        indexB.clear();
        for (int i = 1; i <= sequence1.getLength(); i++) {
            int iq = pair.getIndexInTargetForQueryAt(i);
            int jq = pair.getIndexInQueryForTargetAt(iq);
            if (jq != i) {
                indexA.add(null);
            } else {
                indexA.add(iq);
            }
        }
        for (int i = 1; i <= sequence2.getLength(); i++) {
            int iq = pair.getIndexInQueryForTargetAt(i);
            int jq = pair.getIndexInTargetForQueryAt(iq);
            if (jq != i) {
                indexB.add(null);
            } else {
                indexB.add(iq);
            }
        }
        return pair;
    }

    public static void main(String args[]) {
        SmithWatermanBioJava smithWaterman = new SmithWatermanBioJava(args[0], args[1]);
    }
}
