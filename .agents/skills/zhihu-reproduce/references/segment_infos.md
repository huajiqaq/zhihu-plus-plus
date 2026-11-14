# segment_infos 正文划线协议

正文划线、高亮互动和段落评论的字段参考。具体接口和可用字段仍需按当前生产请求核对，不用于推断无关功能。

## 可参考接口

```text
https://www.zhihu.com/api/v3/feed/topstory/recommend?desktop=true&limit=20
https://www.zhihu.com/api/v4/answers/{answerId}?include=content,paid_info,can_comment,excerpt,thanks_count,voteup_count,comment_count,visited_count,attachment,reaction,ip_info,pagination_info,question.topics,reaction.relation.voting
```

实际接口和 include 参数以 DevTools 捕获为准。不要把上面的 URL 当成固定答案。

## 字段位置

真实样本中，`segment_infos` 常见位置：

- `data[i].target.segment_infos`
- `answer.segment_infos`

## 兼容点

- `allow_segment_interaction` 可能是 `0/1`，不要直接建模为裸 `Boolean`。
- `segment_infos` 可能为空数组。
- 同一段可能有多个 `seg_ids`。
- `marks[*].start_index/end_index` 表示高亮范围，不能把整段文本都当成高亮。

## 核心结构

- `pid`: 对应正文里的 `p[data-pid]`
- `text`: 段落原文
- `marks[*].start_index/end_index`: 高亮范围
- `marks[*].seg_info`: `seg_ids / is_like / like_count / comment_count / my_comment_count / is_span`

## Web DOM 特征

- `span.highlight-wrap.other`
- 有评论时可能带 `has-comments`
- 常见属性包括 `data-highlight-id`、`data-highlight-split-type`

## Android 适配建议

- 如果只改 Markdown 渲染，不要同时改 feed 卡片或 WebView。
- 按 `pid + start/end` 切片生成高亮，不要合并成整段点击区域。
- 弹层动作先按原版和用户需求裁剪，不要默认塞入过多操作。

## 回归样本

可作为回归样本的高密度回答：

- `questionId = 507920275`
- `answerId = 2025269343450080224`
