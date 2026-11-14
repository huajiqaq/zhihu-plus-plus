---
name: zhihu-parallel-pr-workflow
description: 协调 Zhihu++ 独立 issue 或能力的并行实现和 PR。用于用户要求 subagent，或独立范围确有并行收益时；涵盖可信来源、worktree、文件与进程归属、验证和中文 PR。
---

# 并行实现与 PR

窄小单任务不默认拆代理。并行范围按完整能力划分，同一 common 声明、调用方和平台 actual 由同一负责人处理，不按平台拆半套契约。

## 开始

- 读 `AGENTS.md`/`CLAUDE.md`，先通过 issue 的作者、版本和复现要求；未通过时不创建分支或 worktree，不设计补丁。
- 核对脏工作树、现有 worktree、重叠 PR 和 `origin/master`，给每位负责人明确的文件范围、入口、成功状态及验证预算。
- 新实现 worktree 放在 `.worktrees/` 下，基于最新主线。用户明确指定当前工作树时按该范围工作，不擅自搬动或回退。

```bash
git fetch origin master --prune
git worktree add .worktrees/<范围> origin/master -b feat/<范围>
```

构建需要的 `local.properties` 由当前环境提供，不提交个人 SDK 路径。

## 归属

负责人实现并返回 diff、实际验证和阻塞项。主代理协调资源、审查及验收；用户明确把实现或发布交给负责人时，主代理不越权提交或发 PR。

进程也按归属处理：只中断能由会话、PID/父进程、worktree 或唯一命令确认属于本任务的进程。不能按 Gradle、Java、emulator 或 ADB 名称批量结束；不运行会影响其他构建的全局 daemon 停止命令。清理后确认终态。

不默认隔离 Gradle home、改变编译策略或增加缓存参数；只有实际失败需要时才调整。

## 实现与检查

- 复用现有底层能力，保留状态、数据和默认偏好，不加薄 client/helper 或未经验证的 fallback。
- UI 先读 [开发指南](../../../docs/ai-ui-design-guide.md) 和导航模型。协议按需使用 [知乎验证](../zhihu-reproduce/SKILL.md)。
- 先查已有覆盖，再运行最小相关编译、测试和实际 UI 路径。默认不跑全量 instrument，不为取图保留临时测试。
- 使用获准且匹配的设备环境，所有 ADB 命令指向同一目标；结束只清理本任务创建的会话。缺登录、设备或网络时报告，不扩大授权。
- Review 核对基线行为、设置读取、返回上下文、平台 actual、截断/遮挡、重复逻辑、测试价值及异常产物。阻塞反馈交回原负责人。

## 提交与发布

提交、推送和 PR 创建需用户授权及 review。按完整可独立验证的改动拆小提交，不夹带其他脏文件；已共享的提交用后续提交修正，不擅自改写历史。

PR 基于最新主线，中文标题和正文，按实际产品变化选择 `feat:`、`fix:` 或 `refactor:`，解决给定 issue 时写 `Resolves #编号`。可见 UI 变化提供真实最终截图，资产流程见 [PR 截图](../github-pr-assets/SKILL.md)。

读回 PR 的语言、分支、关联和截图；验证与 CI 仍在运行时不能说已通过。结束报告各范围的实际结果、未完成项与所需下一步，不另建共享提交账本。
