# ImageSeek 2.1.2

发布日期：2026-09-23

## 主要更新

- 完整移除详细 DebugLog 系统、HTTP 全量日志、调试图片副本和 DEBUG UI。
- WebView 调试关闭，构建标记为不可调试。
- 重构为正常产品界面：图片预览、质量档位、多引擎切换、上传状态和隐私提示。
- 修复 Android 16 下结果页返回行为：系统返回始终回到 ImageSeek 选择页。
- 新增同图同质量上传缓存，当前会话内最多 8 项、约 55 分钟，切换引擎不再重复上传。
- Litterbox 保留最多 3 次可取消重试，失败自动回退 Uguu。
- 临时上传响应限制为 16 KiB。
- 临时 URL 严格验证 HTTPS / Host / Path / Port / Query / Fragment。
- WebView 禁止 file/content 访问、Mixed Content、第三方 Cookie、弹窗和非 HTTPS 导航。
- 保留 Android Safe Browsing 和渲染进程异常恢复。
- 图片在上传前限制尺寸并重新编码为 JPEG，不直接上传原始文件，因此原始 EXIF/GPS 不随原文件上传。

## 搜索引擎

- Google Lens
- Bing Visual Search
- Yandex Images
- 百度识图
- TinEye
- SauceNAO
- IQDB

## Android

- minSdk 29
- targetSdk 36
- compileSdk 36
- versionName 2.1.2
- versionCode 212

## 构建与审计

本版本通过：

- Gradle Build
- Android Lint
- APK 签名验证
- package / versionCode / versionName / targetSdk 校验
- 16 KiB zipalign 校验
- APK ZIP 完整性校验

APK SHA-256：

```text
ef1852ea1c8ed875492f68680b47018ba6aaa761b58bc86f69d5b9998d1b9547
```

## 隐私说明

图片像素内容会发送到临时图片托管服务和用户选择的第三方搜索引擎。请勿上传身份证件、医疗资料、私密照片或其他敏感内容。

## License

GPL-3.0。ImageSeek 是 AKS-Labs/CircleToSearch 的修改作品，详见 LICENSE 与 NOTICE。
