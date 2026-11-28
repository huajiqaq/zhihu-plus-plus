# 测试与 CI 职责

## 非必要不测试

只维护值得长期防范的回归。新增功能、修过 issue 或允许 mock 复现，都不自动产生永久测试。账号、恢复、崩溃、选择复制、导航和外观使用同一标准，没有免删类别。

新增与保留都需判断具体错误、复发可能、已有覆盖和维护成本。优先实际消费者，不为字段、普通分支、文案或私有实现逐项配测试。低收益用例及综合用例中的冗余断言均可删除；不要求完全等价替代，但须说明覆盖损失。

不能因测试失败而删除，不降低保留断言的阈值，也不把合并方法、移入循环或隐藏手动套件当作减少职责。运行时校验、权限和业务规则不随测试删除而削弱。

## 负责范围

| 入口／负责方 | 职责 |
| --- | --- |
| 应用回归 | 少量有明确失败后果的消费者路径；优先 common/JVM，涉及真实窗口和平台行为才使用设备 |
| PC PR | 编译桌面宿主与资源；复用应用 JVM 回归，不重复发布打包 |
| Web PR | 相关改动构建 Wasm 生产静态目录并检查桥接边界；PR 不部署，Pages 发布使用独立手动入口 |
| macOS PR | 原生专属测试与离屏交互；不重复 common-only 套件，不额外链接无人运行的宿主 |
| Full/Rust/JNI | 相关源码、依赖与构建变更才触发；验证选定的跨语言边界 |
| 上游维护者 | 完整库套件、生成器、probe、字体工具与跨平台矩阵；应用 CI 不复制这套维护工作 |
| 本项目补丁作者 | 针对实际改动运行相关上游原用例及应用消费者，交付真实结果，不修改 golden 或放宽断言 |
| Nightly／正式发布 | 产物、资源、签名与调试隔离；不重复格式检查或整套应用回归 |

## 执行与门禁

- 触发规则、平台版本和分片配置以 [PR 工作流](../.github/workflows/pr.yml) 为准；不在文档复制易变参数或统计数字。
- 仅改动 PR 工作流列为文档的路径时跳过重检查；能识别的平台专属改动只运行相关检查，其他路径按完整应用检查处理。[Build Check](../.github/workflows/other-branches-test.yml) 是独立手动构建/打包入口，不执行回归、启动应用或部署服务；它不取消或替代 PR 中明确选定的行为检查。
- 格式检查与 APK 构建复用同一任务环境；设备分片复用已构建的 APK。无测试模块不启动空任务。
- Build Check 的 macOS 原生构建及 iOS 构建入口在编译成功后保存缓存，不与后续签验或归档校验绑定；缓存存在不代表产物通过。编译失败或取消不保存该轮缓存，后续校验失败仍阻止制品发布。
- 门禁只接受选定任务成功及未选任务跳过；分类失败、缺报告、零用例或实际失败不能放行。模拟器系统故障与产品失败分别报告，不用自动关窗、忽略失败或重试掩盖。
- 应用消费者与来源校验不证明上游所有模块兼容；普通 PR 不保证仅在 Release 打包或优化时出现的问题。被删专项的细分行为可能不再自动检出。

## 手动构建与打包

Actions → **Build Check** → **Run workflow** 的 `target` 当前提供下列选项，实际选择与命令仍以[工作流](../.github/workflows/other-branches-test.yml)为准：

| `target` | 构建或打包内容 |
| --- | --- |
| `all` | 所有下列平台与 AIGC 服务源码检查 |
| `lite` / `full` | 对应 Android Debug APK |
| `linux` / `desktop` | JVM Linux x64 AppImage；`desktop` 为 `linux` 的兼容入口 |
| `windows` | JVM Windows x64 Release 应用目录、per-user MSI 与 unsigned MSIX |
| `desktop-macos` | macOS arm64 JVM 应用归档，含 ad-hoc 签名 |
| `macos` | macOS arm64 原生 Debug 应用归档 |
| `web` | Wasm 生产静态目录及必要资源检查 |
| `ios` | 同时选择设备与模拟器的 Full / Lite 构建 |
| `ios-device` | Full / Lite arm64 Release unsigned IPA 与归档检查，不执行 XCTest |
| `ios-simulator` | Full / Lite arm64 Debug 模拟器应用归档，不启动 Simulator |
| `ios-device-full` / `ios-device-lite` | 仅对应包型的设备 Release unsigned IPA |
| `ios-simulator-full` / `ios-simulator-lite` | 仅对应包型的模拟器 Debug 应用归档 |
| `aigc-server` | 锁定依赖的 `cargo check`，不运行测试或部署 |

Windows MSI 使用 [package-windows-msi.ps1](../.github/scripts/package-windows-msi.ps1) 的官方 JDK `jpackage` per-user 模板及安装器通知注册；Linux 使用 [package-linux-appimage.py](../.github/scripts/package-linux-appimage.py) 打包 JRE、朗读客户端依赖及许可。Web 使用 Node.js 24，检查通知 Service Worker、本地 KaTeX 字体、按需 FFmpeg 核心及来源清单，必须上传完整静态目录。

这些入口只上传 Actions 制品，不发布 Release、Pages 或后端。unsigned MSIX / IPA 仍需适当签名才能安装，macOS ad-hoc 签名不等于公证；文件存在、归档检查或构建成功都不能证明启动、通知、录音、导出与账号写入可用。运行中任务应标为“构建中”，不能预报成功。用户明确要求不回归时只选择纯构建入口，记录“未执行回归”，不修改既有门禁或将未执行记为通过。

既有 `Pull Request Check` 的 Web 边界检查及 [iOS Release / Simulator 验收](../iosApp/README.md#手动构建与-ci-验收)继续保留：iOS 仍通过 `checks=ios` 或 `checks=all` 配合 `apple_release` 显式选择，`ios_stage` 可选 `all`、`release`、`simulator`。这些行为门禁与 Build Check 的职责不同。

## 清理与验证

清理前备份，读清断言与调用方；删除后同步清理独占 helper、资源、依赖和 CI 引用。保留许可证、来源和必要协议说明，不在源码及共享文档留下统计快照、个人路径、历史进度或修复故事。

根据改动选最小检查：纯删除以引用、diff 和必要编译验收；修改驱动或 CI 判断才做对应定向验证。不默认全量、多变体、多设备重跑，不为删测试再建立测试框架。

改动作者解释保留价值及覆盖损失，评审者检查重复与成本，CI 只执行已选择的检查。实际页面验收不等于要新增永久用例；编译成功和旧报告不代替本次行为通过。
