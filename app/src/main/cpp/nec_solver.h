#ifndef ANTCALC_NEC_SOLVER_H
#define ANTCALC_NEC_SOLVER_H

#include <stdbool.h>

enum {
    NEC_MAX_WIRES = 24,
    NEC_MAX_SEGMENTS = 241,
    NEC_AZIMUTH_POINTS = 73,
    NEC_RESULT_SIZE = 2 + NEC_AZIMUTH_POINTS
};

typedef struct {
    double position_m;
    double length_m;
    double diameter_m;
    int segments;
} nec_wire_t;

bool antcalc_nec_solve(const nec_wire_t *wires, int wire_count, int feed,
                       double frequency_mhz, double result[NEC_RESULT_SIZE]);

#endif
