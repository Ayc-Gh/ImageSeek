package com.akslabs.multiimagesearch

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.system.Os
import android.system.OsConstants
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

private data class IncomingImage(val uri: Uri, val token: Long)

private enum class Quality(val title: String, val detail: String, val maxDimension: Int, val jpegQuality: Int) {
    FAST("快速", "1600 px · 省流量", 1600, 82),
    BALANCED("均衡", "2560 px · 推荐", 2560, 88),
    DETAIL("细节", "3200 px · 小目标/文字", 3200, 92)
}

private enum class Engine(val title: String, val shortTitle: String, val host: String) {
    GOOGLE("Google Lens", "Google", "lens.google.com"),
    BING("Bing Visual Search", "Bing", "www.bing.com"),
    YANDEX("Yandex Images", "Yandex", "yandex.com"),
    BAIDU("百度识图", "百度", "graph.baidu.com"),
    TINEYE("TinEye", "TinEye", "tineye.com"),
    SAUCENAO("SauceNAO", "SauceNAO", "saucenao.com"),
    IQDB("IQDB", "IQDB", "iqdb.org");

    fun searchUrl(imageUrl: String): String {
        val source = runCatching { URI(imageUrl) }.getOrNull()
        require(source != null && source.scheme?.lowercase() == "https" && !source.host.isNullOrBlank() && source.userInfo == null)
        val encoded = URLEncoder.encode(source.toString(), StandardCharsets.UTF_8.name())
        return when (this) {
            GOOGLE -> "https://lens.google.com/uploadbyurl?url=$encoded"
            BING -> "https://www.bing.com/images/search?view=detailv2&iss=sbi&FORM=SBIHMP&sbisrc=UrlPaste&q=imgurl:$encoded"
            YANDEX -> "https://yandex.com/images/search?rpt=imageview&url=$encoded"
            BAIDU -> "https://graph.baidu.com/details?isfromtusoupc=1&tn=pc&carousel=0&promotion_name=pc_image_shituindex&image=$encoded"
            TINEYE -> "https://tineye.com/search?url=$encoded"
            SAUCENAO -> "https://saucenao.com/search.php?db=999&url=$encoded"
            IQDB -> "https://iqdb.org/?url=$encoded"
        }
    }
}

class MainActivity : ComponentActivity() {
    private val incomingImage = mutableStateOf<IncomingImage?>(null)
    private var incomingToken = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        DebugLog.start(this)
        DebugLog.intent(intent, "onCreate")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WebView.setWebContentsDebuggingEnabled(true)
        DebugLog.i("ACTIVITY", "onCreate webDebug=true")
        incomingImage.value = extractImageUri(intent)?.let { IncomingImage(it, ++incomingToken) }
        setContent {
            ImageSeekTheme { Surface(Modifier.fillMaxSize()) { ImageSeekApp(incomingImage.value) } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DebugLog.intent(intent, "onNewIntent")
        setIntent(intent)
        extractImageUri(intent)?.let { incomingImage.value = IncomingImage(it, ++incomingToken) }
    }

    private fun extractImageUri(intent: Intent): Uri? {
        DebugLog.intent(intent, "extractImageUri")
        if (intent.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        } ?: intent.clipData?.getItemAt(0)?.uri
    }
}

@Composable
private fun ImageSeekTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme() else lightColorScheme(), content = content)
}

