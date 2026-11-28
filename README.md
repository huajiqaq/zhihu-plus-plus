# Zhihu++：注重隐私、互联网个人权利和无广告的知乎客户端

[![GitHub release](https://img.shields.io/github/v/release/zly2006/zhihu-plus-plus)](https://github.com/zly2006/zhihu-plus-plus/releases)

Zhihu++独创本地推荐算法，把内容推荐完全放在本地进行，为您提供和筛选高质量内容。
本地推荐算法完全独立于知乎算法，依赖爬虫运行，可以自由定制各种推荐权重，保证看到自己想看的内容。
我相信，这点绵薄之力可以帮助广大用户从大公司的手中夺回本该属于我们的权利——选择自己的生活，不被算法奴役的权利。

知乎客户端，以 Android 为主，另有实验性桌面版、[浏览器版](webApp/README.md)和 [iOS 宿主](iosApp/README.md)。去广告，去推广软文，去推销带货，去盐选专栏。

支持手机端/网页端/混合等多种推荐方案。界面支持简体中文与 English，可在「外观 → 界面语言」选择跟随系统或指定语言；正文和用户输入保持原文。

可以设置屏蔽词、AI屏蔽回答、屏蔽用户、屏蔽话题，也支持搜索筛选、沉浸式阅读、AIGC 标记和内容创作。

## 应用截图

| 首页 | 关注 | 日报 | 个人主页 | 文章 |
| --- | --- | --- | --- | --- |
| ![首页截图](fastlane/metadata/android/zh-CN/images/phoneScreenshots/1_home.jpg) | ![关注截图](fastlane/metadata/android/zh-CN/images/phoneScreenshots/2_follow.jpg) | ![日报截图](fastlane/metadata/android/zh-CN/images/phoneScreenshots/3_daily.jpg) | ![个人主页截图](fastlane/metadata/android/zh-CN/images/phoneScreenshots/4_people.jpg) | ![文章截图](fastlane/metadata/android/zh-CN/images/phoneScreenshots/5_article.jpg) |

<div style="  display: flex; flex-flow: column; max-width: 520px; margin: auto; align-items: center" align="center">
  <img height="320" alt="知乎++在老年机上都能流畅运行" src="https://github.com/user-attachments/assets/e85e899c-d899-4309-8382-2a2aee25a1f9" />
  <div>
    知乎++在老年机上都能流畅运行，照片由 @zeky 提供
  </div>
</div>

## 下载

移动端提供 Lite / Full：Android 与 iOS 的 Lite 不包含离线 NLP，功能取舍一致；Full 接入 ONNX 离线语义过滤。PC 默认使用 Full，各端实现与发布状态见[平台能力与边界](docs/platform-capabilities.md)。

[点我下载](https://github.com/zly2006/zhihu-plus-plus/releases)

[下载最新开发版本](https://github.com/zly2006/zhihu-plus-plus/releases/tag/nightly)

[在电脑打开：浏览器扩展使用说明](misc/chrome-open-on-pc/README.md)

## 平台能力

Android、Windows、Linux、macOS、iOS 与 Web 的当前实现、使用前提、未实现功能和待验收项统一见[平台能力与边界](docs/platform-capabilities.md)。如果你对其中的功能感兴趣，欢迎提交 PR 补充完善。

## 功能

- 登录与账号
  - 支持手机验证码登录
  - 支持通过扫码在电脑端登录
  - 支持手动设置 Cookie 登录
- 信息流与推荐
  - 首页推荐支持 Web / 安卓 / 本地 / 混合模式
  - 支持切换 **登录状态 / 非登录状态** 下的推荐，防止信息茧房
  - 支持关注页（推荐/动态，可显示动态来源说明）、热榜、知乎日报、搜索（含热搜、历史、排序/类型/时间筛选）
  - 支持智能内容过滤、质量过滤、反向屏蔽、过滤统计与屏蔽记录
  - **支持屏蔽知乎盐选付费内容**
- 内容浏览
  - 阅读回答
  - 阅读文章
  - 浏览问题详情页（排序、关注、日志、分享、评论）
  - 浏览想法（Pin）详情页（点赞、评论、分享、话题、投票）
  - 浏览收藏夹及收藏夹内容
  - 支持「在电脑打开」：发送到私密收藏夹，用浏览器扩展直接打开最新一条（[说明](misc/chrome-open-on-pc/README.md)）
  - 在用户主页内搜索 TA 的创作
  - 历史记录（在线历史 + 本地历史，支持删除）
  - 展示知乎官方认证徽章
  - 应用内播放知乎视频
- 阅读
  - 朗读内容（听文章 / 听回答）
  - 回答页长按保存图片 **无水印**
  - 回答切换手势（上下/左右切换）与”下一个回答”按钮
  - 沉浸式阅读，可隐藏回答区干扰元素
  - AI 总结内容
  - **导出内容**（图片 / Markdown / HTML / PDF；PDF 的文件导出或打印方式依平台而异）
  - **支持导出整个收藏夹**
  - 内容划线高亮
  - 图片查看器支持动图（GIF）与多图滑动切换
  - 数学公式渲染（LaTeX，内置 KaTeX 字体，可离线使用）
  - 支持调节正文段间距
  - 可拖动滚动条、上划/下划自动隐藏/显示操作按钮
- 内容创作
  - Android 端支持写回答、编辑已有回答、保存草稿和发布回答
  - 写回答支持 Markdown 编辑、预览和插入图片
- 社区互动
  - 支持查看个人主页（含关注订阅板块）、关注/拉黑用户、屏蔽推荐
  - 支持查看回答赞同者列表，以及关注的人赞同
  - 评论区（含子评论、回复、点赞、按时间排序）
  - 通知（支持分类、红点设置、全部标记已读、自动标记已读与通知筛选）
  - 可记名标记疑似 AIGC 内容，并查看有效标记和投票人
  - 表情包
    - 经典表情`[惊喜]` <img src="misc/emojis/emoji_1114211280118018048.png" height="18" style="diSplay:inline">
- 屏蔽系统
  - 屏蔽词（支持正则表达式）
  - NLP 屏蔽词（基于 embedding 和向量相似度匹配，不包含在移动端 Lite 中；各端接入状态见[平台能力与边界](docs/platform-capabilities.md)）
  - 屏蔽用户（含评论、问题回答等场景）
  - 屏蔽话题（含想法流）
  - **导出屏蔽词** & **导入屏蔽词**（支持跨设备迁移）
  - 屏蔽历史记录
- 其他
  - 支持 zse96 v2 网页请求签名
  - 支持模拟安卓端 API 调用
  - 支持 Deep Link 与剪贴板链接识别跳转
  - 支持二维码扫码结果展示和复制，可用于提取网址、Wi-Fi 密码等信息
  - 主界面支持横滑切换标签页
  - 防沉迷提醒
  - 支持自定义初始页面
  - 双击操作快速 **点赞** 或 **打开评论区**
  - 点击底部导航栏回到顶部/刷新

### 遥测

若您同意（可以在设置关闭），本应用会收集一些匿名的数据，用来统计使用量。您可以随时拒绝遥测，这不影响任何功能的使用。我们收集的数据如下：

- 应用启动次数
- 应用启动时间
- 您的IP地址
- 经过SHA256匿名化后的知乎账号ID（如果您登录了的话）

除此之外，我们不会收集任何其他数据，包括但不限于您的浏览记录、推荐算法的输入输出、屏蔽词列表等。

### 其他客户端

如果对其他知乎客户端感兴趣，这些客户端都不需要你root即可使用，也欢迎尝试：

- [Hydrogen](https://github.com/zhihulite/Hydrogen)
- [Zhihu--](https://github.com/huamurui/zhihu-minus-minus)
- [Zhihu++ swift版（iOS）](https://github.com/kangyun1994/zhihu-plus-plus-swift)
- [Zhihu++ HMOS （鸿蒙版）](https://github.com/zhuoyi233/zhihu-plus-plus-HMOS)

注意：这里的所有项目与知乎++无关，在此列出不代表其实得到了知乎++的支持或背书。

## 开发构建

构建统一使用正式版 Eclipse Temurin JDK 25：将 `JAVA_HOME` 和 IDE 的 Gradle JDK 指向同一完整 JDK 安装。[Daemon 配置](gradle/gradle-daemon-jvm.properties)固定主版本 25，不配置 daemon 自动下载；Java/Kotlin 编译工具链及 JVM 字节码目标由[版本目录](gradle/libs.versions.toml)的 `jvm` 统一为 25。Android 仍由 D8/R8 转为 DEX，`minSdk` 不因 JDK 升级而改变，也不表示 Android 提供全部 Java 25 标准库 API。桌面的运行、JNI 编译和 Compose 打包工具使用 daemon 所在 JDK，裁剪后的 Java 25 运行时随包分发，用户无需另装 Java。打包另需 Python 3.11+、Git 和可用的上游输入。Android 需项目指定的 SDK，Full 另需 NDK 与 Rust 工具链；Web 使用 Node.js 24（含 npm），Apple 目标需 macOS 与兼容的 Xcode。具体工具版本以[构建工作流](.github/workflows/other-branches-test.yml)为准。

首次构建新的提椠版本时，先按[上游补丁维护](docs/upstream-dependency-patches.md#构建入口)配置 `ZHIHU_TIQIAN_TOOLCHAIN`。相关任务生成引擎、字体并重放必要补丁；HRM 直接使用官方 Maven 构件。Web 不使用提椠，按[Web 构建说明](webApp/README.md#启动)关闭上游补丁准备。离线只能复用已准备且校验完整的同版本输入；缺失、损坏或补丁冲突都会失败，缓存与生成产物不提交。

### 本地入口

在仓库根目录执行，Windows 将 `./gradlew` 换为 `gradlew.bat`；桌面包在对应操作系统上构建。

| 目标 | 入口 |
| --- | --- |
| Android Lite | `./gradlew :app:assembleLiteDebug -PzhihuAndroidVariant=lite` |
| Android Full | `./gradlew :app:assembleFullDebug -PzhihuAndroidVariant=full`；Full/Lite [分开构建](docs/kmp-android-variants.md) |
| JVM 桌面应用目录 | `./gradlew :desktopApp:createDistributable`；Windows MSI 输入使用 `:desktopApp:createReleaseDistributable` |
| macOS 原生 Debug 应用 | `./gradlew :macosApp:packageDebugMacosApp` |
| Web 开发服务器 / 静态分发 | [启动与生产构建](webApp/README.md#启动) |
| iOS 设备 / 模拟器 | [Xcode 工程与构建命令](iosApp/README.md#构建) |

### 打包与纯构建 CI

- **Windows MSI**：使用 [.github/scripts/package-windows-msi.ps1](.github/scripts/package-windows-msi.ps1)，传入 `-AppImage` 和新的 `-OutputDirectory`，并安装 WiX 3。脚本默认使用 `JAVA_HOME` 指向的同一完整 Temurin JDK 25，也可用 `-JavaHome` 显式指定。脚本基于该 JDK 的官方 `jpackage` 模板生成 per-user 安装包，并纳入通知注册与开始菜单入口。普通 `packageMsi` 不能代替此注册流程。MSIX 使用 [package-msix.ps1](desktopApp/windows/package-msix.ps1)，同样使用该 JDK 编译 JNI 启动器；Build Check 只产出 unsigned 包，不创建或信任证书。
- **Linux AppImage**：使用 [.github/scripts/package-linux-appimage.py](.github/scripts/package-linux-appimage.py)，传入 `--app-image`、`--icon` 和新的 `--output`。脚本打包内置 JRE 的应用目录及朗读客户端依赖，保留许可证，不安装或启动宿主的语音服务。
- **Apple**：macOS 原生与 JVM 应用分别归档；JVM CI 包只有 ad-hoc 签名，不代表已公证。iOS 设备包为 unsigned IPA，模拟器包不是设备安装包。
- **Web**：分发完整静态目录、字体、按需编解码器及第三方许可证；站点部署与编译分开，见 [Web 分发说明](webApp/README.md#生产构建与-github-pages)。

在 Actions 手动运行 **Build Check**，选择 `all` 或所需目标；[目标与职责](docs/testing-maintenance.md#手动构建与打包)说明了各包型。该流程仅构建、检查归档并上传 Actions 制品，不执行回归或发布 Release / Pages；既有 PR 门禁不因此取消。构建与运行结论必须以各任务终态和实际验收为准。

## 参与开发

协作规则见 [CLAUDE.md](CLAUDE.md)，提交 PR 时使用 [PR 模板](.github/PULL_REQUEST_TEMPLATE.md)。

## 贡献者

感谢所有为 Zhihu++ 做出贡献的开发者与用户，正是你们让这个项目持续变得更好。

[![Contributors](https://ghcontrib.pages.dev/image?repo=zly2006/zhihu-plus-plus)](https://github.com/zly2006/zhihu-plus-plus/graphs/contributors)
