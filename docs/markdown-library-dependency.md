# Markdown 依赖

版本与坐标以 [版本目录](../gradle/libs.versions.toml) 为准：

- HRM Markdown：`io.github.huarangmeng:markdown-parser`、`markdown-renderer`；代码使用 `com.hrm.markdown.*`。
- HRM LaTeX：`io.github.huarangmeng:latex-parser`、`latex-renderer`；代码使用 `com.hrm.latex.*`。
- 提椠：`org.tiqian:markdown-compose` 及配套数学字体，供对应平台源集使用。

应用不保留这些库的源码副本。未发布 API 通过 [上游补丁模式](upstream-dependency-patches.md) 接入；准备后的源码和生成产物只在忽略的缓存目录中。

## 更新

先在独立上游仓库验证修复和平台构件。正式版本发布后，从公共仓库核对根构件、平台构件及 metadata，再更新版本目录并移除已包含的补丁。不要混入旧分叉坐标，避免同包名重复类。

阅读、编辑、导出和段评共用 AST。更新时同时核对 parser、渲染器、提椠适配及平台 actual，选择已有相关测试和必要构建；依赖下载成功不等于行为通过。
