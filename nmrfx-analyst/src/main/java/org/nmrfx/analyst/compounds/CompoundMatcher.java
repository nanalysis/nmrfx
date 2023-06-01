package org.nmrfx.analyst.compounds;

import org.nmrfx.processor.math.Vec;

import java.util.*;

/**
 * @author brucejohnson
 */
public class CompoundMatcher {

    Map<String, CompoundMatch> matches = new HashMap<>();

    public void addMatch(CompoundData cData) {
        CompoundMatch match = new CompoundMatch(cData);
        matches.put(cData.getName(), match);
    }

    public CompoundMatch getMatch(String name) {
        return matches.get(name);
    }

    public Collection<CompoundMatch> getMatches() {
        return matches.values();
    }

    public List<String> getNames(String pattern) {
        pattern = pattern.trim();
        List<String> matchNames = new ArrayList<>();
        boolean startsWith = false;
        if (pattern.length() > 0) {
            if (Character.isUpperCase(pattern.charAt(0))
                    || Character.isDigit(pattern.charAt(0))) {
                startsWith = true;
            }
            pattern = pattern.toLowerCase();
            for (String name : matches.keySet()) {
                boolean match = startsWith ? name.startsWith(pattern) : name.contains(pattern);
                if (match) {
                    matchNames.add(name);
                }
            }

        }
        return matchNames;
    }

    public void updateVec(Vec vec) {
        vec.zeros();
        for (CompoundMatch cMatch : matches.values()) {
            cMatch.cData.addToVec(vec, cMatch.shifts, cMatch.scale);
        }
    }
}
