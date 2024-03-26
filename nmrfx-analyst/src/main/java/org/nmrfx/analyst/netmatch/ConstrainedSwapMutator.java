package org.nmrfx.analyst.netmatch;

import io.jenetics.*;
import io.jenetics.util.MSeq;
import io.jenetics.util.Seq;
import org.apache.commons.math3.random.MersenneTwister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * @author Bruce Johnson
 */
public class ConstrainedSwapMutator<
        G extends Gene<?, G>,
        C extends Comparable<? super C>
        > extends SwapMutator<G, C> {

    MersenneTwister twister = new MersenneTwister();
    List<List<ItemMatch>> peakMatches;
    ArrayList<Integer> mutatable = new ArrayList<>();
    PeakMatcher peakMatcher;
    ArrayList<ItemMatch> sortedScores = new ArrayList<>();
    boolean useSorted = false;
    double startProbability;
    double probability;
    double minRate = 0.0001;
    double startRate = 0.02;
    double maxRate = 0.2;
    double gamma = 0.001;

    public ConstrainedSwapMutator(double p) {
        super(p);
        probability = p;
        startProbability = p;
        System.out.println("prob " + probability);

    }

    ConstrainedSwapMutator(PeakMatcher peakMatcher, List<List<ItemMatch>> peakMatches, double p) {
        super(p);
        this.peakMatcher = peakMatcher;
        this.peakMatches = peakMatches;
        // build a list of all the indices of atoms that have more than 1 peak match
        int i = 0;
        for (List<ItemMatch> matches : peakMatches) {
            if (matches.size() > 1) {
                mutatable.add(i);
            }
            i++;
        }
        probability = p;
        startProbability = p;
        System.out.println("prob " + probability);

    }

    int getIndex(Object o) {
        EnumGene gene = (EnumGene) o;
        int iIndex = (Integer) gene.allele();
        return iIndex;
    }

    synchronized private void updateProbability(int nGenes, long generation) {
        int nGenerations = peakMatcher.getNGenerations();
        probability = 0.1 / ((2.0 + generation * (nGenes - 2.0) / (nGenerations - 1.0)));
//        System.out.println("update prob " + nGenerations + " " + nGenes + " " + probability);
    }

    public void calcLocalScore(final Seq<Phenotype> population) {
        Phenotype pheno0 = population.get(0);
        Genotype geno0 = pheno0.genotype();
        int nGenes = geno0.length();
        int[] count = new int[nGenes];
        double[] values = new double[nGenes];
        for (int iGene = 0; iGene < nGenes; iGene++) {
            final int jGene = iGene;
            for (int i = 0; i < count.length; i++) {
                count[i] = 0;
                values[i] = 0.0;
            }
            population.stream().forEach(i -> {
                Phenotype pheno = i;
                double fitness = ((Double) pheno.fitness());
                Chromosome chromo = pheno.genotype().chromosome();
                int index = getIndex(chromo.get(jGene));
                if (index < values.length) {
//                System.out.println("chrom " + chromo);
                    if (index < 0) {
                        System.out.println(" badgene " + jGene + " " + index + " " + chromo);
                    }
                    count[index]++;
                    values[index] += fitness;
                }
            });
            double sumFitness = 0.0;
            int sumCount = 0;
            for (int i = 0; i < count.length; i++) {
                if (count[i] != 0) {
                    sumFitness += values[i];
                    sumCount += count[i];
                    values[i] /= count[i];

                }
            }
            double avgFitness = sumFitness / sumCount;
            for (ItemMatch item : peakMatches.get(iGene)) {
                double rate = item.getLocalScore();
                if (rate == 0.0) {
                    rate = startRate;
                }
                if (item.itemNum < values.length) {
                    if (values[item.itemNum] >= avgFitness) {
                        rate -= gamma;
                    } else {
                        rate += gamma;
                    }
                    if (rate > maxRate) {
                        rate = maxRate;
                    } else if (rate < minRate) {
                        rate = minRate;
                    }
                }
                item.setLocalScore(rate);
//                System.out.println(iGene + " " + item.itemNum + " " + rate + " " + peakMatches.get(iGene).size());
//                System.out.println(iGene + " gene " + item.itemNum + " " + avgFitness + " " + values[item.itemNum]);
//                item.setLocalScore(values[item.itemNum]);
            }
        }
    }

    @Override
    public AltererResult alter(Seq population, long generation) {
        alterMe(population, generation);
        return super.alter(population, generation);
    }

    public void alterMe(Seq<Phenotype> population, long generation) {
        int nGenes = population.size();
        updateProbability(nGenes, generation);
        calcLocalScore(population);
        // AltererResult altererResult = new AltererResult((ISeq) population, 1);
        // return altererResult;
    }

    @Override
    protected MutatorResult<Chromosome<G>> mutate(Chromosome<G> chromosome, double probability, RandomGenerator random) {
//        double p = pow(probability, 1.0 / 3.0);
        double p = probability;
//        System.out.println(probability + " " + _probability + " " + defP + " " + p);

//        double[] localScores = peakMatcher.localScores;
        sortedScores.clear();
        for (int mutateSite : mutatable) {
//            double score = localScores[mutateSite];
            double score = 1.0;
            sortedScores.add(new ItemMatch(mutateSite, score));
        }
        Collections.sort(sortedScores);
        int nSwap = 0;
        var genes = MSeq.of(chromosome);
        for (int mutateSite : mutatable) {
            int jCurrent = getIndex(chromosome.get(mutateSite));
            double rate = p;
            for (ItemMatch itemMatch : peakMatches.get(mutateSite)) {
                if (jCurrent == itemMatch.itemNum) {
                    rate = itemMatch.getLocalScore();
                    break;
                }
            }
            if (rate < p) {
                rate = p;
            }
            rate = p;
            if (twister.nextDouble() > rate) {
                continue;
            }
            int k = twister.nextInt(peakMatches.get(mutateSite).size());
            int iPeak = peakMatches.get(mutateSite).get(k).itemNum;

//            System.out.println(mutateSite + " mutate " + iPeak + " " + itemMatch.getLocalScore());
            // iPeak now is a randomly guessed index of a peak that matches the jth atom
            // jCurrent is now the index of a peak that currently matches the jth atom
            if (iPeak != jCurrent) {
//            System.out.println("no change");
                boolean ok = false;
                int iSwap = 0;

                for (int i = 0; i < chromosome.length(); i++) {
                    if (getIndex(chromosome.get(i)) == iPeak) {
                        iSwap = i;
                        ok = true;
                        break;
                    }
                }
            //    System.out.println(mutateSite + " k " + k + " " + iPeak + " " + jCurrent + " " + iSwap + " " + ok + " " + p);

                if (ok) {
                    genes.swap(iSwap, mutateSite);
              //      System.out.println("gen " + genes.length());
                    nSwap++;
                }
            }
        }
        final MutatorResult<Chromosome<G>> result;
        if (nSwap > 0) {
            result = new MutatorResult<>(
                    chromosome.newInstance(genes.toISeq()),
                    nSwap
            );
        } else {
            result = new MutatorResult<>(chromosome, nSwap);
        }
        return result;
    }
}
