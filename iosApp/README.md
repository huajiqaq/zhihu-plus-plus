# iOS 宿主

SwiftUI 只嵌入一个 `ComposeUIViewController`。启动页、主题、页面、账号、主栈和分栏均来自共享模块。工程在 Apple Silicon macOS 上构建，支持 arm64 iOS 与模拟器；运行状态须在对应平台核验。

## 构建

使用 Apple Silicon Mac、与仓库 Kotlin 版本兼容的 Xcode、JDK 21 和项目要求的 Android SDK。按[上游补丁维护](../docs/upstream-dependency-patches.md)准备工具链；Xcode 的 Run Script 必须能访问这些工具。直接打开 `ZhihuPlusPlus.xcodeproj`，无需生成工程或安装 CocoaPods。

在仓库根目录构建 ARM64 模拟器包（部署目标 iOS 16；不需要证书或设备）：

```sh
xcodebuild -project iosApp/ZhihuPlusPlus.xcodeproj -scheme ZhihuPlusPlus \
  -configuration Debug -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

要单独检查 `iosArm64` 链接，将 destination 换为 `generic/platform=iOS`；未签名产物不可当作真机验收。Xcode 在编译 Swift 前通过[官方直接集成](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html)调用 `:shared:embedAndSignAppleFrameworkForXcode`，由 SDK/配置选择 `Shared.framework` 并同步 Compose 资源。该任务依赖 Xcode 环境，不要单独在 Windows 上调用。

`.app` 应包含 `compose-resources/`、`misc/emoji_mapping.json`、`misc/emojis/`、`LICENSE` 和 `aboutlibraries.json`。在获准的已安装模拟器中核验启动和资源加载；「开源许可」使用 Android Lite 依赖清单（`:app:prepareLibraryDefinitionsLiteDebug`），不等于完整 iOS 依赖审计。应用本身使用[仓库 AGPL-3.0-only 许可证](../LICENSE)。

## 输入与平台边界

SwiftUI `.ignoresSafeArea(.all)` 将整个窗口交给 Compose；`OnFocusBehavior.DoNothing` 禁止控制器另行抬升整个窗口。共享页面按 `WindowInsets` 消费安全区及 IME。不要在宿主再加键盘高度、`imePadding()` 或固定底距。

- 在模拟器关闭「I/O → Keyboard → Connect Hardware Keyboard」，用设置搜索核验屏幕中文键盘的拼音选词、退格、插入点和结果；重连后区分硬件键盘的方向键、输入和退格。离屏回调、粘贴或截图不能代替真实输入。
- 登录、长文编辑和评论输入须能滚到键盘及候选栏上方。核验键盘收放和横竖屏切换后的安全区、末行、选区、草稿与阅读位置，不整体抬升顶栏、裁切正文或留下重复底距。
- 创作和评论核验使用隔离合成数据，不提交内容或发送评论；登录页只输入测试数字，不发送验证码、不登录。缺少输入环境或合成数据时记录未验证。

本地系统通知走启动期 delegate 和共享账号检查，不配置 APNs，不保证 iOS 挂起或退出后轮询。普通网页使用 WKWebView，明确选择「在浏览器打开」才外跳。iOS 没有本地内容数据库，过滤规则也不持久化；打开页面不代表历史缓存或过滤写入成功。
