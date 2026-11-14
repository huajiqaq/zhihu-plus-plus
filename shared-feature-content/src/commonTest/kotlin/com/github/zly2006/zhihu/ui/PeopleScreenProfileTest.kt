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

import io.ktor.client.engine.mock.MockEngine

import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.viewmodel.ProfileLoadEnvironment
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PeopleScreenProfileTest {
    @Test
    fun clearingTheProfileOwnerCancelsEveryChildFeedScope() {
        val model = PersonViewModel(person())
        val children = arrayOf(*model.subFeedModels, model.followingCollectionsFeedModel, model.followingQuestionsFeedModel, model.followingTopicsFeedModel, model.followingColumnsFeedModel)
        val jobs = children.map { checkNotNull(it.viewModelScope.coroutineContext[Job]) }
        val owner = ViewModelStore()
        ViewModelProvider.create(owner, viewModelFactory { initializer { model } })[PersonViewModel::class]
        owner.clear()
        assertEquals(13, jobs.size)
        assertTrue(jobs.all { it.isCancelled }, "父资料页释放时不能留下独立分页协程")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun profileLoadingFailureAndRetryKeepOriginalIdentityAndCachedContent() = runTest {
        var response: suspend () -> JsonObject = { people(isBlocking = true).asJsonObject() }
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine { error("Unexpected HTTP request") })
        val environment = object : ProfileLoadEnvironment {
            override fun httpClient(): HttpClient = client
            override fun authenticatedCookies(): Map<String, String> = emptyMap()
            override suspend fun fetchJson(url: String, include: String): JsonObject {
                requests += url
                return response()
            }
            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        val model = PersonViewModel(person())
        model.load(environment)
        assertTrue(model.hasLoadedProfile)
        assertEquals("Profile User", model.name)
        assertTrue(model.isBlocking)
        assertFalse(model.isProfileLoading)
        assertNull(model.profileErrorMessage)

        val gate = CompletableDeferred<JsonObject>()
        response = { gate.await() }
        val reload = launch { model.load(environment) }
        runCurrent()
        assertTrue(model.isProfileLoading)
        assertEquals("Profile User", model.name)
        assertTrue(model.isBlocking)
        val requestsWhileLoading = requests.size
        model.load(environment)
        assertEquals(requestsWhileLoading, requests.size)
        gate.completeExceptionally(IllegalStateException("offline profile"))
        reload.join()
        assertFalse(model.isProfileLoading)
        assertEquals("offline profile", model.profileErrorMessage)
        assertTrue(model.hasLoadedProfile)
        assertEquals("profile-id", model.memberHashId)
        assertEquals("profile-user", model.person.urlToken)
        assertEquals("Profile User", model.name)
        assertTrue(model.isBlocking)

        response = { people(isBlocking = false).copy(name = "更新用户").asJsonObject() }
        model.load(environment)
        assertNull(model.profileErrorMessage)
        assertFalse(model.isProfileLoading)
        assertEquals("更新用户", model.name)
        assertFalse(model.isBlocking)
        assertEquals(5, requests.size)
        client.close()
    }

    @Test
    fun decodesLiveSocialMediaShapeAndMapsGithubStars() {
        val rawProfile = ZhihuJson.json
            .parseToJsonElement(
                """
                {
                  "id": "profile-id",
                  "url_token": "profile-user",
                  "name": "Profile User",
                  "avatar_url": "https://example.invalid/avatar.png",
                  "url": "https://www.zhihu.com/people/profile-user",
                  "headline": "",
                  "gender": 0,
                  "socialMedias": [
                    {
                      "icon": "https://example.invalid/github.png",
                      "title": "GitHub·zly2006",
                      "link": "zhihu://hybrid?zh_hide_nav_bar=true&zh_url=https:%2F%2Fwww.zhihu.com%2Fappview%2Fgithub%2Fdetail%3Fhash_id=ea09b6c82124e0162caa10d658058c10",
                      "modules": [
                        {"title": "被关注人", "value": "169"},
                        {"title": "公开仓库数", "value": "119"},
                        {"title": "stars", "value": "4.0k"}
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            ).jsonObject

        val githubSocial = ZhihuJson.decodeJson<DataHolder.People>(rawProfile).githubSocialUiState()

        assertEquals("GitHub·zly2006", githubSocial?.title)
        assertEquals("4.0k", githubSocial?.starCount)
        assertEquals("https://github.com/zly2006", githubSocial?.profileUrl)
        assertEquals("https://example.invalid/github.png", githubSocial?.iconUrl)
    }

    @Test
    fun ignoresNonGithubAndGithubEntriesWithoutStars() {
        val profile = people(
            socialMedias = listOf(
                DataHolder.SocialMedia(
                    title = "Other",
                    modules = listOf(DataHolder.SocialMediaModule("stars", "10")),
                ),
                DataHolder.SocialMedia(
                    title = "GitHub·zly2006",
                    link = "https://github.com/zly2006",
                    modules = listOf(DataHolder.SocialMediaModule("公开仓库数", "119")),
                ),
            ),
        )

        assertNull(profile.githubSocialUiState())
    }

    @Test
    fun ignoresGithubEntriesWithoutProfileLink() {
        val profile = people(
            socialMedias = listOf(
                DataHolder.SocialMedia(
                    title = "GitHub·zly2006",
                    modules = listOf(DataHolder.SocialMediaModule("stars", "4.0k")),
                ),
            ),
        )

        assertNull(profile.githubSocialUiState())
    }

    @Test
    fun profileDetailOnlyAddsGithubDataAndKeepsBaseBlockingState() = runTest {
        val environment = RecordingProfileEnvironment(
            baseProfile = people(isBlocking = true).asJsonObject(),
            detailResult = Result.success(
                people(
                    isBlocking = false,
                    socialMedias = listOf(githubSocialMedia()),
                ).asJsonObject(),
            ),
        )
        val viewModel = PersonViewModel(person())

        viewModel.load(environment)

        assertTrue(viewModel.isBlocking)
        assertEquals("4.0k", viewModel.githubSocial?.starCount)
        assertEquals("https://github.com/zly2006", viewModel.githubSocial?.profileUrl)
        assertEquals(
            listOf(
                "https://api.zhihu.com/people/profile-user" to PEOPLE_PROFILE_INCLUDE_PATH,
                "https://api.zhihu.com/people/profile-user/profile/detail" to "",
            ),
            environment.requests,
        )
        environment.close()
    }

    @Test
    fun profileDetailFailureDoesNotFailBaseProfileLoad() = runTest {
        val environment = RecordingProfileEnvironment(
            baseProfile = people(isBlocking = true).asJsonObject(),
            detailResult = Result.failure(IllegalStateException("optional detail unavailable")),
        )
        val viewModel = PersonViewModel(person())

        viewModel.load(environment)

        assertEquals("Profile User", viewModel.name)
        assertTrue(viewModel.isBlocking)
        assertNull(viewModel.githubSocial)
        environment.close()
    }

    private fun person() = Person(
        id = "profile-id",
        name = "Profile User",
        urlToken = "profile-user",
    )

    private fun people(
        isBlocking: Boolean = false,
        socialMedias: List<DataHolder.SocialMedia> = emptyList(),
    ) = DataHolder.People(
        id = "profile-id",
        urlToken = "profile-user",
        name = "Profile User",
        avatarUrl = "https://example.invalid/avatar.png",
        url = "https://www.zhihu.com/people/profile-user",
        headline = "",
        gender = 0,
        isBlocking = isBlocking,
        socialMedias = socialMedias,
    )

    private fun githubSocialMedia() = DataHolder.SocialMedia(
        icon = "https://example.invalid/github.png",
        title = "GitHub·zly2006",
        link = "https://github.com/zly2006",
        modules = listOf(
            DataHolder.SocialMediaModule("被关注人", "169"),
            DataHolder.SocialMediaModule("公开仓库数", "119"),
            DataHolder.SocialMediaModule("stars", "4.0k"),
        ),
    )

    private fun DataHolder.People.asJsonObject(): JsonObject = ZhihuJson.json.encodeToJsonElement(this).jsonObject

    private class RecordingProfileEnvironment(
        private val baseProfile: JsonObject,
        private val detailResult: Result<JsonObject?>,
    ) : ProfileLoadEnvironment {
        val requests = mutableListOf<Pair<String, String>>()

        private val client = HttpClient(MockEngine { error("Unexpected HTTP request") })

        override fun httpClient(): HttpClient = client

        fun close() = client.close()

        override fun authenticatedCookies(): Map<String, String> = emptyMap()

        override suspend fun fetchJson(
            url: String,
            include: String,
        ): JsonObject? {
            requests += url to include
            return if (url.endsWith("/profile/detail")) {
                detailResult.getOrThrow()
            } else {
                baseProfile
            }
        }

        override suspend fun handleFetchFailure(
            tag: String?,
            error: Exception,
        ) = Unit
    }
}
