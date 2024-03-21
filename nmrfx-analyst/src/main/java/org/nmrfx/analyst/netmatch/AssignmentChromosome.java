package org.nmrfx.analyst.netmatch;

import io.jenetics.AbstractChromosome;
import io.jenetics.EnumGene;
import io.jenetics.internal.util.Bits;
import io.jenetics.internal.util.Requires;
import io.jenetics.util.ISeq;
import io.jenetics.util.IntRange;
import io.jenetics.util.RandomRegistry;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.jenetics.internal.util.Bits.getAndSet;
import static java.lang.String.format;

public class AssignmentChromosome<T>
        extends AbstractChromosome<EnumGene<T>>
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L;

    private final ISeq<T> _validAlleles;

    // Private primary constructor.
    private AssignmentChromosome(
            final ISeq<EnumGene<T>> genes,
            final Boolean valid
    ) {
        super(genes);
        assert !genes.isEmpty();
        _validAlleles = genes.get(0).validAlleles();
        _valid = valid;
    }

    public AssignmentChromosome(final ISeq<EnumGene<T>> genes) {
        this(genes, null);
    }

    @Override
    public AssignmentChromosome<T> newInstance() {
        return of(_validAlleles, length());
    }

    @Override
    public AssignmentChromosome<T> newInstance(final ISeq<EnumGene<T>> genes) {
        return new AssignmentChromosome<>(genes);
    }

    public static <T> AssignmentChromosome<T> of(
            final ISeq<? extends T> alleles,
            final int length
    ) {

        Requires.positive(length);
        if (length > alleles.size()) {
            throw new IllegalArgumentException(format(
                    "The sub-set size must be be greater then the base-set: %d > %d",
                    length, alleles.size()
            ));
        }

       final var rnd = RandomRegistry.random();
       // final int[] subset = Subset.next(rnd, alleles.size(), length);
        int[] subset = PeakMatcher.newValid(length);
       // shuffle(subset, rnd);
        ArrayList<Integer> matchArray = new ArrayList<>();
        for (int i : subset) {
            matchArray.add(i);
        }

        final ISeq<T> alleles2 = (ISeq<T>)ISeq.of(matchArray);

        AssignmentChromosome permCh = new AssignmentChromosome<T>(IntStream.range(0, subset.length).mapToObj(i -> EnumGene.of(i, alleles2)).collect(ISeq.toISeq()));
return permCh;
//        final ISeq<EnumGene<T>> genes = IntStream.of(subset)
//                .mapToObj(i -> EnumGene.<T>of(i, alleles))
//                .collect(ISeq.toISeq());
//
//        return new AssignmentChromosome<>(genes, true);
    }

    @Override
    public String toString() {
        return _genes.stream()
                .map(g -> g.allele().toString())
                .collect(Collectors.joining("|"));
    }

    @Override
    public boolean isValid() {
        if (_valid == null) {
            final byte[] check = Bits.newArray(_validAlleles.length());
            _valid = _genes.forAll(g -> !getAndSet(check, g.alleleIndex()));
        }

        return _valid;
    }

    /**
     * Create a new, random chromosome with the given valid alleles.
     *
     * @param <T>     the gene type of the chromosome
     * @param alleles the valid alleles used for this permutation arrays.
     * @return a new chromosome with the given alleles
     * @throws IllegalArgumentException if the given allele sequence is empty.
     */
    public static <T> AssignmentChromosome<T>
    of(final ISeq<? extends T> alleles) {
        return of(alleles, alleles.size());
    }

    /**
     * Create a new, random chromosome with the given valid alleles.
     *
     * @param <T>     the gene type of the chromosome
     * @param alleles the valid alleles used for this permutation arrays.
     * @return a new chromosome with the given alleles
     * @throws IllegalArgumentException if the given allele array is empty.
     * @throws NullPointerException     if one of the alleles is {@code null}
     * @since 2.0
     */
    @SafeVarargs
    public static <T> AssignmentChromosome<T> of(final T... alleles) {
        return of(ISeq.of(alleles));
    }

    /**
     * Create a integer permutation chromosome with the given length.
     *
     * @param length the chromosome length.
     * @return a integer permutation chromosome with the given length.
     * @throws IllegalArgumentException if {@code length <= 0}.
     */
    public static AssignmentChromosome<Integer> ofInteger(final int length) {
        return ofInteger(0, Requires.positive(length));
    }

    /**
     * Create an integer permutation chromosome with the given range.
     *
     * @param start the start of the integer range (inclusively) of the returned
     *              chromosome.
     * @param end   the end of the integer range (exclusively) of the returned
     *              chromosome.
     * @return a integer permutation chromosome with the given integer range
     * values.
     * @throws IllegalArgumentException if {@code start >= end} or
     *                                  {@code start <= 0}
     * @since 2.0
     */
    public static AssignmentChromosome<Integer>
    ofInteger(final int start, final int end) {
        if (end <= start) {
            throw new IllegalArgumentException(format(
                    "end <= start: %d <= %d", end, start
            ));
        }

        return ofInteger(IntRange.of(start, end), end - start);
    }

    /**
     * Create an integer permutation chromosome with the given range and length
     *
     * @param range  the value range
     * @param length the chromosome length
     * @return a new integer permutation chromosome
     * @throws NullPointerException     if the given {@code range} is {@code null}
     * @throws IllegalArgumentException if
     *                                  {@code range.getMax() - range.getMin() < length},
     *                                  {@code length <= 0} or
     *                                  {@code (range.getMax() - range.getMin())*length} will cause an
     *                                  integer overflow.
     * @since 3.4
     */
    public static AssignmentChromosome<Integer>
    ofInteger(final IntRange range, final int length) {
        return of(
                range.stream().boxed().collect(ISeq.toISeq()),
                length
        );
    }
}
