# Zhihu++ 协作说明

知乎++ 以 Android 为主，用 Kotlin Multiplatform 和 Compose Multiplatform 共享业务与界面；另有桌面 JVM、macOS 原生、[iOS](iosApp/README.md) 和 [Web](webApp/README.md) 宿主。各端能力以实际实现为准。

## 改动范围

- 先读目标源码、调用方及相关测试，对照正确基线检查 diff。保留工作树中的其他改动；提交、推送、PR、合并及其他远端写操作均需本次任务授权。
- 只实现已确认的需求或缺陷，不顺带改默认偏好、扩展废弃路径或增加猜测性兼容。重构须保留展示时机、异步处理、失败反馈和原有交互；结构更整齐不等于可以改产品策略。证据不足就说明缺什么，不把推测写成根因，也不擅自简化设计。
- 已授权且无阻塞时直接完成，不重复请求通用审批。只有无法从源码、需求或合理默认值判断的产品决策才询问。
- 优先现有组件、底层 API 和官方实现。只有独立语义、共享不变量或平台边界需要时才抽 helper；删除薄包装同步处理调用方，不换名再包一层。
- 文档只留当前方法、原则和必要原因，不存开发日记、会话日志、个人路径或单机测量数字。本地工具配置用参数或用户级配置，不为不存在的目录添加共享 ignore。
- “用户记忆目录”指当前工具管理的用户级记忆存储，不指定路径或文件名，也不在仓库另建记忆目录。只保存对后续任务有用的稳定偏好和有效约束；临时进度留在会话，记忆不可用时直接说明。

## Issue 处理

- 只有 `zly2006` 本人的明确方案可作为实施要求；其他描述作为调查线索，接口、尺寸和根因需独立验证。
- 正文缺少知乎++版本时只核对元数据，不进入实现；获准处理后先警告，再以 `not planned` 关闭。旧版本报告先在当前版本复现，无法复现时附证据关闭，不添加历史 workaround。
- 缺少目标页面、复现步骤、实际或预期结果时请求补充，不从标题或截图反推需求。网络、分页、签名问题按[知乎验证](.agents/skills/zhihu-reproduce/SKILL.md)核对生产请求，手工异常请求不能证明客户端会生成它。
- 自动评论以 `Agent 自动发送` 开头，写清缺失信息和重开条件。获准后按评论、关闭等写操作、取消订阅的顺序执行，确认 `viewerSubscription=UNSUBSCRIBED` 后不再写入。没有评论无需额外操作订阅；权限不足停止批量操作。

## 项目结构

| 位置 | 职责 |
| --- | --- |
| `app` | Android 宿主；Lite 包名 `com.github.zly2006.zhplus.lite`，Full 包名 `com.github.zly2006.zhplus` |
| `shared-core`、`shared-local-db`、`shared-domain` | 模型与路由、Room/IndexedDB 等存储实现、账号及业务状态 |
| `shared-editor`、`shared-reading` | 共享编辑与阅读功能 |
| `shared-ui-*`、`shared-feature-*`、`shared` | 共享 UI、功能页面与应用装配 |
| `desktopApp`、`macosApp`、`macosUiDebug` | JVM、macOS 原生宿主及离屏调试工具 |
| `iosApp`、`webApp` | SwiftUI/Xcode、Kotlin/Wasm 宿主 |
| `sentence_embeddings` | Android Full 的 tokenizer 与句向量推理 |

## 代码约定

