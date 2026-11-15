package local.antenna.calculator.ui

import local.antenna.calculator.core.layout.GroundPlaneResult
import local.antenna.calculator.core.layout.InvertedVResult
import local.antenna.calculator.core.layout.LoopFeed
import local.antenna.calculator.core.layout.LoopShape
import local.antenna.calculator.core.layout.MoxonDimensions
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun point(x: Number = 0, y: Number = 0, z: Number = 0) = AntennaPoint(x.toFloat(), y.toFloat(), z.toFloat())

internal fun groundPlaneVector(layout: GroundPlaneResult.Layout): AntennaVector {
    val unit = 140 / maxOf(layout.whipMeters, layout.radialMeters)
    val radial = layout.radiusMeters * unit
    val drop = -layout.dropMeters * unit
    val hub = point()
    val ends = listOf(point(radial, drop), point(0, drop, radial), point(-radial, drop), point(0, drop, -radial))
    return AntennaVector(
        listOf(AntennaRod(hub, point(0, layout.whipMeters * unit), 6f, 0), AntennaRod(hub, point(0,-65), 9f, 1)) +
            ends.map { AntennaRod(hub, it, 5f, 0) },
        listOf(AntennaJoint(hub, 15f, 2)),
    )
}

internal fun invertedVVector(layout: InvertedVResult): AntennaVector {
    val half = layout.angleTenths * PI / 3600
    val width = 145 * sin(half)
    val height = 145 * cos(half)
    return AntennaVector(
        listOf(AntennaRod(point(-4,30), point(-width,30-height), 4f, 5),
            AntennaRod(point(4,30), point(width,30-height), 4f, 5),
            AntennaRod(point(0,26), point(0,-80,9), 4f, 1)),
        listOf(AntennaJoint(point(0,30), 12f, 3)),
    )
}

internal fun loopVector(shape: LoopShape, feed: LoopFeed): AntennaVector {
    val vertices = if (shape == LoopShape.SQUARE) {
        listOf(point(-91,-76), point(91,-76), point(91,106), point(-91,106))
    } else listOf(point(-120,-69), point(120,-69), point(0,139))
    fun between(a: Int, b: Int, fraction: Float) = vertices[a] + (vertices[b] - vertices[a]) * fraction
    val at = when (feed) {
        LoopFeed.BASE -> between(0,1,.5f)
        LoopFeed.CORNER -> vertices[0]
        LoopFeed.SIDE_MIDDLE -> between(1,2,.5f)
        LoopFeed.APEX -> vertices[2]
        LoopFeed.QUARTER_FROM_APEX -> between(2,1,.25f)
    }
    return AntennaVector(
        (vertices + vertices.first()).zipWithNext { a,b -> AntennaRod(a,b,5f,5) } +
            AntennaRod(at, at + point(0,-44,12),4f,1),
        listOf(AntennaJoint(at,10f,2)),
    )
}

internal fun moxonVector(model: MoxonDimensions): AntennaVector {
    val scale = 257 / model.widthMm
    val width = model.widthMm * scale / 2
    val depth = (model.drivenTailMm + model.gapMm + model.reflectorTailMm) * scale
    val front = -depth / 2
    val back = depth / 2
    val feed = point(z = front)
    val driven = listOf(-1,1).flatMap { side ->
        val bend = point(side * width, z = front)
        listOf(AntennaRod(point(side * 5, z = front), bend, 6f, 0),
            AntennaRod(bend, bend + point(z = model.drivenTailMm * scale), 6f, 0))
    }
    val reflector = listOf(point(-width,z = back - model.reflectorTailMm * scale), point(-width,z = back),
        point(width,z = back), point(width,z = back - model.reflectorTailMm * scale))
    return AntennaVector(driven + reflector.zipWithNext { a,b -> AntennaRod(a,b,6f,0) } +
        AntennaRod(point(y = -6,z = front), point(y = -6,z = back),9f,1), listOf(AntennaJoint(feed,11f,2)))
}
