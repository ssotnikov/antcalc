package local.antenna.calculator.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val REPOSITORY = "ssotnikov/antcalc"
private const val RELEASES = "https://api.github.com/repos/$REPOSITORY/releases"
private const val RELEASES_BY_ID = "https://api.github.com/repositories/1445683430/releases"
private const val TAG_PREFIX = "github-direct-v"
private const val APK = "antcalc.apk"
private const val MANIFEST = "update-manifest.json"
internal const val LATEST_UPDATE_MANIFEST = "https://github.com/$REPOSITORY/releases/latest/download/$MANIFEST"
private const val TAGGED_MANIFEST_PATH = "/$REPOSITORY/releases/download/"
private const val PAGE_BYTES = 2 * 1024 * 1024
private const val MANIFEST_BYTES = 64 * 1024
private const val MAX_APK_BYTES = 64L * 1024 * 1024
private const val MAX_PAGES = 3
private val LOCAL_BETA = Regex("beta\\.[1-9][0-9]{0,8}")
private val VERSION = Regex("(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})")
private val SHA256 = Regex("[a-f0-9]{64}")

internal data class UpdateRelease(
    val versionName: String,
    val versionCode: Long,
    val minSdk: Long,
    val size: Long,
    val sha256: String,
    val notes: UpdateReleaseNotes? = null,
) {
    val tag: String get() = TAG_PREFIX + versionName
    val pageUrl: String get() = "https://github.com/$REPOSITORY/releases/tag/$tag"
    val apkUrl: String get() = assetUrl(tag, APK)

    fun json() = JSONObject().put("schemaVersion", 1).put("channel", "github-direct")
        .put("tag", tag).put("packageName", "local.antenna.calculator")
        .put("versionName", versionName).put("versionCode", versionCode).put("minSdk", minSdk)
        .put("apk", JSONObject().put("name", APK).put("size", size).put("sha256", sha256))
        .put("notes", notes?.json())

    fun sameApk(other: UpdateRelease?) = other != null && copy(notes = null) == other.copy(notes = null)

    companion object {
        fun parse(json: JSONObject): UpdateRelease? {
            if (json.integer("schemaVersion") != 1L || json.opt("channel") != "github-direct" ||
                json.opt("packageName") != "local.antenna.calculator") return null
            val name = json.opt("versionName") as? String ?: return null
            if (!VERSION.matches(name) || json.opt("tag") != TAG_PREFIX + name) return null
            val code = json.integer("versionCode")?.takeIf { it in 1..Int.MAX_VALUE.toLong() } ?: return null
            val sdk = json.integer("minSdk")?.takeIf { it in 1..Int.MAX_VALUE.toLong() } ?: return null
            val apk = json.optJSONObject("apk") ?: return null
            if (apk.opt("name") != APK) return null
            val size = apk.integer("size")?.takeIf { it in 1..MAX_APK_BYTES } ?: return null
            val hash = (apk.opt("sha256") as? String)?.takeIf(SHA256::matches) ?: return null
            return UpdateRelease(name, code, sdk, size, hash, UpdateReleaseNotes.parse(json.optJSONObject("notes")))
        }
    }
}

private fun JSONObject.integer(key: String): Long? = when (val value = opt(key)) {
    is Int -> value.toLong()
    is Long -> value
    else -> null
}

private fun assetUrl(tag: String, file: String) = "https://github.com/$REPOSITORY/releases/download/$tag/$file"

private fun compareVersions(first: String, second: String): Int {
    val a = first.split('.').map(String::toInt)
    val b = second.split('.').map(String::toInt)
    return a.indices.firstNotNullOfOrNull { index -> a[index].compareTo(b[index]).takeIf { it != 0 } } ?: 0
}

internal data class UpdateHttpResponse(val status: Int, val body: String = "", val headers: Map<String, String> = emptyMap())

internal fun interface UpdateTransport {
    fun get(url: String, etag: String?, maxBytes: Int): UpdateHttpResponse
}

internal class GithubUpdateTransport : UpdateTransport {
    override fun get(url: String, etag: String?, maxBytes: Int): UpdateHttpResponse {
        return withUpdateResponse(url, "application/vnd.github+json", etag) { connection, status ->
            val body = if (status == 200) connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > maxBytes) throw IOException("Update response exceeded limit")
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            } else ""
            val headers = listOf("ETag", "Link", "Retry-After", "X-RateLimit-Remaining", "X-RateLimit-Reset")
                .mapNotNull { name -> connection.getHeaderField(name)?.let { name to it } }.toMap()
            UpdateHttpResponse(status, body, headers)
        }
    }
}

