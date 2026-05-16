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

import org.biojava.nbio.alignment.Alignments;
import org.biojava.nbio.alignment.Alignments.PairwiseSequenceAlignerType;
import org.biojava.nbio.alignment.SimpleGapPenalty;
import org.biojava.nbio.alignment.template.GapPenalty;
import org.biojava.nbio.core.alignment.matrices.SubstitutionMatrixHelper;
import org.biojava.nbio.core.alignment.template.AlignedSequence;
import org.biojava.nbio.core.alignment.template.SequencePair;
import org.biojava.nbio.core.alignment.template.SubstitutionMatrix;
import org.biojava.nbio.core.exceptions.CompoundNotFoundException;
import org.biojava.nbio.core.sequence.ProteinSequence;
import org.biojava.nbio.core.sequence.compound.AminoAcidCompound;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public class SmithWatermanBioJava {

    private final String aString;
    private final String bString;
    private final ArrayList<Integer> indexA = new ArrayList<>();
    private final ArrayList<Integer> indexB = new ArrayList<>();
    AlignedSequence<ProteinSequence, AminoAcidCompound> queryAligned;
    AlignedSequence<ProteinSequence, AminoAcidCompound> targetAligned;
    SubstitutionMatrix<AminoAcidCompound> blosum62 = SubstitutionMatrixHelper.getBlosum62();

    public SmithWatermanBioJava(String aString, String bString) {
        this.aString = aString;
        this.bString = bString;
    }

    public AlignedSequence<ProteinSequence, AminoAcidCompound> getQueryAligned() {
        return queryAligned;
    }
    public AlignedSequence<ProteinSequence, AminoAcidCompound> getTargetAligned() {
        return targetAligned;
    }

    public List<Integer> getA() {
        return indexA;
    }

    public List<Integer> getB() {
        return indexB;
    }

    public short getBlosum(int queryPos, int targetPos) {
        AminoAcidCompound q = queryAligned.getOriginalSequence().getCompoundAt(queryPos);
        AminoAcidCompound t = targetAligned.getOriginalSequence().getCompoundAt(targetPos);
        return blosum62.getValue(q, t);
    }

    public OptionalInt getTargetIndex(
            int queryIndex) {

        int alignmentCol = queryAligned.getAlignmentIndexAt(queryIndex + 1);
        AminoAcidCompound targetCompound = targetAligned.getCompoundAt(alignmentCol);

        if (targetCompound.getShortName().equals("-")) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(targetAligned.getSequenceIndexAt(alignmentCol) - 1);
    }

    public SequencePair<ProteinSequence, AminoAcidCompound> doAlignment() throws IllegalArgumentException {
        ProteinSequence sequence1;
        ProteinSequence sequence2;
        try {
            sequence1 = new ProteinSequence(aString);
            sequence2 = new ProteinSequence(bString);
        } catch (CompoundNotFoundException cnfE) {
            throw new IllegalArgumentException(cnfE.getMessage());
        }
        GapPenalty gapPenalty = new SimpleGapPenalty();
        SequencePair<ProteinSequence, AminoAcidCompound> pair
                = Alignments.getPairwiseAlignment(sequence1, sequence2,
                PairwiseSequenceAlignerType.GLOBAL, gapPenalty, blosum62);

        queryAligned = pair.getQuery();
        targetAligned = pair.getTarget();

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
}
