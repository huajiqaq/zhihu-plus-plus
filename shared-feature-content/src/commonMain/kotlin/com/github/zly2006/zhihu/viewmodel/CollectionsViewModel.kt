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

package com.github.zly2006.zhihu.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.zly2006.zhihu.data.ZhihuCollection
import com.github.zly2006.zhihu.i18n.ContentStrings
import io.ktor.client.call.body
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.reflect.typeOf
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class CollectionsViewModel(
    val urlToken: String,
) : PaginationViewModel<ZhihuCollection>(typeOf<ZhihuCollection>()) {
    var isCreatingCollection by mutableStateOf(false)
        private set
    var createCollectionError by mutableStateOf<String?>(null)
        private set
    var deletingCollectionId by mutableStateOf<String?>(null)
        private set
    var deleteCollectionError by mutableStateOf<String?>(null)
        private set

    override val initialUrl: String
        get() = "https://www.zhihu.com/api/v4/people/$urlToken/collections"

    suspend fun createCollection(
        environment: ZhihuApiEnvironment,
        title: String,
        description: String,
        isPublic: Boolean,
    ): Boolean {
        if (isCreatingCollection) return false
        isCreatingCollection = true
        createCollectionError = null
        return try {
            val response = environment.post("https://www.zhihu.com/api/v4/collections") {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("title", title)
                        put("description", description)
                        put("is_public", isPublic)
                    },
                )
            }
            if (!response.status.isSuccess()) {
                error(ContentStrings.collectionCreateError(response.status))
            }
            val responseBody = response.body<JsonObject>()
            val collectionId = (responseBody["collection"] as? JsonObject)
                ?.get("id")
                ?.jsonPrimitive
                ?.contentOrNull
            if (responseBody["status"]?.jsonPrimitive?.intOrNull != 100 || collectionId.isNullOrBlank()) {
                error(
                    responseBody["message"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.takeIf(String::isNotBlank)
                        ?: ContentStrings.collectionCreateInvalidResponse,
                )
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            createCollectionError = e.message ?: ContentStrings.collectionCreateFailed
            false
        } finally {
            isCreatingCollection = false
        }
    }

    suspend fun deleteCollection(
        environment: ZhihuApiEnvironment,
        collection: ZhihuCollection,
    ): Boolean {
        if (deletingCollectionId != null) return false
        if (collection.isDefault) {
            deleteCollectionError = ContentStrings.defaultCollectionCannotDelete
            return false
        }
        deletingCollectionId = collection.id
        deleteCollectionError = null
        return try {
            val response = environment.delete(
                "https://www.zhihu.com/api/v4/collections/${collection.id}",
            )
            if (!response.status.isSuccess()) {
                error(ContentStrings.collectionDeleteError(response.status))
            }
            val responseBody = response.body<JsonObject>()
            if (responseBody["success"]?.jsonPrimitive?.booleanOrNull != true) {
                error(
                    responseBody["message"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.takeIf(String::isNotBlank)
                        ?: ContentStrings.collectionDeleteInvalidResponse,
                )
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            deleteCollectionError = e.message ?: ContentStrings.collectionDeleteFailed
            false
        } finally {
            deletingCollectionId = null
        }
    }

    fun clearMutationErrors() {
        createCollectionError = null
        deleteCollectionError = null
    }
}
