# 上游补丁维护

优先使用官方 Maven 和公开 API；已确认的必要缺口才从所选官方源码重放最小补丁。HRM 始终使用官方发布构件。提椠的来源、固定提交、补丁校验和、原始 PR 与退出条件集中在 [lock](../gradle/upstream-sources.lock.json)，不依赖私有 Maven 制品。

## 构建入口

准备程序为 `gradle/prepare-upstream-sources.py`，需要 Python 3.11+ 和 Git；直接请求版本见 [版本目录](../gradle/libs.versions.toml)，补丁文件在 `gradle/upstream-patches/`。

- APK 与桌面打包自动启用补丁；依赖未发布接口的独立编译和测试加 `-PzhihuUpstreamPatches=true`。普通编译、测试和依赖检查可用 `-PzhihuUpstreamPatches=false`，不准备源码或补丁资产，但打包不允许此模式。
- 补丁模式用 `includeBuild` / `dependencySubstitution` 替换 lock 登记的提椠模块。最终 Maven 版本按目标变体的 `dependencyInsight` 核对；MIUIX 图标的二进制及元数据校验见 `gradle/verification-metadata.xml`，依赖报告不代替制品校验。
- 准备程序从宿主版本目录读取 AGP、Kotlin/Compose 编译器、Compose 插件与库、Material3、backhandler 和 Skiko，适配根插件及宿主消费模块的直接依赖声明，并计入输入指纹与来源记录。Skiko 版本必须与所选 Compose 官方制品匹配；不单独强制 Skiko 或关闭兼容检查。宿主编译/运行图和 included-build 编译图需分别核对；原上游基线测试不替代对齐后消费者的验证，不修改原测试或 golden。
- 新输入需官方 Haxe 工具链。`ZHIHU_TIQIAN_TOOLCHAIN` 指向本地 JSON：`haxe`、`node` 为可执行文件，Windows 便携版可另传 `neko`；`boring`、`reflaxe`、`format`、`formatter` 为 lock 的 `sources.tiqian-core.generation.tools` 对应干净源码目录（不含 `.git`）。程序校验 Haxe 版本、工具摘要和上游 pin，再执行官方 `gen kotlin-f32`，保留生产源码、原测试和发布工程。
- 直接调用使用 `python3 -I gradle/prepare-upstream-sources.py --package --gradle '<Gradle 可执行文件>' --tiqian-toolchain '<工具链 JSON>'`（Windows 可用 `python`）。Gradle 构建会提供当前 Gradle；`--offline` 只复用匹配的成功输入。

## 输入与校验

源码、工具和生成结果只放在 `.gradle/upstream-inputs/`，不提交工具位置文件、上游树、Maven 仓库或缓存，不修改上游或用户的 Git 索引。

快照使用 lock 的 `snapshotCommit`；未固定的版本解析官方标签或分支并记录实际提交。APK 的 `assets/upstream-patches.json` 与桌面 JAR 的 `upstream-patches.json` 保存提交、补丁状态、源码树和数学资源的相对路径与摘要，不记录机器路径。

复用时校验当前来源记录、打包副本、源码树、字体和元数据。冲突、缺失或摘要不符即失败，不回落到本地制品或隐式重建字体；准备程序和 lock 变更后须先成功准备新输入再离线使用。验证使用当前脚本与源码，不借旧输入绕过失败。Markdown 布局边界见 [依赖说明](markdown-library-dependency.md#缓存与布局契约)。

## 补丁增删

1. 在干净上游基线确认缺陷，提交最小上游修复；将同一补丁的基线、校验和、真实来源和退出条件登记到 lock，用 `git apply --check` 核对。
2. 构建适配与功能补丁分开；补丁作者按实际影响定向运行相关上游原用例和应用消费者，记录范围及结果，不放宽保留断言。应用 CI 不复制上游全量测试、生成器、probe 或非宿主测试编译；上游原测试与来源校验不删，职责见 [测试维护职责](testing-maintenance.md)。
3. 所选正式版本已包含修复时升级并移除补丁，核对标签和原测试。工具、网络或接线阻塞如实报告。
