package org.nmrfx.processor.datasets.vendor.rs2d;

import java.util.List;

public enum RS2DParam {
    // Default Params
    MATRIX_DIMENSION_1D(false),
    MATRIX_DIMENSION_2D(false),
    MATRIX_DIMENSION_3D(false),
    MATRIX_DIMENSION_4D(false),
    OBSERVED_FREQUENCY(true),
    DUMMY_SCAN(true),
    ACQUISITION_MATRIX_DIMENSION_1D(true),
    ACQUISITION_MATRIX_DIMENSION_2D(true),
    ACQUISITION_MATRIX_DIMENSION_3D(true),
    ACQUISITION_MATRIX_DIMENSION_4D(true),
    USER_MATRIX_DIMENSION_1D(true),
    USER_MATRIX_DIMENSION_2D(true),
    USER_MATRIX_DIMENSION_3D(true),
    USER_MATRIX_DIMENSION_4D(true),
    MAGNETIC_FIELD_STRENGTH(true),
    DATA_REPRESENTATION(true),
    ACQUISITION_MODE(true),
    MODALITY(true),
    RECEIVER_COUNT(true),
    NUMBER_OF_AVERAGES(true),
    STATE(false),
    RECEIVER_GAIN(true),
    PULSE_LENGTH(false),
    PULSE_POWER(false),
    PAROPT_PARAM(true),
    SPECTRAL_WIDTH(true),
    DWELL_TIME(true),
    ACQUISITON_TIME_PER_SCAN(true),
    PHASE_FIELD_OF_VIEW_RATIO(false),
    AMPLITUDE_AND_PHASE(false),
    TRANSFORM_PLUGIN(true),
    SETUP_MODE(true),
    SEQUENCE_NAME(true),
    SEQUENCE_TIME(false),
    DIGITAL_FILTER_SHIFT(true),
    DIGITAL_FILTER_REMOVED(true),
    OBSERVED_NUCLEUS(true),
    NUCLEUS_1(true),
    NUCLEUS_2(true),
    NUCLEUS_3(true),
    NUCLEUS_4(true),
    BASE_FREQ_1(true),
    BASE_FREQ_2(true),
    BASE_FREQ_3(true),
    BASE_FREQ_4(true),
    OFFSET_FREQ_1(true),
    OFFSET_FREQ_2(true),
    OFFSET_FREQ_3(true),
    OFFSET_FREQ_4(true),
    TRANSMIT_FREQ_1(true),
    TRANSMIT_FREQ_2(true),
    TRANSMIT_FREQ_3(true),
    TRANSMIT_FREQ_4(true),
    TX_ROUTE(true),
    MANUFACTURER(true),
    SOFTWARE_VERSION(true),
    STATION_NAME(true),
    MODEL_NAME(true),
    LAST_PUT(true),
    INTERMEDIATE_FREQUENCY(true),
    PROBES(true),
    PROBE_MAX_GRADIENT(false),
    PHASE_0(true),
    PHASE_1(true),
    PHASE_APPLIED(true),
    ACCU_DIM(true),
    // Hardware settings
    INSTRUMENT_PREEMPHASIS_LABEL(false),
    INSTRUMENT_PREEMPHASIS(false),
    INSTRUMENT_DC(false),
    INSTRUMENT_A0(false),
    INSTRUMENT_SHIM_LABEL(false),
    INSTRUMENT_SHIM(false),

    INSTRUMENT_PROBE_CALIB_WIDTH_NUC_1(false),
    INSTRUMENT_PROBE_CALIB_WIDTH_NUC_2(false),
    INSTRUMENT_PROBE_CALIB_WIDTH_NUC_3(false),
    INSTRUMENT_PROBE_CALIB_WIDTH_NUC_4(false),

    INSTRUMENT_PROBE_CALIB_POWER_NUC_1(false),
    INSTRUMENT_PROBE_CALIB_POWER_NUC_2(false),
    INSTRUMENT_PROBE_CALIB_POWER_NUC_3(false),
    INSTRUMENT_PROBE_CALIB_POWER_NUC_4(false),


    //NMRDefaultParams
    SOLVENT(true),
    LOCK(true),
    SPECTRAL_WIDTH_2D(true),
    SPECTRAL_WIDTH_3D(true),
    SPECTRAL_WIDTH_4D(false),
    FID_RES(true),
    FID_RES_2D(true),
    SAMPLE_TEMPERATURE(true),
    SPIN_RATE(true),
    SR(true),
    PHASE_MOD(true);

    // param groups
    public static final List<RS2DParam> DIMENSION_PARAMS = List.of(MATRIX_DIMENSION_1D, MATRIX_DIMENSION_2D, MATRIX_DIMENSION_3D, MATRIX_DIMENSION_4D);
    public static final List<RS2DParam> ACQUISITION_DIMENSION_PARAMS = List.of(ACQUISITION_MATRIX_DIMENSION_1D, ACQUISITION_MATRIX_DIMENSION_2D, ACQUISITION_MATRIX_DIMENSION_3D, ACQUISITION_MATRIX_DIMENSION_4D);
    public static final List<RS2DParam> NUCLEUS_PARAMS = List.of(NUCLEUS_1, NUCLEUS_2, NUCLEUS_3, NUCLEUS_4);
    public static final List<RS2DParam> BASE_FREQ_PARAMS = List.of(BASE_FREQ_1, BASE_FREQ_2, BASE_FREQ_3, BASE_FREQ_4);
    public static final List<RS2DParam> OFFSET_FREQ_PARAMS = List.of(OFFSET_FREQ_1, OFFSET_FREQ_2, OFFSET_FREQ_3, OFFSET_FREQ_4);
    public static final List<RS2DParam> TRANSMIT_FREQ_PARAMS = List.of(TRANSMIT_FREQ_1, TRANSMIT_FREQ_2, TRANSMIT_FREQ_3, TRANSMIT_FREQ_4);
    public static final List<RS2DParam> SW_PARAMS = List.of(SPECTRAL_WIDTH, SPECTRAL_WIDTH_2D, SPECTRAL_WIDTH_3D, SPECTRAL_WIDTH_4D);


    private final boolean mandatory; //whether the parameter is mandatory in the header

    RS2DParam(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public boolean isMandatory() {
        return mandatory;
    }
}
