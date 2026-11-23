# 上游补丁维护

优先使用所选版本的官方 Maven 构件和公开 API。只有官方实现无法满足已确认的必要行为时，才从所选上游源码重放最小补丁；构建方便、目录裁剪或已有补丁历史都不是保留理由。HRM Markdown 使用官方发布构件，提椠补丁仍须逐项说明缺口和退出条件，不依赖私有 Maven 制品。

## 构建入口

唯一准备程序为 `gradle/prepare-upstream-sources.py`，需要 Python 3.11+ 和 Git。源码版本、补丁与字体配置保存在 `gradle/upstream-sources.lock.json`，补丁文件在 `gradle/upstream-patches/`。

- APK 和桌面打包为提椠尚未发布的必要接口自动启用补丁；需要这些接口的独立编译、测试用 `-PzhihuUpstreamPatches=true`。
- `-PzhihuUpstreamPatches=false` 的普通编译、测试与依赖检查直接使用官方 Maven，不运行源码准备程序、不注册补丁资产。该模式不能证明尚缺提椠接口的完整应用可打包。HRM 在两种模式下都使用同版官方构件。
- MIUIX 图标在两种模式下都由 Maven Central 解析。`gradle/verification-metadata.xml` 固定根模块和 Android、桌面模块的 POM、Gradle 模块元数据，以及 Android AAR、桌面 JAR 的 SHA-256；验证二进制须实际解析制品，不能只看依赖报告。
- 准备程序的 `--package` 启用打包补丁，`--offline` 复用上一次成功记录的同版本输入，`--gradle` 指定字体工具所用的 Gradle 可执行文件。
- 新的提椠输入需要从上游 Haxe 源码生成 Kotlin 引擎和测试。将 `ZHIHU_TIQIAN_TOOLCHAIN` 指向本地 JSON（直接调用准备程序时也可用 `--tiqian-toolchain`）：`haxe`、`node` 为可执行文件路径，Windows 便携版可另传 `neko`；`boring`、`reflaxe`、`format`、`formatter` 为锁文件 `sources.tiqian-core.generation.tools` 所列版本的干净源码目录（不含 `.git`）。准备程序校验 Haxe 版本、工具目录摘要及上游 pin，调用官方 `gen kotlin-f32`，将生产源码、原测试及 Gradle 发布工程写入生成记录。不提交工具位置文件；匹配的成功输入无需重复生成。
- 快照在 lock 的 `snapshotCommit` 固定已审阅的官方提交，工具 pin 与补丁须匹配该提交，避免分支在验证中漂移；升级时一并审查更新。未固定的版本通过 `git ls-remote` 解析标签或分支。准备结果记录实际提交；已吸收的补丁跳过，冲突、缓存缺失或校验失败就停止，不回落到本地制品。
- 源码、字体工具和生成结果只放在 `.gradle/upstream-inputs/`。APK 的 `assets/upstream-patches.json` 与桌面 JAR 的 `upstream-patches.json` 记录实际提交、补丁状态、源码树和数学资源的相对路径与摘要；字体绝对路径只写入本地上游构建的 `gradle.properties`。
- 复用时按当前输入逐字节校验缓存来源记录和打包副本，并核对源码树、字体与元数据；缺失或不匹配即失败，不隐式启动字体构建。准备程序和锁文件都是输入指纹的一部分，修改后须先成功准备新输入，再使用 `--offline`。
- 验证使用当前源码与当前准备程序；不能替换旧脚本、借旧输入绕过准备失败，或删上游测试依赖后声称通过。网络、工具或上游接线阻塞须如实报告。

补丁模式通过 `includeBuild` / `dependencySubstitution` 将 lock 中登记的公共模块替换为所选源码项目；普通依赖模式仍消费 Maven 发布构件，不能用它证明未发布 API 已经可用。版本目录决定直接请求和源码准备版本，不证明传递依赖的最终版本；按目标变体核对 `dependencyInsight`，并与打包产物的上述输入记录对照。Markdown 的缓存与真实入口边界见 [依赖说明](markdown-library-dependency.md#缓存与布局契约)。

## 补丁增删

1. 在未应用本地补丁的上游基线确认缺陷，先提交最小上游 PR。
2. 将同一份源码修复登记到 lock，记录固定基线、校验和及真实 PR 来源；用 `git apply --check` 核对。构建适配与功能修复分开，不把 AGP 或仓库配置夹进功能补丁。
3. 使用补丁模式执行相关上游原测试和应用现有回归。不能删、跳过或放宽断言来通过。
4. 上游正式发布包含修复的版本后，升级版本并删除对应补丁；确认所选标签包含修复，再验证原测试。

不提交修改后的上游树、Maven 仓库或缓存；不写上游或用户的 Git 索引。
