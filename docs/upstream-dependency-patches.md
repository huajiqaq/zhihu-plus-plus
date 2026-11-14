# 上游补丁维护

版本目录使用正常上游坐标。需要未发布修复时，打包任务从所选上游源码重放已审查补丁，不依赖私有 Maven 制品。

## 构建入口

唯一准备程序为 `gradle/prepare-upstream-sources.py`，需要 Python 3.11+ 和 Git。源码版本、补丁与字体配置保存在 `gradle/upstream-sources.lock.json`，补丁文件在 `gradle/upstream-patches/`。

- APK 和桌面打包自动启用；独立编译、测试用 `-PzhihuUpstreamPatches=true`。
- 准备程序的 `--package` 启用打包补丁，`--offline` 复用上一次成功记录的同版本输入，`--gradle` 指定字体工具所用的 Gradle 可执行文件。
- 通过 `git ls-remote` 解析标签或分支，记录实际提交。已吸收的补丁跳过，冲突、缓存缺失或校验失败就停止，不回落到本地制品。
- 源码、字体工具和生成结果只放在 `.gradle/upstream-inputs/`。APK 的 `assets/upstream-patches.json` 与桌面 JAR 的 `upstream-patches.json` 记录实际输入和补丁状态。

普通依赖模式仍消费发布构件，不能用它证明未发布 API 已经可用。

## 补丁增删

1. 在未应用本地补丁的上游基线确认缺陷，先提交最小上游 PR。
2. 将同一份源码修复登记到 lock，记录固定基线、校验和及真实 PR 来源；用 `git apply --check` 核对。构建适配与功能修复分开，不把 AGP 或仓库配置夹进功能补丁。
3. 使用补丁模式执行相关上游原测试和应用现有回归。不能删、跳过或放宽断言来通过。
4. 上游正式发布包含修复的版本后，升级版本并删除对应补丁；确认所选标签包含修复，再验证原测试。

不提交修改后的上游树、Maven 仓库或缓存；不写上游或用户的 Git 索引。
