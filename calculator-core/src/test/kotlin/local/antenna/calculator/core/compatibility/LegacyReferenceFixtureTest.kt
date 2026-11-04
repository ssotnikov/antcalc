package local.antenna.calculator.core.compatibility

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private fun referenceBytes(): ByteArray =
    requireNotNull(LegacyReferenceFixtureTest::class.java.getResourceAsStream("/reference-verification.json"))
        .use { it.readBytes() }

@RunWith(Parameterized::class)
class LegacyReferenceFixtureTest(private val record: JsonObject) {
    @Test
    fun `recorded machine code result matches exactly`() {
        val current = LegacyFrequency.ofKilohertz(record.get("frequency_khz").asLong)
        val coefficient = LegacyCoefficient.ofPercent(record.get("k_percent").asInt)
        val expected = record.getAsJsonArray("raw_mm").map { it.asLong }
        val labels = record.getAsJsonArray("labels").map { it.asString }
        when (record.get("mode").asString) {
            "LEN" -> {
                val result = calculateLegacyLengths(LegacyLengthsInput(current, coefficient))
                assertEquals(expected, listOf(result.correctedFullLengthMillimeters, result.halfLengthMillimeters,
                    result.quarterLengthMillimeters, result.fiveEighthsLengthMillimeters))
                assertEquals(listOf("FR", "L", "L/2", "L/4", "5/8"), labels)
            }
            "TRIM" -> {
                val target = LegacyFrequency.ofKilohertz(record.get("target_khz").asLong)
                val result = calculateLegacyTrim(LegacyTrimInput(current, target, coefficient))
                assertEquals(expected, listOf(result.currentLengthMillimeters, result.targetLengthMillimeters, result.amountMillimeters))
                val expectedCommand = when (labels.last()) {
                    "+ADD" -> LegacyTrimCommand.ADD
                    "-CUT" -> LegacyTrimCommand.CUT
                    else -> error("Unknown command in reference fixture")
                }
                assertEquals(expectedCommand, result.command)
            }
            else -> error("Unknown mode in reference fixture")
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "reference[{index}]")
        fun cases(): List<Array<Any>> = JsonParser.parseString(referenceBytes().toString(Charsets.UTF_8))
            .asJsonArray.map { arrayOf(it.asJsonObject) }
    }
}

class LegacyFixtureIntegrityTest {
    @Test
    fun `reference file preserves all seven original records byte for byte`() {
        val bytes = referenceBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals("9a12a3c4064b7dbc4d5659f5ce04faf418de6ad7808e7eae2228a21912f20124", hash)
        assertEquals(7, JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonArray.size())
    }
}
