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

package com.github.zly2006.zhihu

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.FollowedQuestion
import com.github.zly2006.zhihu.data.FollowedTopic
import com.github.zly2006.zhihu.data.OfficialBadge
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.CollectionContent
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.Search
import com.github.zly2006.zhihu.test.InstrumentedTestEnvironment
import com.github.zly2006.zhihu.test.MainActivityComposeRule
import com.github.zly2006.zhihu.test.RecordingNavigator
import com.github.zly2006.zhihu.test.ZhihuMockApi
import com.github.zly2006.zhihu.test.resetAppPreferences
import com.github.zly2006.zhihu.test.seedViewModel
import com.github.zly2006.zhihu.test.setScreenContent
import com.github.zly2006.zhihu.ui.FollowingUsersRow
import com.github.zly2006.zhihu.ui.PEOPLE_SCREEN_ACTIVITIES_LIST_TAG
import com.github.zly2006.zhihu.ui.PEOPLE_SCREEN_ANSWERS_LIST_TAG
import com.github.zly2006.zhihu.ui.PEOPLE_SCREEN_AVATAR_TAG
import com.github.zly2006.zhihu.ui.PEOPLE_SCREEN_BLOCK_BUTTON_TAG
import com.github.zly2006.zhihu.ui.PEOPLE_SCREEN_FOLLOW_BUTTON_TAG
import com.github.zly2006.zhihu.ui.PEOPLE_SCREEN_HEADER_TAG
import com.github.zly2006.zhihu.ui.PEOPLE_SCREEN_MORE_BUTTON_TAG
import com.github.zly2006.zhihu.ui.PEOPLE_SCREEN_PROFILE_DETAILS_TAG
import com.github.zly2006.zhihu.ui.PEOPLE_SCREEN_QUESTION_AUTHOR_BLOCK_BUTTON_TAG
import com.github.zly2006.zhihu.ui.PEOPLE_SCREEN_RECOMMENDATION_BLOCK_BUTTON_TAG
import com.github.zly2006.zhihu.ui.PEOPLE_SCREEN_SEARCH_BUTTON_TAG
import com.github.zly2006.zhihu.ui.PEOPLE_SCREEN_SUBSCRIPTIONS_LIST_TAG
import com.github.zly2006.zhihu.ui.PEOPLE_SCREEN_TAB_ROW_TAG
import com.github.zly2006.zhihu.ui.PeopleScreen
import com.github.zly2006.zhihu.ui.PersonViewModel
import io.ktor.http.HttpMethod
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PeopleScreenInstrumentedTest {
    @get:Rule
    val composeRule: MainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        composeRule.resetAppPreferences(forceMock = true)
    }

    @After
    fun tearDown() {
        if (!InstrumentedTestEnvironment.isMockMode()) composeRule.resetAppPreferences()
    }

    /** 回答标识重复时列表仍须显示各条目；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/377。 */
    @Test
    fun duplicatedAuthorAnswerKeysDoNotCrashAnswerListOffline() {
        val duplicatedAnswerId = 2_544_209_984L
        seededViewModel(
            itemCount = 0,
            answers = listOf(
                seededAnswer(
                    id = duplicatedAnswerId,
                    questionId = 1001L,
                    questionTitle = "重复 key 问题 A",
                    excerpt = "重复 key 回答 A",
                ),
                seededAnswer(
                    id = duplicatedAnswerId,
                    questionId = 1002L,
                    questionTitle = "重复 key 问题 B",
                    excerpt = "重复 key 回答 B",
                ),
            ),
            mockAnswers = emptyList(),
        )
        setPeopleScreen(person = ROOT_PERSON.copy(jumpTo = "回答"))

        composeRule.onNodeWithTag(PEOPLE_SCREEN_ANSWERS_LIST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("重复 key 问题 A").assertIsDisplayed()
        composeRule.onNodeWithText("重复 key 问题 B").assertIsDisplayed()
        composeRule.onAllNodesWithTag("people_screen_answer_item_$duplicatedAnswerId").assertCountEquals(2)
    }

    /** 搜索入口须携带当前用户范围；行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/374。 */
    @Test
    fun headerSearchActionNavigatesToMemberScopedSearchOffline() {
        seededViewModel()
        val navigator = setPeopleScreen()
        composeRule.onNodeWithTag(PEOPLE_SCREEN_ACTIVITIES_LIST_TAG).assertIsDisplayed()

        val headerBounds = composeRule
            .onAllNodesWithTag(PEOPLE_SCREEN_HEADER_TAG)
            .fetchSemanticsNodes()
            .single()
            .boundsInRoot
        val avatarBounds = composeRule
            .onAllNodesWithTag(PEOPLE_SCREEN_AVATAR_TAG)
            .fetchSemanticsNodes()
            .single()
            .boundsInRoot
        val searchBounds = composeRule
            .onAllNodesWithTag(PEOPLE_SCREEN_SEARCH_BUTTON_TAG)
            .fetchSemanticsNodes()
            .single()
            .boundsInRoot
        assertTrue(
            "搜索按钮不应占用 TopAppBar actions 槽位并挤压 header，headerBounds=$headerBounds searchBounds=$searchBounds",
            searchBounds.left < headerBounds.right,
        )
        assertTrue(
            "搜索按钮应位于用户信息首屏右上区域，不能落到数据/操作区附近，avatarBounds=$avatarBounds searchBounds=$searchBounds",
            searchBounds.center.y < avatarBounds.bottom,
        )

        composeRule.onNodeWithTag(PEOPLE_SCREEN_SEARCH_BUTTON_TAG).assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf(
                Search(
                    restrictedMemberHashId = ROOT_PERSON.id,
                    restrictedMemberName = "离线用户",
                ),
            ),
            navigator.destinations,
        )
    }

    /**
     * 密集认证布局行为依据：https://github.com/zly2006/zhihu-plus-plus/issues/718。
     * 四项认证保留摘要与完整资料，操作区在展开、完全收起及反向恢复后仍可达。
     * 标签栏上移和裁剪后页头高度共同证明完全收起，不能只看列表滚动。
     */
    @Test
    fun denseProfileBadgesKeepHeaderActionsVisibleOffline() {
        val viewModel = seededViewModel(itemCount = 12)
        setPeopleScreen(person = ROOT_PERSON.copy(jumpTo = "回答"))
        composeRule.waitUntil("用户资料必须加载完成后再设置四项认证", timeoutMillis = 5_000) {
            viewModel.hasLoadedProfile && !viewModel.isProfileLoading
        }
        val badges = listOf(
            OfficialBadge("社区成就", "社区成就说明"),
            OfficialBadge("身份认证", "身份认证说明"),
            OfficialBadge("优秀答主", "优秀答主说明"),
            OfficialBadge("新知答主", "新知答主说明"),
        )
        composeRule.activity.runOnUiThread { viewModel.officialBadgeDetails = badges }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("社区成就: 社区成就说明", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("+3", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(PEOPLE_SCREEN_PROFILE_DETAILS_TAG).assertIsDisplayed().performTouchInput { click() }
        val fullProfile = "离线个人简介\n\n" + badges.joinToString("\n\n") { "${it.title}: ${it.description}" }
        composeRule.onNode(hasAnyAncestor(isDialog()) and hasText(fullProfile)).assertIsDisplayed()
        composeRule.onNode(hasAnyAncestor(isDialog()) and hasText("关闭")).assertIsDisplayed().performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNode(isDialog()).assertDoesNotExist()
        val expandedHeaderBounds = composeRule
            .onNodeWithTag(PEOPLE_SCREEN_HEADER_TAG)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue("展开态资料头必须具有可见高度", expandedHeaderBounds.height > 1f)
        composeRule.onNodeWithTag(PEOPLE_SCREEN_FOLLOW_BUTTON_TAG).assertIsDisplayed()
        val expandedTabTop = composeRule
            .onNodeWithTag(PEOPLE_SCREEN_TAB_ROW_TAG)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        listOf(
            PEOPLE_SCREEN_FOLLOW_BUTTON_TAG,
            PEOPLE_SCREEN_MORE_BUTTON_TAG,
        ).forEach { tag ->
            assertTrue(
                "展开态操作按钮必须完整位于标签栏之前: $tag",
                composeRule
                    .onNodeWithTag(tag)
                    .assertIsDisplayed()
                    .assertIsEnabled()
                    .assertHasClickAction()
                    .fetchSemanticsNode()
                    .boundsInRoot.bottom <= expandedTabTop,
            )
        }

        composeRule.onNodeWithTag(PEOPLE_SCREEN_MORE_BUTTON_TAG).performTouchInput { click() }
        composeRule.onNodeWithTag(PEOPLE_SCREEN_BLOCK_BUTTON_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(PEOPLE_SCREEN_RECOMMENDATION_BLOCK_BUTTON_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(PEOPLE_SCREEN_QUESTION_AUTHOR_BLOCK_BUTTON_TAG).assertIsDisplayed()
        val blockActionTags = listOf(
            PEOPLE_SCREEN_BLOCK_BUTTON_TAG,
            PEOPLE_SCREEN_RECOMMENDATION_BLOCK_BUTTON_TAG,
            PEOPLE_SCREEN_QUESTION_AUTHOR_BLOCK_BUTTON_TAG,
        )
        val menuViewport = composeRule.onNode(isPopup()).fetchSemanticsNode().boundsInRoot
        blockActionTags.forEach { tag ->
            val buttonBounds = composeRule
                .onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertIsEnabled()
                .assertHasClickAction()
                .fetchSemanticsNode()
                .boundsInRoot
            assertTrue(
                "展开态菜单操作必须完整位于真实菜单视口内: $tag",
                buttonBounds.width > 0f &&
                    buttonBounds.height > 0f &&
                    buttonBounds.left >= menuViewport.left &&
                    buttonBounds.top >= menuViewport.top &&
                    buttonBounds.right <= menuViewport.right &&
                    buttonBounds.bottom <= menuViewport.bottom,
            )
        }
        val blockRequestCount = ZhihuMockApi.requestCount(HttpMethod.Post, "/actions/block")
        composeRule.onNodeWithTag(PEOPLE_SCREEN_BLOCK_BUTTON_TAG).performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNode(isPopup()).assertDoesNotExist()
        composeRule.onNode(hasAnyAncestor(isDialog()) and hasText("拉黑 离线用户？")).assertIsDisplayed()
        composeRule
            .onNode(hasAnyAncestor(isDialog()) and hasText("会同步到你的知乎账号。之后可以在这个页面点“取消拉黑”恢复。"))
            .assertIsDisplayed()
        assertEquals("拉黑必须先确认，打开弹窗不能提交", blockRequestCount, ZhihuMockApi.requestCount(HttpMethod.Post, "/actions/block"))
        composeRule
            .onNode(hasAnyAncestor(isDialog()) and hasText("取消"))
            .assertIsDisplayed()
            .performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNode(isDialog()).assertDoesNotExist()
        assertEquals("取消拉黑不能提交", blockRequestCount, ZhihuMockApi.requestCount(HttpMethod.Post, "/actions/block"))
        composeRule.runOnIdle {
            assertTrue("取消确认后仍未拉黑", !viewModel.isBlocking)
        }
        listOf(
            PEOPLE_SCREEN_RECOMMENDATION_BLOCK_BUTTON_TAG,
            PEOPLE_SCREEN_QUESTION_AUTHOR_BLOCK_BUTTON_TAG,
        ).forEach { tag ->
            composeRule.onNodeWithTag(PEOPLE_SCREEN_MORE_BUTTON_TAG).assertIsDisplayed().performTouchInput { click() }
            composeRule.onNodeWithTag(tag).assertIsDisplayed().performTouchInput { click() }
            composeRule.waitUntil("菜单点击必须完成屏蔽: $tag", timeoutMillis = 5_000) {
                if (tag == PEOPLE_SCREEN_RECOMMENDATION_BLOCK_BUTTON_TAG) {
                    viewModel.isBlockedInRecommendations
                } else {
                    viewModel.isBlockedAsQuestionAuthor
                }
            }
            composeRule.onNode(isPopup()).assertDoesNotExist()
            composeRule.onNodeWithTag(PEOPLE_SCREEN_MORE_BUTTON_TAG).assertIsDisplayed().performTouchInput { click() }
            composeRule
                .onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertTextEquals(if (tag == PEOPLE_SCREEN_RECOMMENDATION_BLOCK_BUTTON_TAG) "取消屏蔽推荐" else "取消屏蔽其提问")
                .performTouchInput { click() }
            composeRule.waitUntil("取消屏蔽必须恢复原状态: $tag", timeoutMillis = 5_000) {
                if (tag == PEOPLE_SCREEN_RECOMMENDATION_BLOCK_BUTTON_TAG) {
                    !viewModel.isBlockedInRecommendations
                } else {
                    !viewModel.isBlockedAsQuestionAuthor
                }
            }
            composeRule.onNode(isPopup()).assertDoesNotExist()
        }

        val listNode = composeRule.onNodeWithTag(PEOPLE_SCREEN_ANSWERS_LIST_TAG).assertIsDisplayed()
        val listBefore = listNode.fetchSemanticsNode()
        val listBounds = listBefore.boundsInRoot
        val listUnclippedBounds = listNode.getUnclippedBoundsInRoot()
        val root = composeRule.onRoot()
        val rootBounds = root.fetchSemanticsNode().boundsInRoot
        val systemBars = composeRule.runOnIdle {
            checkNotNull(ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView))
                .getInsets(WindowInsetsCompat.Type.systemBars())
        }
        val safeRootBounds = Rect(
            rootBounds.left + systemBars.left,
            rootBounds.top + systemBars.top,
            rootBounds.right - systemBars.right,
            rootBounds.bottom - systemBars.bottom,
        )
        val gestureBounds = listBounds.intersect(safeRootBounds)
        val startInRoot = Offset(gestureBounds.center.x, gestureBounds.top + gestureBounds.height * 0.8f)
        // 短列表视口限制不了已捕获的拖动；继续拖过页头，避免松手前只有很小的折叠进度。
        val endInRoot = Offset(gestureBounds.center.x, safeRootBounds.top + safeRootBounds.height * 0.1f)
        val gestureMetrics = "listBounds=$listBounds listSize=${listBefore.size} unclippedBounds=$listUnclippedBounds " +
            "rootBounds=$rootBounds systemBars=$systemBars safeRootBounds=$safeRootBounds gestureBounds=$gestureBounds " +
            "start=$startInRoot end=$endInRoot"
        assertTrue(
            "回答列表必须有真实可见手势视口：$gestureMetrics",
            listBounds.width > 0f &&
                listBounds.height > 0f &&
                safeRootBounds.width > 0f &&
                safeRootBounds.height > 0f &&
                gestureBounds.width > 0f &&
                gestureBounds.height > 0f,
        )
        assertTrue(
            "手势须从列表开始，并在非系统栏的根视口内持续拖动：$gestureMetrics",
            listBounds.contains(startInRoot) &&
                rootBounds.contains(startInRoot) &&
                rootBounds.contains(endInRoot) &&
                safeRootBounds.contains(startInRoot) &&
                safeRootBounds.contains(endInRoot),
        )
        assertTrue("折叠手势必须向上：$gestureMetrics", startInRoot.x == endInRoot.x && startInRoot.y > endInRoot.y)
        val scrollBefore = checkNotNull(listBefore.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange))
        val scrollValueBefore = scrollBefore.value()
        val scrollMaxBefore = scrollBefore.maxValue()
        assertTrue(
            "回答列表必须可向前滚动：$gestureMetrics scroll=$scrollValueBefore/$scrollMaxBefore",
            !scrollBefore.reverseScrolling && scrollMaxBefore > scrollValueBefore,
        )
        root.performTouchInput {
            swipe(start = startInRoot - rootBounds.topLeft, end = endInRoot - rootBounds.topLeft)
        }
        composeRule.waitForIdle()
        val scrollAfter = checkNotNull(listNode.fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange))
        val scrollValueAfter = scrollAfter.value()
        val scrollMaxAfter = scrollAfter.maxValue()
        val collapsedTabBounds = composeRule
            .onNodeWithTag(PEOPLE_SCREEN_TAB_ROW_TAG)
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue("收起态标签栏必须保持可见", collapsedTabBounds.height > 0f)
        val collapsedHeaderBounds = composeRule.onNodeWithTag(PEOPLE_SCREEN_HEADER_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "资料头必须完全裁剪收起，允许 1px 舍入：headerBounds=$collapsedHeaderBounds $gestureMetrics",
            collapsedHeaderBounds.height <= 1f,
        )
        assertTrue(
            "滚动后标签栏必须上移，证明资料头已收起：展开=$expandedTabTop，收起=${collapsedTabBounds.top} " +
                "$gestureMetrics scrollBefore=$scrollValueBefore/$scrollMaxBefore scrollAfter=$scrollValueAfter/$scrollMaxAfter",
            expandedTabTop > collapsedTabBounds.top,
        )
        listOf(
            PEOPLE_SCREEN_FOLLOW_BUTTON_TAG,
            PEOPLE_SCREEN_MORE_BUTTON_TAG,
        ).forEach { tag ->
            val buttonBounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue(
                "收起态操作按钮不能覆盖标签栏: $tag",
                buttonBounds.bottom <= collapsedTabBounds.top || buttonBounds.top >= collapsedTabBounds.bottom,
            )
        }
        composeRule.onNodeWithTag(PEOPLE_SCREEN_MORE_BUTTON_TAG).assertIsDisplayed().performTouchInput { click() }
        val collapsedMenuViewport = composeRule.onNode(isPopup()).fetchSemanticsNode().boundsInRoot
        blockActionTags.forEach { tag ->
            val buttonBounds = composeRule
                .onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertIsEnabled()
                .assertHasClickAction()
                .fetchSemanticsNode()
                .boundsInRoot
            assertTrue(
                "收起态菜单操作必须完整位于真实菜单视口内: $tag",
                buttonBounds.width > 0f &&
                    buttonBounds.height > 0f &&
                    buttonBounds.left >= collapsedMenuViewport.left &&
                    buttonBounds.top >= collapsedMenuViewport.top &&
                    buttonBounds.right <= collapsedMenuViewport.right &&
                    buttonBounds.bottom <= collapsedMenuViewport.bottom,
            )
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val targetPackage = composeRule.activity.packageName
        var activePackage: String? = null
        composeRule.waitUntil("更多菜单必须存在活跃应用窗口", timeoutMillis = 5_000) {
            activePackage = automation.rootInActiveWindow?.packageName?.toString()
            activePackage == targetPackage
        }
        assertEquals(targetPackage, activePackage)
        val downTime = SystemClock.uptimeMillis()
        assertTrue(
            automation.injectInputEvent(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0), true),
        )
        assertTrue(
            automation.injectInputEvent(KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0), true),
        )
        composeRule.waitUntil("Android 返回后更多菜单必须关闭", timeoutMillis = 5_000) {
            composeRule.onAllNodes(isPopup()).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNode(isPopup()).assertDoesNotExist()

        val header = composeRule.onNodeWithTag(PEOPLE_SCREEN_HEADER_TAG)
        var scroll = listNode.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        var headerHeight = header.fetchSemanticsNode().boundsInRoot.height
        // exitUntilCollapsed 先让列表消费下拉，只有到顶后的剩余距离能展开页头。
        while (scroll > 0f || headerHeight < expandedHeaderBounds.height - 1f) {
            val restoreViewport = listNode.fetchSemanticsNode().boundsInRoot.intersect(safeRootBounds)
            val restoreStartInRoot = Offset(restoreViewport.center.x, restoreViewport.top + restoreViewport.height * 0.05f)
            val restoreEndInRoot = Offset(restoreViewport.center.x, restoreViewport.bottom - restoreViewport.height * 0.05f)
            assertTrue(
                "恢复手势必须在回答列表的真实安全视口内向下拖动：$restoreViewport",
                restoreViewport.width > 0f &&
                    restoreViewport.height > 0f &&
                    safeRootBounds.contains(restoreStartInRoot) &&
                    safeRootBounds.contains(restoreEndInRoot) &&
                    restoreStartInRoot.y < restoreEndInRoot.y,
            )
            root.performTouchInput {
                swipe(start = restoreStartInRoot - rootBounds.topLeft, end = restoreEndInRoot - rootBounds.topLeft)
            }
            composeRule.waitForIdle()
            val nextScroll = listNode.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            val nextHeight = header.fetchSemanticsNode().boundsInRoot.height
            assertTrue(
                "下拉必须向列表顶部或完整页头推进：scroll=$scroll→$nextScroll header=$headerHeight→$nextHeight viewport=$restoreViewport",
                nextScroll < scroll || (nextScroll == 0f && nextHeight > headerHeight),
            )
            scroll = nextScroll
            headerHeight = nextHeight
        }
        assertEquals("下拉必须回到回答列表顶部", 0f, scroll, 0f)
        val restoredHeaderBounds = header.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val restoredTabBounds = composeRule
            .onNodeWithTag(PEOPLE_SCREEN_TAB_ROW_TAG)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        assertEquals("向下回拖必须恢复完整资料头", expandedHeaderBounds.height, restoredHeaderBounds.height, 1f)
        assertEquals("向下回拖必须恢复标签栏位置", expandedTabTop, restoredTabBounds.top, 1f)
        composeRule.onNodeWithText("+3", useUnmergedTree = true).assertIsDisplayed()
        val follow = composeRule
            .onNodeWithTag(PEOPLE_SCREEN_FOLLOW_BUTTON_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
        assertTrue("恢复后的关注入口不能覆盖标签栏", follow.fetchSemanticsNode().boundsInRoot.bottom <= restoredTabBounds.top)
        follow.performTouchInput { click() }
        composeRule.waitUntil("恢复后真实触摸必须完成关注", timeoutMillis = 5_000) { viewModel.isFollowing }
        composeRule.onNodeWithTag(PEOPLE_SCREEN_FOLLOW_BUTTON_TAG).assertTextEquals("已关注")
    }

    /** 关注订阅的四个入口使用原生导航；需求：https://github.com/zly2006/zhihu-plus-plus/issues/302。 */
    @Test
    fun followingSubscriptionsTabMatchesOfficialEntryPointsOffline() {
        seededViewModel(itemCount = 8)
        val navigator = setPeopleScreen(person = ROOT_PERSON.copy(jumpTo = "关注订阅"))

        composeRule.onNodeWithTag(PEOPLE_SCREEN_SUBSCRIPTIONS_LIST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag("people_screen_subscription_tab_0").assertExists()
        composeRule.onNodeWithTag("people_screen_subscription_tab_1").assertExists()
        composeRule.onNodeWithTag("people_screen_subscription_tab_2").assertExists()
        composeRule.onNodeWithTag("people_screen_subscription_tab_3").assertExists()

        composeRule.onNodeWithTag("people_screen_subscription_tab_1").performClick()
        composeRule.onNodeWithTag("people_screen_followed_topic_item_topic-1").assertIsDisplayed()

        composeRule.onNodeWithTag("people_screen_subscription_tab_2").performClick()
        composeRule.onNodeWithTag("people_screen_followed_question_item_1").performClick()

        composeRule.onNodeWithTag("people_screen_subscription_tab_3").performClick()
        composeRule.onNodeWithTag("people_screen_collection_item_follow-collection-1").performClick()

        assertEquals(
            listOf(
                Question(1L, "关注的问题 1"),
                CollectionContent("follow-collection-1"),
            ),
            navigator.destinations,
        )
    }

    @Test
    fun recentAvatarOpensSelectedUsersActivitiesBeforeFullProfile() {
        val users = """{"data":[
          {"actor":{"id":"first","url_token":"first-user","name":"甲","avatar_url":""},"unread_count":0},
          {"actor":{"id":"second","url_token":"second-user","name":"乙","avatar_url":""},"unread_count":1,"brief":"second-brief"}
        ]}"""
        ZhihuMockApi.mockJsonPrefix(HttpMethod.Get, "https://api.zhihu.com/moments/recent?", users)
        ZhihuMockApi.mockJsonPrefix(HttpMethod.Post, "https://api.zhihu.com/moments/recent/read?", """{"success":true}""")
        for (token in listOf("first-user", "second-user")) {
            ZhihuMockApi.mockJsonPrefix(
                HttpMethod.Get,
                "https://www.zhihu.com/api/v3/moments/$token/activities",
                """{"data":[],"paging":{"is_end":true,"next":""}}""",
            )
        }
        val navigator = composeRule.setScreenContent { FollowingUsersRow() }
        composeRule.onNodeWithTag("following_users_item_second").performClick()
        composeRule.onNodeWithTag("following_user_activities_second").assertIsDisplayed()
        composeRule.waitUntilRequestCount(HttpMethod.Get, "/moments/second-user/activities", 1)
        assertEquals(0, ZhihuMockApi.requestCount(HttpMethod.Get, "/moments/first-user/activities"))
        assertEquals(0, ZhihuMockApi.requestCount(HttpMethod.Get, "/people/second-user"))
        assertTrue(navigator.destinations.isEmpty())

        composeRule.onNode(hasText("甲") and hasAnyAncestor(hasTestTag("following_user_activity_sheet"))).performClick()
        composeRule.onNodeWithTag("following_user_activities_first").assertIsDisplayed()
        composeRule.waitUntilRequestCount(HttpMethod.Get, "/moments/first-user/activities", 1)
        composeRule.onNodeWithTag("following_user_open_profile").performClick()
        val profile = navigator.destinations.single() as Person
        assertEquals("first", profile.id)
        assertEquals("动态", profile.jumpTo)
        assertEquals(1, ZhihuMockApi.requestCount(HttpMethod.Post, "/moments/recent/read"))
    }

    private fun setPeopleScreen(
        person: Person = ROOT_PERSON,
    ): RecordingNavigator = composeRule.setScreenContent {
        PeopleScreen(
            person = person,
        )
    }

    private fun MainActivityComposeRule.waitUntilRequestCount(
        method: HttpMethod,
        urlSubstring: String,
        count: Int,
    ) {
        waitUntil("等待 $count 次包含 $urlSubstring 的 $method 请求", timeoutMillis = 5_000) {
            ZhihuMockApi.requestCount(method, urlSubstring) >= count
        }
    }

    private fun seededViewModel(
        itemCount: Int = 12,
        answers: List<DataHolder.Answer>? = null,
        mockAnswers: List<DataHolder.Answer>? = null,
    ): PersonViewModel {
        val seededViewModel = composeRule.seedViewModel<PersonViewModel> {
            PersonViewModel(ROOT_PERSON.copy())
        }
        val answerData = answers ?: List(itemCount) { index -> seededAnswer(index + 1L) }
        val articleData = List(itemCount) { index -> seededArticle(index + 1L) }
        mockPeopleScreenApis(
            itemCount = itemCount,
            answers = mockAnswers ?: answerData,
            articles = articleData,
        )

        composeRule.activity.runOnUiThread {
            seededViewModel.avatar = "https://example.invalid/avatar/root.png"
            seededViewModel.name = "离线用户"
            seededViewModel.headline = "离线个人简介"
            seededViewModel.officialBadge = OfficialBadge(
                title = "优秀答主",
                description = "英语等 5 个话题下的优秀答主",
                iconUrl = DataHolder.ZH_PLUS_AUTHOR_BADGE_ICON,
            )
            seededViewModel.officialBadgeDetails = listOf(
                OfficialBadge("社区成就", "英语等 5 个话题下的优秀答主"),
            )
            seededViewModel.followerCount = 120
            seededViewModel.followingCount = 45
            seededViewModel.answerCount = answers?.size ?: itemCount
            seededViewModel.articleCount = itemCount
            seededViewModel.isFollowing = false
            seededViewModel.isBlocking = false
            seededViewModel.isBlockedInRecommendations = false
            seededViewModel.isBlockedAsQuestionAuthor = false
            seededViewModel.memberHashId = ROOT_PERSON.id

            seededViewModel.answersFeedModel.allData.clear()
            seededViewModel.answersFeedModel.allData.addAll(answerData)
            seededViewModel.articlesFeedModel.allData.clear()
            seededViewModel.articlesFeedModel.allData.addAll(articleData)
            seededViewModel.collectionsFeedModel.allData.clear()
            seededViewModel.collectionsFeedModel.allData.addAll(List(itemCount) { index -> seededCollection(index + 1) })
            seededViewModel.questionsFeedModel.allData.clear()
            seededViewModel.questionsFeedModel.allData.addAll(List(itemCount) { index -> seededQuestion(index + 1L) })
            seededViewModel.pinsFeedModel.allData.clear()
            seededViewModel.pinsFeedModel.allData.addAll(List(itemCount) { index -> seededPin(index + 1) })
            seededViewModel.columnsFeedModel.allData.clear()
            seededViewModel.columnsFeedModel.allData.addAll(List(itemCount) { index -> seededColumn(index + 1) })
            seededViewModel.followersFeedModel.allData.clear()
            seededViewModel.followersFeedModel.allData.addAll(List(itemCount) { index -> seededPeople("follower", index + 1, "粉丝") })
            seededViewModel.followingFeedModel.allData.clear()
            seededViewModel.followingFeedModel.allData.addAll(List(itemCount) { index -> seededPeople("following", index + 1, "关注的人") })
            seededViewModel.followingColumnsFeedModel.allData.clear()
            seededViewModel.followingColumnsFeedModel.allData.addAll(List(itemCount) { index -> seededFollowedColumn(index + 1) })
            seededViewModel.followingTopicsFeedModel.allData.clear()
            seededViewModel.followingTopicsFeedModel.allData.addAll(List(itemCount) { index -> seededFollowedTopic(index + 1) })
            seededViewModel.followingQuestionsFeedModel.allData.clear()
            seededViewModel.followingQuestionsFeedModel.allData.addAll(List(itemCount) { index -> seededFollowedQuestion(index + 1) })
            seededViewModel.followingCollectionsFeedModel.allData.clear()
            seededViewModel.followingCollectionsFeedModel.allData.addAll(List(itemCount) { index -> seededFollowedCollection(index + 1) })
        }
        composeRule.waitForIdle()
        return seededViewModel
    }

    private fun mockPeopleScreenApis(
        itemCount: Int,
        answers: List<DataHolder.Answer>,
        articles: List<DataHolder.Article>,
    ) {
        val token = ROOT_PERSON.urlToken
        val id = ROOT_PERSON.id
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = "https://api.zhihu.com/people/$token",
            body = ZhihuJson.json.encodeToString(seededProfile(itemCount)),
        )
        ZhihuMockApi.mockJson(
            method = HttpMethod.Get,
            url = "https://api.zhihu.com/people/$token/profile/detail",
            body = ZhihuJson.json.encodeToString(
                seededProfile(itemCount).copy(
                    socialMedias = listOf(
                        DataHolder.SocialMedia(
                            icon = "https://example.invalid/github.png",
                            title = "GitHub·zly2006",
                            link = "zhihu://hybrid?zh_hide_nav_bar=true&zh_url=https:%2F%2Fwww.zhihu.com%2Fappview%2Fgithub%2Fdetail%3Fhash_id=ea09b6c82124e0162caa10d658058c10",
                            modules = listOf(
                                DataHolder.SocialMediaModule("被关注人", "169"),
                                DataHolder.SocialMediaModule("公开仓库数", "119"),
                                DataHolder.SocialMediaModule("stars", "4.0k"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        ZhihuMockApi.mockJson(
            method = HttpMethod.Post,
            url = "https://www.zhihu.com/api/v4/members/$token/followers",
            body = """{"follower_count":121}""",
        )
        ZhihuMockApi.mockJson(
            method = HttpMethod.Post,
            url = "https://www.zhihu.com/api/v4/members/$token/actions/block",
            body = "{}",
        )
        mockPagePrefix("https://www.zhihu.com/api/v4/members/$token/answers?sort_by=", answers)
        mockPagePrefix("https://www.zhihu.com/api/v4/members/$token/articles?sort_by=", articles)
        mockEmptyPagePrefix("https://www.zhihu.com/api/v3/moments/$token/activities")
        mockEmptyPagePrefix("https://www.zhihu.com/api/v4/members/$token/favlists")
        mockEmptyPagePrefix("https://www.zhihu.com/api/v4/members/$token/questions")
        mockEmptyPagePrefix("https://www.zhihu.com/api/v4/v2/pins/$token/moments")
        mockEmptyPagePrefix("https://www.zhihu.com/api/v4/members/$token/column-contributions")
        mockEmptyPagePrefix("https://api.zhihu.com/people/$id/followers")
        mockEmptyPagePrefix("https://www.zhihu.com/api/v4/members/$token/followees")
        mockEmptyPagePrefix("https://www.zhihu.com/api/v4/members/$token/following-columns")
        mockEmptyPagePrefix("https://www.zhihu.com/api/v4/members/$token/following-topic-contributions")
        mockEmptyPagePrefix("https://www.zhihu.com/api/v4/members/$token/following-questions")
        mockEmptyPagePrefix("https://www.zhihu.com/api/v4/members/$token/following-favlists")
    }

    private inline fun <reified T> mockPagePrefix(
        urlPrefix: String,
        data: List<T>,
    ) {
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = urlPrefix,
            body = """{"data":${ZhihuJson.json.encodeToString(data)}}""",
        )
    }

    private fun mockEmptyPagePrefix(urlPrefix: String) {
        ZhihuMockApi.mockJsonPrefix(
            method = HttpMethod.Get,
            urlPrefix = urlPrefix,
            body = """{"data":[]}""",
        )
    }

    private fun seededProfile(itemCount: Int) = DataHolder.People(
        id = ROOT_PERSON.id,
        urlToken = ROOT_PERSON.urlToken,
        name = "离线用户",
        avatarUrl = "https://example.invalid/avatar/root.png",
        url = "https://www.zhihu.com/people/${ROOT_PERSON.urlToken}",
        headline = "离线个人简介",
        gender = 0,
        followerCount = 120,
        followingCount = 45,
        answerCount = itemCount,
        articlesCount = itemCount,
        apiBadgeV2 = DataHolder.BadgeV2(
            title = "优秀答主",
            icon = DataHolder.ZH_PLUS_AUTHOR_BADGE_ICON,
            detailBadges = listOf(
                DataHolder.BadgeV2.Badge(
                    type = "best",
                    detailType = "best_answerer",
                    title = "社区成就",
                    description = "英语等 5 个话题下的优秀答主",
                    icon = DataHolder.ZH_PLUS_AUTHOR_BADGE_ICON,
                    badgeStatus = "passed",
                ),
            ),
        ),
    )

    private fun seededAnswer(
        id: Long,
        questionId: Long = id,
        questionTitle: String = "离线提问 $id",
        excerpt: String = "离线回答摘要 $id",
    ) = DataHolder.Answer(
        answerType = "answer",
        author = seededAuthor("answer-author-$id", "回答作者 $id"),
        canComment = DataHolder.CanComment(status = true, reason = ""),
        content = "<p>离线回答正文 $id</p>",
        createdTime = 1_713_500_000L,
        excerpt = excerpt,
        id = id,
        question = DataHolder.AnswerModelQuestion(
            created = 1_713_400_000L,
            id = questionId,
            questionType = "normal",
            title = questionTitle,
            type = "question",
            updatedTime = 1_713_500_100L,
            url = "https://www.zhihu.com/question/$questionId",
        ),
        thanksCount = 0,
        type = "answer",
        updatedTime = 1_713_500_100L,
        url = "https://www.zhihu.com/question/$questionId/answer/$id",
        voteupCount = id.toInt(),
    )

    private fun seededArticle(id: Long) = DataHolder.Article(
        id = id,
        author = seededAuthor("article-author-$id", "文章作者 $id"),
        canComment = DataHolder.CanComment(status = true, reason = ""),
        title = "离线文章 $id",
        content = "<p>离线文章正文 $id</p>",
        excerpt = "离线文章摘要 $id",
        type = "article",
        created = 1_713_500_000L,
        updated = 1_713_500_200L,
        url = "https://zhuanlan.zhihu.com/p/$id",
        voteupCount = id.toInt(),
    )

    private fun seededQuestion(id: Long) = DataHolder.Question(
        type = "question",
        id = id,
        title = "离线问题 $id",
        questionType = "normal",
        created = 1_713_500_000L,
        updatedTime = 1_713_500_200L,
        url = "https://www.zhihu.com/question/$id",
        answerCount = id.toInt(),
        visitCount = 100 + id.toInt(),
        commentCount = 2,
        followerCount = 10 + id.toInt(),
        detail = "离线问题详情 $id",
        relationship = DataHolder.QuestionRelationship(),
        topics = emptyList(),
        author = seededAuthor("question-author-$id", "提问者 $id"),
        voteupCount = 0,
    )

    private fun seededCollection(index: Int) = DataHolder.Collection(
        id = "collection-$index",
        title = "离线收藏夹 $index",
        url = "https://www.zhihu.com/collection/$index",
        answerCount = index,
        followerCount = 20 + index,
    )

    private fun seededPin(index: Int) = DataHolder.Pin(
        id = index.toString(),
        author = seededAuthor("pin-author-$index", "想法作者 $index"),
        excerptTitle = "离线想法 $index",
        likeCount = index,
        commentCount = index / 2,
    )

    private fun seededColumn(index: Int) = DataHolder.Column(
        id = "column-$index",
        title = "离线专栏 $index",
        description = "专栏描述 $index",
        articlesCount = index,
        followerCount = 30 + index,
    )

    private fun seededFollowedColumn(index: Int) = DataHolder.Column(
        id = "follow-column-$index",
        title = "我订阅的专栏 $index",
        description = "订阅专栏描述 $index",
        articlesCount = index,
        followerCount = 30 + index,
    )

    private fun seededFollowedTopic(index: Int) = FollowedTopic(
        id = "topic-$index",
        name = "关注的话题 $index",
        avatarUrl = "https://example.invalid/topic-$index.png",
    )

    private fun seededFollowedQuestion(index: Int) = FollowedQuestion(
        id = index.toString(),
        title = "关注的问题 $index",
    )

    private fun seededFollowedCollection(index: Int) = DataHolder.Collection(
        id = "follow-collection-$index",
        title = "关注的收藏夹 $index",
        url = "https://www.zhihu.com/collection/follow-collection-$index",
        answerCount = index,
        followerCount = 40 + index,
    )

    private fun seededPeople(prefix: String, index: Int, namePrefix: String) = DataHolder.People(
        id = "$prefix-$index",
        urlToken = "$prefix-token-$index",
        name = "$namePrefix $index",
        avatarUrl = "https://example.invalid/$prefix-$index.png",
        url = "https://www.zhihu.com/people/$prefix-token-$index",
        headline = "$namePrefix $index 的简介",
        gender = 0,
        followerCount = 100 + index,
        followingCount = 50 + index,
        answerCount = index,
        articlesCount = index / 2,
    )

    private fun seededAuthor(id: String, name: String) = DataHolder.Author(
        avatarUrl = "https://example.invalid/avatar/$id.png",
        gender = 0,
        headline = "$name 的签名",
        id = id,
        isAdvertiser = false,
        isOrg = false,
        name = name,
        type = "people",
        url = "https://www.zhihu.com/people/$id",
        urlToken = id,
        userType = "people",
    )

    private companion object {
        val ROOT_PERSON = Person(
            id = "offline-root-person",
            urlToken = "offline-root-person",
            name = "离线用户",
        )
    }
}
