package local.antenna.calculator.journal

import android.util.JsonReader
import android.util.JsonToken
import android.util.MalformedJsonException
import java.io.EOFException
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.CharacterCodingException
import java.nio.CharBuffer
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.UUID
import local.antenna.calculator.core.journal.*
import local.antenna.calculator.core.trimming.AntennaType
import local.antenna.calculator.core.units.Frequency
import local.antenna.calculator.core.units.PhysicalLength
import org.json.JSONArray
import org.json.JSONObject

internal enum class JournalProblem { INVALID, UNSUPPORTED, LIMIT, READ, WRITE, MISSING }
internal class JournalException(val problem: JournalProblem) : Exception()

internal object JournalJson {
    private const val VERSION = 1L
    private const val PHOTO_STORE_VERSION = 2L

    fun writeStore(projects: List<AntennaProject>, output: OutputStream, photoStore: Boolean = projects.any { it.photos.isNotEmpty() }) {
        var written = 0
        fun write(bytes: ByteArray) {
            written += bytes.size
            if (written > JournalLimits.STORAGE_BYTES) throw JournalException(JournalProblem.LIMIT)
            output.write(bytes)
        }
        val version = if (photoStore) PHOTO_STORE_VERSION else VERSION
        write("{\"schemaVersion\":$version,\"projects\":[".encodeToByteArray())
        projects.forEachIndexed { index, project ->
            if (index != 0) write(byteArrayOf(','.code.toByte()))
            write(encode(encodeProject(project, photoStore), JournalLimits.STORAGE_BYTES - written))
        }
        write("]}".encodeToByteArray())
    }

    fun encodeTransfer(project: AntennaProject): ByteArray =
        encode(json("schemaVersion" to VERSION, "project" to encodeProject(project)), JournalLimits.TRANSFER_BYTES)

    fun decodeStore(input: InputStream, onVersion: (Boolean) -> Unit = {}): List<AntennaProject> =
        decode(input, JournalLimits.STORAGE_BYTES, allowPhotoStore = true) { root ->
            val photoStore = root.long("schemaVersion") == PHOTO_STORE_VERSION
            val projects = root.list("projects", JournalLimits.PROJECTS) { decodeProject(it, photoStore) }
            require(projects.map { it.id }.toSet().size == projects.size)
            onVersion(photoStore)
            projects
        }

    fun decodeTransfer(input: InputStream): AntennaProject =
        decode(input, JournalLimits.TRANSFER_BYTES) { decodeProject(it.obj("project")) }

    fun encodePendingSnapshot(snapshot: CalculationSnapshot): ByteArray =
        encode(json("schemaVersion" to VERSION, "snapshot" to encodeSnapshot(snapshot)), 16_384)

    fun decodePendingSnapshot(bytes: ByteArray): CalculationSnapshot =
        decode(bytes.inputStream(), 16_384) { decodeSnapshot(it.obj("snapshot")) }