internal fun <T> withUpdateResponse(
    url: String,
    accept: String,
    etag: String? = null,
    readTimeoutMillis: Int = 5_000,
    consume: (HttpURLConnection, Int) -> T,
): T {
    var uri = URI(url)
    repeat(4) {
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5000
            connection.readTimeout = readTimeoutMillis
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("User-Agent", "AntCalc-Android")
            etag?.let { connection.setRequestProperty("If-None-Match", it) }
            val status = connection.responseCode
            if (status in listOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location") ?: throw IOException("Missing redirect")
                uri = updateRedirect(uri, location)
            } else {
                return consume(connection, status)
            }
        } finally {
            connection.disconnect()
        }
    }
    throw IOException("Too many update redirects")
}

internal fun updateRedirect(from: URI, location: String): URI {
    val next = try { from.resolve(location) } catch (_: IllegalArgumentException) {
        throw IOException("Invalid update redirect")
    }
    val latestTag = next.rawPath.orEmpty().removePrefix(TAGGED_MANIFEST_PATH).removeSuffix("/$MANIFEST")
    val latestToTagged = from.toString() == LATEST_UPDATE_MANIFEST && next.host == "github.com" &&
        next.rawPath == "$TAGGED_MANIFEST_PATH$latestTag/$MANIFEST" && next.rawQuery == null &&
        latestTag.startsWith(TAG_PREFIX) && VERSION.matches(latestTag.removePrefix(TAG_PREFIX))
    val assetRedirect = next.host in setOf("release-assets.githubusercontent.com", "objects.githubusercontent.com")
    if (from.host == "api.github.com" || next.scheme != "https" || (!assetRedirect && !latestToTagged) ||
        next.userInfo != null || next.port !in setOf(-1, 443) || next.fragment != null) {
        throw IOException("Unexpected update redirect")
    }
    return next
}

internal class UpdateRateLimit(val retryAt: Long) : IOException()

internal data class UpdateCatalog(val release: UpdateRelease?, val etag: String?)

