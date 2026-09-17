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
        DebugLog.i("ENGINE", "build search URL engine=$name title=$title imageUrl=$imageUrl")
        val source = runCatching { URI(imageUrl) }.getOrNull()
        require(source != null && source.scheme?.lowercase() == "https" && !source.host.isNullOrBlank() && source.userInfo == null) {
            "Only valid HTTPS image URLs are supported"
        }
        val encoded = URLEncoder.encode(source.toString(), StandardCharsets.UTF_8.name())
        val result = when (this) {
            GOOGLE -> "https://lens.google.com/uploadbyurl?url=$encoded"
            BING -> "https://www.bing.com/images/search?view=detailv2&iss=sbi&FORM=SBIHMP&sbisrc=UrlPaste&q=imgurl:$encoded"
            YANDEX -> "https://yandex.com/images/search?rpt=imageview&url=$encoded"
            BAIDU -> "https://graph.baidu.com/details?isfromtusoupc=1&tn=pc&carousel=0&promotion_name=pc_image_shituindex&image=$encoded"
            TINEYE -> "https://tineye.com/search?url=$encoded"
            SAUCENAO -> "https://saucenao.com/search.php?db=999&url=$encoded"
            IQDB -> "https://iqdb.org/?url=$encoded"
        }
        DebugLog.i("ENGINE", "search URL engine=$name result=$result")
        return result
    }
}

class MainActivity : ComponentActivity() {
    private val incomingImage = mutableStateOf<IncomingImage?>(null)
    private var incomingToken = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        DebugLog.start(this)
        DebugLog.i("ACTIVITY", "onCreate activity=$this savedInstanceState=$savedInstanceState")
        DebugLog.intent(intent, "onCreate")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WebView.setWebContentsDebuggingEnabled(true)
        DebugLog.i("WEBVIEW", "WebView remote debugging enabled=true package=${WebView.getCurrentWebViewPackage()}")
        incomingImage.value = extractImageUri(intent)?.let { uri ->
            DebugLog.i("IMAGE", "initial incoming image uri=$uri")
            IncomingImage(uri, ++incomingToken)
        }
        setContent {
            ImageSeekTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ImageSeekApp(incomingImage.value)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        DebugLog.i("ACTIVITY", "onStart activity=$this")
    }

    override fun onResume() {
        super.onResume()
        DebugLog.i("ACTIVITY", "onResume activity=$this")
    }

    override fun onPause() {
        DebugLog.i("ACTIVITY", "onPause activity=$this")
        super.onPause()
    }

    override fun onStop() {
        DebugLog.i("ACTIVITY", "onStop activity=$this")
        super.onStop()
    }

    override fun onDestroy() {
        DebugLog.i("ACTIVITY", "onDestroy activity=$this finishing=$isFinishing changingConfigurations=$isChangingConfigurations")
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        DebugLog.i("ACTIVITY", "onNewIntent activity=$this")
        DebugLog.intent(intent, "onNewIntent")
        super.onNewIntent(intent)
        setIntent(intent)
        extractImageUri(intent)?.let { uri ->
            DebugLog.i("IMAGE", "new incoming image uri=$uri")
            incomingImage.value = IncomingImage(uri, ++incomingToken)
        }
    }

