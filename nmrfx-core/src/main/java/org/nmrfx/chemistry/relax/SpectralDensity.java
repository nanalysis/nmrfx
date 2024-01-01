package org.nmrfx.chemistry.relax;

import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Compound;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.MoleculeFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@PluginAPI("ring")
public class SpectralDensity {
    String name;
    // two-dimensional array, first row is frequency, 2nd row is J value, third row is J error
    double[][] spectralDensities;

    public SpectralDensity(String name, double[][] spectralDensities) {
        this.name = name;
        this.spectralDensities = spectralDensities;
    }

    public String getName() {
        return name;
    }

    public double[][] getSpectralDensities() {
        return spectralDensities;
    }

    public static void writeToFile(File file) throws IOException {
        MoleculeBase moleculeBase = MoleculeFactory.getActive();
        AtomicBoolean firstRow = new AtomicBoolean(true);
        try (FileWriter fileWriter = new FileWriter(file)) {
            moleculeBase.getAtomArray().stream().sorted((a,b) -> {
                        int aRes = a.getResidueNumber();
                        int bRes = b.getResidueNumber();
                        int c = Integer.compare(aRes, bRes);
                        if (c == 0) {
                            c = a.getName().compareTo(b.getName());
                        }
                        return c;
                    })
                    .forEach(atom -> {
                        var spectralDensityMap = atom.getSpectralDensity();
                        for (var entry : spectralDensityMap.entrySet()) {
                            try {
                                if (!spectralDensityMap.isEmpty() && firstRow.getAndSet(false)) {
                                    writeHeaderToFile(fileWriter, entry.getValue());
                                }
                                writeToFile(fileWriter, atom, entry.getValue());
                            } catch (IOException ioE) {

                            }
                        }
                    });
        }
    }

    public static void writeHeaderToFile(FileWriter fileWriter,  SpectralDensity spectralDensity) throws IOException {
        double[][] values = spectralDensity.spectralDensities;
        fileWriter.write("residue\tatom");
        for (int i = 0; i < values[0].length; i++) {
            fileWriter.write(String.format("\t%.5f\t%5s", values[0][i] * 1e-9, "err"));
        }
        fileWriter.write("\n");

    }

    public static void writeToFile(FileWriter fileWriter, Atom atom, SpectralDensity spectralDensity) throws IOException {
        double[][] values = spectralDensity.spectralDensities;
        Compound compound = (Compound) atom.getEntity();
        fileWriter.write(String.format("%s\t%s",  compound.getNumber(), atom.getName()));
        for (int i = 0; i < values[0].length; i++) {
            fileWriter.write(String.format("\t%.5f\t%.5f", values[1][i] * 1e9, values[2][i] * 1e9));
        }
        fileWriter.write("\n");
    }

}
