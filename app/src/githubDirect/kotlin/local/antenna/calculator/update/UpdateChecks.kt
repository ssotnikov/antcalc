package local.antenna.calculator.update

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import local.antenna.calculator.BuildConfig
import org.json.JSONException
import org.json.JSONObject

private const val FRESH_MILLIS = 6 * 60 * 60 * 1000L

internal sealed interface UpdateCheck {
    data class Available(val release: UpdateRelease) : UpdateCheck
    data object Current : UpdateCheck
    data object Unavailable : UpdateCheck
    data class RateLimited(val retryAt: Long) : UpdateCheck
}

internal data class CachedUpdates(val catalog: UpdateCatalog, val checkedAt: Long)

internal interface UpdateCheckStorage {
    fun read(): CachedUpdates?
    fun write(cache: CachedUpdates)
    var retryAt: Long
}

private class PreferencesUpdateStorage(private val prefs: SharedPreferences) : UpdateCheckStorage {
    private val installation = "${BuildConfig.VERSION_CODE}:${Build.VERSION.SDK_INT}"

    override fun read(): CachedUpdates? = try {
        prefs.getString("catalog_v1", null)?.let { saved ->
            val json = JSONObject(saved)
            if (json.optString("installation") != installation) return@let null
            val release = if (json.isNull("release")) null
                else UpdateRelease.parse(json.getJSONObject("release")) ?: return@let null
            CachedUpdates(
                UpdateCatalog(release, json.optString("etag").takeIf(::safeEtag)),
                json.getLong("checkedAt"),
            )
        }
    } catch (_: JSONException) {
        null
    } catch (_: ClassCastException) {
        null
    }

    override fun write(cache: CachedUpdates) {
        val json = JSONObject().put("installation", installation)
            .put("release", cache.catalog.release?.json() ?: JSONObject.NULL)
            .put("etag", cache.catalog.etag.orEmpty()).put("checkedAt", cache.checkedAt)
        prefs.edit().putString("catalog_v1", json.toString()).apply()
    }

    override var retryAt: Long
        get() = prefs.getLong("retry_at", 0)
        set(value) { prefs.edit().putLong("retry_at", value).apply() }
}

internal class UpdateChecks(
    private val scope: CoroutineScope,
    private val storage: UpdateCheckStorage,
    private val fetch: (UpdateCatalog?) -> UpdateCatalog,
    private val clock: () -> Long,
    private val fetchLatest: (() -> UpdateCatalog)? = null,
) {
    private var pending: Deferred<UpdateCheck>? = null

    suspend fun check(manual: Boolean): UpdateCheck {
        if (manual) {
            return synchronized(this) { activeRequest() ?: startRequest() }.await()
        }
        return withContext(Dispatchers.IO) {
            val request = synchronized(this@UpdateChecks) {
                activeRequest() ?: run {
                    val cached = storage.read()?.takeIf { clock() - it.checkedAt in 0L until FRESH_MILLIS }
                    if (cached != null) return@withContext cached.catalog.result()
                    startRequest()
                }
            }
            request.await()
        }
    }

    fun apiAvailable() = storage.retryAt <= clock()

    private fun activeRequest() = pending?.takeUnless { it.isCompleted }

    private fun startRequest() = scope.async(start = CoroutineStart.LAZY) { resolve() }.also { pending = it }

    private fun resolve(): UpdateCheck {
        val now = clock()
        val cache = storage.read()
        val retryAt = storage.retryAt
        if (retryAt > now) return resolveLatest(retryAt)

        return try {
            val result = fetch(cache?.catalog)
            remember(result)
        } catch (limited: UpdateRateLimit) {
            storage.retryAt = limited.retryAt
            resolveLatest(limited.retryAt)
        } catch (_: IOException) {
            UpdateCheck.Unavailable
        } catch (_: JSONException) {
            UpdateCheck.Unavailable
        }
    }

    private fun resolveLatest(retryAt: Long): UpdateCheck = try {
        fetchLatest?.invoke()?.let(::remember) ?: UpdateCheck.RateLimited(retryAt)
    } catch (_: IOException) {
        UpdateCheck.RateLimited(retryAt)
    } catch (_: JSONException) {
        UpdateCheck.RateLimited(retryAt)
    }

    private fun remember(result: UpdateCatalog): UpdateCheck {
        storage.write(CachedUpdates(result, clock()))
        return result.result()
    }

    private fun UpdateCatalog.result(): UpdateCheck =
        release?.let { UpdateCheck.Available(it) } ?: UpdateCheck.Current

    companion object {
        /** The scope must outlive individual screens and use an I/O dispatcher. */
        fun create(context: Context, scope: CoroutineScope): UpdateChecks {
            val prefs = context.getSharedPreferences("github_direct_updates", Context.MODE_PRIVATE)
            val clock = System::currentTimeMillis
            val releases = GithubReleases(
                GithubUpdateTransport(), BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong(), Build.VERSION.SDK_INT, clock,
            )
            return UpdateChecks(scope, PreferencesUpdateStorage(prefs), releases::fetch, clock, releases::fetchLatest)
        }
    }
}
