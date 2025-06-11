package org.nmrfx.processor.math;

import org.jtransforms.fft.DoubleFFT_1D;
import org.jtransforms.fft.DoubleFFT_3D;

public class FFT4D {
    final int w;
    final int x;
    final int y;
    final int z;

    FFT4D(int w, int x, int y, int z) {
        this.w = w;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void complexForward(double[][][][] data) {
        // FFT along z-axis (last dimension)
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < x; j++) {
                for (int k = 0; k < y; k++) {
                    DoubleFFT_1D fftZ = new DoubleFFT_1D(z);
                    fftZ.complexForward(data[i][j][k]);
                }
            }
        }

        // FFT along y-axis
        double[] tempY = new double[2 * y];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < x; j++) {
                for (int l = 0; l < z; l++) {
                    for (int k = 0; k < y; k++) {
                        tempY[2 * k] = data[i][j][k][2 * l];
                        tempY[2 * k + 1] = data[i][j][k][2 * l + 1];
                    }

                    DoubleFFT_1D fftY = new DoubleFFT_1D(y);
                    fftY.complexForward(tempY);

                    for (int k = 0; k < y; k++) {
                        data[i][j][k][2 * l] = tempY[2 * k];
                        data[i][j][k][2 * l + 1] = tempY[2 * k + 1];
                    }
                }
            }
        }

        // FFT along x-axis
        double[] tempX = new double[2 * x];
        for (int i = 0; i < w; i++) {
            for (int k = 0; k < y; k++) {
                for (int l = 0; l < z; l++) {
                    for (int j = 0; j < x; j++) {
                        tempX[2 * j] = data[i][j][k][2 * l];
                        tempX[2 * j + 1] = data[i][j][k][2 * l + 1];
                    }

                    DoubleFFT_1D fftX = new DoubleFFT_1D(x);
                    fftX.complexForward(tempX);

                    for (int j = 0; j < x; j++) {
                        data[i][j][k][2 * l] = tempX[2 * j];
                        data[i][j][k][2 * l + 1] = tempX[2 * j + 1];
                    }
                }
            }
        }

        // FFT along w-axis
        double[] tempW = new double[2 * w];
        for (int j = 0; j < x; j++) {
            for (int k = 0; k < y; k++) {
                for (int l = 0; l < z; l++) {
                    for (int i = 0; i < w; i++) {
                        tempW[2 * i] = data[i][j][k][2 * l];
                        tempW[2 * i + 1] = data[i][j][k][2 * l + 1];
                    }

                    DoubleFFT_1D fftW = new DoubleFFT_1D(w);
                    fftW.complexForward(tempW);

                    for (int i = 0; i < w; i++) {
                        data[i][j][k][2 * l] = tempW[2 * i];
                        data[i][j][k][2 * l + 1] = tempW[2 * i + 1];
                    }
                }
            }
        }
    }


    public void complexForwardOpt(double[][][][] data) {
        // Step 1: Apply 3D FFT to each slice along the w-axis
        DoubleFFT_3D fft3d = new DoubleFFT_3D(x, y, z);
        for (int i = 0; i < w; i++) {
            fft3d.complexForward(data[i]);  // data[i] is double[x][y][2*z]
        }

        // Step 2: Apply 1D FFT along the w-axis for each (j, k, l)
        DoubleFFT_1D fftW = new DoubleFFT_1D(w);
        double[] temp = new double[2 * w]; // temp array for 1D FFT

        for (int j = 0; j < x; j++) {
            for (int k = 0; k < y; k++) {
                for (int l = 0; l < z; l++) {
                    // Gather complex values along w
                    for (int i = 0; i < w; i++) {
                        temp[2 * i] = data[i][j][k][2 * l];     // real
                        temp[2 * i + 1] = data[i][j][k][2 * l + 1]; // imag
                    }

                    // Perform 1D complex FFT
                    fftW.complexForward(temp);

                    // Store result back into data array
                    for (int i = 0; i < w; i++) {
                        data[i][j][k][2 * l] = temp[2 * i];
                        data[i][j][k][2 * l + 1] = temp[2 * i + 1];
                    }
                }
            }
        }
    }


    public void complexInverse(double[][][][] data) {
        // Step 1: Inverse 1D FFT along w-axis
        DoubleFFT_1D fftW = new DoubleFFT_1D(w);
        double[] temp = new double[2 * w];

        for (int j = 0; j < x; j++) {
            for (int k = 0; k < y; k++) {
                for (int l = 0; l < z; l++) {
                    // Gather complex values across w
                    for (int i = 0; i < w; i++) {
                        temp[2 * i] = data[i][j][k][2 * l];
                        temp[2 * i + 1] = data[i][j][k][2 * l + 1];
                    }

                    // Perform inverse FFT (scale = true)
                    fftW.complexInverse(temp, true);

                    // Store back the result
                    for (int i = 0; i < w; i++) {
                        data[i][j][k][2 * l] = temp[2 * i];
                        data[i][j][k][2 * l + 1] = temp[2 * i + 1];
                    }
                }
            }
        }

        // Step 2: Inverse 3D FFT on each w-slice
        DoubleFFT_3D fft3d = new DoubleFFT_3D(x, y, z);
        for (int i = 0; i < w; i++) {
            fft3d.complexInverse(data[i], true);  // scale = true for normalization
        }
    }

    // Example usage
    public static void do4d() {
        int w = 4;
        int x = 4;
        int y = 4;
        int z = 4;
        FFT4D fft4D = new FFT4D(w, x, y, z);
        double[][][][] data = new double[w][x][y][2 * z]; // interleaved complex format

        // Fill with sample data
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < x; j++) {
                for (int k = 0; k < y; k++) {
                    for (int l = 0; l < z; l++) {
                        data[i][j][k][2 * l] = Math.random();       // real part
                        data[i][j][k][2 * l + 1] = 0.0;             // imaginary part
                    }
                }
            }
        }

        // Perform 4D FFT
        fft4D.complexForward(data);
    }
}
