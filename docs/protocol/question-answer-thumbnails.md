# 问题回答列表缩略图

`GET /api/v4/questions/{id}/feeds` 的回答位于 `data[*].target`，缩略图取其 `thumbnail_info`，不是顶层 `thumbnail` 或 `thumbnails`。其中 `count` 为数量，`thumbnails` 中 image 含 `url`、`token`、`width`、`height`；无图为 `count: 0` 和空数组。

复用 `DataHolder.ThumbnailInfo`、`ThumbnailRich`，取第一张 image 交给 `FeedCard`，沿用尺寸、裁切、`showFeedThumbnail` 和导航。无图不占位，不额外请求回答详情。图片请求使用 `Mozilla/5.0` UA，不发送账号 Cookie。

该字段只在现有问题回答列表链路确认，不推断其他接口也会返回。
