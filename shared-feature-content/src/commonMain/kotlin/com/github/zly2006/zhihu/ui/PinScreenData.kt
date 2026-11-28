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

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.github.zly2006.zhihu.account.ZhihuRequestProfile
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.LocalHistoryStorage
import com.github.zly2006.zhihu.data.decodePinContentDetail
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.viewmodel.ContentLoadEnvironment
import com.github.zly2006.zhihu.viewmodel.ZhihuApiEnvironment
import com.github.zly2006.zhihu.viewmodel.addReadHistory
import com.github.zly2006.zhihu.viewmodel.setAuthorFollowing
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add

internal class PinScreenData : ViewModel() {
    val content = mutableStateOf<DataHolder.Pin?>(null)
    val isLiked = mutableStateOf(false)
    val likeCount = mutableIntStateOf(0)
    val errorMessage = mutableStateOf<String?>(null)
    val isLoading = mutableStateOf(false)
    val authorFollowChanging = mutableStateOf(false)
    val authorFollowError = mutableStateOf<String?>(null)
    var contentClient: HttpClient? = null
        private set
    private var detailLoadJob: Job? = null
    private var detailLoadClient: HttpClient? = null
    private var detailLoadGeneration = 0
    private var authorFollowJob: Job? = null
    private var authorFollowGeneration = 0

    override fun onCleared() {
        detailLoadGeneration++
        detailLoadJob?.cancel()
        cancelAuthorFollow()
        super.onCleared()
    }

    fun canFollowAuthor(environment: ZhihuApiEnvironment): Boolean {
        val pin = content.value ?: return false
        return contentClient === environment.httpClient() &&
            environment.isLoggedIn &&
            !pin.selfCreate &&
            pin.author.id.isNotBlank() &&
            pin.author.id != environment.accountClient
                ?.load()
                ?.profile
                ?.id &&
            pin.author.urlToken.isNotBlank()
    }

    fun cancelAuthorFollow() {
        authorFollowGeneration++
        authorFollowJob?.cancel()
        authorFollowJob = null
        authorFollowChanging.value = false
        authorFollowError.value = null
    }

    suspend fun toggleAuthorFollow(environment: ZhihuApiEnvironment) {
        if (!canFollowAuthor(environment) || isLoading.value || authorFollowChanging.value) return
        val pin = content.value ?: return
        val owner = contentClient ?: return
        val following = !pin.author.isFollowing
        val generation = ++authorFollowGeneration
        authorFollowChanging.value = true
        authorFollowError.value = null
        authorFollowJob = currentCoroutineContext()[Job]
        try {
            environment.setAuthorFollowing(owner, pin.author.urlToken, following)
            if (generation != authorFollowGeneration || !canFollowAuthor(environment)) return
            val current = content.value ?: return
            if (current.author.id == pin.author.id && current.author.urlToken == pin.author.urlToken) {
                content.value = current.copy(author = current.author.copy(isFollowing = following))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (generation == authorFollowGeneration && environment.httpClient() === owner) {
                authorFollowError.value = if (following) ContentStrings.followFailedRetry else ContentStrings.unfollowFailedRetry
            }
        } finally {
            if (generation == authorFollowGeneration) {
                authorFollowChanging.value = false
                authorFollowJob = null
            }
        }
    }

    suspend fun load(environment: ContentLoadEnvironment, pin: Pin) {
        val client = environment.httpClient()
        if (isLoading.value && detailLoadClient === client) return
        detailLoadJob?.cancel()
        cancelAuthorFollow()
        val generation = ++detailLoadGeneration
        detailLoadClient = client
        detailLoadJob = currentCoroutineContext()[Job]
        isLoading.value = true
        errorMessage.value = null
        try {
            environment.addReadHistory(pin.id.toString(), "pin")
            currentCoroutineContext().ensureActive()
            if (generation != detailLoadGeneration || environment.httpClient() !== client) {
                throw CancellationException("加载已失效")
            }
            val jsonObject = environment
                .get("https://api.zhihu.com/v2/pins/${pin.id}", profile = ZhihuRequestProfile.Mobile)
                .raiseForStatus()
                .body<JsonObject>()
            val loadedPin = decodePinContentDetail(jsonObject)
            currentCoroutineContext().ensureActive()
            if (generation != detailLoadGeneration || environment.httpClient() !== client) {
                throw CancellationException("加载已失效")
            }
            LocalHistoryStorage().add(pin.copy(authorName = loadedPin.author.name))
            environment.recordContentOpenEvent(destination = pin)
            currentCoroutineContext().ensureActive()
            if (generation != detailLoadGeneration || environment.httpClient() !== client) {
                throw CancellationException("加载已失效")
            }
            contentClient = client
            content.value = loadedPin
            isLiked.value = loadedPin.virtuals.booleanCompat("isLiked", "is_liked")
            likeCount.intValue = loadedPin.likeCount
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (generation == detailLoadGeneration && environment.httpClient() === client) {
                errorMessage.value = error.message?.takeIf(String::isNotBlank) ?: ContentStrings.pinDetailLoadFailed
            }
        } finally {
            if (generation == detailLoadGeneration) {
                isLoading.value = false
                detailLoadJob = null
            }
        }
    }
}
