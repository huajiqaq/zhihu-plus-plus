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

package com.github.zly2006.zhihu.editor

import com.fleeksoft.ksoup.Ksoup
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

fun buildPinContentPayload(
    title: String,
    content: CompiledPinMarkdown,
    images: List<UploadedZhihuImage>,
): PinContentPayload =
    PinContentPayload(
        publish = PublishTrace(traceId = newPublishTraceId()),
        title = title
            .takeIf { it.isNotBlank() }
            ?.let { PinContentTitle(title = it) },
        hybrid = content.html
            .takeIf { it.isNotBlank() }
            ?.let {
                PinContentHybrid(
                    html = it,
                    textLength = calculatePinHtmlTextLength(it),
                )
            },
        media = images
            .takeIf { it.isNotEmpty() }
            ?.let { uploadedImages ->
                PinContentMedia(
                    medias = uploadedImages.map { image ->
                        PinContentMediaItem(
                            image = PinContentImage(
                                height = image.rawHeight,
                                width = image.rawWidth,
                                url = image.url,
                                originalUrl = image.originalUrl,
                                watermark = image.watermarkMode
                                    ?: image.watermark?.let { if (it) "watermark" else "original" },
                                watermarkUrl = image.watermarkUrl,
                            ),
                        )
                    },
                )
            },
        topic = content.topics
            .takeIf { it.isNotEmpty() }
            ?.map { it.copy(topicName = "#${it.topicName}#") }
            ?.let(::PinContentTopic),
    )

private fun calculatePinHtmlTextLength(html: String): Int =
    Ksoup
        .parseBodyFragment(html)
        .body()
        .text()
        .length

@Serializable
data class PinContentRequest(
    val action: String = "pin",
    val data: PinContentPayload,
)

@Serializable
data class PinContentPayload(
    val publish: PublishTrace,
    val commentsPermission: PublishCommentsPermission = PublishCommentsPermission(),
    @SerialName("extra_info")
    val extraInfo: PinContentExtraInfo = PinContentExtraInfo(),
    val draft: PinContentDraft = PinContentDraft(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val title: PinContentTitle? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val hybrid: PinContentHybrid? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val media: PinContentMedia? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val topic: PinContentTopic? = null,
)

@Serializable
data class PinContentExtraInfo(
    @SerialName("view_permission")
    val viewPermission: String = "all",
    val publisher: String = "pc",
)

@Serializable
data class PinContentDraft(
    val disabled: Int = 1,
)

@Serializable
data class PinContentTitle(
    val title: String,
)

@Serializable
data class PinContentHybrid(
    val html: String,
    val textLength: Int,
)

@Serializable
data class PinContentMedia(
    val medias: List<PinContentMediaItem>,
)

@Serializable
data class PinContentMediaItem(
    val image: PinContentImage,
)

@Serializable
data class PinContentImage(
    val height: Int,
    val width: Int,
    val url: String,
    val originalUrl: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val watermark: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val watermarkUrl: String? = null,
)

@Serializable
data class PinContentTopic(
    val topics: List<PinContentTopicItem> = emptyList(),
)

@Serializable
data class PinContentTopicItem(
    @SerialName("topic_id")
    val topicId: String,
    @SerialName("topic_name")
    val topicName: String,
)

@Serializable
data class PinTopicSuggestionRequest(
    val title: String,
    val content: String,
)

@Serializable
data class PinTopicSuggestionResponse(
    val data: PinTopicSuggestionData = PinTopicSuggestionData(),
)

@Serializable
data class PinTopicSuggestionData(
    val list: List<PinTopicSuggestion> = emptyList(),
)

@Serializable
data class PinTopicSuggestion(
    val id: String,
    val name: String,
    val topicId: Long,
    val discussCount: String = "",
)
