package org.nmrfx.processor.math;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class SignalCoupler {
    List<Double> positions;

    record Delta(int i, double delta) {}

    public class Group {
        List<Integer> ids = new ArrayList<>();
        double center = 0.0;
        boolean active = true;

        Group() {}

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
            System.out.println(center + " " + group.center);
            System.out.println(ids);
            System.out.println(group.ids);
            ids.addAll(group.ids);
            updateCenter();
            group.active = false;
        }
    }

    public List<Group> couple(List<Double> positions, double tol) {
        this.positions = positions;
        List<Group> groups = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            Group group = new Group();
            group.add(i);
            groups.add(group);
        }

        boolean anyMerged = true;
        while (anyMerged) {
            anyMerged = false;
            List<Group> activeGroups = groups.stream()
                    .filter(g -> g.active)
                    .toList();

            List<Delta> deltaList = new ArrayList<>();
            for (int i = 1; i < activeGroups.size(); i++) {
                double delta = activeGroups.get(i).center - activeGroups.get(i - 1).center;
                deltaList.add(new Delta(i, delta));
            }
            deltaList.sort(Comparator.comparingDouble(Delta::delta));

            for (Delta delta : deltaList) {
                if (delta.delta() < tol) {
                    Group group0 = activeGroups.get(delta.i() - 1);
                    Group group1 = activeGroups.get(delta.i());
                    if (group0.active && group1.active) {
                        group0.merge(group1);
                        anyMerged = true;
                    }
                }
            }
        }
        return groups.stream()
                .filter(g -> g.active)
                .collect(Collectors.toList());
    }
}