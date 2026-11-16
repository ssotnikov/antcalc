package local.antenna.calculator.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import local.antenna.calculator.R
import local.antenna.calculator.core.layout.LoopShape
import local.antenna.calculator.state.AppLanguage
import local.antenna.calculator.state.Section

internal const val GUIDE_ORIGIN = "https://appassets.androidplatform.net"
private const val GUIDE_DIRECTORY = "$GUIDE_ORIGIN/userguide/"
private const val ROUTE_PREFIX = "$GUIDE_ORIGIN/open/"
private const val EXAMPLE_PREFIX = "https://github.com/ssotnikov/antcalc/raw/refs/heads/main/examples/"
private val exampleNames = setOf(
    "demo-dipole-145mhz-before.s1p", "demo-dipole-145mhz-after.s1p",
    "demo-dipole-journal-145mhz-en.json", "demo-dipole-journal-145mhz-ru.json",
)
internal val guideExampleUrls = exampleNames.mapTo(mutableSetOf()) { EXAMPLE_PREFIX + it }

internal enum class GuideDestination(
    val slug: String,
    val page: Page,
    val section: Section? = null,
    val shape: LoopShape? = null,
) {
    SIZING("sizing", Page.CALCULATOR, Section.SIZING),
    TUNING("tuning", Page.CALCULATOR, Section.TRIMMING),
    TOOLS("tools", Page.CALCULATOR, Section.TOOLS),
    ANTENNA_CALCULATORS("antenna-calculators", Page.ANTENNA_PARAMETER_TOOLS),
    INVERTED_V("inverted-v", Page.INVERTED_V),
    GROUND_PLANE("ground-plane", Page.GROUND_PLANE),
    FULL_WAVE_LOOP("full-wave-loop", Page.FULL_WAVE_LOOP),
    QUAD_LOOP("quad-loop", Page.FULL_WAVE_LOOP, shape = LoopShape.SQUARE),
    DELTA_LOOP("delta-loop", Page.FULL_WAVE_LOOP, shape = LoopShape.TRIANGLE),
    J_POLE("j-pole", Page.J_POLE),
    SLIM_JIM("slim-jim", Page.SLIM_JIM),
    YAGI("yagi", Page.YAGI_CALCULATOR),
    MOXON("moxon", Page.MOXON),
    HB9CV("hb9cv", Page.HB9CV),
    CANTENNA("cantenna", Page.CANTENNA),
    HALO("halo", Page.HALO_CB),
    TRAP_DIPOLE("trap-dipole", Page.TRAP_DIPOLE),
    CONSTRUCTION("construction", Page.ANTENNA_CONSTRUCTION),
    R3UDK_GUIDE("r3udk-guide", Page.CONSTRUCTION_YAGI),
    R3UDK_CALCULATOR("r3udk-calculator", Page.YAGI_CALCULATOR),
    SWR("swr", Page.MATCHING),
    SMITH("smith", Page.SMITH_CHART),
    CABLE("cable", Page.CABLE),
    FEEDLINE("feedline", Page.FEEDLINE),
    ADDITIONAL_TOOLS("additional-tools", Page.ADDITIONAL_TOOLS),
    VNA("vna", Page.MEASUREMENTS),
    BANDWIDTH("bandwidth", Page.SWR_BANDWIDTH),
    PROJECTS("projects", Page.JOURNAL),
    SETTINGS("settings", Page.SETTINGS),
    HELP("help", Page.ABOUT),
    REFERENCES("references", Page.REFERENCES),
    LICENSES("licenses", Page.LICENSES),
    CHECK_UPDATES("check-updates", Page.USER_GUIDE),
}

internal fun guideDestination(address: String): GuideDestination? =
    GuideDestination.entries.firstOrNull { address == ROUTE_PREFIX + it.slug }

private val guideImage = Regex("""images/(en|ru)/[a-z0-9-]+\.webp""")

internal fun guideAssetPath(address: String): String? {
    if (!address.startsWith(GUIDE_DIRECTORY)) return null
    return address.removePrefix(GUIDE_DIRECTORY).takeIf {
        it == "en.html" || it == "ru.html" || guideImage.matches(it)
    }
}

