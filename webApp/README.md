# 浏览器版

通过 Kotlin/Wasm 运行共享 `ZhihuMain`、导航、阅读和评论页面。使用支持 WebAssembly GC 的现代浏览器；这是实验性宿主，不是 Android APK 的网页包装。当前限制、未实现功能和验收状态见[平台能力与边界](../docs/platform-capabilities.md#web)。

## 启动

支持两个入口，请根据是否自行启动开发服务器选择：

- 本地：<http://127.0.0.1:8080/>，或 `http://127.0.0.1:8080/index.html`。
- GitHub Pages：<https://zly2006.github.io/zhihu-plus-plus/>，或 `https://zly2006.github.io/zhihu-plus-plus/index.html`；需先完成下述部署，源码构建本身不会发布网站。

本地开发使用 Node.js 24（含 npm）和 [Temurin JDK 25](../README.md#开发构建)，再在仓库根目录执行（Windows 使用 `gradlew.bat`）：

```sh
./gradlew -PzhihuUpstreamPatches=false -Pkotlin.daemon.jvmargs=-Xmx4g :webApp:wasmJsBrowserDevelopmentRun
```

开发服务器仅监听 `127.0.0.1:8080`。Node/npm 使用本机工具链；生产优化所需 Binaryen 由 Kotlin 插件从 settings 中限定的官方发行源取得。

### 生产构建与 GitHub Pages

```sh
./gradlew -PzhihuUpstreamPatches=false -Pkotlin.daemon.jvmargs=-Xmx4g :webApp:wasmJsBrowserDistribution
```

将 **`webApp/build/dist/wasmJs/productionExecutable/` 的完整内容**作为站点分发，包括 HTML、带内容哈希的 JavaScript/Wasm、Compose 资源、导出模板、`web-version.json`、`zhihu-web-bridge.user.js`、`zhihu-notifications-sw.js`、`export/`、`voice-codec/`、`nlp/`、`LICENSE` 和 `aboutlibraries.json`，不要只上传 HTML/JS。页面使用相对资源地址和 hash 路由，可直接部署到 `/zhihu-plus-plus/`，无需改 `base` 或添加 404 路由回退。生产 source map 留在本地构建目录，不随站点发布。

图片导出使用 `html2canvas-pro 2.6.5`，数学公式使用随包分发的 KaTeX CSS 与字体，集合 ZIP 使用 `fflate`。录音转换使用 `@ffmpeg/core 0.12.10` 的单线程构建，在独立 Worker 中按需加载本地 `voice-codec/ffmpeg-core.js` 和 Wasm；核心资源约 32 MB，**不参与首屏预加载**。离线 NLP 的 ONNX Runtime Web 及校验组件放在 `nlp/`，按需加载，模型由用户另行下载。不要遗漏这些静态资源及其原始许可证、FFmpeg 和 ONNX Runtime 第三方声明；应用内许可证清单也会合并对应组件。

首屏在应用下载、编译期间显示原生加载状态，失败可重试；加载完成后由共享 UI 接管。哈希资源可复用浏览器 HTTP 缓存，实际传输体积和缓存时长取决于托管服务的压缩与响应头，不是 Service Worker 离线缓存。

首次启用时，由 `zly2006` 或具备仓库 maintain/admin 权限的维护者，在 GitHub 的 **Settings → Pages → Build and deployment → Source** 选择 **GitHub Actions**；仅有 write 权限未必能修改该设置。之后在 Actions 中手动运行 Pages 发布工作流，选择 `master`。本地构建成功不代表 Pages 已启用或部署成功，应以发布任务结果为准。

部署时完整替换静态分发。生产构建同步生成 `web-version.json` 与 HTML 构建标识；「设置 → 系统与更新 → 检查更新」匿名读取当前站点的版本清单，发现更新后先保存输入、再确认刷新。刷新保留 hash 地址和已保存的网站数据，不自动刷新或清缓存。通知专用 Service Worker 不接管资源更新。

## 页面地址

应用导航同步到可读的 hash 地址。复制地址或刷新可恢复对应页面与主页签，例如 `#/settings/appearance?tab=daily`、`#/answer/23?tab=follow&from=question%2F12`。只有已提交的搜索进入地址，不把输入草稿、账号、缓存正文或页面访问 ID 写入 URL。浏览器前进/后退与应用返回使用同一导航状态。

已支持的知乎业务链接保留在应用内；原生 App 本应独立打开的网页，在浏览器里打开新标签页，不改变原页面的 hash 或前进/后退历史。弹窗被拦截时可手动重试。私信、任意外部网页及内部交接不生成可分享路由；无效地址会提示并返回默认入口。

## 独立请求桥

浏览器不能直接发送原生客户端的 Cookie、UA 或绕过跨域策略。知乎业务通过单独安装的 [油猴脚本](../misc/zhihu-web-bridge/zhihu-web-bridge.user.js)完成具名读取、互动、创作和上传，分发包也带有同名文件；它不是任意 URL 代理。

脚本安装地址（对应服务启动或部署后可用）：

- Pages：<https://zly2006.github.io/zhihu-plus-plus/zhihu-web-bridge.user.js>
- 本地：<http://127.0.0.1:8080/zhihu-web-bridge.user.js>

使用同一源码构建的完整 Web 分发及其配套脚本，保留脚本的 `@sandbox DOM` 设置。只启用一个脚本实例，安装后重新加载应用页面；不要混用不配套的页面与脚本。

1. 使用更新版 Tampermonkey 安装脚本；5.4 是安全重定向控制的最低门槛，不代表所有后续版本的 Cookie 行为均已验证。运行时只允许上述本地与 Pages 的顶层根入口及各自的 `index.html`，拒绝带用户名/密码的 URL、iframe、其他端口或路径；hash 导航不受影响。授权确认和油猴菜单均标出当前站点，请核对后再操作。知乎域上的精确匹配仅用于权限检查，脚本不在官网安装应用桥监听器。
2. 打开「登录」。扫码方式需本人在知乎 App 确认，再在本标签页确认使用此账号。二维码在本机绘制，只有本次登录签发的 Cookie 可以由油猴写入。不支持安全设置 `HttpOnly` Cookie 或不能安全展示扫码链接时，使用「备用网页」在知乎官网完成登录（包括短信），回来点击「我已授权」核对基本资料。已有官网会话无需重新设置 Cookie。
3. 遇到人机验证时，点「打开知乎验证」，手动完成官方 `unhuman` 页面，再回到弹窗点「我已授权」。只有知乎确认通过才关闭提示，原操作需自行重试。普通 403 不会冒充人机验证。

确认扫码写入会同时登录知乎官网。若此时关闭本标签页或复核失败，应用页面不会认定登录成功；已提交给浏览器的 Cookie 写入不能原子撤回，可在官网检查或退出当前账号。

**不要同时在本地版和 Pages 版扫码写入，也不要同时在知乎官网登录或切换账号。** 现有 Web Locks 只能协调同 origin 的标签页，本地 `http://127.0.0.1:8080` 与 Pages `https://zly2006.github.io` 的锁不共享，官网也不受该锁保护。写入前后的 Cookie 和身份检查不能原子阻止异源并发覆盖；这不是跨站点锁。

读取、互动、创作和上传统一使用唯一具名请求桥，按能力域独立授权。在应用授权提示或油猴菜单中选择所需能力；登录只核对基本资料，**不会自动授予写权限**。公共内容仍匿名读取，浏览历史读取另行授权并核对官网与历史 API 为同一账号。

| 授权域 | 用途 |
| --- | --- |
| `private` | 个人列表、关注、收藏、草稿、通知及私信等私有读取 |
| `interact` | 点赞、收藏、关注、屏蔽、投票、评论、发送私信及通知已读 |
| `create` | 创建、编辑、保存和发布内容，以及图片、语音上传 |
| `history` | 添加、删除、清空浏览历史及修改记录开关 |

授权仅对当前标签页的已验证账号有效，可由油猴菜单撤销，换账号或离开页面后失效；授权期间不会为每次写操作再次弹出桥权限确认，操作可能不可撤销。桥只接受白名单具名操作与受限参数，不接受页面任意指定 URL、请求头或认证 Cookie，不自动跟随重定向。Cookie、Token、扫码票据和 OSS 上传密钥都留在桥内，页面只收到业务结果。媒体的 **32 MiB 是客户端内存与上传预算，不是知乎服务端限制**。

登录后核对返回的账号身份；脚本安装成功不代表登录或业务操作成功。真实 Tampermonkey 端到端验收状态见[平台能力与边界](../docs/platform-capabilities.md#web)。

入口路径检查是防误调用，不是浏览器的安全隔离：同属 `https://zly2006.github.io` 的其他 Pages 项目与此站同 origin，可共享存储并可能通过同源窗口或 History API 相互影响。不要把 `/zhihu-plus-plus/` 当成独立安全来源，只在信任该 origin 内容时授权。路径变化后旧请求不会发布资料或授权结果；若改变了 pathname，请重新加载受支持入口。

网页图像使用独立的普通浏览器请求，不经过账号桥；受来源跨域策略限制的图像会显示加载失败。

## 导出与分享

- 从阅读页导出 HTML、PNG 长图或带评论图片，从收藏夹入口导出集合 ZIP。HTML 复用共享正文清洗与模板，图片优先匿名内嵌；无法跨域读取时保留安全原链接并提示需联网图片。长图中的受限图片可能显示占位，须留意缺图提示，不能保证跨域图片完整离线保存。
- 图片与导出文件可请求浏览器下载；支持 Web Share 文件分享的浏览器会传递真实 `File`，不是把文件路径或下载链接冒充附件。分享需用户点击并由浏览器支持，不支持时使用下载入口。
- “已请求下载”不代表浏览器已将文件保存到磁盘，请查看浏览器下载记录。字体、资源或画布处理失败会报告错误。
- PDF 入口准备保留文字排版的文档，再由用户点击打开浏览器打印界面；支持时选择“另存为 PDF”。应用只报告已打开打印界面，不能判断最终保存或取消，也不能读取或直接分享另存后的 PDF。

## 语音与通知

- 阅读页「朗读」接入 Web Speech 和完整共享播放器，包括队列、暂停/继续、进度及语速控制；可用音色由浏览器与系统提供。
- 语音评论通过 `MediaRecorder` 录音，按需使用本地 FFmpeg Worker 转换为 AAC 后再经桥上传。需要 HTTPS 或本地可信入口、麦克风权限及对应桥授权。录音、语音播放与 TTS 共用音频占用协调，不能把互斥或中断当成后台同时播放。
- 页面关闭、系统休眠或浏览器后台节流会影响朗读、录音和通知轮询；不保证后台连续运行。
- 在通知设置中主动启用并授予浏览器许可；只在页面可见时轮询。`zhihu-notifications-sw.js` 承接页面投递的通知及点击恢复，点击后核对账号并导航。关闭页面后不再获取新通知；后台与 Web Push 范围见[平台能力与边界](../docs/platform-capabilities.md#web)。

## WebDAV 与 AIGC

- 在设置中的「WebDAV 同步」填写连接信息；请求使用标准 `fetch`，不经过知乎桥。服务必须支持当前站点来源、所用方法与认证头的 CORS；远程地址需 HTTPS。连接信息与凭据仅保留于页面状态，不写入浏览器持久存储，重开后需重新输入。
- AIGC 标记默认关闭，由用户主动开启；客户端使用独立服务而非知乎凭据。使用前须确认服务可达并允许当前站点的 CORS；[服务端部署状态](../docs/platform-capabilities.md#web)与客户端构建独立。

## 存储与平台差异

- 非敏感设置、本地历史及缓存保存在当前来源的浏览器存储中；过滤规则和本地内容库使用 IndexedDB。清理网站数据会清空这些本地记录。
- 网页阅读使用有 Web 制品的共享 HRM 渲染，不提供仅 Android/JVM 发布的提椠引擎。「外观 → 界面皮肤」支持 Material 与 Miuix，偏好保存在当前浏览器来源；移动端 Android Lite 与 iOS Lite 仅提供 Material 皮肤。
- 支持浏览器前进/后退、文件选择、剪贴板、共享翻页控件及回答切换手势；不显示 Android 音量键翻页设置。图片查看支持缩放、平移、双击、导航按钮和键盘操作。
- 在 NLP 屏蔽词设置中主动下载并加载模型，状态就绪后执行本地语义匹配；可卸载运行中的模型或删除下载文件。模型缓存、浏览器要求与尚未移植的自动抽词见[平台能力与边界](../docs/platform-capabilities.md#web)。
- Web 不是原生后台常驻应用，也不提供 APK 安装。请求桥缺失、未授权、登录过期和网络失败分别处理，不将未支持请求伪装成成功。

以上说明实现和运行条件，不代表已通过浏览器端到端验收。源码与完整静态分发遵循[仓库许可证](../LICENSE)，第三方组件保留各自许可。
