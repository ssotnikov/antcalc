package local.antenna.calculator.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import local.antenna.calculator.R
import org.json.JSONArray
import org.json.JSONObject

private const val ARTICLE_ROOT = "construction/yagi-lizaalert"

@Composable
internal fun yagiArticleBlocks(): JSONArray {
    val assets = LocalContext.current.assets
    val articleFile = if (LocalConfiguration.current.locales[0].language == "ru") "article.json" else "article-en.json"
    return remember(assets, articleFile) {
        assets.open("$ARTICLE_ROOT/$articleFile").bufferedReader().use { JSONArray(it.readText()) }
    }
}

@Composable
internal fun YagiStlExport() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportFailed by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            exportFailed = withContext(Dispatchers.IO) {
                try {
                    val target = context.contentResolver.openOutputStream(uri) ?: throw IOException()
                    target.use { output -> context.assets.open("$ARTICLE_ROOT/models.zip").use { it.copyTo(output) } }
                    false
                } catch (_: IOException) {
                    true
                } catch (_: SecurityException) {
                    true
                }
            }
        }
    }

    TextButton(onClick = {
        exportFailed = false
        export.launch("yagi-lizaalert-stl.zip")
    }) {
        Text(stringResource(R.string.construction_yagi_export_models))
    }
    if (exportFailed) {
        Text(stringResource(R.string.construction_yagi_export_failed), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun YagiArticleBlock(block: JSONObject) {
    val text = block.optString("text")
    when (block.getString("type")) {
        "heading" -> Text(text, Modifier.semantics { heading() }, color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.titleMedium)
        "image" -> ArticleImage(block.getString("path"), text, block.getDouble("aspectRatio").toFloat())
        "divider" -> HorizontalDivider()
        "text" -> Text(buildAnnotatedString {
            text.split("~~").forEachIndexed { part, value ->
                if (part % 2 == 0) append(value)
                else withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(value) }
            }
        }, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ArticleImage(path: String, caption: String, aspectRatio: Float) {
    val assets = LocalContext.current.assets
    val bitmap by produceState<ImageBitmap?>(null, path, assets) {
        value = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            assets.open("$ARTICLE_ROOT/$path").use { BitmapFactory.decodeStream(it, null, options) }
            options.inJustDecodeBounds = false
            options.inSampleSize = Integer.highestOneBit((maxOf(options.outWidth, options.outHeight) / 1400).coerceAtLeast(1))
            assets.open("$ARTICLE_ROOT/$path").use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
        }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(aspectRatio)) {
        bitmap?.let {
            Image(it, caption.ifBlank { stringResource(R.string.construction_yagi_article_image) }, Modifier.fillMaxSize())
        }
    }
    if (caption.isNotBlank()) Text(caption, style = MaterialTheme.typography.bodySmall)
}