    private fun extractImageUri(intent: Intent): Uri? {
        DebugLog.d("INTENT", "extractImageUri action=${intent.action} type=${intent.type}")
        if (intent.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) {
            DebugLog.d("INTENT", "extractImageUri rejected action/type")
            return null
        }
        val stream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        val result = stream ?: intent.clipData?.getItemAt(0)?.uri
        DebugLog.i("INTENT", "extractImageUri extraStream=$stream clipFirst=${intent.clipData?.getItemAt(0)?.uri} result=$result")
        return result
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

    LaunchedEffect(Unit) {
        DebugLog.i("UI", "ImageSeekApp composed initialIncoming=${incomingImage?.uri} log=${DebugLog.location()}")
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        DebugLog.i("PICKER", "photo picker result uri=$uri")
        if (uri != null) {
            uploadToken++
            selectedUri = uri
            hostedUrl = null
            error = null
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        DebugLog.i("PICKER", "open document result uri=$uri")
        if (uri != null) {
            uploadToken++
            selectedUri = uri
            hostedUrl = null
            error = null
        }
    }

    LaunchedEffect(incomingImage?.token) {
        incomingImage?.let {
            DebugLog.i("IMAGE", "LaunchedEffect incoming token=${it.token} uri=${it.uri}")
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
        DebugLog.i("IMAGE", "selected uri=$uri quality=${quality.name} maxDimension=${quality.maxDimension} jpegQuality=${quality.jpegQuality}")
        decoding = true
        try {
            val decoded = withContext(Dispatchers.IO) {
                DebugLog.uriMetadata(context, uri, "selected-image")
                DebugLog.captureSource(context, uri, "selected-image")
                decodeImage(context, uri, quality.maxDimension)
            }
            bitmap = decoded
            DebugLog.i("IMAGE", "decoded result=${decoded.width}x${decoded.height} config=${decoded.config} byteCount=${decoded.byteCount} allocationByteCount=${decoded.allocationByteCount}")
        } catch (cancelled: CancellationException) {
            DebugLog.w("IMAGE", "decode cancelled uri=$uri")
            throw cancelled
        } catch (t: Throwable) {
            DebugLog.exception("IMAGE", t, "decode failed uri=$uri quality=${quality.name}")
            error = t.message ?: "无法读取图片"
        } finally {
            decoding = false
            DebugLog.d("IMAGE", "decode finished uri=$uri decoding=false")
        }
    }

    if (hostedUrl != null) {
        DebugLog.i("UI", "show results hostedUrl=$hostedUrl engine=${engine.name}")
        ResultsScreen(hostedUrl!!, engine, {
            DebugLog.i("ENGINE", "results switch ${engine.name} -> ${it.name} hostedUrl=$hostedUrl")
            engine = it
        }, {
            DebugLog.i("UI", "results back hostedUrl=$hostedUrl")
            hostedUrl = null
        })
        return
    }

    SelectionScreen(
        bitmap = bitmap,
        quality = quality,
        engine = engine,
        decoding = decoding,
        uploading = uploading,
        error = error,
        onQualityChanged = {
            if (!uploading) {
                DebugLog.i("QUALITY", "change ${quality.name} -> ${it.name}")
                quality = it
            }
        },
        onEngineChanged = {
            DebugLog.i("ENGINE", "selection switch ${engine.name} -> ${it.name}")
            engine = it
        },
        onPickPhoto = {
            DebugLog.i("PICKER", "launch PickVisualMedia ImageOnly")
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onPickFile = {
            DebugLog.i("PICKER", "launch OpenDocument image/*")
            filePicker.launch(arrayOf("image/*"))
        },
        onSearch = {
            val currentBitmap = bitmap ?: return@SelectionScreen
            val token = ++uploadToken
            val currentQuality = quality
            DebugLog.i("SEARCH", "start token=$token engine=${engine.name} quality=${currentQuality.name} bitmap=${currentBitmap.width}x${currentBitmap.height}")
            scope.launch {
                error = null
                uploading = true
                try {
                    val result = uploadTemporaryWithFallback(context, currentBitmap, currentQuality.jpegQuality)
                    DebugLog.i("SEARCH", "upload completed token=$token result=$result activeToken=$uploadToken")
                    if (token == uploadToken) hostedUrl = result
                } catch (cancelled: CancellationException) {
                    DebugLog.w("SEARCH", "search cancelled token=$token")
                    throw cancelled
                } catch (t: Throwable) {
                    DebugLog.exception("SEARCH", t, "search failed token=$token engine=${engine.name}")
                    if (token == uploadToken) error = t.message ?: "上传失败"
                } finally {
                    if (token == uploadToken) uploading = false
                    DebugLog.i("SEARCH", "finish token=$token activeToken=$uploadToken uploading=$uploading")
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
                        Text("Android 16 / 多引擎反向搜图 · DEBUG LOG", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = { TextButton(onClick = { DebugLog.i("UI", "open diagnostics"); diagnostics = true }) { Text("诊断") } },
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
                Text("详细 Debug 日志已启用：运行信息、图片副本、HTTP 请求/响应、完整 URL、WebView 导航与异常都会写入日志。", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("Debug 日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("日志位置：${DebugLog.location()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("此 Debug 版本按你的要求不做隐私脱敏，并会在 Download/ImageSeek/logs/assets 保存所选原图与上传 JPEG 的调试副本。", fontWeight = FontWeight.SemiBold)
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
    if (diagnostics) DiagnosticsDialog(onDismiss = { DebugLog.i("UI", "close diagnostics"); diagnostics = false })
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

    LaunchedEffect(searchUrl) {
        DebugLog.i("WEBVIEW", "ResultsScreen engine=${engine.name} hostedUrl=$hostedUrl searchUrl=$searchUrl generation=$generation")
    }

    val navigateBack: () -> Unit = {
        val view = webView
        DebugLog.i("WEBVIEW", "navigateBack currentUrl=${view?.url} canGoBack=${view?.canGoBack()}")
        if (view?.canGoBack() == true) view.goBack() else onBack()
    }

    PredictiveBackHandler(enabled = true) { progress ->
        try {
            progress.collect { event -> DebugLog.d("BACK", "predictive progress=$event") }
            DebugLog.i("BACK", "predictive back committed")
            navigateBack()
        } catch (_: CancellationException) {
            DebugLog.i("BACK", "predictive back cancelled")
        }
    }

    DisposableEffect(Unit) {
        DebugLog.i("WEBVIEW", "WebView screen effect created")
        onDispose {
            val view = webView
            DebugLog.i("WEBVIEW", "dispose url=${view?.url} originalUrl=${view?.originalUrl} title=${view?.title} progress=${view?.progress} cookies=${view?.url?.let { CookieManager.getInstance().getCookie(it) }}")
            view?.stopLoading()
            view?.destroy()
            webView = null
            CookieManager.getInstance().removeAllCookies { removed -> DebugLog.i("WEBVIEW", "removeAllCookies callback=$removed") }
            WebStorage.getInstance().deleteAllData()
            DebugLog.i("WEBVIEW", "WebStorage deleteAllData requested")
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(engine.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { TextButton(onClick = navigateBack) { Text("返回") } },
                actions = { TextButton(onClick = { DebugLog.i("WEBVIEW", "open external currentUrl=$currentUrl"); openExternal(context, currentUrl) }) { Text("浏览器") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Engine.entries) { item ->
                    FilterChip(selected = item == engine, onClick = { DebugLog.i("ENGINE", "results chip click current=${engine.name} new=${item.name}"); onEngineChanged(item) }, label = { Text(item.shortTitle) })
                }
            }
            if (pageError != null) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(pageError!!, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = { DebugLog.i("WEBVIEW", "retry page error=$pageError"); pageError = null; generation++ }) { Text("重试") }
                    }
                }
            }
            key(generation) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        DebugLog.i("WEBVIEW", "create WebView generation=$generation searchUrl=$searchUrl")
                        secureWebView(ctx, { currentUrl = it }, { pageError = it }, {
                            webView = null
                            pageError = "网页渲染进程已退出，已准备重新加载"
                            generation++
                        }).also {
                            webView = it
                            DebugLog.i("WEBVIEW", "initial loadUrl=$searchUrl")
                            it.loadUrl(searchUrl)
                        }
                    },
                    update = { view ->
                        webView = view
                        DebugLog.d("WEBVIEW", "AndroidView update viewUrl=${view.url} desired=$searchUrl progress=${view.progress}")
                        if (view.url != searchUrl) {
                            DebugLog.i("WEBVIEW", "update loadUrl=$searchUrl from=${view.url}")
                            view.loadUrl(searchUrl)
                        }
                    }
                )
            }
        }
    }
}

private fun secureWebView(context: Context, onUrlChanged: (String) -> Unit, onError: (String) -> Unit, onRendererGone: () -> Unit): WebView =
    WebView(context).apply {
        DebugLog.i("WEBVIEW", "secureWebView created=$this webViewPackage=${WebView.getCurrentWebViewPackage()}")
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
        DebugLog.i(
            "WEBVIEW_SETTINGS",
            "userAgent=${settings.userAgentString} javaScript=${settings.javaScriptEnabled} domStorage=${settings.domStorageEnabled} allowFile=${settings.allowFileAccess} allowContent=${settings.allowContentAccess} mixedContent=${settings.mixedContentMode} cacheMode=${settings.cacheMode} multipleWindows=${settings.supportMultipleWindows()} jsOpenWindows=${settings.javaScriptCanOpenWindowsAutomatically} safeBrowsing=${settings.safeBrowsingEnabled}"
        )
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                DebugLog.d("WEBVIEW_PROGRESS", "progress=$newProgress url=${view?.url} title=${view?.title}")
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                DebugLog.i("WEBVIEW_TITLE", "title=$title url=${view?.url}")
            }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url
                val scheme = uri?.scheme?.lowercase()
                val block = uri == null || scheme != "https"
                DebugLog.i(
                    "WEBVIEW_NAV",
                    "shouldOverride url=$uri method=${request?.method} mainFrame=${request?.isForMainFrame} redirect=${request?.isRedirect} gesture=${request?.hasGesture()} headers=${request?.requestHeaders} block=$block"
                )
                return block
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                DebugLog.i("WEBVIEW_PAGE", "started url=$url originalUrl=${view?.originalUrl} favicon=${favicon?.width}x${favicon?.height} cookies=${url?.let { CookieManager.getInstance().getCookie(it) }}")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val cookie = url?.let { CookieManager.getInstance().getCookie(it) }
                DebugLog.i("WEBVIEW_PAGE", "finished url=$url originalUrl=${view?.originalUrl} title=${view?.title} progress=${view?.progress} cookies=$cookie")
                url?.let(onUrlChanged)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                DebugLog.e(
                    "WEBVIEW_ERROR",
                    "url=${request?.url} method=${request?.method} mainFrame=${request?.isForMainFrame} redirect=${request?.isRedirect} gesture=${request?.hasGesture()} headers=${request?.requestHeaders} errorCode=${error?.errorCode} description=${error?.description}"
                )
                if (request?.isForMainFrame == true) onError(error?.description?.toString() ?: "页面加载失败")
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                DebugLog.e(
                    "WEBVIEW_HTTP",
                    "url=${request?.url} method=${request?.method} mainFrame=${request?.isForMainFrame} requestHeaders=${request?.requestHeaders} status=${errorResponse?.statusCode} reason=${errorResponse?.reasonPhrase} mime=${errorResponse?.mimeType} encoding=${errorResponse?.encoding} responseHeaders=${errorResponse?.responseHeaders}"
                )
            }

            override fun onSafeBrowsingHit(view: WebView?, request: WebResourceRequest?, threatType: Int, callback: SafeBrowsingResponse?) {
                DebugLog.e("WEBVIEW_SAFE_BROWSING", "url=${request?.url} threatType=$threatType headers=${request?.requestHeaders}")
                callback?.backToSafety(true)
                onError("Android Safe Browsing 已拦截风险页面")
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                DebugLog.e("WEBVIEW_RENDERER", "render process gone didCrash=${detail?.didCrash()} rendererPriorityAtExit=${detail?.rendererPriorityAtExit()} url=${view?.url}")
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
        DebugLog.i("DIAGNOSTICS", "export result uri=$uri")
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(info) }
        }.onFailure { DebugLog.exception("DIAGNOSTICS", it, "export failed uri=$uri") }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设备与运行诊断") },
        text = { Text(info, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { DebugLog.i("DIAGNOSTICS", "launch export"); export.launch("imageseek-diagnostics.txt") }) { Text("导出 TXT") } },
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
    val text = buildString {
        appendLine("ImageSeek ${appInfo.versionName} (${appInfo.longVersionCode})")
        appendLine("Debug detailed logging: ${DebugLog.isEnabled()}")
        appendLine("Debug log: ${DebugLog.location()}")
        appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        appendLine("Security patch: ${Build.VERSION.SECURITY_PATCH}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Build fingerprint: ${Build.FINGERPRINT}")
        appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Page size: $pageSize bytes")
        appendLine("App max heap: ${runtime.maxMemory() / 1024 / 1024} MiB")
        appendLine("App total heap: ${runtime.totalMemory() / 1024 / 1024} MiB")
        appendLine("App free heap: ${runtime.freeMemory() / 1024 / 1024} MiB")
        appendLine("Display modes: $modes")
        appendLine("WebView: ${web?.packageName ?: "unknown"} ${web?.versionName ?: "unknown"}")
        appendLine("Target SDK: ${context.applicationInfo.targetSdkVersion}")
        appendLine("Permissions: INTERNET + AndroidX internal dynamic receiver permission")
    }
    DebugLog.i("DIAGNOSTICS", "generated\n$text")
    return text
}

private fun decodeImage(context: Context, uri: Uri, maxDimension: Int): Bitmap {
    require(maxDimension in 512..4096)
    val started = System.nanoTime()
    DebugLog.i("DECODE", "start uri=$uri maxDimension=$maxDimension")
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val width = info.size.width
        val height = info.size.height
        DebugLog.i("DECODE", "header uri=$uri source=${width}x${height} mime=${info.mimeType} colorSpace=${info.colorSpace}")
        if (width <= 0 || height <= 0) throw IOException("图片尺寸无效")
        val longest = maxOf(width, height)
        if (longest > maxDimension) {
            val scale = maxDimension.toFloat() / longest
            val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
            val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
            DebugLog.i("DECODE", "resize source=${width}x${height} -> target=${targetWidth}x${targetHeight} scale=$scale")
            decoder.setTargetSize(targetWidth, targetHeight)
        } else {
            DebugLog.i("DECODE", "no resize source=${width}x${height}")
        }
    }.also { bitmap ->
        DebugLog.i("DECODE", "complete uri=$uri result=${bitmap.width}x${bitmap.height} config=${bitmap.config} colorSpace=${bitmap.colorSpace} byteCount=${bitmap.byteCount} elapsedMs=${(System.nanoTime() - started) / 1_000_000L}")
    }
}

private fun openExternal(context: Context, url: String) {
    DebugLog.i("EXTERNAL", "openExternal requested url=$url")
    val uri = runCatching { Uri.parse(url) }.getOrElse {
        DebugLog.exception("EXTERNAL", it, "Uri.parse failed url=$url")
        return
    }
    if (uri.scheme?.lowercase() != "https") {
        DebugLog.w("EXTERNAL", "blocked non-https uri=$uri")
        return
    }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        .onSuccess { DebugLog.i("EXTERNAL", "startActivity success uri=$uri") }
        .onFailure { DebugLog.exception("EXTERNAL", it, "startActivity failed uri=$uri") }
}