@Composable
internal fun UserGuideScreen(
    language: AppLanguage, dark: Boolean, canCheckForUpdates: Boolean, onOpen: (GuideDestination) -> Unit,
) {
    val context = LocalContext.current
    val initialDocument = if (language == AppLanguage.RUSSIAN) "ru.html" else "en.html"
    var document by rememberSaveable(initialDocument) { mutableStateOf(initialDocument) }
    var scrollY by rememberSaveable(document) { mutableIntStateOf(0) }
    var imagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val open by rememberUpdatedState(onOpen)
    val background = MaterialTheme.colorScheme.background.toArgb()
    webView?.let { view ->
        BindPageScrollToTop(view) {
            animate(view.scrollY.toFloat(), 0f) { value, _ -> view.scrollTo(0, value.toInt()) }
        }
    }
    key(document, dark, canCheckForUpdates) {
        AndroidView(
            modifier = Modifier.fillMaxSize().testTag("user-guide-content"),
            factory = { viewContext ->
                createGuideView(viewContext, document, scrollY, dark, canCheckForUpdates, background,
                    onScroll = { scrollY = it },
                    onDocument = {
                        if (document != it) {
                            scrollY = 0
                            document = it
                        }
                    },
                    onImage = { imagePath = it },
                    onOpen = { open(it) },
                ).also { webView = it }
            },
            onRelease = { view ->
                if (webView === view) webView = null
                view.stopLoading()
                view.destroy()
            },
        )
    }
    imagePath?.let { path ->
        val image by produceState<ImageBitmap?>(null, path) {
            value = withContext(Dispatchers.IO) {
                try {
                    context.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                } catch (_: IOException) {
                    null
                }
            }
        }
        image?.let {
            FullscreenImage(it, stringResource(R.string.user_guide), { imagePath = null })
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createGuideView(
    context: Context, document: String, initialY: Int, dark: Boolean, canCheckForUpdates: Boolean,
    background: Int, onScroll: (Int) -> Unit, onDocument: (String) -> Unit, onImage: (String) -> Unit,
    onOpen: (GuideDestination) -> Unit,
): WebView {
    val url = GUIDE_DIRECTORY + document
    var restored = false
    return object : WebView(context) {
        override fun onScrollChanged(x: Int, y: Int, oldX: Int, oldY: Int) {
            super.onScrollChanged(x, y, oldX, oldY)
            if (restored) onScroll(y)
        }
    }.apply {
        setBackgroundColor(background)
        settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            domStorageEnabled = false
        }
        val assets = context.assets
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                val asset = guideAssetPath(request.url.toString())
                if (request.method == "GET" && asset != null) {
                    try {
                        return if (guideImage.matches(asset)) {
                            WebResourceResponse("image/webp", null, assets.open(asset))
                        } else guideHtml(assets, asset, dark, canCheckForUpdates)
                    } catch (_: IOException) {
                        // A missing bundled asset has no network fallback.
                    }
                }
                return WebResourceResponse("text/plain", "UTF-8", 404, "Not found", emptyMap(), null)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame || !request.hasGesture()) return true
                val address = request.url.toString()
                if (request.url.encodedFragment != null && address.substringBefore('#') == url) return false
                val route = guideDestination(address)
                val asset = guideAssetPath(address)
                when {
                    route != null -> onOpen(route)
                    asset == "en.html" || asset == "ru.html" -> onDocument(asset)
                    asset != null && guideImage.matches(asset) -> onImage(asset)
                    else -> guideExternalLink(address)?.let { openSourceWithFeedback(context, it) }
                }
                return true
            }

            override fun onPageFinished(view: WebView, loadedUrl: String?) {
                if (restored || loadedUrl?.substringBefore('#') != url) return
                view.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        view.scrollTo(0, initialY)
                        restored = true
                    }
                })
            }
        }
        loadUrl(url)
    }
}

private fun guideHtml(assets: AssetManager, asset: String, dark: Boolean, canCheckForUpdates: Boolean): WebResourceResponse {
    val classes = "in-app" + (if (dark) " dark" else "") + (if (canCheckForUpdates) " direct" else "")
    val html = assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() }
        .replaceFirst("<body>", "<body class=\"$classes\">")
        .replace(" download>", ">")
    return WebResourceResponse("text/html", "UTF-8", 200, "OK", mapOf(
        "Content-Security-Policy" to "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'",
    ), html.byteInputStream())
}

internal fun guideExternalLink(address: String): String? = when (address) {
    GITHUB_RELEASES_URL -> GITHUB_RELEASES_URL
    "$GUIDE_ORIGIN/PRIVACY.md" -> PRIVACY_POLICY_URL
    "https://github.com/ssotnikov/antcalc/issues" -> BUG_REPORT_URL
    else -> {
        val relative = address.substringAfter("$GUIDE_ORIGIN/examples/", "")
        (EXAMPLE_PREFIX + relative).takeIf { address == "$GUIDE_ORIGIN/examples/$relative" && relative in exampleNames }
    }
}
