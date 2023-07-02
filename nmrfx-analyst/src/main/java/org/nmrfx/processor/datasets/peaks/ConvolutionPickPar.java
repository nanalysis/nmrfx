package org.nmrfx.processor.datasets.peaks;

public record ConvolutionPickPar(boolean state, int iterations, double squash,
                                 double scale, double directWidth, double indirectWidth) {
}
