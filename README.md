# 图搜 · ImageSeek

一个基于 Android / Jetpack Compose 的多引擎反向图片搜索 App，源自 [AKS-Labs/CircleToSearch](https://github.com/AKS-Labs/CircleToSearch) 的 GPL-3.0 代码。

ImageSeek 把原项目的“截图 → 圈选 → 搜索”收缩为更直接、更低权限的流程：

```text
选择照片 / 打开图片文件 / Android 分享图片
                    ↓
      按质量档位采样解码（1600 / 2560 / 3200 px）
                    ↓
         JPEG 重编码，去除原始文件元数据
                    ↓
       Litterbox 临时上传（1 小时）
                    ↓
          同一个临时 URL 切换 7 个引擎
```

## 搜索引擎

### 通用搜索

- Google Lens
- Bing Visual Search
- Yandex Images
- 百度识图

### 来源追踪

- TinEye
- SauceNAO
- IQDB

SauceNAO、IQDB 与百度均通过无需 API Key 的网页 URL 入口适配。百度属于网页适配器，不是稳定公开 API；如果提供方改版，只需要调整 `SearchUrlFactory`。

## 2.1.0 主要变化

- 针对 Android 16 / API 36 完整保留 edge-to-edge，并把结果页返回迁移到 `PredictiveBackHandler`。
- 新增三档图片预处理：快速 1600 px / JPEG 82%、均衡 2560 px / JPEG 88%、细节 3200 px / JPEG 92%。
- `ImageDecoder` 在解码阶段直接设置目标尺寸，避免先把超大照片完整展开到内存。
- multipart 上传改为 prefix / JPEG / suffix 分段写入，不再构造第二份完整大请求体；12 MiB 以上 JPEG 会自动降低质量重试。
- 新增 WebView renderer 异常恢复：渲染进程崩溃或被系统回收后重建 WebView，并给出可重试提示。
- 新增 Android Safe Browsing 回调，命中风险页面时返回安全页并提示。
- 新增“设备与运行诊断”：读取 App、Android、ABI、page size、RAM、存储、屏幕刷新率、HDR/WCG、WebView provider/版本/User-Agent。
- 诊断页支持手动并行检测 7 个搜索站点的 DNS/TLS/HTTP 可达性，并可通过系统文档选择器导出 TXT；不需要存储权限。
- 修正 2.0.0 的 Compose 工具链组合：API 36 / AGP 8.13.2 下固定到 `2026.06.00` BOM，避免 Compose 1.12 / API 37 的工具链要求冲突。
- 继续保持 Manifest 只有 `INTERNET`；Root 只可作为外部测试机辅助，App 本身不检测、不申请也不依赖 Root。

## 2.0.0 基础能力

- 7 个反向搜图引擎。
- Android Photo Picker、OpenDocument 和 `ACTION_SEND image/*`。
- Material 3 动态色、大圆角、强化排版、边到边布局。
- WebView 禁止文件访问、Content URI 访问、Mixed Content、自定义 scheme 跳转与第三方 Cookie。
- WebView 使用 `LOAD_NO_CACHE`，关闭表单保存；启动时及正常离开结果页时清理 Cookie / WebStorage。
- Litterbox 返回地址严格限制为 `https://litter.catbox.moe/<单层安全文件名>`，不允许永久 Catbox fallback。
- 修复 `singleTop` 下连续分享相同图片 URI 可能不触发第二次处理的问题。

## 权限

Manifest 仅声明：

```text
android.permission.INTERNET
```

不申请相册、媒体、外部存储、悬浮窗、无障碍、安装包或应用列表权限。Photo Picker、OpenDocument、CreateDocument 与分享 URI grant 由 Android 系统提供授权。

## 隐私

为了让多个第三方搜索引擎读取同一张本地图片，App 会先把图片重新编码后上传到 Litterbox，并请求 1 小时后过期。上传的是重编码后的 JPEG 像素数据，不是原始文件，因此不会携带原始文件中的 EXIF / GPS 元数据。

图片临时 URL 只保存在当前进程状态中；App 不建立自己的搜索历史列表。搜索服务、Litterbox 以及外部浏览器仍可能按照各自政策处理请求，因此不要搜索身份证件、医疗资料、私密照片或其他敏感内容。

“检测引擎”是显式手动操作，会访问 7 个搜索站点首页以验证 DNS/TLS/HTTP 可达性；不会上传用户图片。

## 设备兼容

- `minSdk = 29`
- `targetSdk = 36`
- `compileSdk = 36`
- Android 16 edge-to-edge / predictive back
- 60 / 90 / 120 Hz 等刷新率按系统调度，不强制锁帧
- App 自身不引入 NDK/native `.so`；纯 Java/Kotlin 应用天然兼容 4 KB 与 16 KB page-size 设备

## 架构

- `MainActivity.kt`：Photo Picker、文件选择、分享接收、主 Compose UI、结果 WebView。
- `SearchQuality.kt`：三档本地图片预处理参数。
- `ImageLoader.kt`：采样解码与尺寸约束。
- `ImageSearchUploader.kt`：JPEG 编码、大小保护与 Litterbox HTTPS 上传。
- `LitterboxMultipart.kt`：multipart prefix/suffix 与流式写入。
- `TemporaryImageUrl.kt`：Litterbox 临时直链严格校验。
- `SearchEngine.kt`：引擎元数据和分组。
- `SearchUrlFactory.kt`：7 个网页搜索入口的唯一映射层。
- `diagnostics/DeviceDiagnostics.kt`：零 Root 运行环境诊断。
- `diagnostics/EngineHealthChecker.kt`：用户主动触发的 7 引擎可达性检测。
- `ui/DiagnosticsDialog.kt`：诊断展示与 TXT 导出。
- `ui/theme/Theme.kt`：Material 3 动态色、Typography 与 Shape 系统。

## 构建

要求：

- Android SDK 36
- JDK 17
- Android Gradle Plugin 8.13.2
- Kotlin / Compose Compiler 2.3.21
- Gradle 8.13（distribution SHA-256 已锁定）
- Compose BOM `2026.06.00`

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

Debug APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions 会显式准备 Android SDK 36 / Build Tools 35.0.0，再用受信任的 Gradle 8.13 重生成 Wrapper，随后执行单元测试、Lint、Debug APK 构建，并检查 APK 是否意外引入 native `.so`。

## License

GPL-3.0。原项目作者与许可证声明见 `NOTICE` 和 `LICENSE`。
