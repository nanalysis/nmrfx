package org.nmrfx.structure.seqassign;

import io.jenetics.*;
import io.jenetics.engine.*;
import io.jenetics.internal.util.Requires;
import io.jenetics.util.ISeq;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.jenetics.engine.EvolutionResult.toBestPhenotype;
import static io.jenetics.engine.Limits.bySteadyFitness;
import static java.util.function.Function.identity;

public class SeqGeneticAlgorithm {

    public static List<List<Integer>> seqResMatches;
    ResSeqMatcher resSeqMatcher;

    Consumer<Double> progressConsumer;

    SeqGenParameters seqGenParameters = new SeqGenParameters();

    public SeqGeneticAlgorithm(ResSeqMatcher resSeqMatcher, SeqGenParameters seqGenParameters) {
        this.resSeqMatcher = resSeqMatcher;
        this.seqGenParameters = seqGenParameters;
    }

    public double getValue(int[] matching) {
        return resSeqMatcher.matcher(matching);
    }

    public static InvertibleCodec<int[], EnumGene<Integer>>
    ofPermutation(final int length) {
        Requires.positive(length);

        final AssignmentChromosome<Integer> chromosome =
                AssignmentChromosome.ofInteger(length);

        final Map<Integer, EnumGene<Integer>> genes = chromosome.stream()
                .collect(Collectors.toMap(EnumGene::allele, identity()));

        return InvertibleCodec.of(
                Genotype.of(chromosome),
                gt -> gt.chromosome().stream()
                        .mapToInt(EnumGene::allele)
                        .toArray()
                ,
                val ->
                        Genotype.of(
                                new AssignmentChromosome<>(
                                        IntStream.of(val)
                                                .mapToObj(genes::get)
                                                .collect(ISeq.toISeq())
                                )
                        )
        );
    }

    public static int[] newValid(int size) {
        int nSys = seqResMatches.size();
        int nRes = size - nSys;
        List<Integer> elements = new ArrayList<>(nSys);
        for (int i = 0; i < nSys; i++) {
            elements.add(i);
        }
        int[] result = new int[size];
        boolean[] used = new boolean[size];
        Collections.shuffle(elements);

        for (int i = 0; i < nSys; i++) {
            int index = elements.get(i);
            int iPeak = -1;
            List<Integer> itemMatches = seqResMatches.get(index);
            List<Integer> sortedMatches = new ArrayList<>(itemMatches);
            sortedMatches.add(i + nRes);
            if (sortedMatches.size() == 1) {
                iPeak = sortedMatches.get(0);
            } else {
                Collections.shuffle(sortedMatches);
                for (int k = 0; k < sortedMatches.size(); k++) {
                    int testPeak = sortedMatches.get(k);
                    if (!used[testPeak]) {
                        iPeak = testPeak;
                        break;
                    }
                }
            }
            if (iPeak != -1) {
                result[index] = iPeak;
                used[iPeak] = true;
            }
        }
        int j = seqResMatches.size();
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) {
                result[j++] = i;
            }
        }
        return result;
    }

    private void update(final EvolutionResult<EnumGene<Integer>, Double> result) {
        long generation = result.generation();
        if ((generation % 10) == 0) {
            DescriptiveStatistics dStat = new DescriptiveStatistics();
            result.population().stream().forEach(pheno -> dStat.addValue(pheno.fitness()));
            System.out.printf("%5d %10.3f %10.3f %10.4f\n", result.generation(), dStat.getMin(), dStat.getMean(), dStat.getMax());
            if (progressConsumer != null) {
                progressConsumer.accept(dStat.getMin());
            }
        }
    }

    private List<Genotype<EnumGene<Integer>>> initGenotypes(List<ResSeqMatcher.Matching> initMatches, int stops) {
        initMatches.sort(Comparator.comparing(ResSeqMatcher.Matching::score));
        int nMulti = Math.min(seqGenParameters.multiMaxLimit(), initMatches.size());
        // System.out.println(" nMultiMatches " + initMatches.size() + " multiLimit " + multiMaxLimit + " nMulti " + nMulti);
        List<Integer> alleleList = new ArrayList<>();
        for (int i = 0; i < stops; i++) {
            alleleList.add(i);
        }
        final ISeq<Integer> alleleSeq = ISeq.of(alleleList);
        List<Genotype<EnumGene<Integer>>> genotypes = new ArrayList<>();

        for (ResSeqMatcher.Matching matching : initMatches.subList(0, nMulti)) {
            //  System.out.println("multi value " + matching.score());
            ArrayList<Integer> matchArray = new ArrayList<>();
            boolean[] used = new boolean[stops];
            for (int i : matching.matches()) {
                used[i] = true;
                matchArray.add(i);
            }
            int j = seqResMatches.size();
            for (int i = 0; i < used.length; i++) {
                if (!used[i]) {
                    matchArray.add(i);
                }
            }
            final ISeq<Integer> alleles = ISeq.of(matchArray);
            // System.out.println(alleles);
            AssignmentChromosome<EnumGene<Integer>> permCh = new AssignmentChromosome(matchArray.stream().map(i -> EnumGene.of(i, alleleSeq)).collect(ISeq.toISeq()));

            Genotype gtype = Genotype.of(permCh);
            genotypes.add(gtype);
        }
        return genotypes;
    }

    public ResSeqMatcher.Matching apply(List<ResSeqMatcher.Matching> initMatches, Consumer<Double> progressConsumer) {
        int nSys = resSeqMatcher.getSysResidueList().size();
        int nRes = resSeqMatcher.getResidueSysList().size();
        this.progressConsumer = progressConsumer;
        final int stops = nSys + nRes;
        final Engine<EnumGene<Integer>, Double> engine = Engine
                .builder(
                        this::getValue,
                        SeqGeneticAlgorithm.ofPermutation(stops)
                )
                .optimize(Optimize.MINIMUM)
                .maximalPhenotypeAge(seqGenParameters.maximumPhenoTypeAge())
                .populationSize(seqGenParameters.populationSize())
                .survivorsSelector(new EliteSelector<>(seqGenParameters.eliteNumber()))
                .offspringSelector(new TournamentSelector<>())
                .alterers(
                        new ConstrainedSwapMutatorResSeq<>(resSeqMatcher, this, seqGenParameters.mutationRate()),
                        new PartiallyMatchedCrossover<>(seqGenParameters.crossoverRate()))
                .build();

        List<Genotype<EnumGene<Integer>>> genotypes = initGenotypes(initMatches, stops);

        final EvolutionStatistics<Double, ?> statistics = EvolutionStatistics.ofNumber();
        final Phenotype<EnumGene<Integer>, Double> best
                = engine.stream(genotypes)
                // Truncate the evolution stream after n "steady"
                // generations.
                .limit(bySteadyFitness(seqGenParameters.steadyLimit()))
                .sequential()
                // The evolution will stop after maximal n
                // generations.
                .limit(seqGenParameters.nGenerations())
                // Update the evaluation statistics after
                // each generation
                .peek(statistics)
                 .peek(this::update)
                // Collect (reduce) the evolution stream to
                // its best phenotype.
                .collect(toBestPhenotype());


        int[] finalMatching = Codecs.ofPermutation(stops).decoder().apply(best.genotype());
        double fitness = best.fitness();
        ResSeqMatcher.Matching matching = new ResSeqMatcher.Matching(fitness, finalMatching);
//        for (int i=0;i<finalMatching.length;i++) {
//            System.out.println(i + " " + finalMatching[i]);
//        }
        return matching;
    }

    public int getNGenerations() {
        return seqGenParameters.nGenerations();
    }
}
