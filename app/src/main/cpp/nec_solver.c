#include "nec_solver.h"

#include <locale.h>
#include <stdarg.h>

#include "opennec.h"
#include "internals.h"

static bool add_card(deck_t *deck, const char *format, ...) {
    char card[MAX_LINE_LEN];
    va_list values;
    va_start(values, format);
    int length = vsnprintf(card, sizeof(card), format, values);
    va_end(values);
    if (length <= 0 || length >= (int) sizeof(card) || append_card_from_text(deck, card) != 0) return false;
    // The parser rebuilds these fields from orig_str for programmatically added cards.
    card_t *raw = &deck->cards[deck->num_cards - 1];
    free(raw->card_str);
    raw->card_str = NULL;
    free(raw->comment);
    raw->comment = NULL;
    return true;
}

static bool contains_error(const errors_list_t *errors) {
    for (int i = 0; i < errors->num_errors; ++i) {
        if (errors->errors[i].severity >= ONEC_SEV_ERROR) return true;
    }
    return false;
}

static void discard_log(void *unused, int level, const char *message) {
    (void) unused;
    (void) level;
    (void) message;
}

bool antcalc_nec_solve(const nec_wire_t *wires, int wire_count, int feed,
                      double frequency_mhz, double result[NEC_RESULT_SIZE]) {
    if (wire_count < 1 || wire_count > NEC_MAX_WIRES || feed < 0 || feed >= wire_count ||
        !isfinite(frequency_mhz) || frequency_mhz < 0.1 || frequency_mhz > 6000) return false;

    int segments = 0;
    double previous = -1.0;
    double previous_radius = 0.0;
    for (int i = 0; i < wire_count; ++i) {
        const nec_wire_t wire = wires[i];
        const double radius = wire.diameter_m / 2;
        if (!isfinite(wire.position_m) || wire.position_m < 0 || wire.position_m > 1000 ||
            (i > 0 && wire.position_m - previous <= radius + previous_radius) ||
            !isfinite(wire.length_m) || wire.length_m <= 0 || wire.length_m > 1000 ||
            !isfinite(radius) || radius <= 0 ||
            wire.segments < 3 || wire.segments > NEC_MAX_SEGMENTS || wire.segments % 2 != 1 ||
            wire.length_m / wire.segments < 4 * radius ||
            wire.length_m / wire.segments > 0.1 * CVEL / frequency_mhz) return false;
        segments += wire.segments;
        if (segments > NEC_MAX_SEGMENTS) return false;
        previous = wire.position_m;
        previous_radius = radius;
    }

    context_t *ctx = create_context();
    if (!ctx) return false;
    locale_t c_locale = newlocale(LC_NUMERIC_MASK, "C", (locale_t) 0);
    if (!c_locale) {
        destroy_context(ctx);
        return false;
    }
    locale_t old_locale = uselocale(c_locale);
    if (!old_locale) {
        freelocale(c_locale);
        destroy_context(ctx);
        return false;
    }
    set_log_callback(ctx, discard_log, NULL);
    deck_t deck;
    init_deck(&deck);
    errors_list_t errors = {0};
    bool success = add_card(&deck, "CM AntCalc wire model") && add_card(&deck, "CE");
    for (int i = 0; i < wire_count && success; ++i) {
        const nec_wire_t wire = wires[i];
        success = add_card(&deck, "GW %d %d %.12g %.12g 0 %.12g %.12g 0 %.12g",
                           i + 1, wire.segments, wire.position_m, -wire.length_m / 2,
                           wire.position_m, wire.length_m / 2, wire.diameter_m / 2);
    }
    // OpenNEC treats a pattern card immediately after EX as an orphaned request.
    success = success && add_card(&deck, "GE 0") &&
        add_card(&deck, "EX 0 %d %d 0 1 0", feed + 1, wires[feed].segments / 2 + 1) &&
        add_card(&deck, "FR 0 1 0 0 %.12g", frequency_mhz) &&
        add_card(&deck, "RP 0 1 73 1000 90 0 0 5") && add_card(&deck, "EN");
    if (success) {
        parse_deck(ctx, &deck, &errors);
        success = !contains_error(&errors) && run_simulation(ctx, &deck) == 0 &&
            !contains_error(&ctx->errors) && ctx->netcx.ninp == 1 &&
            ctx->rpat.num_points == NEC_AZIMUTH_POINTS;
    }
    if (success) {
        result[0] = creal(ctx->netcx.inp_z[0]);
        result[1] = cimag(ctx->netcx.inp_z[0]);
        for (int i = 0; i < NEC_AZIMUTH_POINTS; ++i) result[2 + i] = ctx->rpat.points[i].gtot;
        success = result[0] > 0;
        for (int i = 0; i < NEC_RESULT_SIZE && success; ++i) success = isfinite(result[i]);
    }
    for (int i = 0; i < errors.num_errors; ++i) free(errors.errors[i].message);
    free(errors.errors);
    destroy_deck(&deck);
    destroy_context(ctx);
    uselocale(old_locale);
    freelocale(c_locale);
    return success;
}
