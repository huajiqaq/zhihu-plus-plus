---
name: background-ui-debug
description: 通过独立离屏调试器检查 macOS Kotlin/Native 页面的语义、交互和绘制，用于 UI 回归、卡死及导航验收。不启动前台窗口，不操作用户桌面。
---

<!--
Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
Copyright (C) 2024-2026, zly2006 <i@zly2006.me>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation (version 3 only).

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.
-->

# 后台 UI 调试

调试器直接驱动 Compose 语义树，在离屏 Skia 画布中布局和绘制，不代替真实窗口或输入法验收。

## 边界

- 只运行独立 debug 二进制；正式应用和 release 不得包含控制协议。不创建、显示或激活应用窗口，不改变 Dock、菜单栏或焦点；禁止 `open`、AppleScript、System Events、桌面截图及全局键鼠注入。
- 操作用 `testTag`、文本或内容描述，截图只取离屏画布；找不到节点就报告失败，不退回屏幕坐标。
- 组合 UI 前创建唯一临时数据根，账号、Cookie、设置、历史、数据库和下载只读写该目录，退出后删除。仅获准真实账号验收时可显式传 `--use-real-account` 复制账号到临时根；运行仍为 `dataMode=isolated`，不直接读写生产数据。
- 布局和动画优先复用已有 debug-only fixture，不为进入页面要求真实登录。发布、关注、投票、删除等只验到提交前；获准的真实副作用另用独立测试账号和执行面，不解除本调试器隔离。

## 操作

1. 确认生产应用未运行，按[测试与 CI 职责](../../../docs/testing-maintenance.md)选定受影响入口。用 `scripts/start_background_ui_debug.sh` 构建并启动调试器，不启动 `macosApp`。
2. 按 [JSONL 协议](references/protocol.md)通信；`ready` 和 `state` 均须确认 `windowHost=false`、`dataMode=isolated` 及系统临时目录内的 `dataHome`，缺失即停止。
3. 动作前 `dump`，按当前语义定位，动作后 `wait` 目标终态并读取状态。只覆盖本次相关交互与返回路径；仅任务要求全面遍历时枚举所有按钮和分页节点。
4. 卡死或失败时保留最后请求、响应、语义变化、耗时及必要的脱敏诊断，布局问题补离屏截图。未达目标、异常、超时或空白不能报通过；修复后复核同一路径，不扩无关矩阵。
5. `quit` 后确认进程结束、临时目录删除。修改调试协议、隔离接线或正式发布时，检查 release 不含 `ZHPP_BACKGROUND_UI_DEBUG_V1`；普通页面修改不为此额外构建 release。

结果注明调试产物、请求 id、选择器、响应和未验范围。交互以目标语义状态为准，布局查看实际绘制；进程存活、`ok` 或非空 PNG 都不是通过依据。
