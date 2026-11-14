# UI 开发指南

改 UI 前先确认页面入口、状态所有者和复用组件；文档不代替源码。

## 代码入口

- [NavDestination.kt](../shared-core/src/commonMain/kotlin/com/github/zly2006/zhihu/navigation/NavDestination.kt)：路由类型和参数。
- [ZhihuMain.kt](../shared/src/commonMain/kotlin/com/github/zly2006/zhihu/ui/ZhihuMain.kt)：route 表、主 pager、底栏和分栏装配。
- Android 的 `MainActivity`、JVM 的 `DesktopZhihuMain`、macOS 的 `MacosZhihuMain`：平台导航和生命周期。
- [共享组件](../shared-ui-components/src/commonMain/kotlin/com/github/zly2006/zhihu/ui/components/)：`AppChrome`、`AppPrimitives`、`SettingItem`、`FeedCard`。
- [设置页面](../shared-feature-settings/src/commonMain/kotlin/com/github/zly2006/zhihu/ui/subscreens/)：设置归属、搜索和直达高亮。
- [正文渲染](../shared-ui-base/src/commonMain/kotlin/com/github/zly2006/zhihu/markdown/)：Markdown、段评、选择和搜索接线。

查调用方时覆盖所有共享模块，不只搜 `shared`：

```bash
rg '<组件名或 preference key>' app shared* desktopApp macosApp -g '*.kt' -g '!**/build/**'
```

## 导航与启动

- 新增目标先检查 `NavDestination` 和宿主的路由注册；页面使用宿主提供的导航入口，不另建返回栈。
- 当前 `NavDestination` 实现 `NavKey`；主栈根为 `MainTabs`，详情栏根为 `EmptyDetail`。`OnlineHistoryRoute` 是普通页面，不是主 pager 页。
- 业务目标相同不意味着同一次进入；保留页面实例、ViewModel、保存状态和返回上下文，不按业务 ID 删除已有实例。
- `bottom_bar_items`、`startDestination` 和账号快捷入口尊重用户保存的选择，不为了迁移改变默认值、顺序或在线历史的进入方式。
- 平台入口继续承担外链解析、历史记录和来源信息。新增 deep link 同时检查 Android intent、剪贴板与桌面入口。
- 返回沿用现有宿主。详情、弹层和主 tab 的返回优先级分别核对，不叠加会重复退栈的 handler。
- 大屏按实际栏位和布局边界处理转场，不能裁切或压暗另一栏；窄宽切换保留阅读实例与来源上下文。
- 桌面窗口只保留框架的返回 dispatcher；焦点控件先处理按键，未消费才转换为返回。真实 ESC 链路在打包应用中验证，离屏 handler 测试不能代替。
- 原生 chrome 只渲染调用侧提供的模型、选中态和动作，不硬编码产品目的地，不用可变回调槽转发。macOS 分组侧栏使用 source-list 语义，保留 Compose 根视图，并在全部 overlay 的共同父布局施加侧栏 inset。
- Android Launcher 为 `SplashActivity`，动画完成后进入 `MainActivity`；外链直接进入主 Activity。部署时解析真实 Launcher，不把直接打开业务页当作启动页验收。

## 设置

| 页面 | 内容 |
| --- | --- |
| 外观 | 皮肤、明暗、配色、AMOLED、玻璃与悬浮控件 |
| 导航与交互 | 启动页、底栏、分栏、返回、分享和搜索行为 |
| 阅读与排版 | 排版预览、字号/行高/段间距、正文渲染、回答切换；电纸书翻页为子页 |
| 朗读与播放 | 朗读预览、内容顺序、时间与评论、队列 |
| 推荐与信息流 | 推荐来源、信息流样式与刷新 |
| 内容过滤 | 质量、去重、屏蔽规则、记录与维护 |

- 复用 `AppSettingsPage` 和 `SettingItem*`。跳转行有箭头；开关和选择行由控件表达状态，整行保持可操作。
- 设置读取使用现有可观察绑定。需确认后保存的输入仍持有草稿，不改成每次按键立即写入。
- 新增或移动设置时核对 key、默认值、运行时读取、父项禁用、设置搜索和 `settingKey` 高亮。目标被隐藏时跳到父项，不跳到空白位置。
- 父项禁用时，行内分段控件和按钮也禁用；不只改说明文字。恢复备份不覆盖本机连接信息和凭据。
- `themeMode`、`uiThemeStyle`、`bottom_bar_items`、`startDestination` 等状态只从权威设置读取，平台 toolbar 不再维护另一份选择。

## 皮肤与共享组件

