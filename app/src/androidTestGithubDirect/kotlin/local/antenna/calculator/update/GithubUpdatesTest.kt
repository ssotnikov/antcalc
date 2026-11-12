package local.antenna.calculator.update

import java.io.IOException
import java.net.URI
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class GithubUpdatesTest {
    private val hash = "a".repeat(64)
    private val first = UpdateRelease("1.10.0", 61, 26, 4096, hash)
    private val newest = UpdateRelease("1.11.0", 62, 26, 8192, hash)

    @Test fun selectsHighestCompatibleCodeAcrossPages() {
        val incompatible = newest.copy(versionName = "1.12.0", versionCode = 63, minSdk = 35)
        val noApk = newest.copy(versionName = "1.13.0", versionCode = 64)
        val inconsistent = newest.copy(versionName = "1.14.0", versionCode = 65)
        val malformed = newest.copy(versionName = "1.15.0", versionCode = 66)
        val olderName = first.copy(versionName = "1.8.0", versionCode = 67)
        val sameCode = first.copy(versionName = "1.16.0", versionCode = 59)
        val wrongDigest = newest.copy(versionName = "1.17.0", versionCode = 68)
        val next = "https://api.github.com/repositories/1445683430/releases?per_page=30&page=2"
        val fetch = catalog { url, _ ->
            when {
                url.endsWith("page=1") -> page(
                    listing(first), listing(incompatible), listing(noApk).put("assets", JSONArray()),
                    listing(inconsistent), listing(malformed), listing(olderName), listing(sameCode),
                    listing(wrongDigest, digest = "sha256:" + "0".repeat(64)),
                    listing(newest).put("tag_name", "v2.0.0"),
                    listing(newest).put("draft", true),
                    listing(newest).put("prerelease", true),
                    headers = mapOf("Link" to "<$next>; rel=\"next\""),
                )
                url == next -> page(listing(newest))
                url.endsWith("/${incompatible.tag}/update-manifest.json") -> manifest(incompatible)
                url.endsWith("/${first.tag}/update-manifest.json") -> manifest(first)
                url.endsWith("/${newest.tag}/update-manifest.json") -> manifest(newest)
                url.endsWith("/${inconsistent.tag}/update-manifest.json") -> manifest(first)
                url.endsWith("/${malformed.tag}/update-manifest.json") -> UpdateHttpResponse(200, "{")
                url.endsWith("/${sameCode.tag}/update-manifest.json") -> manifest(sameCode)
                url.endsWith("/${wrongDigest.tag}/update-manifest.json") -> manifest(wrongDigest)
                else -> error("Unexpected request: $url")
            }
        }
        assertEquals(UpdateCatalog(newest, null), fetch.fetch(null))
    }

    @Test fun rejectsForgedPaginationAndReusesACompleteSinglePageOn304() {
        val unsafe = catalog { _, _ ->
            page(headers = mapOf("Link" to "<https://example.com/releases>; rel=\"next\""))
        }
        assertThrows(IOException::class.java) { unsafe.fetch(null) }
        val previous = UpdateCatalog(first, "\"cached\"")
        val unchanged = catalog { _, etag ->
            assertEquals(previous.etag, etag)
            UpdateHttpResponse(304)
        }
        assertEquals(previous, unchanged.fetch(previous))
    }

    @Test fun installerRechecksTheExactReleaseBeforeDownloading() {
        var response = page(listing(first)).body
        response = JSONArray(response).getJSONObject(0).toString()
        val source = catalog { url, _ ->
            if (url.endsWith("/tags/${first.tag}")) UpdateHttpResponse(200, response) else manifest(first)
        }
        source.confirm(first)
        assertThrows(IOException::class.java) { source.confirm(first.copy(sha256 = "0".repeat(64))) }
        response = listing(first).put("draft", true).toString()
        assertThrows(IOException::class.java) { source.confirm(first) }
    }

    @Test fun localBetaCanUpgradeToStableOfTheSameVersion() {
        val stable = first.copy(versionName = "1.9.0", versionCode = 69)
        val beta = GithubReleases(UpdateTransport { url, _, _ ->
            if (url.endsWith("page=1")) page(listing(stable)) else manifest(stable)
        }, "1.9.0-beta.1", 68, 26) { 0 }
        assertEquals(stable, beta.fetch(null).release)
        assertEquals(stable, beta.fetchLatest().release)
    }

    @Test fun manifestRequiresTheExpectedSchemaAndTypes() {
        assertEquals(first, UpdateRelease.parse(first.json()))
        assertNull(UpdateRelease.parse(first.json().put("versionCode", "61")))
        assertNull(UpdateRelease.parse(first.json().put("packageName", "other")))
        assertNull(UpdateRelease.parse(first.json().put("channel", "store")))
        assertNull(UpdateRelease.parse(first.json().put("schemaVersion", 2)))
    }

    @Test fun olderManifestUsesLocalizedGithubNotesWithoutBindingThemToTheApk() {
        val body = "## Что изменилось\r\n\r\n- Зазор над навигацией\r\n\r\n## What's changed\r\n\r\n- Navigation spacing"
        val listing = listing(first).put("body", body)
        val source = catalog { url, _ ->
            when {
                url.endsWith("page=1") -> page(listing)
                url == LATEST_UPDATE_MANIFEST -> manifest(first)
                else -> manifest(first)
            }
        }
        val offered = requireNotNull(source.fetch(null).release)
        assertEquals("• Зазор над навигацией", offered.notes?.text("ru"))
        assertEquals("• Navigation spacing", offered.notes?.text("en"))
        assertEquals(offered, UpdateRelease.parse(offered.json()))
        source.confirm(offered, apiAvailable = false)
    }

    @Test fun latestManifestSuppliesNotesWithNoReleasesApiAndIgnoresOversizedText() {
        val release = first.copy(notes = UpdateReleaseNotes("а".repeat(4_001), "- Navigation spacing"))
        val source = catalog { url, _ ->
            assertEquals(LATEST_UPDATE_MANIFEST, url)
            manifest(release)
        }
        val offered = source.fetchLatest().release
        assertEquals(UpdateReleaseNotes(null, "- Navigation spacing"), offered?.notes)
        assertEquals("• Navigation spacing", offered?.notes?.text("ru"))
        source.confirm(release, apiAvailable = false)
    }

    @Test fun usesFreshCacheAndCoalescesRequestsButManualCheckRevalidates() = runTest {
        val old = UpdateCatalog(first, "\"first\"")
        val latest = UpdateCatalog(newest, "\"newest\"")
        val storage = MemoryStorage(CachedUpdates(old, 0))
        var calls = 0
        val checks = UpdateChecks(backgroundScope, storage, { previous ->
            calls++
            assertEquals(old, previous)
            latest
        }, { 1000 })
        assertEquals(UpdateCheck.Available(first), checks.check(false))
        assertEquals(0, calls)
        val manual = async(start = CoroutineStart.UNDISPATCHED) { checks.check(true) }
        val concurrent = async(start = CoroutineStart.UNDISPATCHED) { checks.check(false) }
        assertEquals(UpdateCheck.Available(newest), manual.await())
        assertEquals(UpdateCheck.Available(newest), concurrent.await())
        assertEquals(1, calls)
        assertEquals(latest, storage.cache?.catalog)
    }

    @Test fun expiredCacheIsNotOfferedOfflineAndRateLimitBlocksRepeatedRequests() = runTest {
        val now = 24 * 60 * 60 * 1000L
        val storage = MemoryStorage(CachedUpdates(UpdateCatalog(first, null), 0))
        val offline = UpdateChecks(backgroundScope, storage, { throw IOException() }, { now })
        assertEquals(UpdateCheck.Unavailable, offline.check(false))
        for (status in listOf(403, 429)) {
            storage.retryAt = 0
            var calls = 0
            val limitedCatalog = catalog(now) { _, _ ->
                calls++
                UpdateHttpResponse(status, headers = mapOf("Retry-After" to "120"))
            }
            val checks = UpdateChecks(backgroundScope, storage, limitedCatalog::fetch, { now })
            assertEquals(UpdateCheck.RateLimited(now + 120_000), checks.check(false))
            assertEquals(UpdateCheck.RateLimited(now + 120_000), checks.check(true))
            assertEquals(1, calls)
        }
    }

    @Test fun usesLatestDirectManifestWhileTheRestApiIsRateLimited() = runTest {
        val storage = MemoryStorage(null)
        val requests = mutableListOf<String>()
        var latest = first
        val source = catalog { url, _ ->
            requests += url
            if (url == LATEST_UPDATE_MANIFEST) manifest(latest)
            else UpdateHttpResponse(403, headers = mapOf("X-RateLimit-Remaining" to "0"))
        }
        val checks = UpdateChecks(backgroundScope, storage, source::fetch, { 0 }, source::fetchLatest)
        assertEquals(UpdateCheck.Available(first), checks.check(true))
        assertEquals(UpdateCheck.Available(first), checks.check(true))
        source.confirm(first, apiAvailable = checks.apiAvailable())
        latest = newest
        assertThrows(IOException::class.java) { source.confirm(first, apiAvailable = checks.apiAvailable()) }
        assertEquals(1, requests.count { it.startsWith("https://api.github.com/") })
    }

    @Test fun fallbackOnlyAcceptsDirectManifestAndValidLatestRedirect() {
        val source = catalog { _, _ -> manifest(first.copy(versionName = "1.9.0", versionCode = 59)) }
        assertEquals(UpdateCatalog(null, null), source.fetchLatest())
        assertThrows(IOException::class.java) {
            catalog { _, _ -> UpdateHttpResponse(404) }.fetchLatest()
        }
        val latest = URI(LATEST_UPDATE_MANIFEST)
        val tagged = first.apkUrl.replace("antcalc.apk", "update-manifest.json")
        assertEquals(URI(tagged), updateRedirect(latest, tagged))
        assertThrows(IOException::class.java) { updateRedirect(latest, tagged.replace("ssotnikov", "other")) }
        assertThrows(IOException::class.java) { updateRedirect(latest, tagged.replace("github-direct-", "")) }
    }

    private fun catalog(now: Long = 0, get: (String, String?) -> UpdateHttpResponse) =
        GithubReleases(UpdateTransport { url, etag, _ -> get(url, etag) }, "1.9.0", 59, 26, { now })

    private fun manifest(release: UpdateRelease) = UpdateHttpResponse(200, release.json().toString())

    private fun page(vararg releases: JSONObject, headers: Map<String, String> = emptyMap()) =
        UpdateHttpResponse(200, JSONArray(releases.toList()).toString(), headers)

    private fun listing(release: UpdateRelease, digest: String = "sha256:${release.sha256}"): JSONObject {
        fun asset(name: String, size: Long, url: String) =
            JSONObject().put("name", name).put("size", size).put("browser_download_url", url).put("state", "uploaded")
        val apk = asset("antcalc.apk", release.size, release.apkUrl).put("digest", digest)
        val metadata = asset("update-manifest.json", 1024, release.apkUrl.replace("antcalc.apk", "update-manifest.json"))
        return JSONObject().put("tag_name", release.tag).put("draft", false).put("prerelease", false)
            .put("published_at", "2026-11-11T00:00:00Z").put("assets", JSONArray(listOf(apk, metadata)))
    }

    private class MemoryStorage(var cache: CachedUpdates?) : UpdateCheckStorage {
        override fun read() = cache
        override fun write(cache: CachedUpdates) { this.cache = cache }
        override var retryAt = 0L
    }
}