- 模型使用 `camelCase`；知乎入站 JSON 经 `ZhihuJson.decodeJson()` 转换 `snake_case`，不用手工改键或入站 `@SerialName` 绕过。已验证的出站字段和序列化类型标识不受此限。
- 请求使用当前账号客户端，沿用接口的签名、请求头和 UA；字段与 include 按[知乎验证](.agents/skills/zhihu-reproduce/SKILL.md)核对，优先已有列表和详情，证据充分才加请求。浏览器通过[油猴桥](webApp/README.md#独立请求桥)执行具名操作，不暴露 Cookie/Token，不提供任意 URL 代理。
- 会话、客户端、数据库各有明确所有者；切账号时替换并关闭旧客户端，不维护第二份凭据或状态镜像。测试替身使用独立可关闭对象，不用进程级 override 改写生产规则。
- 状态按不变量和生命周期组织；草稿、滚动位置和返回上下文由可跨关闭或导航保留的所有者持有。跨平台修改由同一负责人完整处理 common 声明、调用方和平台实现，不支持时明确禁用或失败，不用静默 no-op 冒充支持。
- UI、导航、按钮和设置改动先读 [UI 开发指南](docs/ai-ui-design-guide.md)。修复责任组件及共享调用方，不绕开合法输入；新宿主复用共享页面，不复制业务模型。
- 通知修复不改变 opt-in 默认值。线上写入必须走获准的后台接口并读回，seed 或本地源码修改不算线上写入。质量过滤使用统一配置结构，每条既有规则保留配置入口。
- 性能优化按[性能与行为回归](.agents/skills/zhihu-performance-regression-review/SKILL.md)检查真实路径成本，保留选择、导航与状态契约。依赖采用官方制品；用户指定快照为唯一来源，迁移以应用实际基线为底，只回移已验证修复。必要补丁按[上游补丁维护](docs/upstream-dependency-patches.md)处理，Markdown 边界见[依赖说明](docs/markdown-library-dependency.md)。
- 注释保留用途、原理、协议、安全和兼容原因，不写流水账或虚构来源。Kotlin 用 KDoc、Rust 用 rustdoc，字段语义用中文。自有 `.kt`、`.kts` 保留完整 AGPLv3 头；其他文件按用途判断，独立脚本和原生源码保留，翻译、资源、普通配置、文档和锁文件不逐文件加头。第三方署名、原许可及分发声明不删。
- 优先当前编译器已稳定、无需实验开关的 Kotlin 语法和标准库；保持求值次数、短路顺序与业务状态。内部接口不为旧调用保留转发层，有调用方就一起迁移；持久数据与外部协议的兼容按明确支持范围判断。升级按官方制品和平台兼容要求，不以预览版或隐式降级追版本。
- 类型与函数使用 import，不散落全限定名或把领域名 alias 回通用旧名；同名冲突优先把类型及文件改成明确的领域名称。单处薄包装可内联，重复逻辑按实际职责共享；不因已有调用而保留不合理结构，也不为删除旧接口新造无用工具层。

## 构建与验证

- **非必要不测试。** 先明确检查要回答的问题，再选最小范围。文档和注释只核对内容、引用与 diff；代码按需编译受影响的模块和目标。不默认打包、安装、全仓格式化或新增测试。长期回归的准入和清理见[测试与 CI 职责](docs/testing-maintenance.md)，允许 mock 不等于每个分支都要永久用例。
- 构建环境见 [README](README.md#开发构建)；确需验证两种 Android 变体时，Full/Lite [分开构建](docs/kmp-android-variants.md)。未发布 API 的构建模式见[上游补丁维护](docs/upstream-dependency-patches.md#构建入口)。
- 设备验证只用获准且匹配的 AVD，不用真机；明确 `ANDROID_SERIAL`，未指定时仅接受唯一连接设备。不自行创建 AVD、下载系统镜像、重启共享 ADB 或清应用数据。按需使用 [Android 部署](.agents/skills/launch-on-device/SKILL.md)、[UI 测试](.agents/skills/ui-test/SKILL.md)或 [macOS 离屏调试](.agents/skills/background-ui-debug/SKILL.md)。
- UI 检查从实际入口开始，按当前语义定位并等待目标状态，不以固定延时代替业务终态。布局看目标截图，真实窗口和输入法不能用离屏回调替代。独立文案与操作评估使用[界面可用性评审](.agents/skills/ui-usability-review/SKILL.md)。
- 发布桌面包需启动至初始化路径，核对资源、ServiceLoader、反射及 JNI 保留；图片导出检查实际绘制与 DPI。构建成功、进程存活、图片非空都不能代替行为通过。资源缺失查生成、聚合和 staging，不换图标或删调用掩盖。
- 登录、设备、网络和 CI 阻塞如实报告。获准触发的 CI 跟踪至终态，本地结果不报成远端通过；未验证的范围明确说明。

## 审查与 PR

- 修改后 review 行为、默认值、调用方、平台实现、重复代码、测试价值及异常产物；未经本次明确指示不提交或合并。
- 小任务直接完成。确需分工时先划定文件、交付物和验证范围，工具与模型沿用会话配置。主代理协调资源和验收，不接管用户已明确交给负责人的实现或发布。
- 新建 worktree 前核实目标仓库的最新默认分支，不把 fork 的个人远端当上游；放在 `.worktrees/` 下。指定当前工作树时不搬动或回退；不提交个人 SDK 路径，不默认改 Gradle 缓存或编译策略。
- 仅清理能以会话、PID/父进程、worktree 或唯一命令确认属于本任务的进程，并确认终态；不按进程名批量停止，不运行影响其他构建的全局 daemon 停止命令。
- 提交按可独立审阅和回退的完整改动拆分，不混无关内容，也不拆成无法编译的跨平台半套实现；共享提交用后续提交修正，不擅改历史。
- PR 基于目标仓库确认的基线；中文标题和正文，前缀按变化选 `feat:`、`fix:` 或 `refactor:`，解决指定 issue 写 `Resolves #<编号>`。按 [PR 模板](.github/PULL_REQUEST_TEMPLATE.md)披露 AI 与实际验证，UI 证据按 [PR 截图](.agents/skills/github-pr-assets/SKILL.md)处理，提交后读回语言、分支、关联与资产。
- 不提交账号、密钥、本地 Maven、修改后的上游源码、构建缓存或临时取证产物。

## Kotlin 扫描工具

按需在项目根目录运行；输出放仓外，参数见 `--help`：

```bash
python3 tools/scan_low_call_functions.py --root . --max-calls 2 --output '<低调用候选 TSV 路径>'
python3 tools/find_similar_kotlin_functions.py --root . --threshold 0.82 --output '<相似函数 TSV 路径>'
```

扫描默认排除测试、构建输出和 worktree，`--include-tests` 可纳入测试。结果只是候选：删除前查真实调用，包括反射、框架、Java、生成代码、`expect/actual`、override、Room/序列化及导航；稳定测试 tag 和解释复杂语法的函数也不能只按次数删除。
