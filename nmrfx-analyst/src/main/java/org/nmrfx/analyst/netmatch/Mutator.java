package org.nmrfx.analyst.netmatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.apache.commons.math3.genetics.Chromosome;
import org.apache.commons.math3.genetics.MutationPolicy;
import org.apache.commons.math3.random.MersenneTwister;

/**
 *
 * @author brucejohnson
 */
public class Mutator implements MutationPolicy {

    MersenneTwister twister = new MersenneTwister();
    ArrayList<ArrayList<ItemMatch>> peakMatches;
    ArrayList<ArrayList<ItemMatch>> atomMatches;
    ArrayList<Integer> mutatable = new ArrayList<>();
    PeakMatcher peakMatcher;
    ArrayList<ItemMatch> sortedScores = new ArrayList<>();
    boolean useSorted = false;

    Mutator(PeakMatcher peakMatcher, ArrayList<ArrayList<ItemMatch>> peakMatches, ArrayList<ArrayList<ItemMatch>> atomMatches) {
        this.peakMatcher = peakMatcher;
        this.peakMatches = peakMatches;
        this.atomMatches = atomMatches;
        int i = 0;
        for (ArrayList<ItemMatch> matches : peakMatches) {
            if (matches.size() > 1) {
                mutatable.add(i);
            }
            i++;
        }
    }

    @Override
    public Chromosome mutate(Chromosome chrmsm) throws MathIllegalArgumentException {
        if (chrmsm instanceof PeakMatchChromosome) {
//            double[] localScores = peakMatcher.localScores;
//            sortedScores.clear();
//            for (int mutateSite : mutatable) {
//                double score = localScores[mutateSite];
//                sortedScores.add(new ItemMatch(mutateSite, score));
//            }
            Collections.sort(sortedScores);
            PeakMatchChromosome peakMatchChromo = (PeakMatchChromosome) chrmsm;
            List<Integer> rep = new ArrayList<Integer>(peakMatchChromo.getRep());
            int range = twister.nextInt(sortedScores.size());
            int j;
            if (useSorted) {
                if (range == 0) {
                    range = 1;
                }
                j = sortedScores.get(twister.nextInt(range)).itemNum;
            } else {
                j = sortedScores.get(range).itemNum;
            }
            int k = twister.nextInt(peakMatches.get(j).size());
            int iPeak = peakMatches.get(j).get(k).itemNum;
            int jCurrent = rep.get(j);
            boolean ok = false;
            for (int i = 0; i < rep.size(); i++) {
                if (rep.get(i) == iPeak) {
                    rep.set(i, jCurrent);
                    ok = true;
                    break;
                }
            }
            rep.set(j, iPeak);
            if (!ok) {
                throw new RuntimeException("couldn't find match");
            }
            return peakMatchChromo.newFixedLengthChromosome(rep);
        } else {
            throw new MathIllegalArgumentException(LocalizedFormats.valueOf("INVALID Chromosome type"), getClass().getSimpleName());
        }
    }
}
