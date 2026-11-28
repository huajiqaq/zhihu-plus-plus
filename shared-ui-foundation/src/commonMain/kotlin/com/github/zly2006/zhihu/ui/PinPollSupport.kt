/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.ui

import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.pinSummaryText
import com.github.zly2006.zhihu.i18n.WidgetsStrings
import kotlin.time.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

const val ZHIHU_PLUS_AUTHOR_URL_TOKEN = "scanmenge"
const val ZHIHU_PLUS_AUTHOR_PINS_URL = "https://www.zhihu.com/api/v4/v2/pins/$ZHIHU_PLUS_AUTHOR_URL_TOKEN/moments"
const val ZHIHU_PLUS_TOPIC_ID = "2064846813258109867"

enum class HomePinAnnouncementKind {
    Poll,
    Topic,
}

data class HomePinAnnouncement(
    val pinId: Long,
    val kind: HomePinAnnouncementKind,
    val createdAtEpochSeconds: Long = 0L,
    private val sourceTitle: String,
    val optionCount: Int,
    val memberCount: Int,
) {
    val title: String
        get() = sourceTitle.ifBlank {
            when (kind) {
                HomePinAnnouncementKind.Poll -> WidgetsStrings.pinPoll
                HomePinAnnouncementKind.Topic -> WidgetsStrings.appUpdatePost
            }
        }
}

fun decodeHomePinAnnouncements(
    response: JsonObject,
): List<HomePinAnnouncement> =
    response["data"]
        ?.jsonArray
        ?.mapNotNull { item ->
            val pin = runCatching {
                ZhihuJson.decodeJson<DataHolder.Pin>(item.jsonObject)
            }.getOrNull()
            pin?.toHomePinAnnouncement()
        }
        ?: emptyList()

fun DataHolder.Pin.withSelectedPinPollOption(
    pollId: String,
    optionId: String,
): DataHolder.Pin {
    val bottomPoll = bottomPoll ?: return this
    val voting = bottomPoll.voting ?: return this
    if (voting.id != pollId || voting.isVoted || voting.options.none { it.id == optionId }) {
        return this
    }

    val updatedVoting = voting.copy(
        isVoted = true,
        votingCount = voting.votingCount + 1,
        memberCount = voting.memberCount + 1,
        options = voting.options.map { option ->
            if (option.id == optionId) {
                option.copy(
                    votingCount = option.votingCount + 1,
                    isSelected = true,
                )
            } else {
                option
            }
        },
    )
    return copy(bottomPoll = bottomPoll.copy(voting = updatedVoting))
}

fun DataHolder.Pin.Poll.acceptsVote(nowEpochSeconds: Long = Clock.System.now().epochSeconds): Boolean =
    !isReviewing && (endAt !in 0..nowEpochSeconds)

fun DataHolder.Pin.Poll.statusText(): String {
    val voteState = if (isVoted) {
        WidgetsStrings.pollVoted
    } else if (maxSelections > 1) {
        WidgetsStrings.pollChooseUpTo(maxSelections)
    } else {
        WidgetsStrings.pollChooseOne
    }
    val validity = when {
        endAt < 0 -> WidgetsStrings.pollNoEndDate
        endAt <= Clock.System.now().epochSeconds -> WidgetsStrings.pollEnded
        else -> null
    }
    val participation = if (isVoted || memberCount > 0) {
        WidgetsStrings.pollStatusDetail(voteState, WidgetsStrings.pollParticipants(memberCount))
    } else {
        voteState
    }
    return validity?.let { WidgetsStrings.pollStatusDetail(participation, it) } ?: participation
}

fun DataHolder.Pin.toHomePinAnnouncement(): HomePinAnnouncement? {
    val pinId = id.toLongOrNull() ?: return null
    val poll = bottomPoll?.voting
    if (poll != null && poll.acceptsVote() && !poll.isVoted) {
        return HomePinAnnouncement(
            pinId = pinId,
            kind = HomePinAnnouncementKind.Poll,
            createdAtEpochSeconds = created,
            sourceTitle = poll.title,
            optionCount = poll.options.size,
            memberCount = poll.memberCount,
        )
    }

    if (topics.orEmpty().none { it.id == ZHIHU_PLUS_TOPIC_ID }) {
        return null
    }
    return HomePinAnnouncement(
        pinId = pinId,
        kind = HomePinAnnouncementKind.Topic,
        createdAtEpochSeconds = created,
        sourceTitle = excerptTitle.substringBefore("<br").pinSummaryText(),
        optionCount = 0,
        memberCount = 0,
    )
}
