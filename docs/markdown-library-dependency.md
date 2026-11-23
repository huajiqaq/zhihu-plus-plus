# Markdown 依赖

公共坐标、直接依赖请求及源码准备的所选版本以 [版本目录](../gradle/libs.versions.toml) 为准；最终运行时解析结果不能只由版本目录证明：

- HRM Markdown：直接使用官方 Maven 的 `io.github.huarangmeng:markdown-parser`、`markdown-renderer`；代码使用 `com.hrm.markdown.*`。普通编译和打包均不替换为本地源码项目。
- HRM LaTeX：`io.github.huarangmeng:latex-parser`、`latex-renderer`；代码使用 `com.hrm.latex.*`。
- 提椠：`org.tiqian:markdown-compose` 及配套数学字体，供对应平台源集使用。Android Full/Lite 共用 `androidMain` 的渲染和选择菜单实现；皮肤版本隔离不再决定是否提供提椠排版。

应用不保留这些库的源码副本。优先使用所选版本的官方公开 API；提椠仍缺少必要能力时，才通过 [上游补丁模式](upstream-dependency-patches.md) 接入有依据的最小补丁。准备后的源码和生成产物只在忽略的缓存目录中。

Markdown `1.7.1` 的发布依赖请求 LaTeX `1.7.1`；版本目录保留的 `1.6.3` 条目不代表运行时一定使用它。通过目标变体的 `dependencyInsight` 确认 HRM 解析为官方发布模块，而不是 composite 项目；打包产物中的 `assets/upstream-patches.json`（APK）或 `upstream-patches.json`（桌面 JAR）只记录实际准备的必要补丁输入，不能代替 Maven 构件解析结果。

HRM 公式通过官方 `LatexConfig` 默认加载随库发布的 KaTeX 字体。Full/Lite 不另下载同一字体或覆盖默认字体角色；打包核对真实字体资产，设备验证异步加载后实际绘制而非系统字体兜底。提椠数学字体选择仍由其正式配置独立管理。

## 属性、事件与导出边界

HTML 转换入口 [MdAst.kt](../shared-domain/src/commonMain/kotlin/com/github/zly2006/zhihu/markdown/MdAst.kt) 将原始块属性、段落 `data-pid` 和图片尺寸等保存到正式 AST；Figure 同时保留容器和带 `img.` 前缀的原图属性。它们仍是来自内容源的数据，属性透传不等于允许执行脚本或信任事件参数；宿主处理和导出须按用途校验，区分原文与应用注入的选区、事件标记。

属性容量和安全保证以所选上游 API、真实消费路径及相应验证为准，不能把旧候选计划中的 1KB 限制或预期策略当成现版本已经实现的约束。保留仍成立的协议与安全说明，不恢复已废弃的实施路线或重复侧表。

## 缓存与布局契约

完整 AST 保存解析与全局语义，完整 IR 保存全文渲染结构；两者的复用不等于屏外内容已延迟测量。可见布局缓存保存当前宽度、字体、主题等环境下的测量与几何结果，须按环境失效。三层缓存各有所有者和生命周期，不能相互当成惰性调度的证明。

当前 HRM Markdown `1.7.1` 的外部滚动入口 `enableScroll=false` 选择 `StaticColumn`，通过 `EagerMarkdownLayoutSource` 全量布局；内部存在块缓存或 `layoutSession` 不代表该入口可用惰性布局。能力须核对当前正式 API 与实际入口，不能从缓存类推断或以候选实现代替。

- 保留完整 AST/IR，使脚注、图片列表、全文选择和全局后处理不依赖当前可见子树。未来只有真实性能测量证明需要时才采用惰性布局；届时覆盖嵌套容器、实测高度复用及内容和环境失效。
- 公式仅后台解析仍可能在 UI 线程同步测量，`yield()` 也不会移动这段工作。核对受限后台准备及文档级有界缓存的实际覆盖，按输入、字体与布局环境失效；不能仅凭缓存类存在判定通过。
- 文档级选择源与可见几何分别管理生命周期，内容替换须使旧选择失效。滚离后返回须恢复可见高亮与手柄，并通过真实可见工具栏完整复制；不要求屏外节点销毁或点击不可见菜单。未来引入回收时仍须保留同一选择源与复制语义，脚注往返须显示真实目标。

按 [性能回归审查](../.agents/skills/zhihu-performance-regression-review/SKILL.md) 分阶段测到实际布局与绘制完成，并用 [公式语料](../shared-domain/src/jvmTest/resources/zhihu-formula-corpus/formulas.json) 验证真实像素、行内尺寸、滚离返回、链接、长容器尾部、脚注往返及完整选区。长正文首帧性能仍须测量，缓存复用或源码能力不是当前行为已通过的证据。

## 更新

先在独立上游仓库验证修复和平台构件。正式版本发布后，从公共仓库核对根构件、平台构件及 metadata，再更新版本目录并移除已包含的补丁。不要混入旧分叉坐标，避免同包名重复类。

阅读、编辑、导出和段评共用 AST。更新时同时核对 parser、渲染器、提椠适配及平台 actual，选择已有相关测试和必要构建；依赖下载成功不等于行为通过。
