package org.nmrfx.processor.datasets.peaks;

public enum LineShapes {
    LORENTZIAN(3) {
        @Override
        public double calculate(double x, double... pars) {
            double amp = pars[0];
            double freq = pars[1];
            double b = pars[2] * 0.5;
            double dXb = (x - freq) / b;
            return amp / (1.0 + dXb * dXb);
        }
    },
    GAUSSIAN(3) {
        @Override
        public double calculate(double x, double... pars) {
            double amp = pars[0];
            double freq = pars[1];
            double b = pars[2];
            double dXb = (x - freq) / b;
            return amp * Math.exp(-4.0 * LN2 * dXb * dXb);
        }
    },
    PSEUDOVOIGT(4) {
        @Override
        public double calculate(double x, double... pars) {
            double amp = pars[0];
            double freq = pars[1];
            double b = pars[2] * 0.5;
            double f = pars[3];

            double dXb = (x - freq) / b;
            double lorentz = 1.0 / (1.0 + dXb * dXb);
            double gauss = Math.exp(-1.0 * LN2 * dXb * dXb);
            return amp * (f * lorentz + (1.0 - f) * gauss);
        }
    },
    G_LORENTZIAN(4) {
        @Override
        public double calculate(double x, double... pars) {
            double amp = pars[0];
            double freq = pars[1];
            double b = pars[2] / 2.0;
            double k = pars[3];
            double dXb = (x - freq) / b;
            double dXb2 = dXb * dXb;
            double lorentz = 1.0 / (1.0 + dXb2);
            double gauss = (1.0 + (dXb2 / 2.0)) / (1.0 + dXb2 + dXb2 * dXb2);
            return amp * ((1.0 - k) * lorentz + k * gauss);
        }
    };
    final int nPars;
    static final double LN2 = Math.log(2.0);

    LineShapes(int nPars) {
        this.nPars = nPars;
    }

    public abstract double calculate(double x, double... pars);

    public int nPars() {
        return nPars;
    }
}
