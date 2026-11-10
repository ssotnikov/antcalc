package local.antenna.calculator.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import local.antenna.calculator.R
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

enum class SourceOpenResult { OPENED, NO_BROWSER, BLOCKED }

internal const val INVERTED_V_SOURCE_RU = "https://ruqrz.com/petlevoj-vibrator-v-antenne-inverted-v-2/"
internal const val BUG_REPORT_URL = "https://github.com/ssotnikov/antcalc/issues/new"
private const val AUTHOR_TELEGRAM = "tg://resolve?domain=ssotnikov"
internal const val AUTHOR_TELEGRAM_WEB = "https://t.me/ssotnikov"

internal fun openAuthorContact(context: Context) {
    if (dispatchExternal(context, AUTHOR_TELEGRAM) != SourceOpenResult.OPENED) {
        openSourceWithFeedback(context, AUTHOR_TELEGRAM_WEB)
    }
}

internal fun openSourceWithFeedback(context: Context, url: String) {
    val message = when (openWebSource(context, url)) {
        SourceOpenResult.OPENED -> null
        SourceOpenResult.NO_BROWSER -> R.string.browser_unavailable
        SourceOpenResult.BLOCKED -> R.string.link_open_failed
    }
    if (message != null) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

/** Strict parsing avoids discrepancies between permissive URI parsers and browser routing. */
internal fun validatedSourceAddress(address: String): String? {
    if (address.length > 2048) return null
    val uri = try { URI(address) } catch (_: URISyntaxException) { return null }
    val allowedHosts = setOf(
        "physics.nist.gov", "rsgb.org", "www.arrl.org", "www.n1fd.org", "ssotnikov.pro",
        "www.itu.int", "www.rigexpert.com", "www.kadamm.com", "scdn.rohde-schwarz.com",
        "eng.libretexts.org", "www.analog.com", "helpfiles.keysight.com",
        "radiojove.gsfc.nasa.gov", "practicalantennas.com",
    )
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.isOpaque || uri.rawUserInfo != null ||
        uri.port !in setOf(-1, 443)) return null
    val url = uri.toASCIIString()
    return url.takeIf { it == BUG_REPORT_URL || it == AUTHOR_TELEGRAM_WEB || it == INVERTED_V_SOURCE_RU || uri.host?.lowercase(Locale.ROOT) in allowedHosts }
}

internal fun openWebSource(context: Context, address: String): SourceOpenResult {
    val validated = validatedSourceAddress(address) ?: return SourceOpenResult.BLOCKED
    return dispatchExternal(context, validated)
}

private fun dispatchExternal(context: Context, address: String): SourceOpenResult {
    val intent = Intent(Intent.ACTION_VIEW, address.toUri())
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        SourceOpenResult.OPENED
    } catch (_: ActivityNotFoundException) {
        SourceOpenResult.NO_BROWSER
    } catch (_: SecurityException) {
        SourceOpenResult.BLOCKED
    }
}
