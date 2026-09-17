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
        require(source != null && source.scheme?.lowercase() == "https" && !source.host.isNullOrBlank() && source.userInfo == null) {
            "Only valid HTTPS image URLs are supported"
        }
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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WebView.setWebContentsDebuggingEnabled(false)
        incomingImage.value = extractImageUri(intent)?.let { IncomingImage(it, ++incomingToken) }
        setContent {
            ImageSeekTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ImageSeekApp(incomingImage.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractImageUri(intent)?.let { incomingImage.value = IncomingImage(it, ++incomingToken) }
    }

    private fun extractImageUri(intent: Intent): Uri? {
        if (intent.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return null
        val stream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        return stream ?: intent.clipData?.getItemAt(0)?.uri
    }
}

@Composable
private fun ImageSeekTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
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

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            uploadToken++
            selectedUri = uri
            hostedUrl = null
            error = null
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            uploadToken++
            selectedUri = uri
            hostedUrl = null
            error = null
        }
    }

    LaunchedEffect(incomingImage?.token) {
        incomingImage?.let {
            uploadToken++
            selectedUri = it.uri
            hostedUrl = null
            error = null
        }
    }

    LaunchedEffect(selectedUri, quality) {
        bitmap = null
        hostedUrl = null
        error = null
        val uri = selectedUri ?: return@LaunchedEffect
        decoding = true
        try {
            bitmap = withContext(Dispatchers.IO) { decodeImage(context, uri, quality.maxDimension) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            error = t.message ?: "无法读取图片"
        } finally {
            decoding = false
        }
    }

    if (hostedUrl != null) {
        ResultsScreen(hostedUrl!!, engine, { engine = it }, { hostedUrl = null })
        return
    }

    SelectionScreen(
        bitmap = bitmap,
        quality = quality,
        engine = engine,
        decoding = decoding,
        uploading = uploading,
        error = error,
        onQualityChanged = { if (!uploading) quality = it },
        onEngineChanged = { engine = it },
        onPickPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onPickFile = { filePicker.launch(arrayOf("image/*")) },
        onSearch = {
            val currentBitmap = bitmap ?: return@SelectionScreen
            val token = ++uploadToken
            val currentQuality = quality
            scope.launch {
                error = null
                uploading = true
                try {
                    val result = uploadTemporaryWithFallback(currentBitmap, currentQuality.jpegQuality)
                    if (token == uploadToken) hostedUrl = result
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    if (token == uploadToken) error = t.message ?: "上传失败"
                } finally {
                    if (token == uploadToken) uploading = false
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionScreen(
    bitmap: Bitmap?,
    quality: Quality,
    engine: Engine,
    decoding: Boolean,
    uploading: Boolean,
    error: String?,
    onQualityChanged: (Quality) -> Unit,
    onEngineChanged: (Engine) -> Unit,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onSearch: () -> Unit
) {
    var diagnostics by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("图搜 · ImageSeek", style = MaterialTheme.typography.titleLarge)
                        Text("Android 16 / 多引擎反向搜图", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = { TextButton(onClick = { diagnostics = true }) { Text("诊断") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("一张图，七个引擎。", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("选择或分享图片，只上传一次，再快速切换 Google、Bing、Yandex、百度、TinEye、SauceNAO 与 IQDB。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Card(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 440.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable(enabled = !decoding && !uploading, onClick = onPickPhoto),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            decoding -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator()
                                Text("正在读取图片…")
                            }
                            bitmap != null -> Image(bitmap.asImageBitmap(), "待搜索图片", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(28.dp)) {
                                Text("选择一张图片", style = MaterialTheme.typography.titleLarge)
                                Text("支持系统照片选择器、文件以及其他 App 的“分享图片”入口", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (bitmap != null) Text("预处理尺寸：${bitmap.width} × ${bitmap.height}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onPickPhoto, enabled = !decoding && !uploading, modifier = Modifier.weight(1f)) { Text("选择照片") }
                        OutlinedButton(onClick = onPickFile, enabled = !decoding && !uploading, modifier = Modifier.weight(1f)) { Text("从文件打开") }
                    }
                }
            }

            SectionCard("图片质量") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    items(Quality.entries) { item ->
                        FilterChip(selected = item == quality, onClick = { onQualityChanged(item) }, enabled = !uploading, label = { Text("${item.title} · ${item.detail}") })
                    }
                }
            }

            SectionCard("搜索引擎") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    items(Engine.entries) { item ->
                        FilterChip(selected = item == engine, onClick = { onEngineChanged(item) }, label = { Text(item.shortTitle) })
                    }
                }
                Text("当前：${engine.title}", style = MaterialTheme.typography.titleMedium)
            }

            Card(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("上传与隐私", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("图片会按 ${quality.maxDimension}px 上限采样并重新编码为 JPEG，原始 EXIF/GPS 不会随原文件上传；优先使用 Litterbox 1 小时临时存储，服务异常时自动切换到约 3 小时过期的 Uguu 临时存储。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("不要搜索身份证件、医疗资料、私密照片或其他敏感内容。", fontWeight = FontWeight.SemiBold)
                }
            }

            if (error != null) {
                Card(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(error, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            Button(onClick = onSearch, enabled = bitmap != null && !decoding && !uploading, modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).height(56.dp)) {
                if (uploading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("正在生成临时图片…")
                } else {
                    Text("开始搜索 · ${engine.shortTitle}")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (diagnostics) DiagnosticsDialog(onDismiss = { diagnostics = false })
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultsScreen(hostedUrl: String, engine: Engine, onEngineChanged: (Engine) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val searchUrl = remember(hostedUrl, engine) { engine.searchUrl(hostedUrl) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(searchUrl) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }

    val navigateBack: () -> Unit = {
        val view = webView
        if (view?.canGoBack() == true) view.goBack() else onBack()
    }

    PredictiveBackHandler(enabled = true) { progress ->
        try {
            progress.collect { }
            navigateBack()
        } catch (_: CancellationException) {
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(engine.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { TextButton(onClick = navigateBack) { Text("返回") } },
                actions = { TextButton(onClick = { openExternal(context, currentUrl) }) { Text("浏览器") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Engine.entries) { item ->
                    FilterChip(selected = item == engine, onClick = { onEngineChanged(item) }, label = { Text(item.shortTitle) })
                }
            }
            if (pageError != null) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(pageError!!, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = { pageError = null; generation++ }) { Text("重试") }
                    }
                }
            }
            key(generation) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        secureWebView(ctx, { currentUrl = it }, { pageError = it }, {
                            webView = null
                            pageError = "网页渲染进程已退出，已准备重新加载"
                            generation++
                        }).also {
                            webView = it
                            it.loadUrl(searchUrl)
                        }
                    },
                    update = { view ->
                        webView = view
                        if (view.url != searchUrl) view.loadUrl(searchUrl)
                    }
                )
            }
        }
    }
}

private fun secureWebView(context: Context, onUrlChanged: (String) -> Unit, onError: (String) -> Unit, onRendererGone: () -> Unit): WebView =
    WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.saveFormData = false
        settings.safeBrowsingEnabled = true
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                return uri.scheme?.lowercase() != "https"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                url?.let(onUrlChanged)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) onError(error?.description?.toString() ?: "页面加载失败")
            }

            override fun onSafeBrowsingHit(view: WebView?, request: WebResourceRequest?, threatType: Int, callback: SafeBrowsingResponse?) {
                callback?.backToSafety(true)
                onError("Android Safe Browsing 已拦截风险页面")
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                view?.destroy()
                onRendererGone()
                return true
            }
        }
    }

@Composable
private fun DiagnosticsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val info = remember { diagnosticsText(context) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(info) } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设备与运行诊断") },
        text = { Text(info, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { export.launch("imageseek-diagnostics.txt") }) { Text("导出 TXT") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

private fun diagnosticsText(context: Context): String {
    val web = WebView.getCurrentWebViewPackage()
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    }
    val modes = display?.supportedModes?.map { "${it.physicalWidth}x${it.physicalHeight}@${it.refreshRate.roundToInt()}Hz" }?.distinct()?.joinToString() ?: "unknown"
    val pageSize = runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }.getOrDefault(-1L)
    val runtime = Runtime.getRuntime()
    val appInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    return buildString {
        appendLine("ImageSeek ${appInfo.versionName} (${appInfo.longVersionCode})")
        appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        appendLine("Security patch: ${Build.VERSION.SECURITY_PATCH}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Page size: $pageSize bytes")
        appendLine("App max heap: ${runtime.maxMemory() / 1024 / 1024} MiB")
        appendLine("Display modes: $modes")
        appendLine("WebView: ${web?.packageName ?: "unknown"} ${web?.versionName ?: "unknown"}")
        appendLine("Target SDK: ${context.applicationInfo.targetSdkVersion}")
        appendLine("Permissions: INTERNET only")
    }
}

private fun decodeImage(context: Context, uri: Uri, maxDimension: Int): Bitmap {
    require(maxDimension in 512..4096)
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val width = info.size.width
        val height = info.size.height
        if (width <= 0 || height <= 0) throw IOException("图片尺寸无效")
        val longest = maxOf(width, height)
        if (longest > maxDimension) {
            val scale = maxDimension.toFloat() / longest
            decoder.setTargetSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
        }
    }
}

private fun openExternal(context: Context, url: String) {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
    if (uri.scheme?.lowercase() != "https") return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}
