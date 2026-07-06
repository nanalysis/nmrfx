package org.nmrfx.processor.math;

import java.util.*;
import java.util.stream.Collectors;

public class SignalCoupler {
    List<Double> positions;

    public class Group {
        List<Integer> ids = new ArrayList<>();
        double center = 0.0;
        boolean active = true;

        Group() {
        }

        public List<Integer> ids() {
            return active ? ids : Collections.emptyList();
        }

        void add(int i) {
            ids.add(i);
            updateCenter();
        }

        void updateCenter() {
            double sum = 0.0;
            for (int id : ids) {
                sum += positions.get(id);
            }
            center = sum / ids.size();
        }

        void merge(Group group) {
            ids.addAll(group.ids);
            updateCenter();
            group.active = false;
        }
    }

    public List<Group>  couple(List<Double> positions, double tol) {
        this.positions = positions;

        int n = positions.size();
        List<Group> groups = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Group group = new Group();
            group.add(i);
            groups.add(group);
        }

        int[] prev = new int[n];
        int[] next = new int[n];
        for (int i = 0; i < n; i++) {
            prev[i] = i - 1;
            next[i] = i + 1;
        }

        PriorityQueue<double[]> heap = new PriorityQueue<>(Comparator.comparingDouble(e -> e[0]));
        for (int i = 1; i < n; i++) {
            heap.offer(new double[]{groups.get(i).center - groups.get(i - 1).center, i - 1, i});
        }

        while (!heap.isEmpty()) {
            double[] entry = heap.poll();
            if (entry[0] >= tol) break; // min-heap: all remaining entries also >= tol

            int li = (int) entry[1];
            int ri = (int) entry[2];

            if (!groups.get(li).active || !groups.get(ri).active) continue;

            if (groups.get(ri).center - groups.get(li).center >= tol) continue;

            // Merge ri into li
            groups.get(li).merge(groups.get(ri));

            // Splice ri out of the linked list and enqueue new neighbor deltas
            int nextRi = next[ri];
            next[li] = nextRi;
            if (nextRi < n) {
                prev[nextRi] = li;
                heap.offer(new double[]{
                        groups.get(nextRi).center - groups.get(li).center, li, nextRi});
            }
            int prevLi = prev[li];
            if (prevLi >= 0) {
                heap.offer(new double[]{
                        groups.get(li).center - groups.get(prevLi).center, prevLi, li});
            }
        }

        return groups.stream()
                .filter(g -> g.active)
                .collect(Collectors.toList());
    }
}