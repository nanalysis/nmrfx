package org.nmrfx.processor.compoundLib;

import java.util.ArrayList;
import java.util.List;
import org.nmrfx.processor.math.Vec;

/**
 *
 * @author brucejohnson
 */
public class CompoundMatcher {

    List<CompoundMatch> matches = new ArrayList<>();

    public void addMatch(CompoundData cData) {
        CompoundMatch match = new CompoundMatch(cData);
        matches.add(match);
    }

    public List<CompoundMatch> getMatches() {
        return matches;
    }

    public void updateVec(Vec vec) {
        vec.zeros();
        for (CompoundMatch cMatch : matches) {
            cMatch.cData.addToVec(vec, cMatch.shifts, cMatch.scale);
        }
    }
}
