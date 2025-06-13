package org.nmrfx.processor.datasets.peaks;


public class ConvolutionPickPar {
    private  boolean state;
    private  int iterations;
    private  double squash;
    private  double scale;
    private  double directWidth;
    private  double indirectWidth;

    public ConvolutionPickPar(
            boolean state, int iterations, double squash,
            double scale, double directWidth, double indirectWidth) {
        this.state = state;
        this.iterations = iterations;
        this.squash = squash;
        this.scale = scale;
        this.directWidth = directWidth;
        this.indirectWidth = indirectWidth;
    }

    public int iterations() {
        return iterations;
    }

    public boolean state() {
        return state;
    }

    public double squash() {
        return squash;
    }

    public double scale() {
        return scale;
    }

    public double directWidth() {
        return directWidth;
    }

    public double indirectWidth() {
        return indirectWidth;
    }

    public ConvolutionPickPar state(boolean state) {
        this.state = state;
        return this;
    }

    public ConvolutionPickPar iterations(int iterations) {
        this.iterations = iterations;
        return this;
    }

    public ConvolutionPickPar squash(double squash) {
        this.squash = squash;
        return this;
    }

    public ConvolutionPickPar scale(double scale) {
        this.scale = scale;
        return this;
    }

    public ConvolutionPickPar directWidth(double directWidth) {
        this.directWidth = directWidth;
        return this;
    }

    public ConvolutionPickPar indirectWidth(double indirectWidth) {
        this.indirectWidth = indirectWidth;
        return this;
    }
}