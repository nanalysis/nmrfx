package org.nmrfx.processor.math;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.DoubleStream;

import static org.junit.Assert.*;

public class SignalCouplerTest {

    double[] positions = {5.221, 5.222, 5.227, 5.228, 7.312, 7.312, 7.314, 7.316, 7.321, 7.322, 7.324, 7.325, 7.326,
            7.327, 7.328, 7.352, 7.355, 7.357, 7.363, 7.366, 7.367, 7.371, 7.377, 7.379, 7.380, 7.381, 7.402, 7.405,
            7.405, 7.406, 7.407, 7.409, 7.417, 7.418, 7.419, 7.420, 7.420, 7.422, 7.428, 7.431, 7.431, 7.432, 7.433,
            7.526, 7.527, 7.529, 7.530, 7.537, 7.537, 7.539, 7.540, 7.542, 7.550, 7.552, 7.553, 7.553, 7.554, 7.612,
            7.614, 7.616, 7.624, 7.625, 7.626, 7.627, 7.628, 7.630, 7.637, 7.639, 7.641, 7.813, 7.816, 7.817, 7.819,
            7.825, 7.826, 7.827, 7.827, 7.828, 7.829, 7.830};

    @Test
    public void testCoupler() {

        List<Double> positionsList = DoubleStream.of(positions)
                .boxed()
                .toList();

        SignalCoupler signalCoupler = new SignalCoupler();
        List<SignalCoupler.Group> groups = signalCoupler.couple(positionsList, 0.03);
        for (SignalCoupler.Group group : groups) {
            for (int id : group.ids) {
                System.out.printf("%.3f ", positionsList.get(id));
            }
            System.out.println();

        }
    }
}