@Composable
private fun ImageSeekApp(incomingImage: IncomingImage?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(incomingImage?.uri) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var quality by remember { mutableStateOf(Quality.BALANCED) }
    var engine by remember { mutableStateOf(Engine.GOOGLE) }
    var decoding by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hostedUrl by remember { mutableStateOf<String?>(null) }
    var uploadToken by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) { DebugLog.i("UI", "ImageSeek started log=${DebugLog.location()}") }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        DebugLog.i("PICKER", "photo=$uri")
        if (uri != null) { uploadToken++; selectedUri = uri; hostedUrl = null; error = null }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        DebugLog.i("PICKER", "file=$uri")
        if (uri != null) { uploadToken++; selectedUri = uri; hostedUrl = null; error = null }
    }

    LaunchedEffect(incomingImage?.token) { incomingImage?.let { selectedUri = it.uri } }

    LaunchedEffect(selectedUri, quality) {
        val uri = selectedUri ?: return@LaunchedEffect
        decoding = true
        try {
            bitmap = withContext(Dispatchers.IO) {
                DebugLog.uriMetadata(context, uri, "selected")
                DebugLog.captureSource(context, uri, "selected")
                decodeImage(context, uri, quality.maxDimension)
            }
        } catch (t: Throwable) {
            DebugLog.exception("DECODE", t)
            error = t.message
        } finally { decoding = false }
    }

    if (hostedUrl != null) {
        ResultsScreen(hostedUrl!!, engine, { engine = it }, { hostedUrl = null })
        return
    }

    SelectionScreen(bitmap, quality, engine, decoding, uploading, error,
        { quality = it }, { engine = it },
        { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        { filePicker.launch(arrayOf("image/*")) },
        {
            val b = bitmap ?: return@SelectionScreen
            val token = ++uploadToken
            uploading = true
            scope.launch {
                try {
                    val url = uploadTemporaryWithFallback(context, b, quality.jpegQuality)
                    if (token == uploadToken) hostedUrl = url
                } catch (t: Throwable) {
                    DebugLog.exception("SEARCH", t)
                    error = t.message
                } finally { uploading = false }
            }
        })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionScreen(bitmap: Bitmap?, quality: Quality, engine: Engine, decoding: Boolean, uploading: Boolean, error: String?, onQualityChanged: (Quality)->Unit, onEngineChanged:(Engine)->Unit, onPickPhoto:()->Unit, onPickFile:()->Unit, onSearch:()->Unit) {
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing, topBar = { TopAppBar(title = { Text("图搜 · ImageSeek DEBUG") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("详细日志已开启")
            Button(onClick = onPickPhoto) { Text("选择照片") }
            OutlinedButton(onClick = onPickFile) { Text("文件") }
            LazyRow { items(Quality.entries) { FilterChip(selected = it == quality, onClick = { onQualityChanged(it) }, label = { Text(it.title) }) } }
            LazyRow { items(Engine.entries) { FilterChip(selected = it == engine, onClick = { onEngineChanged(it) }, label = { Text(it.shortTitle) }) } }
            Text("日志: ${DebugLog.location()}")
            error?.let { Text(it) }
            Button(onClick = onSearch, enabled = bitmap != null && !uploading) { Text(if (uploading) "上传中" else "搜索") }
        }
    }
}

@Composable
private fun ResultsScreen(hostedUrl:String, engine:Engine, onEngineChanged:(Engine)->Unit, onBack:()->Unit) {
    val url = remember(hostedUrl, engine) { engine.searchUrl(hostedUrl) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    Scaffold { padding -> AndroidView(Modifier.fillMaxSize().padding(padding), factory = { ctx -> secureWebView(ctx).also { webView=it; it.loadUrl(url) } }) }
}

private fun secureWebView(context: Context): WebView = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    webViewClient = object: WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) { DebugLog.i("WEBVIEW", "finished=$url") }
        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) { DebugLog.e("WEBVIEW", "error=$error url=${request?.url}") }
        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean { DebugLog.e("WEBVIEW", "gone=$detail"); return true }
    }
}

@Composable
private fun DiagnosticsDialog(onDismiss:()->Unit) { onDismiss() }

private fun diagnosticsText(context:Context):String = "ImageSeek ${DebugLog.location()}"

private fun decodeImage(context: Context, uri: Uri, maxDimension: Int): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val scale = maxDimension.toFloat() / maxOf(info.size.width, info.size.height)
        if (scale < 1) decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
    }
}
