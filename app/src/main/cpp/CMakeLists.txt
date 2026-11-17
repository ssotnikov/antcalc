cmake_minimum_required(VERSION 3.22.1)
project(antcalc_nec LANGUAGES C)

set(CMAKE_C_STANDARD 99)
set(CMAKE_C_STANDARD_REQUIRED ON)

add_library(opennec STATIC
    opennec/calculations.c opennec/card_validation.c opennec/control.c
    opennec/deck.c opennec/deck_validations.c opennec/fields.c
    opennec/geometry.c opennec/ground.c opennec/input.c opennec/matrix.c
    opennec/misc.c opennec/network.c opennec/output.c opennec/radiation.c
    opennec/reporting.c opennec/somnec.c opennec/tinyexpr.c opennec/types.c
    opennec/import-export/maa-support.c opennec/import-export/nc-support.c
    opennec/import-export/nec2-support.c opennec/import-export/nec4-support.c
    opennec/import-export/yo-support.c
)
target_include_directories(opennec PUBLIC opennec)
target_compile_definitions(opennec PRIVATE USE_ORIGINAL_MATRIX _GNU_SOURCE)
set_target_properties(opennec PROPERTIES POSITION_INDEPENDENT_CODE ON C_VISIBILITY_PRESET hidden)
target_link_libraries(opennec PUBLIC m)

add_library(antcalc_nec SHARED nec_solver.c)
if(ANDROID)
    target_sources(antcalc_nec PRIVATE nec_jni.c)
else()
    target_compile_definitions(antcalc_nec PRIVATE _POSIX_C_SOURCE=200809L)
endif()
target_link_libraries(antcalc_nec PRIVATE opennec)