    private fun encode(value: JSONObject, limit: Int): ByteArray {
        val text = value.toString()
        if (text.length > limit) throw JournalException(JournalProblem.LIMIT)
        val bytes = Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(text))
        if (bytes.remaining() > limit) throw JournalException(JournalProblem.LIMIT)
        return ByteArray(bytes.remaining()).also(bytes::get)
    }

    private fun <T> decode(input: InputStream, limit: Int, allowPhotoStore: Boolean = false, parse: (JsonObject) -> T): T {
        return try {
            val source = InputStreamReader(LimitedInput(input, limit), Charsets.UTF_8.newDecoder())
            JsonReader(source).use { reader ->
                val root = reader.readValue(0) as? JsonObject ?: invalid()
                require(reader.peek() == JsonToken.END_DOCUMENT)
                val version = root.long("schemaVersion")
                if (version != VERSION && !(allowPhotoStore && version == PHOTO_STORE_VERSION)) {
                    throw JournalException(JournalProblem.UNSUPPORTED)
                }
                parse(root)
            }
        } catch (_: IllegalArgumentException) {
            invalid()
        } catch (_: IllegalStateException) {
            invalid()
        } catch (_: DateTimeParseException) {
            invalid()
        } catch (_: MalformedJsonException) {
            invalid()
        } catch (_: EOFException) {
            invalid()
        } catch (_: CharacterCodingException) {
            invalid()
        }
    }

    private class LimitedInput(input: InputStream, private val limit: Int) : FilterInputStream(input) {
        private var readBytes = 0

        override fun read(): Int = super.read().also { if (it >= 0) count(1) }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, minOf(length, limit - readBytes + 1)).also { if (it > 0) count(it) }

        private fun count(size: Int) {
            readBytes += size
            if (readBytes > limit) throw JournalException(JournalProblem.LIMIT)
        }
    }

    // Strict parsing also rejects duplicate object keys and excessive nesting.
    private fun JsonReader.readValue(depth: Int): Any {
        require(depth <= 10)
        return when (peek()) {
            JsonToken.BEGIN_OBJECT -> {
                beginObject()
                val fields = mutableMapOf<String, Any>()
                while (hasNext()) {
                    val key = nextName()
                    require(fields.size < 32 && key.length <= 48 && key !in fields)
                    fields[key] = readValue(depth + 1)
                }
                endObject()
                JsonObject(fields)
            }
            JsonToken.BEGIN_ARRAY -> {
                beginArray()
                val values = mutableListOf<Any>()
                while (hasNext()) {
                    if (values.size >= JournalLimits.RECORDS) throw JournalException(JournalProblem.LIMIT)
                    values += readValue(depth + 1)
                }
                endArray()
                values
            }
            JsonToken.STRING -> nextString().also { value ->
                require(value.length <= JournalLimits.NOTE)
                if (value.any(Char::isSurrogate)) require(Charsets.UTF_8.newEncoder().canEncode(value))
            }
            JsonToken.NUMBER -> JsonNumber(nextString().also { require(it.length <= 64) })
            JsonToken.BOOLEAN -> nextBoolean()
            JsonToken.NULL -> { nextNull(); JSONObject.NULL }
            else -> invalid()
        }
    }

    private fun encodeProject(p: AntennaProject, photoStore: Boolean = false) = json(
        "id" to p.id.toString(), "title" to p.title, "antennaType" to p.antennaType.name,
        "targetHz" to p.target?.hertz, "initialLengthNm" to p.initialLength?.nanometers, "note" to p.note,
        "installation" to p.installation, "feedline" to p.feedline, "records" to array(p.records, ::encodeRecord),
        "sweeps" to p.sweeps.takeIf { it.isNotEmpty() }?.let { array(it, ::encodeSweep) },
        "photos" to p.photos.takeIf { photoStore && it.isNotEmpty() }?.let { photos ->
            array(photos) { photo ->
                json("id" to photo.id.toString(), "fileId" to photo.fileId.toString(), "format" to photo.format.name,
                    "addedAt" to photo.addedAt.toString(), "comment" to photo.comment)
            }
        },
    )

    private fun decodeProject(o: JsonObject, photoStore: Boolean = false) = AntennaProject(
        id = o.id(), title = o.string("title"), antennaType = AntennaType.valueOf(o.string("antennaType")),
        target = o.frequency("targetHz"), initialLength = o.length("initialLengthNm"),
        note = o.string("note"), installation = o.string("installation"), feedline = o.string("feedline"),
        records = o.list("records", JournalLimits.RECORDS, ::decodeRecord),
        sweeps = o.optionalList("sweeps", JournalLimits.RECORDS, ::decodeSweep).orEmpty(),
        photos = if (photoStore) o.optionalList("photos", PhotoAttachment.MAX_PER_PROJECT) {
            PhotoAttachment(it.id(), it.uuid("fileId"), PhotoFormat.valueOf(it.string("format")),
                OffsetDateTime.parse(it.string("addedAt")), it.string("comment"))
        }.orEmpty() else emptyList(),
    )

    private fun encodeSweep(sweep: SweepComparison) = json("traces" to array(sweep.traces) { ref ->
        json("id" to ref.id, "name" to ref.name, "plane" to ref.plane, "referenceOhms" to ref.referenceOhms,
            "points" to ref.points, "firstHz" to ref.firstHz, "lastHz" to ref.lastHz)
    })

    private fun decodeSweep(o: JsonObject) = SweepComparison(o.list("traces", 2) { ref ->
        SweepAttachment(ref.string("id"), ref.string("name"), ref.string("plane"),
            ref.optionalDouble("referenceOhms") ?: invalid(),
            ref.long("points").also { require(it in 1..20_000) }.toInt(),
            ref.optionalDouble("firstHz") ?: invalid(), ref.optionalDouble("lastHz") ?: invalid())
    })

    private fun encodeRecord(r: JournalRecord) = json(
        "id" to r.id.toString(), "title" to r.title, "time" to r.time.toString(), "antennaType" to r.antennaType.name,
        "note" to r.note, "installation" to r.installation, "feedline" to r.feedline,
        "measurement" to r.measurement?.let(::encodeMeasurement), "calculation" to r.calculation?.let(::encodeSnapshot),
    )

    private fun decodeRecord(o: JsonObject) = JournalRecord(
        id = o.id(), title = o.string("title"), time = OffsetDateTime.parse(o.string("time").also { require(it.length <= 40) }),
        antennaType = AntennaType.valueOf(o.string("antennaType")),
        note = o.string("note"), installation = o.string("installation"), feedline = o.string("feedline"),
        measurement = o.optionalObj("measurement")?.let(::decodeMeasurement),
        calculation = o.optionalObj("calculation")?.let(::decodeSnapshot),
    )

    private fun encodeMeasurement(m: JournalMeasurement) = json(
        "lengthNm" to m.length?.nanometers, "resonanceHz" to m.resonance?.hertz, "testFrequencyHz" to m.testFrequency?.hertz,
        "swr" to m.swr, "resistanceOhms" to m.resistanceOhms, "reactanceOhms" to m.reactanceOhms, "point" to m.point.name,
        "referenceOhms" to m.referenceOhms,
    )

    private fun decodeMeasurement(o: JsonObject) = JournalMeasurement(
        o.length("lengthNm"), o.frequency("resonanceHz"), o.frequency("testFrequencyHz"),
        o.optionalDouble("swr"), o.optionalDouble("resistanceOhms"), o.optionalDouble("reactanceOhms"),
        MeasurementPoint.valueOf(o.string("point")), o.optionalDouble("referenceOhms"),
    )

    private fun encodeSnapshot(s: CalculationSnapshot) = json(
        "model" to s.model, "revision" to s.revision, "inputs" to array(s.inputs, ::encodeValue),
        "outputs" to array(s.outputs, ::encodeValue), "warnings" to JSONArray(s.warnings),
    )

    private fun decodeSnapshot(o: JsonObject) = CalculationSnapshot(
        o.string("model"), o.long("revision").also { require(it in 1..Int.MAX_VALUE.toLong()) }.toInt(),
        o.list("inputs", 24, ::decodeValue), o.list("outputs", 24, ::decodeValue), o.strings("warnings", 24),
    )

    private fun encodeValue(v: SnapshotValue) = json("key" to v.key, "value" to v.value, "unit" to v.unit.name)

    private fun decodeValue(o: JsonObject) = SnapshotValue(
        o.string("key"), o.optionalDouble("value") ?: invalid(), SnapshotUnit.valueOf(o.string("unit")),
    )

    private fun json(vararg values: Pair<String, Any?>): JSONObject = JSONObject().apply {
        for ((key, value) in values) if (value != null) put(key, value)
    }

    private fun <T> array(values: List<T>, encode: (T) -> JSONObject) =
        JSONArray().apply { values.forEach { put(encode(it)) } }

    private data class JsonNumber(val raw: String)

    private class JsonObject(private val fields: Map<String, Any>) {
        fun string(key: String): String = fields[key] as? String ?: invalid()
        fun long(key: String): Long = optionalLong(key) ?: invalid()
        private fun optionalLong(key: String): Long? = fields[key]?.let { (it as? JsonNumber)?.raw?.toLongOrNull() ?: invalid() }
        fun optionalDouble(key: String): Double? = fields[key]?.let { (it as? JsonNumber)?.raw?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: invalid() }
        fun optionalObj(key: String): JsonObject? = fields[key]?.let { it as? JsonObject ?: invalid() }
        fun obj(key: String): JsonObject = optionalObj(key) ?: invalid()
        fun frequency(key: String): Frequency? = optionalLong(key)?.let(Frequency::ofHertz)
        fun length(key: String): PhysicalLength? = optionalLong(key)?.let(PhysicalLength::ofNanometers)
        fun id(): UUID = uuid("id")
        fun uuid(key: String): UUID = string(key).let { raw -> UUID.fromString(raw).also { require(it.toString() == raw) } }
        fun strings(key: String, limit: Int): List<String> {
            val values = fields[key] as? List<*> ?: invalid()
            if (values.size > limit) throw JournalException(JournalProblem.LIMIT)
            return values.map { it as? String ?: invalid() }
        }

        fun <T> optionalList(key: String, limit: Int, parse: (JsonObject) -> T): List<T>? =
            if (key in fields) list(key, limit, parse) else null

        fun <T> list(key: String, limit: Int, parse: (JsonObject) -> T): List<T> {
            val values = fields[key] as? List<*> ?: invalid()
            if (values.size > limit) throw JournalException(JournalProblem.LIMIT)
            return values.map { parse(it as? JsonObject ?: invalid()) }
        }
    }

    private fun invalid(): Nothing = throw JournalException(JournalProblem.INVALID)
}