internal class GithubReleases(
    private val transport: UpdateTransport,
    private val versionName: String,
    private val versionCode: Long,
    private val sdk: Int,
    private val clock: () -> Long,
) {
    private val installedStableVersion = versionName.substringBefore('-')

    init {
        require(VERSION.matches(installedStableVersion))
        require(versionName == installedStableVersion || versionName.substringAfter('-').matches(LOCAL_BETA))
    }

    private fun compareToInstalled(stable: String): Int =
        compareVersions(stable, installedStableVersion).let { comparison ->
            if (comparison == 0 && versionName != installedStableVersion) 1 else comparison
        }

    fun fetch(previous: UpdateCatalog?): UpdateCatalog {
        val first = request("$RELEASES?per_page=30&page=1", previous?.etag, PAGE_BYTES)
        if (first.status == 304 && previous?.etag != null) return previous
        if (first.status != 200) throw IOException("Could not read releases")

        var page = first
        var best: UpdateRelease? = null
        var manifestRequests = 0
        for (number in 1..MAX_PAGES) {
            val items = JSONArray(page.body)
            if (items.length() > 30) throw IOException("Unexpected releases page size")
            for (index in 0 until items.length()) {
                val release = items.getJSONObject(index)
                val candidate = readRelease(release) {
                    if (++manifestRequests > 12) throw IOException("Too many update manifests")
                } ?: continue
                val current = best
                if (current != null && candidate.versionCode == current.versionCode && !candidate.sameApk(current)) {
                    throw IOException("Conflicting update versions")
                }
                if (current == null || candidate.versionCode > current.versionCode) best = candidate
            }
            val next = nextPage(page.headers["Link"]) ?: return UpdateCatalog(
                best, first.headers["ETag"]?.takeIf { number == 1 && safeEtag(it) },
            )
            if (number == MAX_PAGES) throw IOException("Release search limit reached")
            val expectedPage = number + 1
            val validPage = listOf(RELEASES, RELEASES_BY_ID).any { endpoint ->
                next == "$endpoint?per_page=30&page=$expectedPage" || next == "$endpoint?page=$expectedPage&per_page=30"
            }
            if (!validPage) {
                throw IOException("Unexpected releases page link")
            }
            page = request(next, null, PAGE_BYTES)
            if (page.status != 200) throw IOException("Could not read releases page")
        }
        throw IOException("Release search incomplete")
    }

    fun fetchLatest(): UpdateCatalog {
        val response = request(LATEST_UPDATE_MANIFEST, null, MANIFEST_BYTES)
        if (response.status != 200) throw IOException("Latest release has no update manifest")
        val candidate = UpdateRelease.parse(JSONObject(response.body))
            ?: throw IOException("Invalid latest update manifest")
        val comparison = compareToInstalled(candidate.versionName)
        if (comparison <= 0 && candidate.versionCode <= versionCode) return UpdateCatalog(null, null)
        if (comparison <= 0 || candidate.versionCode <= versionCode || candidate.minSdk > sdk) {
            throw IOException("Could not establish a compatible latest update")
        }
        return UpdateCatalog(candidate, null)
    }

    fun confirm(release: UpdateRelease, apiAvailable: Boolean = true) {
        if (!VERSION.matches(release.versionName)) throw IOException("Invalid update version")
        val response = try {
            if (apiAvailable) request("$RELEASES/tags/${release.tag}", null, PAGE_BYTES) else null
        } catch (_: UpdateRateLimit) {
            null
        }
        if (response == null) {
            if (!release.sameApk(fetchLatest().release)) throw IOException("The selected update is no longer available")
            return
        }
        if (response.status != 200 || !release.sameApk(readRelease(JSONObject(response.body)) {})) {
            throw IOException("The selected update is no longer available")
        }
    }

    private fun readRelease(release: JSONObject, beforeManifest: () -> Unit): UpdateRelease? {
        if (release.opt("draft") != false || release.opt("prerelease") != false ||
            (release.opt("published_at") as? String).isNullOrBlank()) return null
        val tag = release.opt("tag_name") as? String ?: return null
        if (!tag.startsWith(TAG_PREFIX)) return null
        val name = tag.removePrefix(TAG_PREFIX)
        if (!VERSION.matches(name) || compareToInstalled(name) <= 0) return null

        val assets = release.optJSONArray("assets") ?: return null
        val manifest = asset(assets, tag, MANIFEST) ?: return null
        val apk = asset(assets, tag, APK) ?: return null
        val apkSize = apk.integer("size") ?: return null
        val manifestSize = manifest.integer("size") ?: return null
        if (manifestSize !in 1L..MANIFEST_BYTES.toLong() || apkSize !in 1..MAX_APK_BYTES) return null
        beforeManifest()
        val response = request(assetUrl(tag, MANIFEST), null, MANIFEST_BYTES)
        if (response.status == 404 || response.status == 410) return null
        if (response.status != 200) throw IOException("Could not read update manifest")
        val candidate = try { UpdateRelease.parse(JSONObject(response.body)) } catch (_: JSONException) { null }
            ?: return null
        if (candidate.tag != tag || candidate.versionCode <= versionCode || candidate.minSdk > sdk ||
            candidate.size != apkSize) return null
        val digest = apk.opt("digest")
        if (digest != null && digest != JSONObject.NULL && digest != "sha256:${candidate.sha256}") return null

        return candidate.copy(notes = candidate.notes ?: UpdateReleaseNotes.fromGithubBody(release.opt("body") as? String))
    }

    private fun asset(assets: JSONArray, tag: String, name: String): JSONObject? {
        val matching = (0 until assets.length()).mapNotNull(assets::optJSONObject).filter { it.opt("name") == name }
        return matching.singleOrNull()?.takeIf {
            it.opt("state") == "uploaded" && it.opt("browser_download_url") == assetUrl(tag, name)
        }
    }

    private fun request(url: String, etag: String?, limit: Int): UpdateHttpResponse {
        val response = transport.get(url, etag, limit)
        if (response.status == 403 || response.status == 429) {
            val now = clock()
            val retryAfter = response.headers["Retry-After"]?.toLongOrNull()
                ?.takeIf { it in 0..86_400 }?.let { now + it * 1000 }
            val reset = if (response.headers["X-RateLimit-Remaining"] == "0") {
                response.headers["X-RateLimit-Reset"]?.toLongOrNull()
                    ?.takeIf { it in 0..(now / 1000 + 86_400) }?.times(1000)
            } else null
            throw UpdateRateLimit(maxOf(now + 60_000, retryAfter ?: 0, reset ?: 0))
        }
        return response
    }

    private fun nextPage(link: String?): String? =
        link?.split(',')?.firstNotNullOfOrNull { part ->
            Regex("""^\s*<([^>]+)>;\s*rel="next"\s*$""").matchEntire(part)?.groupValues?.get(1)
        }
}

internal fun safeEtag(value: String) = value.length in 2..256 && value.all { it in '!'..'~' }
