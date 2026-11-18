package local.antenna.calculator.nec

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import local.antenna.calculator.core.layout.YagiScaling.Element
import local.antenna.calculator.core.matching.MatchingMeasure
import local.antenna.calculator.core.matching.matchingFromImpedance
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenNecAcceptanceTest {
    private val dipole = listOf(Element(0.0, 1.0, .008))
    private val yagi = listOf(
        Element(0.0, 1.04, .008), Element(.215, .970, .008),
        Element(.415, .945, .008), Element(.810, .908, .008),
    )

    @Test fun dipoleAndYagiMatch4nec2OnTheSameGrid() = runBlocking {
        assertTrue(OpenNec.available)
        val fixture = InstrumentationRegistry.getInstrumentation().context.assets
            .open("opennec/nec2-reference.json").bufferedReader().use { JSONObject(it.readText()) }
        for ((name, geometry) in listOf("dipole" to dipole, "yagi" to yagi)) {
            val grid = listOf(144_000_000L, 145_000_000L, 146_000_000L)
            val points = checkNotNull(OpenNec.sweep(geometry, List(geometry.size) { 21 }, minOf(1, geometry.lastIndex), grid))
            val reference = fixture.getJSONArray(name)
            points.forEachIndexed { index, point ->
                val expected = reference.getJSONObject(index)
                val azimuth = expected.getJSONArray("azimuthDbi")
                val resistance = expected.getDouble("r")
                val reactance = expected.getDouble("x")
                val swr = matchingFromImpedance(resistance, reactance, 50.0).swr as MatchingMeasure.Finite
                assertEquals(grid[index], point.frequencyHz)
                assertEquals(resistance, point.resistance, .01)
                assertEquals(reactance, point.reactance, .01)
                assertEquals(swr.value, point.swr, .001)
                assertEquals(azimuth.getDouble(0), point.forwardDbi, .02)
                assertEquals(azimuth.getDouble(0) - azimuth.getDouble(36), point.frontToBackDb, .04)
                point.azimuthDbi.forEachIndexed { angle, gain ->
                    val expectedGain = azimuth.getDouble(angle)
                    if (maxOf(expectedGain, gain) > -70) assertEquals("$name $index $angle", expectedGain, gain, .02)
                }
            }
        }
    }

    @Test fun dk7zbMeshRefinementMatchesTheDesktopReference() = runBlocking {
        val geometry = listOf(Element(0.0, 1.010, .010), Element(.210, .920, .010))
        val grid = (0..20).map { 144_000_000L + 100_000L * it }
        val counts = checkNotNull(OpenNec.segmentCounts(geometry, grid.last()))
        val finer = checkNotNull(OpenNec.finerSegments(geometry, counts))
        val coarse = checkNotNull(OpenNec.sweep(geometry, counts, 0, grid))
        val fine = checkNotNull(OpenNec.sweep(geometry, finer, 0, grid))
        assertEquals(1.081, coarse[10].reactance, .01)
        assertEquals(3.278, fine[10].reactance, .01)
        assertEquals(2.47, coarse.zip(fine).maxOf { (a, b) -> kotlin.math.abs(a.reactance - b.reactance) }, .02)
    }

    @Test fun invalidAndCancelledRunsAllowASecondSimulation() = runBlocking {
        val grid = (0..40).map { 144_000_000L + 50_000L * it }
        assertNull(OpenNec.sweep(yagi, List(yagi.size) { 61 }, 1, grid))
        assertNull(OpenNec.sweep(yagi, List(yagi.size) { 21 }, 1, grid.reversed()))
        assertNull(OpenNec.sweep(listOf(yagi.first(), yagi.first()), listOf(21, 21), 1, grid))
        val job = launch { OpenNec.sweep(yagi, List(yagi.size) { 51 }, 1, grid) }
        delay(10)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        val next = checkNotNull(OpenNec.sweep(yagi, List(yagi.size) { 21 }, 1, listOf(145_000_000L)))
        assertEquals(145_000_000L, next.single().frequencyHz)
    }

    @Test fun recordsTimeAndSampledNativeHeapForABoundedSweep() = runBlocking {
        val grid = (0..40).map { 144_000_000L + 50_000L * it }
        val segments = List(yagi.size) { 51 }
        assertEquals(1, OpenNec.sweep(yagi, segments, 1, listOf(grid.first()))?.size)
        val baseline = Debug.getNativeHeapAllocatedSize()
        val peak = AtomicLong(baseline)
        val sampler = launch(Dispatchers.Default) {
            while (isActive) {
                peak.accumulateAndGet(Debug.getNativeHeapAllocatedSize(), ::maxOf)
                delay(5)
            }
        }
        val start = SystemClock.elapsedRealtime()
        try {
            assertEquals(41, OpenNec.sweep(yagi, segments, 1, grid)?.size)
        } finally {
            val elapsedMs = SystemClock.elapsedRealtime() - start
            sampler.cancelAndJoin()
            Log.i("AntCalcOpenNec", "41 points, 204 segments, ${elapsedMs} ms, sampled native heap increase ${peak.get() - baseline} bytes")
        }
    }
}
