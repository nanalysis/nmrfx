package org.nmrfx.processor.project;

import org.nmrfx.processor.datasets.Dataset;

public class ProjectText {

    public static String genText(Dataset dataset) {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(dataset.getName()).append("\n");
        sBuilder.append(String.format("%15s %10s\n", "Solvent", dataset.getSolvent()));
        for (int i = 0; i < dataset.getNDim(); i++) {
            sBuilder.append(String.format("F%d Parameters\n", (i + 1)));
            sBuilder.append(String.format("%15s %10.2f\n", "SF", dataset.getSf(i)));
            sBuilder.append(String.format("%15s %10.2f\n", "SW", dataset.getSw(i)));
            sBuilder.append(String.format("%15s %10d\n", "Size", dataset.getSizeReal(i)));
        }
        return sBuilder.toString();
    }
}
