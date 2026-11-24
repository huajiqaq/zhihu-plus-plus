# UI 开发指南

改 UI 前先确认页面入口、状态所有者和复用组件；文档不代替源码。

## 代码入口

- [路由与参数](../shared-core/src/commonMain/kotlin/com/github/zly2006/zhihu/navigation/NavDestination.kt)、[目的地分类](../shared-core/src/commonMain/kotlin/com/github/zly2006/zhihu/navigation/NavDestinationClassification.kt)。
- [主壳](../shared/src/commonMain/kotlin/com/github/zly2006/zhihu/ui/ZhihuMain.kt)：主栈、详情栈、主 pager 和分栏；各平台复用 `ZhihuMainNavigationCoordinator`，宿主接入窗口、通知和生命周期。
- 功能模块注册业务路由：[搜索](../shared-feature-feed/src/commonMain/kotlin/com/github/zly2006/zhihu/navigation/FeedNavEntries.kt)、[内容与创作](../shared-feature-content/src/commonMain/kotlin/com/github/zly2006/zhihu/navigation/ContentNavEntries.kt)、[账号、设置与通知](../shared-feature-settings/src/commonMain/kotlin/com/github/zly2006/zhihu/navigation/SettingsNavEntries.kt)。
- [共享组件](../shared-ui-components/src/commonMain/kotlin/com/github/zly2006/zhihu/ui/components/)、[设置页面](../shared-feature-settings/src/commonMain/kotlin/com/github/zly2006/zhihu/ui/subscreens/)、[正文渲染](../shared-ui-base/src/commonMain/kotlin/com/github/zly2006/zhihu/markdown/)。

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
- 点开链接用 `resolveNavigationUrl`：已支持的知乎内容进原生页，其他 HTTP(S) 链接进 `WebPage`。明确要求网页视图时直接用 `rememberWebViewUrlOpener`；只有「在浏览器打开」使用系统浏览器。被动识别剪贴板仍用 `resolveContent`，不要自动打开普通网页。
- 返回沿用现有宿主。详情、弹层和主 tab 的返回优先级分别核对，不叠加会重复退栈的 handler。
- 大屏按实际栏位和布局边界处理转场，不能裁切或压暗另一栏；窄宽切换保留阅读实例与来源上下文。
- 分隔线拖动时预览栏宽，松手后保存 `landscape_list_pane_ratio`。窗口变化按当前可用宽度换算，不把单栏或窄窗口的临时宽度写回设置。
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
- 提椠使用 `tiqian_markdown`、`tiqian_math_font`。主壳初始化时仅在正式键缺失且旧 `duo3_*` 键存在时迁入；两套键并存以正式键为准，不删除旧键或用默认值覆盖显式选择。
- 账号设置按“账号与安全／显示与阅读／内容与隐私／系统／关于”分组，账号与登录维护不依赖开发者模式。手机底栏默认主页、关注、日报；历史从账号面板进入，不占手机底栏位置。其余可选项目保留用户选择，没有主页时须保留账号设置出口。

## 皮肤与共享组件

- Lite 只编译 Material 3 Expressive 主题，但保留 miuix-nav 和共享玻璃。Full、JVM 和 Apple 目标保留双皮肤；Lite 读取旧 `MIUIX` 偏好时降级显示，不删除用户偏好。
- Miuix 实现放在 `miuixThemeMain`，Lite 提供对应 actual。共享页面通过 `App*` 和 `LocalAppSkinTokens` 取控件、颜色与字阶，不直接依赖 miuix theme/basic/popup。
- API 与平台能力以版本目录、上游 lock 及源码为准；参考镜像保留出处与许可，不当作运行实现。
- 主题根的组合位置保持不变，换肤只更新值，避免重建导航和浮层状态。两套皮肤共用业务状态，不复制页面或 ViewModel。
- Miuix 的「HyperOS 风格」通过独立 `miuixStyle` 偏好选择 HyperOS 3 / 4，默认 HyperOS 3。明暗、配色与玻璃仍独立；关闭玻璃或设备不支持渲染时保留可用控件，不改写已保存的风格。HyperOS 4 在共享呈现入口采用已发布玻璃组件；复杂标题、复合菜单及导航标签不能为更换材质而丢失。
- Miuix 容器中的 Material `Text`/`Icon` 需要在容器内容作用域桥接内容色；只改页面颜色不能修复按钮或卡片中的前景色。Markdown 等 Material 路径也要使用投影后的配色。
- 形状、字阶和动效优先采用原生 token；共享固定尺寸使用 `AppDimens`。无实际布局或状态差异时，不增加 index、圆角或颜色参数。
- 顶栏复用 `AppTopBar`、`AppCompactTopBar`、`AppPageTitleBar`，通过 `onBack` 和 `backTag` 提供返回。保留已有 tag；Miuix 不提供逐项 tag 的原生控件按容器和文字定位，不加透明影子点击项。
- 分组标题与行内前导容器对齐，前导图标使用共享尺寸；底部滑杆和分段选择占满可用宽度。大字号放不下时允许增高或换成单选列表。
- 单值选择使用现有原生选择菜单；带独立删除动作的复合菜单不强改成单选菜单。长菜单可滚动，末项必须可达。

## 状态式输入与控件

