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

private fun sleeve(at: AntennaPoint, axis: AntennaPoint, length: Float, diameter: Float, material: Int): AntennaRod {
    val half = axis * (length / 2)
    return AntennaRod(at - half, at + half, diameter, material)
}

private fun coaxConnector(at: AntennaPoint): List<AntennaRod> = listOf(
    sleeve(at, point(y = 1), 11f, 11f, 0),
    sleeve(at + point(y = 4), point(y = 1), 3f, 13f, 2),
    sleeve(at + point(y = -5), point(y = 1), 2f, 12f, 2),
    sleeve(at + point(y = -11), point(y = 1), 9f, 7f, 1),
)

internal fun groundPlaneVector(layout: GroundPlaneResult.Layout): AntennaVector {
    val unit = 140 / maxOf(layout.whipMeters, layout.radialMeters)
    val radial = layout.radiusMeters * unit
    val drop = -layout.dropMeters * unit
    val hub = point()
    val ends = listOf(point(radial, drop), point(0, drop, radial), point(-radial, drop), point(0, drop, -radial))
    val top = point(y = layout.whipMeters * unit)
    val coupling = minOf(12f, top.y / 3)
    val ferrules = ends.map { tip ->
        val direction = tip * (1f / tip.length)
        sleeve(direction * minOf(11f, tip.length * .22f), direction, 9f, 8f, 2)
    }
    return AntennaVector(
        listOf(AntennaRod(hub, top, 6f, 0), AntennaRod(point(y = -17), point(y = -54), 6f, 1),
            sleeve(point(y = coupling), point(y = 1), 9f, 9f, 0), sleeve(hub, point(y = 1), 8f, 18f, 2)) +
            ends.map { AntennaRod(hub, it, 5f, 0) } + ferrules + coaxConnector(point(y = -10)),
        ends.map { AntennaJoint(it, 4f, 0) } + AntennaJoint(top, 4f, 0),
    )
}

internal fun invertedVVector(layout: InvertedVResult): AntennaVector {
    val half = layout.angleTenths * PI / 3600
    val width = 145 * sin(half)
    val height = 145 * cos(half)
    val tips = listOf(point(-width, 30 - height), point(width, 30 - height))
    val feed = point(0, 30)
    return AntennaVector(
        tips.mapIndexed { index, tip -> AntennaRod(point(if (index == 0) -5 else 5, 30), tip, 4f, 5) } +
            listOf(sleeve(feed, point(x = 1), 18f, 11f, 3), AntennaRod(point(0, 19), point(0, -80, 6), 4f, 1)) +
            coaxConnector(point(0, 20)),
        tips.map { AntennaJoint(it, 5f, 4) } + listOf(AntennaJoint(point(-6, 31, 5), 4f, 2), AntennaJoint(point(6, 31, 5), 4f, 2)),
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
    val perimeter = (vertices + vertices.first()).zipWithNext { a, b ->
        val axis = (b - a) * (1f / (b - a).length)
        val start = if ((at - a).length < 1f) a + axis * 5f else a
        val end = if ((at - b).length < 1f) b - axis * 5f else b
        if ((a - at).length + (b - at).length - (b - a).length < .01f && start == a && end == b) {
            listOf(AntennaRod(a, at - axis * 5f, 5f, 5), AntennaRod(at + axis * 5f, b, 5f, 5))
        } else listOf(AntennaRod(start, end, 5f, 5))
    }.flatten()
    return AntennaVector(
        perimeter + coaxConnector(at + point(0, -10, 4)) + listOf(AntennaRod(at, at + point(0, -44, 12), 4f, 1)),
        vertices.filter { it != at }.map { AntennaJoint(it, 6f, 4) } + AntennaJoint(at + point(z = 3), 9f, 3),
    )
}

internal fun moxonVector(model: MoxonDimensions, includeMount: Boolean = true): AntennaVector {
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
    val gapSpacers = listOf(-1, 1).map { side ->
        AntennaRod(point(side * width, z = front + model.drivenTailMm * scale),
            point(side * width, z = back - model.reflectorTailMm * scale), 7f, 3)
    }
    val outline = driven + reflector.zipWithNext { a, b -> AntennaRod(a, b, 6f, 0) } + gapSpacers
    val feedJoint = AntennaJoint(feed, 10f, 2)
    if (!includeMount) return AntennaVector(outline, listOf(feedJoint))
    return AntennaVector(outline + coaxConnector(feed + point(y = -8)) +
        AntennaRod(point(y = -6, z = front), point(y = -6, z = back), 9f, 1),
        listOf(feedJoint, AntennaJoint(point(y = -5, z = back), 8f, 3)))
}

internal fun moxonPublishedVector(profile: MoxonProfile): AntennaVector? {
    val model = profile.dimensions ?: return null
    val scale = 257 / model.widthMm
    val front = -model.depthMm * scale / 2
    val back = -front
    if (profile.hybrid) {
        val base = moxonVector(model, includeMount = false)
        val extra = moxonSatelliteYagi.mapIndexed { index, (position, length) ->
            val z = back - position * scale
            AntennaRod(point(-length * scale / 2, z = z), point(length * scale / 2, z = z),
                4f, if (index == 0) 2 else 0)
        }
        val boom = AntennaRod(point(y = -5, z = back + 24 * scale),
            point(y = -5, z = back - 957 * scale), 8f, 1)
        return base.copy(rods = base.rods + extra + boom)
    }
    if (profile.view !in setOf(MoxonView.CB_PENNANT, MoxonView.CB_VERTICAL)) return null
    val base = moxonVector(model, includeMount = false)
    fun upright(p: AntennaPoint) = AntennaPoint(-p.y, p.x, p.z)
    val rods = base.rods.map { it.copy(first = upright(it.first), last = upright(it.last)) }
    val joints = base.joints.map { it.copy(point = upright(it.point)) }
    val mast = point(x = -10, z = front + 650 * scale)
    val supportLength = 1470 * scale
    val halfHeight = model.widthMm * scale / 2
    val support = listOf(-halfHeight, halfHeight).map { height ->
        AntennaRod(point(x = -5, y = height, z = front - 20 * scale),
            point(x = -5, y = height, z = front - 20 * scale + supportLength), 4f, 3)
    }
    val mastRod = AntennaRod(mast + point(y = 145), mast + point(y = -153), 8f, 1)
    val feed = point(z = front)
    val lead = if (profile.view == MoxonView.CB_PENNANT) {
        val guideEnd = point(z = front + 580 * scale)
        support + AntennaRod(feed, guideEnd, 4f, 3) +
            listOf(feed, guideEnd, mast + point(y = -9), mast + point(y = -153))
                .zipWithNext { a, b -> AntennaRod(a, b, 2f, 2) }
    } else {
        support + listOf(feed, point(z = mast.z), mast + point(y = -153))
            .zipWithNext { a, b -> AntennaRod(a, b, 2f, 2) }
    }
    return AntennaVector(rods + mastRod + lead, joints)
}
