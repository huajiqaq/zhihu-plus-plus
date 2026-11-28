---
name: ui-test
description: 使用当前 UIAutomator 语义树和 testTag 测试 Zhihu++ Android 页面，支持定位、点击和截图。用于功能验证、UI 回归和交互流程检查。
license: CC BY-NC-SA 4.0
---

# Android UI 测试

## 环境

仅使用获准的 AVD，设备边界见 [协作说明](../../../CLAUDE.md#构建与验证)。明确 `ANDROID_SERIAL`；远端 ADB 和脚本在同一远端环境运行，没有可用环境就报告阻塞。

构建与安装见 [Android 部署](../launch-on-device/SKILL.md)。登录数据只在获准后恢复；不要把登录页截图当成业务页验证。

## 命令

从项目根目录运行：

```bash
export ANDROID_SERIAL='<目标设备序列号>'
python3 .agents/skills/ui-test/llm_test_helper.py dump
python3 .agents/skills/ui-test/llm_test_helper.py find --tag feed_card_more_btn --within-text '<卡片标题>'
python3 .agents/skills/ui-test/llm_test_helper.py tap --tag nav_tab_home
python3 .agents/skills/ui-test/llm_test_helper.py tap --tag feed_card_more_btn --within-text '<卡片标题>'
python3 .agents/skills/ui-test/llm_test_helper.py tap --text '取消'
python3 .agents/skills/ui-test/llm_test_helper.py tap --desc '返回'
python3 .agents/skills/ui-test/llm_test_helper.py screenshot '<输出 PNG 路径>'
```

每次点击前读取当前 `dump`，操作后重新读取或截图确认状态。脚本每次生成独立 dump 并清理临时文件，失败时不能使用上一次结果。

## 定位

- 优先使用稳定 tag，支持裸 tag 和 `<包名>:id/<tag>`。
- 相同 tag 出现多次时，用 `--within-text` 限定卡片；仍有多个候选时加 `--index N`，从 0 开始。
- 没有 tag 时使用显示文字或 contentDescription。`--within-text` 仅用于 tag；文字支持 `--index`，`--desc` 不支持这两个参数。歧义时换 tag 或文字定位。
- 无标识可点击节点可用 `--text '' --index N`；N 是本次空文本候选的序号，不是整个 dump 的编号。
- `[C]` 表示可点击，`[N]` 表示只读信息。弹窗出现时脚本尽量限定在弹窗内容内；先确认范围没有选错。
- 歧义、找不到或越界都停止并重新检查，不静默选第一个，不使用旧截图的坐标。

主导航 tag 为 `nav_tab_<目的地名小写>`；常见入口有 `nav_tab_home`、`nav_tab_follow`、`nav_tab_hotlist`、`nav_tab_daily`、`nav_tab_account`。实际显示由用户设置决定，其他入口从本次 dump 读取。

`feed_card` 和 `feed_card_more_btn` 通常重复，必须消歧。新增入口沿用页面常量和 `Modifier.testTag()`，不要为了测试复制点击区。

## 验证边界

截图生成后打开查看，证明目标内容、布局和状态；非空文件不是通过条件。折叠检查展开和收起，长列表检查末项可达，滑动切换先确认内容身份已变化。只有必须手势且没有语义操作时才按当前屏幕尺寸计算滑动位置。

默认只运行受影响的交互和用例。一次性取图代码不常驻；发布、关注、删除和发送等真实副作用未经授权停在提交前。
