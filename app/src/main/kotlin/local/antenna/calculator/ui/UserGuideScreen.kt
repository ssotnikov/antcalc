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
    val anchor: String = slug,
) {
    SIZING("sizing", Page.CALCULATOR, Section.SIZING, anchor = "starting-dimensions"),
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
    YAGI_SCALING("yagi-scaling", Page.YAGI_SCALING),
    YAGI_SEGMENTATION("yagi-segmentation", Page.YAGI_SEGMENTATION),
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

internal fun toolGuideDestination(page: Page, section: Section, projectsList: Boolean): GuideDestination? = when (page) {
    Page.CALCULATOR -> when (section) {
        Section.SIZING -> GuideDestination.SIZING
        Section.TRIMMING -> GuideDestination.TUNING
        Section.TOOLS -> null
    }
    Page.MATCHING -> GuideDestination.SWR
    Page.SMITH_CHART -> GuideDestination.SMITH
    Page.CABLE -> GuideDestination.CABLE
    Page.FEEDLINE -> GuideDestination.FEEDLINE
    Page.INVERTED_V -> GuideDestination.INVERTED_V
    Page.GROUND_PLANE -> GuideDestination.GROUND_PLANE
    Page.FULL_WAVE_LOOP -> GuideDestination.FULL_WAVE_LOOP
    Page.J_POLE -> GuideDestination.J_POLE
    Page.SLIM_JIM -> GuideDestination.SLIM_JIM
    Page.MOXON -> GuideDestination.MOXON
    Page.HB9CV -> GuideDestination.HB9CV
    Page.CANTENNA -> GuideDestination.CANTENNA
    Page.HALO_CB -> GuideDestination.HALO
    Page.TRAP_DIPOLE -> GuideDestination.TRAP_DIPOLE
    Page.YAGI_CALCULATOR -> GuideDestination.YAGI
    Page.CONSTRUCTION_YAGI -> GuideDestination.R3UDK_GUIDE
    Page.YAGI_SCALING -> GuideDestination.YAGI_SCALING
    Page.YAGI_SEGMENTATION -> GuideDestination.YAGI_SEGMENTATION
    Page.MEASUREMENTS -> GuideDestination.VNA
    Page.SWR_BANDWIDTH -> GuideDestination.BANDWIDTH
    Page.JOURNAL -> GuideDestination.PROJECTS.takeIf { projectsList }
    else -> null
}

private const val SEGMENTATION_GUIDE_IMAGE = "images/yagi-segmentation.svg"
private val guideImage = Regex("""images/(en|ru)/(light/)?[a-z0-9-]+\.webp""")
private val guideImageReference = Regex("""((?:href|src)="images/(?:en|ru)/)([a-z0-9-]+\.webp")""")

internal fun guideImageReferences(html: String, dark: Boolean): String =
    if (dark) html else guideImageReference.replace(html) { it.groupValues[1] + "light/" + it.groupValues[2] }

internal fun guideAssetPath(address: String): String? {
    guideSearchRequest(address)?.let { return it.document }
    if (!address.startsWith(GUIDE_DIRECTORY)) return null
    return address.removePrefix(GUIDE_DIRECTORY).takeIf {
        it == "en.html" || it == "ru.html" || it == SEGMENTATION_GUIDE_IMAGE || guideImage.matches(it)
    }
}

@Composable
internal fun UserGuideScreen(
    language: AppLanguage, dark: Boolean, canCheckForUpdates: Boolean, anchor: String?,
    onOpen: (GuideDestination) -> Unit,
) {
    val context = LocalContext.current
    val document = if (language == AppLanguage.RUSSIAN) "ru.html" else "en.html"
    var scrollY by key(language.tag) { rememberSaveable { mutableStateOf<Int?>(null) } }
    var searchQuery by key(language.tag) { rememberSaveable { mutableStateOf("") } }
    var searchMatch by key(language.tag) { rememberSaveable { mutableIntStateOf(0) } }
    var imagePath by key(language.tag, dark) { rememberSaveable { mutableStateOf<String?>(null) } }
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
                    search = GuideSearchRequest(document, searchQuery, searchMatch),
                    anchor = anchor,
                    onSearch = { searchQuery = it.query; searchMatch = it.match },
                    onScroll = { scrollY = it },
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
    context: Context, document: String, initialY: Int?, dark: Boolean, canCheckForUpdates: Boolean,
    background: Int, search: GuideSearchRequest, anchor: String?, onSearch: (GuideSearchRequest) -> Unit,
    onScroll: (Int) -> Unit, onImage: (String) -> Unit,
    onOpen: (GuideDestination) -> Unit,
): WebView {
    var restored = false
    return object : WebView(context) {
        override fun onScrollChanged(x: Int, y: Int, oldX: Int, oldY: Int) {
            super.onScrollChanged(x, y, oldX, oldY)
            if (restored) onScroll(y)
        }
    }.apply {
        setBackgroundColor(background)
        // Android 8.0 WebView can leave black compositor tiles around HTML form controls.
        if (android.os.Build.VERSION.SDK_INT == 26) setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
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
                                                if (asset == SEGMENTATION_GUIDE_IMAGE) {
                            return WebResourceResponse("image/svg+xml", "UTF-8", assets.open(asset))
                        }
                        return if (guideImage.matches(asset)) {
                            WebResourceResponse("image/webp", null, assets.open(asset))
                        } else guideHtml(assets, asset, dark, canCheckForUpdates,
                            requireNotNull(guideSearchRequest(request.url.toString())))
                    } catch (_: IOException) {
                        // A missing bundled asset has no network fallback.
                    }
                }
                return WebResourceResponse("text/plain", "UTF-8", 404, "Not found", emptyMap(), null)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame || !request.hasGesture() || request.method != "GET") return true
                val address = request.url.toString()
                val searchRequest = guideSearchRequest(address)
                if (searchRequest?.document == document) {
                    onSearch(searchRequest)
                    if (searchRequest.query != search.query) {
                        view.clearFocus()
                        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                            ?.hideSoftInputFromWindow(view.windowToken, 0)
                    }
                    return false
                }
                val route = guideDestination(address)
                val asset = guideAssetPath(address)
                when {
                    route != null -> onOpen(route)
                    asset != null && guideImage.matches(asset) -> onImage(asset)
                    else -> guideExternalLink(address)?.let { openSourceWithFeedback(context, it) }
                }
                return true
            }

            override fun onPageFinished(view: WebView, loadedUrl: String?) {
                val loadedSearch = loadedUrl?.let(::guideSearchRequest) ?: return
                if (loadedSearch.document != document) return
                view.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        if (!restored && loadedSearch == search) {
                            initialY?.let { view.scrollTo(0, it) }
                        } else if (loadedSearch.query.isNotEmpty() && loadedUrl.contains("#guide-match-")) {
                            // Leave the matched text below the persistent search controls.
                            @Suppress("DEPRECATION")
                            val offset = (160 * view.scale).toInt()
                            view.scrollBy(0, -offset)
                        }
                        restored = true
                    }
                })
            }
        }
        val initialAddress = guideSearchAddress(search)
        loadUrl(if (initialY == null && search.query.isEmpty() && anchor != null) "$initialAddress#$anchor" else initialAddress)
    }
}

private fun guideHtml(assets: AssetManager, asset: String, dark: Boolean, canCheckForUpdates: Boolean,
    search: GuideSearchRequest,
): WebResourceResponse {
    val classes = "in-app" + (if (dark) " dark" else "") + (if (canCheckForUpdates) " direct" else "")
    val html = assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() }
        .replaceFirst("<body>", "<body class=\"$classes\">")
        .replace(" download>", ">")
    return WebResourceResponse("text/html", "UTF-8", 200, "OK", mapOf(
        "Content-Security-Policy" to "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'self'",
    ), searchableGuideHtml(guideImageReferences(html, dark), search).byteInputStream())
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
