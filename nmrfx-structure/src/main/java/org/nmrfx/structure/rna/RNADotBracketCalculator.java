package org.nmrfx.structure.rna;

import java.util.*;

/**
 * FCFS (First-Come First-Served) algorithm for RNA pseudoknot detection.
 *
 */

public class RNADotBracketCalculator {

    public static class Region {
        int start;   // smallest  index of the region (i side)
        int end;     // largest   index of the region (i side, i.e. the 5' end of outermost pair)
        int pStart;  // smallest  index on the j side
        int pEnd;    // largest   index on the j side
        int ord;     // assigned order / level (0 = no conflict, 1 = first pseudoknot level, …)

        /**
         * Base pairs belonging to this region, stored as (i, j) with i < j.
         */
        List<int[]> pairs = new ArrayList<>();

        Region(int start, int end, int pStart, int pEnd) {
            this.start = start;
            this.end = end;
            this.pStart = pStart;
            this.pEnd = pEnd;
        }

        @Override
        public String toString() {
            return String.format("Region[%d-%d | %d-%d, ord=%d, pairs=%d]",
                    start, end, pStart, pEnd, ord, pairs.size());
        }
    }

    // -----------------------------------------------------------------------
    // Step 1 – find all paired regions
    // -----------------------------------------------------------------------

    /**
     * Scans ssin and groups consecutive (on the 5' side) base pairs into
     * regions.  A region break occurs whenever the gap between successive
     * 5'-side indices is > 1 OR the implied 3'-side indices are not
     * contiguous / reversed.
     */
    public static List<Region> findAllPairedRegions(int[] ssin) {
        List<Region> regions = new ArrayList<>();
        int n = ssin.length;

        int i = 0;
        while (i < n) {
            // skip unpaired positions and already-visited j-sides
            if (ssin[i] < 0 || ssin[i] < i) {   // j < i means we're on the 3' side
                i++;
                continue;
            }

            // start a new region with pair (i, ssin[i])
            int regStart = i;
            int regEnd = i;
            int regPStart = ssin[i];
            int regPEnd = ssin[i];
            List<int[]> pairs = new ArrayList<>();
            pairs.add(new int[]{i, ssin[i]});

            int iPrev = i;
            int jPrev = ssin[i];
            i++;

            while (i < n) {
                int j = ssin[i];
                if (j < 0 || j < i) { // unpaired or 3'-side position
                    break;
                }
                // Check contiguity: both sides must step by exactly ±1
                if (i == iPrev + 1 && j == jPrev - 1) {
                    // extend region
                    regEnd = i;
                    regPStart = j;
                    pairs.add(new int[]{i, j});
                    iPrev = i;
                    jPrev = j;
                    i++;
                } else {
                    break;
                }
            }

            Region r = new Region(regStart, regEnd, regPStart, regPEnd);
            r.pairs = pairs;
            regions.add(r);
        }
        return regions;
    }

    // -----------------------------------------------------------------------
    // Step 2 – sort by start point (already guaranteed by linear scan,
    //          but we sort explicitly to match the pseudocode)
    // -----------------------------------------------------------------------
    static void sortRegionsByStartPoint(List<Region> regs) {
        regs.sort(Comparator.comparingInt(r -> r.start));
    }


    static boolean conflicted(Region a, Region b) {
        if (a.start > b.start) {
            Region tmp = a;
            a = b;
            b = tmp;
        }

        return b.start > a.start
                && b.start <= a.pEnd         // b's 5' side overlaps a's 3' side
                && b.pEnd > a.pEnd         // b's 3' side extends beyond a's
                && b.pStart >= a.pStart;     // b's 3' side is "outside" a
    }

    static void setRegionOrders(List<Region> regs) {
        int n = regs.size();
        if (n == 0) return;

        regs.getFirst().ord = 0;

        for (int i = 1; i < n; i++) {
            int order = 0;
            for (int j = 0; j < i; j++) {
                if (regs.get(j).ord == order && conflicted(regs.get(j), regs.get(i))) {
                    order++;
                    // restart the inner scan so we check all previous regions
                    // against the new candidate order
                    j = -1;  // will be incremented to 0 by the for-loop
                }
            }
            regs.get(i).ord = order;
        }
    }

    static char openBracket(int order) {
        return switch (order) {
            case 0 -> '(';
            case 1 -> '[';
            case 2 -> '{';
            case 3 -> '<';
            default ->
                // A=4, B=5, … (uppercase letters as open, lowercase as close)
                    (char) ('A' + (order - 4));
        };
    }

    static char closeBracket(int order) {
        return switch (order) {
            case 0 -> ')';
            case 1 -> ']';
            case 2 -> '}';
            case 3 -> '>';
            default -> (char) ('a' + (order - 4));
        };
    }

    /**
     * Builds the DBL string: one character per position.
     * Unpaired → '.'
     * 5'-side of a pair at order k → openBracket(k)
     * 3'-side of a pair at order k → closeBracket(k)
     */
    static String encodeBasePairs(int[] ssin, List<Region> regs) {
        char[] out = new char[ssin.length];
        Arrays.fill(out, '.');

        for (Region r : regs) {
            for (int[] pair : r.pairs) {
                int i = pair[0];
                int j = pair[1];
                out[i] = openBracket(r.ord);
                out[j] = closeBracket(r.ord);
            }
        }
        return new String(out);
    }

    public static String fcfs(int[] ssin) {
        List<Region> regs = findAllPairedRegions(ssin);   // step 1
        sortRegionsByStartPoint(regs);                     // step 2
        setRegionOrders(regs);                             // step 3
        return encodeBasePairs(ssin, regs);                // step 4
    }
}