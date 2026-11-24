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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import com.fleeksoft.ksoup.Ksoup
import com.github.zly2006.zhihu.data.MobileNotificationAuthor
import com.github.zly2006.zhihu.data.MobileNotificationContent
import com.github.zly2006.zhihu.data.MobileNotificationHead
import com.github.zly2006.zhihu.data.MobileNotificationTarget
import com.github.zly2006.zhihu.data.MobileNotificationTargetSource
import com.github.zly2006.zhihu.data.MobileNotificationTimelineItem
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.CommentHolder
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.WebPage
import com.github.zly2006.zhihu.navigation.resolveNavigationUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NotificationScreenTest {
    @Test
    fun newAnswerNotificationPrefersAnswerLinkOverActorProfile() {
        val notification = MobileNotificationTimelineItem(
            content = MobileNotificationContent(
                title = "关注的问题有了新回答",
                targetLink = "https://www.zhihu.com/people/answerer",
            ),
            targetSource = MobileNotificationTargetSource(
                targetLink = "https://www.zhihu.com/question/123/answer/456",
            ),
            target = MobileNotificationTarget(
                id = "answerer-id",
                type = "people",
                name = "回答者",
                urlToken = "answerer",
            ),
        )

        val destination = assertIs<Article>(notification.navDestination())

        assertEquals(ArticleType.Answer, destination.type)
        assertEquals(456L, destination.id)
    }

    @Test
    fun followNotificationFallsBackToTargetProfile() {
        val notification = MobileNotificationTimelineItem(
            content = MobileNotificationContent(title = "测试用户 关注了你"),
            target = MobileNotificationTarget(
                id = "follower-id",
                type = "people",
                name = "测试用户",
                urlToken = "tester",
            ),
        )

        val destination = assertIs<Person>(notification.navDestination())

        assertEquals("follower-id", destination.id)
        assertEquals("tester", destination.urlToken)
    }

    @Test
    fun conversationPrefersInboxLinkOverNotificationEntryLink() {
        val notification = MobileNotificationTimelineItem(
            content = MobileNotificationContent(
                title = "测试会话",
                targetLink = "https://www.zhihu.com/notifications/v3/timeline/entry/system",
            ),
            head = MobileNotificationHead(
                targetLink = "https://www.zhihu.com/inbox/peer-token",
            ),
        )

        val destination = assertIs<Notification.Message>(notification.navDestination())

        assertEquals("peer-token", destination.peerId)
        assertEquals("测试会话", destination.name)
    }

    @Test
    fun singleAuthorAvatarIsIndependentOfCommentAnchorAndPrivateChat() {
        val notification = MobileNotificationTimelineItem(
            head = MobileNotificationHead(
                author = MobileNotificationAuthor(id = "actor-id", urlToken = "actor-token", name = "作者"),
                targetLink = "https://www.zhihu.com/people/other-token",
            ),
            content = MobileNotificationContent(
                targetLink = "zhihu://comment/list/answer/456?anchor_comment_id=789",
            ),
        )
        val author = assertIs<Person>(notification.avatarDestination())
        assertEquals("actor-id", author.id)
        assertEquals("actor-token", author.urlToken)
        assertEquals("作者", author.name)
        val comment = assertIs<CommentHolder>(notification.navDestination())
        assertEquals("789", comment.commentId)
        assertEquals(456L, assertIs<Article>(comment.article).id)
        val card = notification.copy(
            content = notification.content!!.copy(subTargetLink = "zhihu://comment/list/answer/456?anchor_comment_id=10"),
            targetSource = MobileNotificationTargetSource(targetLink = "zhihu://answers/456"),
        )
        assertEquals("789", assertIs<CommentHolder>(card.bodyDestination).commentId)
        assertEquals("10", assertIs<CommentHolder>(card.quotedDestination).commentId)
        assertEquals(456L, assertIs<Article>(card.sourceDestination).id)
        val deleted = card.copy(content = card.content!!.copy(isDeleted = true, isSubDeleted = true))
        assertNull(deleted.bodyDestination)
        assertNull(deleted.quotedDestination)
        val webpage = "https://example.org/notice?part=2"
        val linked = card.copy(content = MobileNotificationContent(targetLink = webpage))
        assertEquals(WebPage(webpage), linked.bodyDestination)
        assertEquals(linked.sourceDestination, linked.listDestination)
        assertNull(linked.copy(content = linked.content!!.copy(isDeleted = true)).bodyDestination)
        assertNull(deleted.navDestination())
        assertEquals(456L, assertIs<Article>(deleted.sourceDestination).id)

        val conversation = notification.copy(
            head = notification.head!!.copy(targetLink = "https://www.zhihu.com/inbox/peer-id"),
            content = MobileNotificationContent(title = "私信", targetLink = "https://www.zhihu.com/inbox/peer-id"),
        )
        assertEquals("actor-id", assertIs<Person>(conversation.avatarDestination()).id)
        assertEquals("peer-id", assertIs<Notification.Message>(conversation.navDestination()).peerId)
    }

    @Test
    fun avatarOnlyUsesReliableSinglePersonAndRejectsSystemOrInvitation() {
        val single = MobileNotificationTimelineItem(
            head = MobileNotificationHead(
                author = MobileNotificationAuthor(id = "author", name = "作者"),
                avatarUrls = listOf("https://pic.example/one.jpg"),
            ),
        )
        assertNotNull(single.avatarDestination())
        assertNull(single.copy(head = single.head!!.copy(avatarUrls = listOf("one", "two"))).avatarDestination())
        assertNull(single.copy(cardType = "noti_invite_card").avatarDestination())
        assertNull(single.copy(content = MobileNotificationContent(targetLink = "https://www.zhihu.com/notifications/v3/timeline/entry/system")).avatarDestination())
        assertNull(single.copy(head = single.head!!.copy(targetLink = "https://www.zhihu.com/inbox/-10040")).avatarDestination())
        assertNull(MobileNotificationTimelineItem(head = MobileNotificationHead(author = MobileNotificationAuthor(name = "只有名字"))).avatarDestination())
        assertEquals("profile", assertIs<Person>(MobileNotificationTimelineItem(head = MobileNotificationHead(targetLink = "https://www.zhihu.com/people/profile")).avatarDestination()).urlToken)
        val target = MobileNotificationTarget(type = "people", id = "follower", urlToken = "follower-token", avatarUrl = "one")
        assertEquals("follower", assertIs<Person>(MobileNotificationTimelineItem(target = target).avatarDestination()).id)
        assertNull(MobileNotificationTimelineItem(head = MobileNotificationHead(avatarUrl = "other"), target = target).avatarDestination())
    }

    /** 官方 11.12.0 两个通知 ViewHolder 的主摘要/引用评论字段契约，不以本地化文案选字段。 */
    @Test
    fun bodyAndQuotedPreviewKeepTheirFieldContractAcrossLabelsAndMissingFields() {
        for (subtitle in listOf("", "评论了你的回答", "喜欢了你的评论", "Someone replied")) {
            val notification = MobileNotificationTimelineItem(
                cardType = "noti_simple_card",
                content = MobileNotificationContent(
                    subTitle = subtitle,
                    text = "完整通知信息",
                    abstractText = "<p>摘要</p>",
                    subText = "<p>引用评论</p>",
                ),
            )
            assertEquals("<p>摘要</p>", notification.bodyPreview)
            assertEquals("<p>引用评论</p>", notification.quotedPreview)
            assertEquals("完整通知信息", notification.copy(content = notification.content!!.copy(abstractText = " ")).bodyPreview)
            assertEquals("该内容已被删除", notification.copy(content = notification.content!!.copy(isDeleted = true)).bodyPreview)
            assertEquals("该内容已被删除", notification.copy(content = notification.content!!.copy(isSubDeleted = true)).quotedPreview)
        }
        assertEquals("", MobileNotificationTimelineItem().bodyPreview)
        assertEquals("", MobileNotificationTimelineItem().quotedPreview)
        val likedComment = MobileNotificationTimelineItem(content = MobileNotificationContent(subText = "只返回引用评论"))
        assertEquals("", likedComment.bodyPreview)
        assertEquals("只返回引用评论", likedComment.quotedPreview)
    }

    @Test
    fun commentAndLikePreviewRetainHtmlLinksAndInlineEmoji() {
        val html = """<p>评论 &amp; 回复 [微笑] <a href="https://www.zhihu.com/people/actor">作者</a><br>第二行</p>"""
        val notification = MobileNotificationTimelineItem(content = MobileNotificationContent(abstractText = html, subText = html))
        for (preview in listOf(notification.bodyPreview, notification.quotedPreview)) {
            val emojis = mutableSetOf<String>()
            val navigated = mutableListOf<Person>()
            val rendered = buildAnnotatedString {
                dfsSimple(
                    node = Ksoup.parseBodyFragment(preview).body(),
                    onOpenLink = { navigated += assertIs<Person>(resolveNavigationUrl(it)) },
                    linkColor = Color.Black,
                    componentUsed = emojis,
                )
            }
            assertEquals("评论 & 回复 [微笑] 作者\n第二行", rendered.text)
            assertEquals(setOf(assertNotNull(commentEmojiInlineKey("[微笑]"))), emojis)
            val link = assertIs<LinkAnnotation.Clickable>(rendered.getLinkAnnotations(0, rendered.length).single().item)
            assertNotNull(link.linkInteractionListener).onClick(link)
            assertEquals("actor", navigated.single().urlToken)
        }
    }
}
