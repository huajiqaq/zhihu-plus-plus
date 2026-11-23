# iOS 宿主

SwiftUI 只嵌入一个 `ComposeUIViewController`。启动页、主题、页面、账号、主栈和分栏均来自共享模块。这个工程需要在 macOS 上验证；仅有工程文件不代表已编译或运行。

## 构建

使用 Apple Silicon Mac、与仓库 Kotlin 版本兼容的 Xcode、JDK 21 和项目要求的 Android SDK。按[上游补丁维护](../docs/upstream-dependency-patches.md)准备工具链；Xcode 的 Run Script 必须能访问这些工具。直接打开 `ZhihuPlusPlus.xcodeproj`，无需生成工程或安装 CocoaPods。

在仓库根目录构建 ARM64 模拟器包（部署目标 iOS 16；不需要证书或设备）：

```sh
xcodebuild -project iosApp/ZhihuPlusPlus.xcodeproj -scheme ZhihuPlusPlus \
  -configuration Debug -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

要单独检查 `iosArm64` 链接，将 destination 换为 `generic/platform=iOS`；未签名产物不可当作真机验收。Xcode 在编译 Swift 前通过[官方直接集成](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html)调用 `:shared:embedAndSignAppleFrameworkForXcode`，由 SDK/配置选择 `Shared.framework` 并同步 Compose 资源。该任务依赖 Xcode 环境，不要单独在 Windows 上调用。

检查 `.app` 中有 `compose-resources/`、`misc/emoji_mapping.json`、`misc/emojis/`、`LICENSE` 和 `aboutlibraries.json`；在获准的已安装模拟器中 Run，确认启动图进入首页，能够进出「搜索设置」和「开源许可」。后者沿用现有 Apple 包使用的 Android Lite 依赖清单（`:app:prepareLibraryDefinitionsLiteDebug`），不等于完整 iOS 依赖审计。应用本身使用[仓库 AGPL-3.0-only 许可证](../LICENSE)。

## 真实输入检查

SwiftUI `.ignoresSafeArea(.all)` 将整个窗口交给 Compose；`OnFocusBehavior.DoNothing` 禁止控制器另行抬升整个窗口。共享页面按 `WindowInsets` 消费安全区及 IME。不要在宿主再加键盘高度、`imePadding()` 或固定底距。

1. 在模拟器关闭「I/O → Keyboard → Connect Hardware Keyboard」，点开「搜索设置名称或关键词」。使用屏幕中文键盘逐字输入拼音、选词并退格，检查候选栏、插入点和实际搜索结果。离屏回调、粘贴或截图不能代替这个步骤。
2. 在未登录的手机号登录页聚焦「短信验证码」，只输入测试数字，不发送验证码、不登录。检查输入框和页内操作可滚动到键盘上方，顶栏不整体上移；横竖屏切换、收起再弹出，不能残留键盘等高的空白或重复底距。返回首页后核对状态栏和 Home 指示条不遮挡操作。
3. 用隔离的合成数据进入想法、写回答和写文章页。让 Markdown 正文超过一屏，在最后一行用中文输入法选词，拖动选区，收起再弹出键盘并旋转。插入点和末行须能滚到候选栏上方；按设计收起的快捷栏不能连同正文一起裁切。想法另检查话题前插字、撤销、退出后恢复草稿，不提交内容。
4. 用同一隔离数据在回答末段打开评论输入框。检查底部输入框和发送操作在软键盘上方、正文和评论列表仍能滚到底；收起键盘返回后检查原阅读位置。不发送评论，没有合成数据时将该场景记为未验证。
5. 重连硬件键盘，在设置搜索里分别操作方向键、输入和退格。区分屏幕键盘与硬件键盘结果；没有可用输入环境就记录未验证。

本地系统通知走启动期 delegate 和共享账号检查，不配置 APNs，不保证 iOS 挂起或退出后轮询。网页链接交给系统浏览器，不提供内嵌文章 WebView。既有 iOS actual 没有本地内容数据库，过滤规则也不持久化；打开页面不是历史缓存或过滤写入成功的证据。
