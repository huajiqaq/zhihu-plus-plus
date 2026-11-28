# 通知中心

「消息」提供四个通知分类、未读数、邀请回答和私信会话；通知卡可独立打开正文、引用、来源和作者，可靠评论目标支持文本回复及喜欢切换。邀请列表可分页并进入问题、写回答或已有回答。私信支持分页读取、文本发送和逐条时间切换。

通知设置沿用既有默认值；邀请保持 opt-in。不支持新建私信会话、媒体/贴纸发送、邀请或通知删除、想法行内反应、媒体播放和实时推送。服务端关系筛选的 `filter_type` 值未确认，不发送猜测参数或展示无效菜单。

## 接口与状态

请求沿用已核对的知乎 Android 协议，主机为 `api.zhihu.com`。

| 页面 | 请求 |
| --- | --- |
| 消息主页 | `GET /notifications/v3/message/v3?limit=20`，后续每页一次 |
| 邀请回答 | `GET /notifications/v3/timeline/entry/invite?invite_with_time_slice=1&limit=20`，后续每页一次 |
| 通知分类 | `GET /notifications/v3/timeline/entry/{entry}?limit=20`，后续每页一次 |
| 私信查看 | 首屏并行 `GET /messages/user/{peer}` 与 `GET /messages?limit=20&sender_id={peer}`，后续每页一次消息请求 |
| 私信发送 | 每次发送一次 `POST /messages`，直接插入服务端确认的消息，不立即二次拉取 |

- `head` 提供四类及未读数，`column_head` 提供邀请入口，`data` 提供会话。分类按目标入口或已知 ID 识别，缺失才回退到标题；邀请不固定取第一栏，同时含 `inbox/{peer}` 的条目优先进入私信。
- 首页总角标读取顶层 `unread.message.count`，已登录且开启角标时恢复前台重取，失败保留旧值；不对首屏或本地分类求和。通知主页进入或返回前台刷新首屏，失败保留已有内容，成功按 ID 更新首屏并保留尾部。
- 时间线、邀请和私信各持有独立分页状态。应用内显示按动作副标题优先匹配，不用私信正文过滤会话或隐藏主动打开的邀请入口。
- 自动已读在时间线读取成功后才调用本板块的 `POST .../actions/readall`，成功才同步 `is_read` 与 `unread_count`；同轮历史分页沿用结果，刷新重新读取。批量已读只逐类处理有未读的四类，不顺带清除邀请、私信、未知或失败分类。关闭角标只隐藏未读呈现，不改服务端。
- 卡片显示不预取详情。喜欢切换使用 `POST` / `DELETE /reaction/comments/{comment_id}/like`，成功才更新；回复确认发送后用 `comment_v5`，携带 `reply_comment_id`，必须得到服务端评论对象。失败保留输入。
- 私信发送将 `receiver_id`、`content`、`content_type=0`、`source_type=message_list` 编为 form，再加密并标明协议版本；成功清空草稿，失败保留。
- 私信在 `RESUMED` 时每轮完成后等待 5 秒静默同步，离开前台取消。分页到已连续同步的消息或末页，按稳定 ID 去重并保留服务端顺序与历史游标；刚发送的消息不作为连续同步边界。失败不打断内容或草稿，手动刷新使旧轮询失效。

## 字段与写操作边界

- `column_head.avatar_urls` 是含 `url`、`night_url` 的对象数组，不是字符串数组。
- 正文、引用、来源和作者分别使用 `content.target_link`、`content.sub_target_link`、`target_source.target_link`、`head.target_link`；缺失或删除不借用其他链接。
- 删除标记分别为 `content.is_deleted`、`content.is_sub_deleted`、`target_source.is_deleted`。来源图只取未删除的 HTTPS 知乎图片；`has_video` 仅作封面标记，不表示可播放。
- 评论动作须为 `extra_action.data.type=comment`，且 `data.id`、`data.resource_type`、`extra_action.resource_id` 与正文评论锚点一致；删除、折叠或目标不明不提供写操作，缺少 `voting` 不提供喜欢切换。想法反应不借用评论 ID 或状态。
- 邀请奖励说明仅在已确认的 HTML 字段提取可见文字；未知私信接收类型保留降级文案，发送只支持已验证的纯文本。
- 原始抓包和未脱敏响应留在仓库外；凭据、私信正文和用户标识不进入共享资料。

## 本机系统通知

四种类型默认关闭，与应用内显示及角标独立。显式开启才申请权限；在「消息 → 通知设置」显示实际宿主、权限和通道状态。

只读已订阅的 `like`、`comment`、`invite` 时间线，不重复请求共享时间线，不在后台标读、回复或喜欢。首次启用或新增类型的第一次成功同步只建静默基线；沿合法同源 `paging.next` 到已知边界或末页，任一页失败不推进进度。

订阅、基线和一次性回执仅存本机且排除备份，换号或退出撤销订阅及通知，不随可移植偏好恢复。只保存导航所需 ID，不保存作者或正文，锁屏显示通用提示。点击用不可猜测回执，核对账号和事件后消费一次；失效或跨账号点击不借用当前账号打开。

投递记录先持久化再发布，防止重试重复响铃；二者不是同一事务，进程在其间退出可能漏掉提醒。分类标读成功只撤回请求开始时仍有效的原账号同类回执，不影响等待期间的新提醒；单条点击不将整类标读。

| 宿主 | 检查与点击限制 |
| --- | --- |
| Android | WorkManager 在联网条件下每 15 分钟检查，实际调度受系统限制；Android 13+ 需通知权限。显式、不可变 PendingIntent 携带回执。 |
| Linux 桌面 | 进程运行时检查；使用支持 actions 的本机 `org.freedesktop.Notifications`，连接为 `unix:path`。点击唤醒原窗口，退出后不能恢复。 |
| macOS 原生 `.app`、iOS | 应用可运行时检查；启动安装 `UNUserNotificationCenter` delegate，点击等待账号和导航就绪。挂起或退出不保证检查，不是 APNs；见 [iOS 宿主](../iosApp/README.md)。 |
| Windows x64 签名安装的可选 MSIX | 进程运行时检查，原生宿主用 WinRT 投递；已投递通知可激活应用并在账号、导航就绪后消费回执，关闭期间不检查新消息。 |
| Windows 普通 MSI | 通过正式 per-user 安装注册通知；未注册的 MSI、直接运行 JVM 或复制应用目录只提供应用内消息。 |
| macOS JVM `.app` | 真实 `.app` 通过便携通知宿主接入；直接运行 JAR 不具备应用标识，不能替代安装。 |
| Web | 仅页面可见时轮询；Service Worker 处理页面已投递通知与点击恢复，不在关页后抓取新消息。 |

系统接受投递不代表用户已看到；勿扰、通道和窗口管理器可能影响显示或聚焦。宿主安装条件、签名、Web Push 范围和待验收项统一见[平台能力与边界](platform-capabilities.md)。
