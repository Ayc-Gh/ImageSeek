# 图搜 · ImageSeek

一个面向 Android 的多引擎反向图片搜索 App，基于 Jetpack Compose 构建。

ImageSeek 源自 [AKS-Labs/CircleToSearch](https://github.com/AKS-Labs/CircleToSearch) 的 GPL-3.0 代码，但已经重构为更直接、低权限的图片搜索流程：

```text
选择照片 / 打开图片文件 / Android 分享图片
                    ↓
   按质量档位限制尺寸（1600 / 2560 / 3200 px）
                    ↓
        重新编码为 JPEG，去除原始 EXIF/GPS
                    ↓
        临时上传（Litterbox，失败回退 Uguu）
                    ↓
       同一临时 URL 切换多个反向搜图引擎
```

## 主要特性

- 7 个反向图片搜索引擎：
  - Google Lens
  - Bing Visual Search
  - Yandex Images
  - 百度识图
  - TinEye
  - SauceNAO
  - IQDB
- 三档图片质量：
  - 快速：1600 px / JPEG 82
  - 均衡：2560 px / JPEG 88
  - 细节：3200 px / JPEG 92
- 支持 Android Photo Picker、文件选择和 `ACTION_SEND image/*`
- 同一图片 + 同一质量在当前会话中复用临时 URL，最多缓存 8 项，约 55 分钟
- Litterbox 最多 3 次可取消重试，失败后自动回退 Uguu
- 临时上传响应体限制为 16 KiB
- 上传返回 URL 做 HTTPS / Host / Path / Port / Query / Fragment 严格校验
- WebView 仅允许 HTTPS 导航
- WebView 禁止 file/content 访问、Mixed Content、第三方 Cookie 和弹窗
- Android Safe Browsing 风险页拦截
- WebView renderer 异常可恢复
- 结果页系统返回始终回到 ImageSeek 选择页，不被 WebView 历史栈困住
- WebView 调试关闭
- 不包含详细 DebugLog、请求/响应全文日志、原图调试副本或上传 JPEG 调试副本

## 权限

Manifest 当前只声明：

```text
android.permission.INTERNET
```

不申请：

- 相册/媒体库广泛读取权限
- 外部存储权限
- 悬浮窗权限
- 无障碍权限
- 应用列表权限
- Root 权限

图片选择与分享依赖 Android 系统提供的 URI grant。

## 隐私说明

ImageSeek 会把用户选择的图片重新解码并重新编码为 JPEG，然后上传到临时图片服务，使第三方搜索引擎能够读取图片。

重新编码意味着原始文件中的 EXIF / GPS 等文件级元数据不会随原文件一起上传，但**图片像素内容本身仍会发送到临时托管服务和用户选择的搜索引擎**。

当前实现：

- 优先使用 Litterbox，并请求 1 小时过期
- Litterbox 失败后自动回退到 Uguu
- App 不维护自己的搜索历史数据库
- 临时 URL 仅保存在当前进程内的会话缓存中

不要上传身份证件、医疗材料、私密照片或其他敏感内容。

## 返回行为

在搜索结果页：

```text
系统返回 / 顶部“返回”
        ↓
回到 ImageSeek 图片选择页
        ↓
再次系统返回
        ↓
退出 App / 回到桌面或上一个任务
```

结果页不会优先消费 WebView 内部历史，从而避免被搜索站点自己的重定向历史困住。

## 兼容性

- `minSdk = 29`
- `targetSdk = 36`
- `compileSdk = 36`
- JDK 17
- Android Gradle Plugin 8.13.2
- Kotlin 2.3.21
- Compose BOM `2026.06.00`
- Gradle 8.13

App 当前不引入 NDK/native `.so`，因此没有自带 native page-size 兼容层需要维护。

## 当前架构

仓库刻意保持精简，核心业务代码只有两个 Kotlin 文件：

- `MainActivity.kt`
  - Photo Picker
  - OpenDocument
  - 分享图片接收
  - Compose 主界面
  - 图片预览和质量选择
  - 图片尺寸限制解码
  - 7 个搜索引擎 URL 生成
  - 会话上传缓存
  - 安全 WebView
  - 返回行为
- `TemporaryImageUploader.kt`
  - JPEG 编码
  - 12 MiB 上传大小保护
  - Litterbox multipart 上传
  - 重试和 Uguu fallback
  - 16 KiB 响应上限
  - HTTPS 临时 URL 严格校验

## 构建

要求：

- JDK 17
- Android SDK 36
- Gradle 8.13

```bash
gradle --no-daemon clean testDebugUnitTest lintDebug assembleDebug
```

产物：

```text
app/build/outputs/apk/debug/app-debug.apk
```

当前 `debug` build type 也显式设置为：

```text
isDebuggable = false
```

因此它虽然由 `assembleDebug` 产出，但应用本身不会以 debuggable 模式运行。

## 正式发布与签名

正式分发 APK 应使用**固定且长期保存的 release signing key**。不要把 keystore、密码或 `keystore.properties` 提交到公开仓库。

推荐做法：

1. 在本地或受信任 CI 中生成/保存 release keystore。
2. 通过环境变量或 CI Secret 注入签名信息。
3. 使用 `assembleRelease` 构建。
4. 发布前验证：
   - package / versionCode / versionName
   - APK 签名
   - `zipalign -P 16`
   - Android Lint
   - 依赖与权限变化

如果更换签名证书，Android 会把 APK 视为不同的更新链，已有安装将无法直接覆盖升级。

## 版本

当前源码：

```text
versionName = 2.1.2
versionCode = 212
```

详细变更见 [CHANGELOG.md](CHANGELOG.md)。

## 安全

安全策略见 [SECURITY.md](SECURITY.md)。

## 贡献

开发与 PR 要求见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 上游与许可证

本项目是 [AKS-Labs/CircleToSearch](https://github.com/AKS-Labs/CircleToSearch) 的修改作品。

- 上游项目：Circle To Search
- 许可证：GNU GPL v3.0
- 本仓库同样使用 GPL-3.0

详见：

- [LICENSE](LICENSE)
- [NOTICE](NOTICE)
