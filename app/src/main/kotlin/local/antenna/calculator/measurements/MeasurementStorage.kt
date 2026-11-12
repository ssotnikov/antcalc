package local.antenna.calculator.measurements

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.io.IOException
import java.security.MessageDigest
import local.antenna.calculator.core.journal.SweepAttachment
import local.antenna.calculator.core.measurements.S11Point
import local.antenna.calculator.core.measurements.S11Sweep
import local.antenna.calculator.core.measurements.Touchstone

internal data class MeasurementTrace(val name: String, val sweep: S11Sweep, val plane: SweepPlane = SweepPlane.UNKNOWN)
internal enum class SweepPlane { UNKNOWN, FEED_POINT, TRANSCEIVER }

internal class MeasurementStorage(private val directory: File) {
    private val draft = File(directory, "workspace.bin")
    private val attachments = File(directory, "attachments")

    fun readWorkspace(): List<MeasurementTrace?> {
        if (Files.notExists(draft.toPath())) return listOf(null, null)
        return DataInputStream(draft.inputStream().buffered()).use { input ->
            checkData(input.readInt() == SCHEMA)
            List(2) { if (input.readBoolean()) input.readTrace() else null }.also {
                checkData(input.read() == -1)
            }
        }
    }

    fun writeWorkspace(traces: List<MeasurementTrace?>) {
        // A damaged or newer workspace remains available for recovery.
        readWorkspace()
        writeAtomically(draft) { output ->
            output.writeInt(SCHEMA)
            traces.forEach { trace ->
                output.writeBoolean(trace != null)
                if (trace != null) output.writeTrace(trace)
            }
        }
    }

    fun addAttachments(traces: List<MeasurementTrace>, referencedIds: Set<String>): List<SweepAttachment> {
        if (!attachments.isDirectory && !attachments.mkdirs()) throw IOException()
        val existing = attachments.listFiles() ?: throw IOException()
        existing.filter { it.extension == "bin" && it.nameWithoutExtension !in referencedIds }.forEach { it.delete() }
        var used = (attachments.listFiles() ?: throw IOException()).sumOf { it.length() }
        return traces.map { trace ->
            val bytes = java.io.ByteArrayOutputStream().also { buffer ->
                DataOutputStream(buffer).use { output -> output.writeInt(SCHEMA); output.writeTrace(trace) }
            }.toByteArray()
            val id = contentId(bytes)
            val file = File(attachments, "$id.bin")
            if (!file.isFile) {
                if (used + bytes.size > 20L * 1024 * 1024) throw AttachmentLimitException()
                writeAtomically(file) { it.write(bytes) }
                used += bytes.size
            }
            SweepAttachment(id, trace.name, trace.plane.name, trace.sweep.referenceOhms,
                trace.sweep.points.size, trace.sweep.points.first().hertz, trace.sweep.points.last().hertz)
        }
    }

    fun readAttachment(ref: SweepAttachment): MeasurementTrace? {
        val file = File(attachments, "${ref.id}.bin")
        if (!file.isFile) return null
        checkData(file.length() in 1..Touchstone.MAX_BYTES.toLong())
        val bytes = file.readBytes()
        checkData(contentId(bytes) == ref.id)
        return DataInputStream(bytes.inputStream()).use { input ->
            checkData(input.readInt() == SCHEMA)
            input.readTrace().also { checkData(input.read() == -1) }
        }
    }

    private fun contentId(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun writeAtomically(file: File, write: (DataOutputStream) -> Unit) {
        val parent = checkNotNull(file.parentFile)
        Files.createDirectories(parent.toPath())
        val temporary = File(parent, ".${file.name}.pending")
        try {
            FileOutputStream(temporary).use { stream ->
                val output = DataOutputStream(stream.buffered())
                write(output)
                output.flush()
                stream.fd.sync()
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    private fun DataOutputStream.writeTrace(trace: MeasurementTrace) {
        writeUTF(trace.name)
        writeUTF(trace.plane.name)
        writeDouble(trace.sweep.referenceOhms)
        writeInt(trace.sweep.points.size)
        trace.sweep.points.forEach { point ->
            writeDouble(point.hertz)
            writeDouble(point.real)
            writeDouble(point.imaginary)
        }
    }

    private fun DataInputStream.readTrace(): MeasurementTrace {
        val name = readUTF()
        val planeName = readUTF()
        val plane = SweepPlane.entries.find { it.name == planeName } ?: throw IOException()
        val reference = readDouble()
        val count = readInt()
        checkData(name.isNotBlank() && name.length <= 120 && reference.isFinite() && reference > 0 &&
            count in 1..Touchstone.MAX_POINTS)
        var previous = 0.0
        val points = List(count) {
            val hertz = readDouble()
            val real = readDouble()
            val imaginary = readDouble()
            checkData(hertz in 1e5..6e9 && hertz > previous && real.isFinite() && imaginary.isFinite())
            previous = hertz
            S11Point(hertz, real, imaginary)
        }
        return MeasurementTrace(name, S11Sweep(reference, points), plane)
    }

    private fun checkData(valid: Boolean) {
        if (!valid) throw IOException()
    }

    private companion object { const val SCHEMA = 1 }
}

internal class AttachmentLimitException : IOException()
