---
name: zhihu-pp-ai-slop-cleaner
description: 审查 Zhihu++ Kotlin 中的低调用函数、死代码、重复实现、薄包装和多余状态容器。用于代码精简、重复 helper 检查或 PR 清理；扫描结果需结合真实调用方判断。
---

# Kotlin 代码精简

低调用次数和相似函数只是候选，不是删除依据。

## 扫描

在项目根目录运行，输出放到本次临时目录：

```bash
python3 .agents/skills/zhihu-pp-ai-slop-cleaner/scripts/scan_low_call_functions.py \
  --root . --max-calls 2 --output '<低调用候选 TSV 路径>'
python3 .agents/skills/zhihu-pp-ai-slop-cleaner/scripts/find_similar_kotlin_functions.py \
  --root . --threshold 0.82 --output '<相似函数 TSV 路径>'
```

脚本覆盖直接模块的 Java/Kotlin 源集，默认排除测试、构建输出和 worktree。`--include-tests` 可纳入测试。扫描不能识别所有反射、框架分派、Java 或生成代码调用。

## 判断与修改

1. 读声明、真实调用方和附近实现，再分类为删除、内联、合并或保留。顺带检查同类包装，不为凑数量扩范围。
2. 没有分支、状态或独立语义的转发直接在调用点使用底层 API；单调用业务请求不经 environment getter、client 和 helper 层层转发。
3. 重复代码按共同语义合并，不把平台差异、错误处理、安全校验或不同数据库类型混在一起。
4. 框架入口、`expect/actual`、override、Room/序列化钩子、导航、稳定测试 tag 和解释复杂语法的函数不能只按次数删除。
5. 状态容器需要共同不变量或生命周期，不因同页、同名前缀或字段数量而创建。只汇集依赖和转发回调的 Runtime/State 应拆回真实所有者；清理后不留下重复参数或换名 adapter。
6. 按完整改动检查生产行数和新增层级，不用删掉一个大文件掩盖其他调用方变复杂。小函数保留的理由应能从语义看出来。
7. 合并冲突时保留新的产品行为和已确认的职责边界，不恢复被清理的旧包装。

## 测试与验收

新增测试先查同一模型、codec 和生产路径的已有覆盖，缺什么补什么；不为测试开放 helper、增加时钟或进程级 override。设备用例按 [测试取舍](../zhihu-instrument-test-governance/SKILL.md) 判断。

修改后用 `rg` 核对旧名字和调用方，检查 diff、重复实现与平台接线，再执行最小相关验证。真实回归已有修复前失败和修复后通过证据时复用，不重建所有变体。用户限制测试或构建预算时遵守。

提交只包含本次完整改动，保留其他脏文件。PR 使用中文，按行为选择类型并说明实际验证，不把扫描输出当作功能通过。
