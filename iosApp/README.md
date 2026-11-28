# iOS 宿主

SwiftUI 只嵌入一个 `ComposeUIViewController`。启动页、主题、页面、账号、主栈和分栏均来自共享模块。工程支持 arm64 iOS 与模拟器；设备包可在 Intel 或 Apple Silicon Mac 上构建，arm64 模拟器验收使用 Apple Silicon。包型差异、当前限制和验收状态见[平台能力与边界](../docs/platform-capabilities.md#ios)。

**因设备及验证环境条件限制，本轮 iOS 未验证，未运行对应构建 CI。** 下列构建和验收入口继续保留；它们不是当前版本已通过的证明。

## 构建

使用 macOS、与仓库 Kotlin 版本兼容的 Xcode、正式 Temurin JDK 25 和项目要求的 Android SDK；运行 arm64 模拟器需要 Apple Silicon。按[上游补丁维护](../docs/upstream-dependency-patches.md)准备工具链；Xcode 的 Run Script 必须能访问这些工具。直接打开 `ZhihuPlusPlus.xcodeproj`，无需生成工程或安装 CocoaPods。

工程默认 Full；Lite 使用独立应用标识 `com.github.zly2006.zhihu.ios.lite`，不包含 NLP 和可选 Miuix 皮肤。Lite 保留共享导航与布局组件，与 Android Lite 的产品取舍一致。

| 包型 | Scheme | Debug / Release 配置 |
| --- | --- | --- |
| Full | `ZhihuPlusPlus` | `Debug` / `Release` |
| Lite | `ZhihuPlusPlusLite` | `DebugLite` / `ReleaseLite` |

在仓库根目录构建 ARM64 模拟器包（部署目标 iOS 16；不需要证书或设备）：

```sh
xcodebuild -project iosApp/ZhihuPlusPlus.xcodeproj -scheme ZhihuPlusPlus \
  -configuration Debug -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build

xcodebuild -project iosApp/ZhihuPlusPlus.xcodeproj -scheme ZhihuPlusPlusLite \
  -configuration DebugLite -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

Xcode 配置通过 `ZHIHU_IOS_VARIANT` 向 Gradle 传递 `-PzhihuIosVariant=full|lite`；直接运行 iOS Gradle 任务时可显式指定，缺省为 `full`。Full/Lite 分开构建，不混用框架、资源或应用目录。

要单独检查 `iosArm64` 链接，将 destination 换为 `generic/platform=iOS`；未签名产物不可当作真机验收。Xcode 在编译 Swift 前通过[官方直接集成](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html)调用 `:shared:embedAndSignAppleFrameworkForXcode`，由 SDK/配置选择 `Shared.framework` 并同步 Compose 资源。该任务依赖 Xcode 环境，不要单独在 Windows 上调用。

`.app` 应包含 `compose-resources/`、`misc/emoji_mapping.json`、`misc/emojis/`、`LICENSE`、`aboutlibraries.json` 和对应的 `licenses/`。依赖清单按当前 iOS 包型及 SDK 生成，设备和模拟器分别使用 `:shared:prepareIosArm64LicenseManifest`、`:shared:prepareIosSimulatorArm64LicenseManifest`；Full 必须携带 ONNX Runtime 原始许可与第三方声明，Lite 不混入 Full 清单或运行库。核验实际归档的资源、启动及应用内许可展示，不能只看生成任务成功。应用本身使用[仓库 AGPL-3.0-only 许可证](../LICENSE)。

## 手动构建与 CI 验收

只构建/打包时使用 [Build Check](../.github/workflows/other-branches-test.yml)：`target=ios` 同时构建 Full / Lite 的 arm64 设备 Release unsigned IPA 与 arm64 模拟器 Debug `.app.zip`。`ios-device`、`ios-simulator` 分别只构建设备或模拟器的两种包型；仅需一种包型时选择 `ios-device-full`、`ios-device-lite`、`ios-simulator-full` 或 `ios-simulator-lite`。该入口只上传 Actions 制品，不启动模拟器、不执行回归或公开发布。

纯构建脚本 [verify-ios-acceptance.py](../.github/scripts/verify-ios-acceptance.py) 使用 `--stage release` 或 `--stage simulator-package`，配合 `--variant full|lite`（默认 `full`）、`--root` 和 `--output`。该脚本限显式选择的 GitHub-hosted Mac runner，输出必须在 `RUNNER_TEMP` 下独立新目录；本地构建使用上文 Xcode 命令。脚本检查对应应用身份、资源、许可和 ORT 链接边界，不启动 Simulator 或执行 XCTest。

**unsigned IPA 不是可直接安装的签名包**；设备安装还需合适的证书、描述文件和签名流程。归档、编译与链接成功都不代表模拟器或实机交互已验收。

`Pull Request Check` 工作流默认不运行 iOS，XCTest 验收阶段只覆盖 Full，不代表 Lite 已验收。手动选择 `checks=ios` 可只重验 iOS；`checks=all` 并开启 `apple_release` 则一并验收 Apple Release。`ios_stage=all` 默认运行两项，任一失败都会使聚合门禁失败；仅重验失败阶段时可选 `release` 或 `simulator`，单项成功不代表完整 iOS 验收。两项职责分别为：

- `release`：使用内存较大的 Intel 宿主交叉链接优化的 `Release` / `iosArm64`。检查应用身份、arm64、资源与调试协议隔离后生成 `Payload/ZhihuPlusPlus.app` 结构的 unsigned IPA，并校验归档结构、CRC 和内容。它未签名，不代表真机可安装、已通过运行验收或可提交 App Store。
- `simulator`：两个独立 job 均使用 Apple Silicon `macos-26`。构建 job 在临时工程副本生成 XCTest target，执行 Debug `build-for-testing`；成功后归档可搬迁的 `Build/Products`（含唯一 `.xctestrun`），保留可执行位与树内相对链接。测试 job 不安装 Gradle/Haxe，只通过 `-xctestrun test-without-building` 在本任务创建的 Simulator 检查登录说明展开/收起、游客进入外观及返回；不依赖 Release 构建，不注入生产调试协议，不使用真实账号。结束后删除本任务 Simulator。

验收证据上传为 Actions 制品 `iOS Acceptance-release-<attempt>`、`iOS Acceptance-simulator-build-<attempt>` 和 `iOS Acceptance-simulator-test-<attempt>`。Release 成功时提供未签名 IPA 和 `SHA256SUMS`；Simulator 的结论以实际执行的 XCTest 结果为准。失败时也会上传已生成的日志，不能仅凭制品存在判断通过。对应脚本为 [verify-ios-acceptance.py](../.github/scripts/verify-ios-acceptance.py)，运行时必须指定 `--stage release|simulator-build|simulator-test`；模拟器阶段另需 `--handoff` 目录。`--self-check` 只做本地静态与合成归档校验。该流程不创建公开发布。

模拟器交接制品 `ios-simulator-products-<run_id>-<attempt>` 仅由成功构建上传，测试 job 按 producer 输出的不可变 artifact ID 下载，并核对归档 SHA-256、commit、仓库、workflow、run ID、Xcode/SDK 和 arm64 架构；拒绝越界路径与依赖构建机临时目录的运行路径。只接受同一次 workflow run；“Re-run failed jobs” 可以复用更早 attempt 成功的构建，不要求 producer attempt 等于当前 attempt。制品保留 7 天；过期或宿主 Xcode/SDK 已变更时需要重新运行构建，不能跳过校验。

## 输入与平台边界

SwiftUI `.ignoresSafeArea(.all)` 将整个窗口交给 Compose；`OnFocusBehavior.DoNothing` 禁止控制器另行抬升整个窗口。共享页面按 `WindowInsets` 消费安全区及 IME。不要在宿主再加键盘高度、`imePadding()` 或固定底距。

- 在模拟器关闭「I/O → Keyboard → Connect Hardware Keyboard」，用设置搜索核验屏幕中文键盘的拼音选词、退格、插入点和结果；重连后区分硬件键盘的方向键、输入和退格。离屏回调、粘贴或截图不能代替真实输入。
- 登录、长文编辑和评论输入须能滚到键盘及候选栏上方。核验键盘收放和横竖屏切换后的安全区、末行、选区、草稿与阅读位置，不整体抬升顶栏、裁切正文或留下重复底距。
- 创作和评论核验使用隔离合成数据，不提交内容或发送评论；登录页只输入测试数字，不发送验证码、不登录。缺少输入环境或合成数据时记录未验证。

## 平台能力与使用入口

- **阅读与翻页**：使用 HRM 渲染。「阅读与排版 → 翻页」提供距离、参考线、首尾切换回答和默认关闭的悬浮按钮，按钮避让安全区与软件键盘；回答切换复用共享手势。外接键盘可用方向键、Page Up/Down、空格和 Shift+空格翻页，Home/End 到首尾；只在当前阅读页且未编辑、未打开覆盖层时处理。音量键保留系统用途。键盘接线的验收状态见[平台能力与边界](../docs/platform-capabilities.md#ios)。
- **朗读与语音**：阅读页「朗读」通过 `AVSpeechSynthesizer` 接入共享播放器的队列、暂停/继续、进度与语速控制，音色取决于系统。语音评论使用 `AVAudioRecorder` 生成 AAC，需麦克风许可；录音、播放和 TTS 由音频会话统一协调占用及中断，后台行为仍受 iOS 生命周期限制。
- **导出与分享**：阅读页提供 HTML、PDF、PNG 长图及带评论图片导出。PDF 由 WebKit 生成保留文字的连续单页，不是 A4 分页。PNG 通过 Photos 写入照片图库，需添加照片权限；HTML、PDF 等文件通过 Files 文档导出选择器保存。「导航与交互 → 分享操作」使用 `UIActivityViewController` 分享真实文件附件、链接或 Markdown 文本，iPad 使用当前窗口内的 popover 锚点。复制链接走剪贴板，取消或拒绝权限不会报成保存成功。
- **通知与网页**：本地系统通知走启动期 delegate 和共享账号检查，不配置 APNs，不保证应用挂起或退出后轮询。普通网页使用 WKWebView，明确选择「在浏览器打开」才外跳。
- **本地数据**：内容与过滤规则由 Room、`BundledSQLiteDriver` 及共享迁移持久化，保存在应用 Documents 下的 `local-content.db` 和 `content-filter.db`。目录不可用时明确失败，不退回临时库或空库。
- **同步与标记**：设置中的 WebDAV 同步使用用户配置的服务；AIGC 标记默认关闭，需主动启用并连接可用的独立服务。客户端能力不代表服务已经部署。
- **离线语义过滤**：Full 在 NLP 屏蔽词设置中提供模型下载、加载、卸载与删除入口，模型就绪后本地执行语义匹配；Lite 不包含 NLP。HanLP 自动抽词仍是未移植功能，与 embedding 推理不同。

PDF、NLP 与外接键盘等接线尚未经过 CI 和运行验收，签名与商店发布状态统一见[平台能力与边界](../docs/platform-capabilities.md#ios)。如果你对此感兴趣，欢迎提交 PR 补充实现与验收。
