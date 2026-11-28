# Markdown 依赖

各库的 Maven 坐标与直接请求版本见[版本目录](../gradle/libs.versions.toml)。提椠的源码来源、固定提交及补丁见[源码锁文件](../gradle/upstream-sources.lock.json)。

- HRM Markdown：官方 Maven 的 `io.github.huarangmeng:markdown-parser`、`markdown-renderer`，包名 `com.hrm.markdown.*`；编译和打包均不替换为本地源码项目。
- HRM LaTeX：`io.github.huarangmeng:latex-parser`、`latex-renderer`，包名 `com.hrm.latex.*`。公式使用 `LatexConfig` 默认的随库 KaTeX 字体，不另下载或覆盖同一字体；提椠数学字体独立配置。
- 提椠：`org.tiqian:markdown-compose` 及配套数学字体，用于 Android Full/Lite 和 JVM。Android 两版共用渲染与选择菜单；Native 使用 HRM，不提供提椠渲染。

不提交库源码副本或生成产物；必要的提椠缺口按 [上游补丁维护](upstream-dependency-patches.md) 处理。目标变体的最终传递依赖以 `dependencyInsight` 为准，确认 HRM 来自官方发布模块；打包补丁记录不代替 Maven 解析结果。

## 属性、事件与导出边界

[MdAst.kt](../shared-domain/src/commonMain/kotlin/com/github/zly2006/zhihu/markdown/MdAst.kt) 将原始块属性、段落 `data-pid` 和图片尺寸保存到 AST；Figure 保留容器属性与带 `img.` 前缀的原图属性。内容源属性仍不可信：宿主事件与导出按用途校验，区分原文和应用注入标记，不因属性透传而执行脚本。容量与安全约束以实际 API 和消费路径为准。

## 缓存与布局契约

- AST 保留全文语义，IR 保留全文渲染结构；可见布局缓存保存测量与几何结果，按内容、宽度、字体和主题失效。缓存复用不等于惰性测量。
- 正文使用 `enableScroll=false` 和宿主持有的 `ScrollState`；布局能力须核对该实际入口，不能从内部缓存类推断。
- 全文选择、脚注、图片列表和全局后处理不依赖可见子树。内容替换使旧选择失效；滚离返回仍保留高亮、手柄和完整复制。
- 公式后台解析不等于后台测量；性能判断测到实际布局与绘制完成，方法见 [性能回归审查](../.agents/skills/zhihu-performance-regression-review/SKILL.md)。

## 更新

核对官方根构件、平台构件及 metadata 后更新版本目录，移除所选版本已包含的补丁，不混入同包名分叉。阅读、编辑、导出和段评共用 AST，更新时一起核对 parser、渲染器、提椠适配与平台 actual 的相关行为和字体绘制。
