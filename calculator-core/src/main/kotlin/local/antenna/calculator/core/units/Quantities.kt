package local.antenna.calculator.core.units

/** A working frequency, stored exactly in whole hertz. */
@JvmInline
value class Frequency private constructor(val hertz: Long) {
    companion object {
        const val MIN_HERTZ = 100_000L
        const val MAX_HERTZ = 6_000_000_000L

        fun ofHertz(hertz: Long): Frequency {
            require(hertz in MIN_HERTZ..MAX_HERTZ) { "Frequency is outside the working range" }
            return Frequency(hertz)
        }
    }
}

/** The measured conductor length, stored exactly in nanometers; excludes the feed gap. */
@JvmInline
value class PhysicalLength private constructor(val nanometers: Long) {
    companion object {
        const val MIN_NANOMETERS = 1_000_000L
        const val MAX_NANOMETERS = 10_000_000_000_000L

        fun ofNanometers(nanometers: Long): PhysicalLength {
            require(nanometers in MIN_NANOMETERS..MAX_NANOMETERS) { "Length is outside the working range" }
            return PhysicalLength(nanometers)
        }
    }
}

/** A physical-length model coefficient with four decimal places of resolution. */
@JvmInline
value class ShorteningFactor private constructor(val tenThousandths: Int) {
    companion object {
        const val MIN_TEN_THOUSANDTHS = 1
        const val MAX_TEN_THOUSANDTHS = 10_000

        val DEFAULT = ofTenThousandths(9_500)
        val PRESETS = listOf(10_000, 9_700, 9_500, 9_300).map(::ofTenThousandths)

        fun ofTenThousandths(value: Int): ShorteningFactor {
            require(value in MIN_TEN_THOUSANDTHS..MAX_TEN_THOUSANDTHS) { "Invalid shortening factor" }
            return ShorteningFactor(value)
        }
    }
}