- Lite 只编译 Material 3 Expressive 主题，但保留 miuix-nav 和共享玻璃。Full、JVM 和 Apple 目标保留双皮肤；Lite 读取旧 `MIUIX` 偏好时降级显示，不删除用户偏好。
- Miuix 实现放在 `miuixThemeMain`，Lite 提供对应 actual。共享页面通过 `App*` 和 `LocalAppSkinTokens` 取控件、颜色与字阶，不直接依赖 miuix theme/basic/popup。
- 主题根的组合位置保持不变，换肤只更新值，避免重建导航和浮层状态。两套皮肤共用业务状态，不复制页面或 ViewModel。
- Miuix 容器中的 Material `Text`/`Icon` 需要在容器内容作用域桥接内容色；只改页面颜色不能修复按钮或卡片中的前景色。Markdown 等 Material 路径也要使用投影后的配色。
- 形状、字阶和动效优先采用原生 token；共享固定尺寸使用 `AppDimens`。不为了模仿参考项目给所有调用点增加 index、圆角或颜色参数。
- 顶栏复用 `AppTopBar`、`AppCompactTopBar`、`AppPageTitleBar`，通过 `onBack` 和 `backTag` 提供返回。保留已有 tag；Miuix 不提供逐项 tag 的原生控件按容器和文字定位，不加透明影子点击项。
- 分组标题与行内前导容器对齐，前导图标使用共享尺寸；底部滑杆和分段选择占满可用宽度。大字号放不下时允许增高或换成单选列表。
- 单值选择使用现有原生选择菜单；带独立删除动作的复合菜单不强改成单选菜单。长菜单可滚动，末项必须可达。

## 玻璃与弹层

- `AppScaffold` 登记当前层的内容采样源；采样前铺不透明底色，避免透明区模糊扩散。浮动控件只采样下方内容，不采样自身。
- 开启玻璃时容器透明，tint 在模糊结果中混合；另画不透明底色会盖住模糊。半径和 tint 取共享值。
- RuntimeShader 不支持或用户关闭效果时退回实心容器，导航文字、选中反馈和手势仍保留。
- 悬浮底栏保留文字、整格 tab 语义和 `nav_tab_*`。点击交给原生项，拖动只在真正切换目标时提交一次，不叠两套手势反馈。
- `AppOverlayHost` 在背景模糊之外承载 popup。确认框使用能覆盖当前 Sheet/Dialog 的窗口级宿主；独立评论窗口内的排序菜单不能投递到下层页面。
- 页面被新路由覆盖时隐藏所属弹层，但不执行 dismiss、不重置草稿和滚动位置；返回后恢复。发布、删除和退出等动作先确认。

## 阅读与页面状态

- 完整标题、作者、正文和操作区共用阅读列；平板最大宽度为 720dp。图片和悬浮控件按实际 pane 与遮挡范围避让，不用物理窗口宽度或固定 FAB 常量推算。
- 长标题展开时完整测量，收起状态按组件契约显示。不为新增按钮把原标题或正文改成省略号。
- 加载、失败和成功空态分开。刷新与追加失败保留已有内容；旧请求或异步过滤不能回写新一轮状态。
- 正文字号、行高、段间距变化不需要重建 AST。WebView 正文仅保留兼容，不接新阅读功能。
- 正文搜索与用户选区独立，使用真实命中行几何和现有 requester 定位，见 [正文搜索](article-search-design.md)。
- 段评创建要求可靠的原始段落和偏移，保留正文格式；选区规则见 [段评选区契约](segment-selection-contract.md)。评论计数按客户端和正文身份隔离，失败不增加计数。
- 段评末端图标不占原文字符，复制、搜索和选区不能泄漏布局占位符。
- 正文格式优先于段评注入。保留加粗、斜体；脚注、链接、图片或公式无法可靠保留时跳过该段高亮，不展平原文来匹配段评。

## 验证

按改动挑选最小检查，不默认展开全页面、全状态矩阵。

- 查共享组件的消费者和平台 actual；涉及两皮肤时分别核对，涉及 Lite 边界时检查对应变体。
- 布局改动看真实截图和语义：展开/收起、短视口或大字号、必要的明暗状态，以及最后一项是否可操作。
- 交互后证明目标状态变化，不能只看命令成功、进程存活或截图非空。没有登录态时不拿登录页冒充目标页。
- 文档和纯注释改动只需检查链接、源码入口和 `git diff --check`。设备流程见 [UI 测试](../.agents/skills/ui-test/SKILL.md)，macOS 使用 [后台调试](../.agents/skills/background-ui-debug/SKILL.md)。
- 原生规范查看 [Miuix](https://github.com/compose-miuix-ui/miuix) 和 [Material Components](https://github.com/material-components/material-components-android) 的对应版本；构建变体见 [KMP Android 变体](kmp-android-variants.md)。
