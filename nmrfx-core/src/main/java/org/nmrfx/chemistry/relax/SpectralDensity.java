package org.nmrfx.chemistry.relax;

import org.nmrfx.annotations.PluginAPI;
import org.nmrfx.chemistry.*;

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

    public static void writeToFile(File file, String sepChar) throws IOException {
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
                                    writeHeaderToFile(fileWriter, sepChar, entry.getValue());
                                }
                                writeToFile(fileWriter, sepChar, atom, entry.getValue());
                            } catch (IOException ioE) {

                            }
                        }
                    });
        }
    }

    public static void writeHeaderToFile(FileWriter fileWriter,  String sepChar, SpectralDensity spectralDensity) throws IOException {
        double[][] values = spectralDensity.spectralDensities;
        String header = "Chain" + sepChar + "Residue" + sepChar + "ResName" + sepChar + "Atom" + sepChar + "Omega" + sepChar + "J" + sepChar + "J_err";
        fileWriter.write(header + "\n");
    }

    public static void writeToFile(FileWriter fileWriter, String sepChar, Atom atom, SpectralDensity spectralDensity) throws IOException {
        String precFormat = "%.5f";
        String format1 = "%s" + sepChar + "%s" + sepChar + "%s" + sepChar + "%s";
        String format2 = sepChar + precFormat + sepChar + precFormat + sepChar + precFormat + "\n";
        double[][] values = spectralDensity.spectralDensities;
        Compound compound = (Compound) atom.getEntity();
        for (int i = 0; i < values[0].length; i++) {
            String chainName = "";
            if (compound instanceof Residue residue) {
                chainName = residue.getPolymer().getName();
                if (chainName == null) {
                    chainName = "A";
                }
            }
            fileWriter.write(String.format(format1,  chainName, compound.getNumber(), compound.getName(), atom.getName()));
            fileWriter.write(String.format(format2, values[0][i] * 1.0e-9, values[1][i] * 1e9, values[2][i] * 1e9));
        }
    }

}
