package com.github.zly2006.zhihu.notification

import kotlinx.serialization.Serializable

/** 本机授权与一次性回执；存储实现必须排除备份，不能从应用内偏好恢复订阅。 */
@Serializable
internal data class DeviceNotificationState(
    val accountId: String? = null,
    val epoch: String = "",
    val enabledTypes: Set<NotificationType> = emptySet(),
    val checkpoint: SystemNotificationCheckpoint = SystemNotificationCheckpoint(),
    val reserved: Set<String> = emptySet(),
    val clicks: Map<String, SystemNotificationEvent> = emptyMap(),
)

internal data class SystemNotificationReadSnapshot(
    val accountId: String,
    val entryName: String,
    val clicks: Map<String, SystemNotificationEvent>,
)

internal data class SystemNotificationChange(
    val state: DeviceNotificationState,
    val removed: List<SystemNotificationEvent> = emptyList(),
)

/** 平台只解析自己可信的点击入口；导航目标始终从本机回执取，不接受外部传入的目标。 */
internal data class SystemNotificationClick(
    val accountId: String,
    val eventKey: String,
    val receipt: String,
)

internal data class SystemNotificationClickResult(
    val state: DeviceNotificationState,
    val event: SystemNotificationEvent,
)

internal fun DeviceNotificationState.withTypeEnabled(
    type: NotificationType,
    enabled: Boolean,
    accountId: String,
    epoch: String,
): SystemNotificationChange {
    require(this.accountId == null || this.accountId == accountId)
    if ((type in enabledTypes) == enabled) return SystemNotificationChange(this)
    val types = if (enabled) enabledTypes + type else enabledTypes - type
    val next = if (types.isEmpty()) {
        DeviceNotificationState()
    } else {
        copy(
            accountId = accountId,
            epoch = epoch,
            enabledTypes = types,
            checkpoint = checkpoint.copy(baselinedTypes = checkpoint.baselinedTypes - type),
            clicks = clicks.filterValues { it.type in types },
        )
    }
    return SystemNotificationChange(next, clicks.filterKeys { it !in next.clicks }.values.toList())
}

internal fun DeviceNotificationState.readSnapshot(entryName: String): SystemNotificationReadSnapshot? {
    val accountId = accountId ?: return null
    if (enabledTypes.none { it.timelineEntry == entryName }) return null
    return SystemNotificationReadSnapshot(accountId, entryName, clicks.filterValues { it.type.timelineEntry == entryName })
}

/** 仅撤回请求开始时仍存在的回执；等待服务端期间投递的新提醒不属于本次标读。 */
internal fun DeviceNotificationState.markRead(snapshot: SystemNotificationReadSnapshot): SystemNotificationChange {
    if (accountId != snapshot.accountId) return SystemNotificationChange(this)
    val removed = snapshot.clicks.filter { (receipt, event) ->
        event.type.timelineEntry == snapshot.entryName && clicks[receipt] == event
    }
    val next = copy(clicks = clicks - removed.keys)
    val remainingKeys = next.clicks.values.mapTo(mutableSetOf()) { it.key }
    return SystemNotificationChange(next, removed.values.distinctBy { it.key }.filter { it.key !in remainingKeys })
}

/** 返回状态须先持久化再投递，失败重试不能重复响铃。 */
internal fun DeviceNotificationState.reserve(event: SystemNotificationEvent, receipt: String): DeviceNotificationState? {
    if (accountId == null || event.type !in enabledTypes || event.key in reserved || receipt.isBlank() || receipt in clicks) return null
    return copy(reserved = reserved + event.key, clicks = clicks + (receipt to event))
}

/** 返回状态须先持久化再撤通知和导航，重复点击不能再次消费回执。 */
internal fun DeviceNotificationState.consumeClick(click: SystemNotificationClick, currentAccountId: String?): SystemNotificationClickResult? {
    val event = clicks[click.receipt] ?: return null
    if (click.accountId != currentAccountId || click.accountId != accountId || event.key != click.eventKey || event.type !in enabledTypes) return null
    return SystemNotificationClickResult(copy(clicks = clicks - click.receipt), event)
}
