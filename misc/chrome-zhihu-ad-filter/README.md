# 知乎推荐流广告过滤（Chrome 扩展）

扩展向 `www.zhihu.com` 注入 fetch 拦截，过滤以下接口返回的广告条目：

- `https://www.zhihu.com/api/v3/feed/topstory/recommend`

## 广告规则

规则与应用的 `checkForAd` 对齐。条目满足任一条件时被移除：

- 存在 `target.paid_info` 或 `target.paidInfo`；
- 内容包含以下广告链接或标记：
  - `xg.zhihu.com`
  - `d.zhihu.com`
  - `data-edu-card-id`
  - `mp.weixin.qq.com`

存在以下显式广告标记时也会移除：

- 条目类型为 `feed_advert`；
- `promotion_extra`、`ad_info`、`feed_specific`、`monitor_urls`。

## 文件

- `manifest.json`：MV3 扩展元数据；
- `content.js`：在 `document_start` 向页面执行环境注入脚本；
- `injected.js`：fetch 拦截与响应过滤逻辑；
- `test/filter.test.mjs`：核心逻辑的轻量 Node 测试。

## 在 Chrome 中加载

1. 打开 `chrome://extensions`。
2. 开启“开发者模式”。
3. 点击“加载已解压的扩展程序”。
4. 选择 `misc/chrome-zhihu-ad-filter` 文件夹。

## 本地测试

在项目根目录执行：

```bash
node misc/chrome-zhihu-ad-filter/test/filter.test.mjs
```
