# 问题回答列表缩略图

列表请求 `GET /api/v4/questions/{id}/feeds` 的回答位于 `data[*].target`，图片来自其中的 `thumbnail_info`，不是顶层 `thumbnail` 或 `thumbnails`。

```json
{"thumbnail_info":{"count":1,"type":"thumbnail_info","thumbnails":[{"height":375,"token":"v2-d4e49469d89079f500cd67ab8ae0c954","type":"image","url":"https://picx.zhimg.com/80/v2-d4e49469d89079f500cd67ab8ae0c954_720w.jpg?source=1def8aca","width":800}]}}
```

无图回答使用 `count: 0` 和空数组。真实响应夹具在 `shared/src/jvmTest/resources/real-api/question-answer-thumbnails.json`，包含无图、单图和多图，作者身份已脱敏；解码断言放在现有 `RealZhihuApiCorpusTest`。

## 展示

复用 `DataHolder.ThumbnailInfo` 和 `ThumbnailRich`，问题列表取第一张 image 交给 `FeedCard`。尺寸、裁切、`showFeedThumbnail` 和点击导航沿用卡片实现；无图不占位，也不额外请求回答详情。

样本图片对默认 UA 返回 403，浏览器 UA 可正常加载。缩略图通过带 `Mozilla/5.0` 的 `ImageRequest` 读取，不发送账号 Cookie。

该字段仅在现有问题回答列表链路中确认，不据此推断其他知乎接口也会返回。
