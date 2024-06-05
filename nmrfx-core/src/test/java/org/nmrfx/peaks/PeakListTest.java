package org.nmrfx.peaks;

import org.junit.Assert;
import org.junit.Test;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.Sequence;
import org.nmrfx.peaks.io.PeakReader;
import org.nmrfx.project.ProjectBase;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class PeakListTest {
    PeakList getPeakList(String fileName) throws IOException {
        ProjectBase.getActive().clearAllPeakLists();
        PeakReader peakReader = new PeakReader();
        Path path = Path.of("src", "test", "data", "peaks", fileName);
        PeakList peakList = peakReader.readXPK2Peaks(path.toString());
        return peakList;
    }

    @Test
    public void getAssignmentStatus() throws IOException, MoleculeIOException {
        MoleculeBase.removeAll();
        Sequence sequence = new Sequence();
        var seq = List.of("ALA", "ALA", "ALA", "ALA", "ALA", "ALA");
        var mol = sequence.read("A", seq, null);
        MoleculeFactory.setActive(mol);
        PeakList peakList = getPeakList("testassigncount.xpk2");
        var map = peakList.getAssignmentStatus();
        for (var entry : map.entrySet()) {
            Assert.assertEquals(entry.getKey().description, 1, entry.getValue().longValue());
        }
    }
}