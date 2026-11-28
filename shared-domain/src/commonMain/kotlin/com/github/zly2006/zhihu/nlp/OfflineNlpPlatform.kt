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

package com.github.zly2006.zhihu.nlp

/** 模型制品固定到不可变版本；下载后的实际字节数与 SHA-256 均须匹配。 */
data class OfflineNlpModelFile(
    val name: String,
    val url: String,
    val expectedBytes: Long,
    val sha256: String,
)

data class OfflineNlpModelSpec(
    val id: String,
    val revision: String,
    val files: List<OfflineNlpModelFile>,
) {
    val embeddingDimension: Int get() = 768
    val maxTokens: Int get() = 512
    val outputName: String get() = "last_hidden_state"
}

/** 状态只描述本机资源与推理器，不修改已保存的屏蔽规则或启用偏好。 */
sealed interface OfflineNlpModelState {
    data class Unsupported(val reason: OfflineNlpFailure) : OfflineNlpModelState

    data object NotDownloaded : OfflineNlpModelState

    /** 资源已准备过，但推理器已从内存卸载。 */
    data object Unloaded : OfflineNlpModelState

    data class Downloading(
        val fileName: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : OfflineNlpModelState

    data class Verifying(val fileName: String) : OfflineNlpModelState

    data object Loading : OfflineNlpModelState

    data object Ready : OfflineNlpModelState

    data class Error(val failure: OfflineNlpFailure) : OfflineNlpModelState
}

/** 只有小型 tokenizer JSON 跨平台传递；模型文件由平台实现持有路径或持久化句柄。 */
interface OfflineNlpModelAssets {
    val tokenizerJson: String
}

interface OfflineNlpSession {
    /** 三个 int64 输入形状均为 [1, tokenCount]，返回经共享 pooling/归一化处理的 768 维向量。 */
    suspend fun encode(
        inputIds: LongArray,
        attentionMask: LongArray,
        tokenTypeIds: LongArray,
    ): FloatArray

    suspend fun close()
}

/** BERT 只需要这些 Unicode 类别；平台按完整 code point 分类，不能截成 UTF-16 Char。 */
enum class OfflineNlpUnicodeCategory {
    CONTROL_OR_UNASSIGNED,
    NON_SPACING_MARK,
    PUNCTUATION,
    OTHER,
}

interface OfflineNlpPlatform {
    /** 非 null 时不得下载资源或加载 native 库。 */
    val unsupportedReason: OfflineNlpFailure?

    /** BERT 去音调使用规范分解 NFD，不能替换为 NFKD。 */
    fun normalizeNfd(text: String): String

    fun unicodeCategory(codePoint: Int): OfflineNlpUnicodeCategory

    /** 本地有效资源直接返回；否则流式下载、校验并原子发布，不经账号客户端。 */
    suspend fun prepareModel(
        spec: OfflineNlpModelSpec,
        onProgress: (OfflineNlpModelState) -> Unit,
    ): OfflineNlpModelAssets

    /**
     * 后端在会话真正失效时回调一次；null 表示已终止，非 null 为待展示时本地化的错误码与参数。
     * 回调不可挂起，不等待 controller 清理；主动 close 不再回调，仍有效的后台暂停不算失效。
     */
    suspend fun openSession(
        assets: OfflineNlpModelAssets,
        onInvalidated: (failure: OfflineNlpFailure?) -> Unit,
    ): OfflineNlpSession

    /** 调用前 controller 已关闭使用这些文件的会话。 */
    suspend fun deleteModel(spec: OfflineNlpModelSpec)
}

expect fun createOfflineNlpPlatform(): OfflineNlpPlatform
