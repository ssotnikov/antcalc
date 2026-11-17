#include <jni.h>

#include "nec_solver.h"

JNIEXPORT jdoubleArray JNICALL Java_local_antenna_calculator_nec_OpenNec_solvePoint(
    JNIEnv *env, jobject instance, jdoubleArray geometry, jintArray segment_counts,
    jint feed, jdouble frequency_mhz
) {
    (void) instance;
    if (!geometry || !segment_counts) return NULL;
    jsize count = (*env)->GetArrayLength(env, segment_counts);
    if (count < 1 || count > NEC_MAX_WIRES || (*env)->GetArrayLength(env, geometry) != count * 3) return NULL;

    jdouble packed[NEC_MAX_WIRES * 3];
    jint segments[NEC_MAX_WIRES];
    (*env)->GetDoubleArrayRegion(env, geometry, 0, count * 3, packed);
    (*env)->GetIntArrayRegion(env, segment_counts, 0, count, segments);
    if ((*env)->ExceptionCheck(env)) return NULL;

    nec_wire_t wires[NEC_MAX_WIRES];
    for (jsize i = 0; i < count; ++i) {
        wires[i] = (nec_wire_t) {packed[i * 3], packed[i * 3 + 1], packed[i * 3 + 2], segments[i]};
    }
    double result[NEC_RESULT_SIZE];
    if (!antcalc_nec_solve(wires, count, feed, frequency_mhz, result)) return NULL;
    jdoubleArray array = (*env)->NewDoubleArray(env, NEC_RESULT_SIZE);
    if (array) (*env)->SetDoubleArrayRegion(env, array, 0, NEC_RESULT_SIZE, result);
    return array;
}
