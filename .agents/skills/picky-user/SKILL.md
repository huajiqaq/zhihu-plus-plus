---
name: picky-user
description: 由独立 subagent 从新用户和老用户视角审查 UI 的文案、层级、布局与操作效率。用于用户或项目要求“挑剔的用户”评审时；结合语义树和真实截图，沿用未关闭意见并记录处理结论。
license: CC BY-NC-SA 4.0
---

# 挑剔的用户

由独立 subagent 执行，主代理提供目标页面、修改点和预期行为，不能自己扮演评审者替代 subagent。

## 未关闭意见

评审前读取用户配置的记忆工具中的未关闭意见，不只查最近记录。仍成立的意见沿用原 ID；重新出现的问题重开原记录，不另建编号。主代理必须对每条有效意见给出修复、驳回或无效结论及理由，并用工具回写 `fixed`、`rejected` 或 `invalid`，未处理的意见下次继续提出。

工具位置和存储根由用户配置提供，不写个人路径，也不为本地工具增加仓库配置。使用工具实际支持的参数；不能假定移动脚本后仍能从脚本目录推算项目根。工具不可用时说明未读或未回写，不能宣称记忆已同步。

记忆只保留问题、复现方法、判断依据和处理结论，不保存逐次操作日志、机器信息或会话流水账。

## 两个视角

- **新用户**：页面是否无需猜测就能理解；文案、图标和下一步是否明确；层级是否自然；是否容易误触或迷路。
- **老用户**：常用路径是否高效；层级是否便于扫描；操作距离和布局密度是否合适；是否保留既有操作习惯。

## 检查

1. 通过 [UI 测试](../ui-test/SKILL.md) 读取当前语义树，不猜目标位置。macOS Kotlin/Native 使用 [后台离屏调试](../background-ui-debug/SKILL.md)。
2. 判断对齐、裁切、溢出和视觉层级时查看实际目标截图，取证放在仓库外。
3. 分别从两个视角给出有效意见。默认 5–10 条；不足时说明“已检查但未发现更多有效问题”，不凑数。
4. 每条意见写明严重程度、问题、原因、复现和预期；有效意见通过记忆工具记录。

## 返回

- `pass`：没有新的有效意见。
- `needs-work`：有明确可执行的问题。
- `carry-over`：原有意见仍未合理关闭。

按未关闭意见、新用户意见、老用户意见列出，附原 ID 和处理建议。区分必须改和可选改；没执行的检查明确写出。

## 工具参数示例

工具路径由用户配置，以下示例不绑定个人目录。仓库现有 `ui-review-memory/memory_store.py` 支持这些参数；若移动工具，先按工具实际能力配置存储根，不能假定原相对目录仍有效。

```bash
MEMORY_TOOL='<用户配置的记忆工具路径>'
TODAY=$(date +%F)
python3 "$MEMORY_TOOL" show-pending --agent picky-user --date "$TODAY"
python3 "$MEMORY_TOOL" record-issue \
  --agent picky-user --date "$TODAY" \
  --persona new-user --severity medium --kind clarity \
  --title '筛选入口语义不清' \
  --why '首次进入页面时，无法区分筛选范围。' \
  --repro '打开目标页并观察筛选入口。' \
  --expected '入口名称明确表达筛选范围。'
python3 "$MEMORY_TOOL" update-status \
  --agent picky-user --date "$TODAY" --id '<原意见 ID>' \
  --status rejected --note '<处理理由>'
```

同一问题重新出现时，在 `record-issue` 中使用 `--reopen`；关闭或重开均沿用原 ID，不新增重复记录。