- `AppTextField`、`AppSearchField` 直接接收所有者持有的 `TextFieldState`，不在控件内复制 String 状态。输入过滤仅修改 `TextFieldBuffer`；搜索、字段联动和保存等业务副作用由所有者处理。提交直接读取当前状态，不等待观察器回写第二份文本。
- M3E 表单使用官方 `roundedShape`、`tonalColors()` 与 `TextFieldLabelPosition.Inside` 配套样式；普通输入和安全输入一致，搜索保留胶囊，Miuix 保留原生外观。
- 密码、Token 和 Cookie 使用 `AppSecureTextField`，显隐复用同一状态；不得使用普通输入加遮罩替代安全文本框，也不把敏感内容放进 Saver。Cookie 导入是单行横向滚动，完整粘贴文本仍交给导入校验。
- `AppSlider` 使用 `SliderState` 和明确的变更/完成回调。范围和步数由状态定义，外部设置变更同步到同一状态；即时保存和松手提交不能互换。
- 想法正文在规范 Markdown 话题链接中保存 ID，`OutputTransformation` 只改变显示；选择、删除及撤销使用同一文本状态。预览、草稿与发布从同一快照生成 HTML 和话题列表，不按显示名称重新绑定 ID。系统复制提供可见文字，「复制 Markdown」保留选区中的明确话题链接。旧草稿只在读入时迁移，无法解码或版本未知时不自动覆盖原稿。

## 评论发送

- 发件账号入口使用真实头像和昵称；选择账号不修改全局登录状态或草稿，只在发送时使用所选身份。
- 「默认同步」是设备设置，取消勾选同时关闭默认；段评不发送同步选项。账号绑定的附件不能静默改投其他身份。
- 关联身份切回失败时保留恢复信息，后续恢复不得重发已经成功的评论；错误必须与发送结果区分。

## 玻璃与弹层

- `AppScaffold` 登记当前层的内容采样源；采样前铺不透明底色，避免透明区模糊扩散。浮动控件只采样下方内容，不采样自身。
- 开启玻璃时容器透明，tint 在模糊结果中混合；另画不透明底色会盖住模糊。半径和 tint 取共享值。
- RuntimeShader 不支持或用户关闭效果时退回实心容器，导航文字、选中反馈和手势仍保留。
- 默认导航使用各皮肤的官方图文项；Apple 悬浮底栏在 Full 的 MD3E 与 Lite 共用布局和折射材质，不切换主题。两种样式保留整格 tab 语义及 `nav_tab_*`，重选交给分页宿主处理；底栏不拦截横拖切页，页面仍可左右滑动。
- `AppOverlayHost` 在背景模糊之外承载 popup。确认框使用能覆盖当前 Sheet/Dialog 的窗口级宿主；独立评论窗口内的排序菜单不能投递到下层页面。
- 页面被新路由覆盖时隐藏所属弹层，但不执行 dismiss、不重置草稿和滚动位置；返回后恢复。发布、删除和退出等动作先确认。
- 窗口级底部面板使用 `AppBottomSheet` 与 `rememberBottomSheetState`，通过 `enabledValues` 明确可达状态。录音等业务限制保留 `confirmValueChange`；关闭须在 `hide()` 完成且状态确实隐藏后通知页面，评论返回仍先收键盘、再收表情面板。

## 阅读与页面状态

- 完整标题、作者、正文和操作区共用阅读列，最大宽度 720dp；双栏阅读列靠所在栏起点，单栏居中。图片和悬浮控件按实际 pane 与遮挡范围避让。
- 长标题展开时完整测量，收起状态按组件契约显示。不为新增按钮把原标题或正文改成省略号。
- 加载、失败和成功空态分开。刷新与追加失败保留已有内容；旧请求或异步过滤不能回写新一轮状态。
- 下拉刷新使用业务层的同一加载真值，在刷新回调首次挂起前同步发布刷新状态，避免控件先回弹再进入加载；不另建计时状态镜像。
- 触摸拖拽、鼠标滚轮和键盘滚动按各自输入路径验证，短内容也单独核对 overscroll；框架默认效果与自定义效果只保留一份，不用触摸结果代替桌面输入验收。
- 正文字号、行高、段间距变化不需要重建 AST。WebView 正文仅保留兼容，不接新阅读功能。
- 正文搜索与用户选区独立，使用真实命中行几何和现有 requester 定位，见 [正文搜索](article-search-design.md)。
- 段评创建要求可靠的原始段落和偏移，保留正文格式；选区规则见 [段评选区契约](segment-selection-contract.md)。评论计数按客户端和正文身份隔离，失败不增加计数。
- 段评末端图标不占原文字符，复制、搜索和选区不能泄漏布局占位符。
- 正文格式优先于段评注入。保留加粗、斜体；脚注、链接、图片或公式无法可靠保留时跳过该段高亮，不展平原文来匹配段评。

## 验证与参考

按 [测试维护方法](testing-maintenance.md) 选择最小检查，覆盖受影响的共享消费者、平台 actual、皮肤和变体。布局核对真实截图与语义中的展开/收起、大字号及末项可达性；交互确认目标状态。

设备流程见 [UI 测试](../.agents/skills/ui-test/SKILL.md)，macOS 使用 [后台调试](../.agents/skills/background-ui-debug/SKILL.md)。原生规范查看 [Miuix](https://github.com/compose-miuix-ui/miuix) 和 [Material Components](https://github.com/material-components/material-components-android) 的对应版本；变体见 [KMP Android 构建](kmp-android-variants.md)。
