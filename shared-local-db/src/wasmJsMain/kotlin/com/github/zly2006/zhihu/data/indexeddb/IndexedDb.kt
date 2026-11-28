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

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.data.indexeddb

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.RestrictsSuspension
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal sealed class Table<T>(
    val name: String,
    val serializer: KSerializer<T>,
)

internal class TextTable<T>(
    name: String,
    serializer: KSerializer<T>,
    val key: (T) -> String,
) : Table<T>(name, serializer)

internal class IdTable<T>(
    name: String,
    serializer: KSerializer<T>,
    val id: (T) -> Long,
    val withId: (T, Long) -> T,
    val lookup: ((T) -> String)? = null,
) : Table<T>(name, serializer)

private const val SEQUENCES = "sqlite_sequence"
private const val NATURAL_KEY = "naturalKey"
private const val LOOKUP = "lookup"
private val rowJson = Json {
    encodeDefaults = true
    allowSpecialFloatingPointValues = true
}

/** IndexedDB 不接受 BigInt 键；定宽有序编码保留 SQLite 的有符号 64 位行号。 */
private fun rowKey(id: Long): String = (id xor Long.MIN_VALUE).toULong().toString(16).padStart(16, '0')

private fun nextId(last: Long): Long {
    check(last < Long.MAX_VALUE) { "No more database row IDs" }
    return last + 1
}

internal class IndexedDatabase(
    val name: String,
    private vararg val tables: Table<*>,
) : AutoCloseable {
    private val revisions = tables.associate { it.name to MutableStateFlow(0L) }
    private var opening: CompletableDeferred<IdbDatabase>? = null
    private var connection: IdbDatabase? = null
    private var closed = false
    private val notifications = BroadcastChannel("zhihu++:indexeddb:$name").apply {
        onmessage = { event -> revisions[event.data]?.let { it.value++ } }
    }

    private suspend fun open(): IdbDatabase {
        check(!closed) { "Database is closed: $name" }
        connection?.let { return it }
        opening?.let { return it.await() }
        val request = openDatabase(name)
        val pending = CompletableDeferred<IdbDatabase>()
        opening = pending
        request.onupgradeneeded = {
            val db = checkNotNull(request.result)
            db.createObjectStore(SEQUENCES)
            for (table in tables) {
                val store = db.createObjectStore(table.name)
                if (table is TextTable) store.createIndex(NATURAL_KEY, NATURAL_KEY, indexOptions(true))
                if (table is IdTable && table.lookup != null) store.createIndex(LOOKUP, LOOKUP, indexOptions(false))
            }
        }
        request.onerror = {
            opening = null
            pending.completeExceptionally(IllegalStateException(request.error?.message ?: "Cannot open IndexedDB: $name"))
        }
        request.onsuccess = {
            opening = null
            val db = checkNotNull(request.result)
            if (closed) {
                db.close()
                pending.completeExceptionally(IllegalStateException("Database is closed: $name"))
            } else {
                connection = db
                db.onversionchange = {
                    db.close()
                    connection = null
                }
                db.onclose = { connection = null }
                pending.complete(db)
            }
        }
        return pending.await()
    }

    override fun close() {
        closed = true
        connection?.close()
        connection = null
        notifications.close()
    }

    fun <T> observe(table: Table<*>, query: suspend () -> T): Flow<T> = revisions.getValue(table.name).map { query() }

    suspend fun <T> read(table: Table<*>, block: suspend IndexedTransaction.() -> T): T =
        transact(arrayOf(table.name), false, block)

    suspend fun <T> write(vararg tables: Table<*>, block: suspend IndexedTransaction.() -> T): T =
        transact((tables.map { it.name } + SEQUENCES).toTypedArray(), true, block)

    private suspend fun <T> transact(stores: Array<String>, write: Boolean, block: suspend IndexedTransaction.() -> T): T {
        val db = open()
        return suspendCancellableCoroutine { caller ->
            val tx = db.transaction(stores.map { it.toJsString() }.toTypedArray().toJsArray(), if (write) "readwrite" else "readonly")
            val scope = IndexedTransaction(tx)
            var result: Result<T>? = null
            tx.onabort = {
                caller.resumeWithException(result?.exceptionOrNull() ?: IllegalStateException(tx.error?.message ?: "IndexedDB transaction aborted"))
            }
            tx.oncomplete = {
                scope.changed.forEach { table ->
                    revisions.getValue(table).value++
                    if (!closed) notifications.postMessage(table)
                }
                caller.resumeWith(checkNotNull(result))
            }
            caller.invokeOnCancellation { runCatching { tx.abort() } }
            /** 无调度器且仅允许等待 IDB 请求，下一条请求在当前活动事务的浏览器回调内发出。 */
            block.startCoroutine(
                scope,
                Continuation(EmptyCoroutineContext) { outcome ->
                    result = outcome
                    if (outcome.isFailure) runCatching { tx.abort() }
                },
            )
        }
    }
}

@RestrictsSuspension
internal class IndexedTransaction(
    private val tx: IdbTransaction,
) {
    val changed = mutableSetOf<String>()

    private suspend fun <T : JsAny> IdbRequest<T>.await(): T? = suspendCoroutine { caller ->
        onsuccess = { caller.resume(resultOrNull(this)) }
        onerror = { caller.resumeWithException(IllegalStateException(error?.message ?: "IndexedDB request failed")) }
    }

    private fun <T> decode(table: Table<T>, row: StoredRow): T = rowJson.decodeFromString(table.serializer, row.payload)

    private fun <T> put(table: Table<T>, id: Long, value: T, replace: Boolean) {
        val key = rowKey(id)
        val row = storedRow(
            key,
            rowJson.encodeToString(table.serializer, value),
            (table as? TextTable<T>)?.key?.invoke(value),
            (table as? IdTable<T>)?.lookup?.invoke(value),
        )
        val store = tx.objectStore(table.name)
        if (replace) store.put(row, key) else store.add(row, key)
        changed += table.name
    }

    suspend fun <T> insert(table: IdTable<T>, value: T, replace: Boolean = false): Long {
        val sequences = tx.objectStore(SEQUENCES)
        val last = sequences
            .get(table.name)
            .await()
            ?.payload
            ?.toLong() ?: 0L
        val supplied = table.id(value)
        val id = if (supplied == 0L) nextId(last) else supplied
        if (id > last) sequences.put(storedRow(table.name, id.toString(), null, null), table.name)
        put(table, id, if (id == supplied) value else table.withId(value, id), replace)
        return id
    }

    suspend fun <T> replace(table: TextTable<T>, value: T): Long {
        val store = tx.objectStore(table.name)
        val last = store
            .openKeyCursor(null, "prev")
            .await()
            ?.key
            ?.toULong(16)
            ?.toLong()
            ?.xor(Long.MIN_VALUE) ?: 0L
        val id = nextId(last)
        val previous = store.index(NATURAL_KEY).getKey(table.key(value)).await()
        if (previous != null) store.delete(previous.toString())
        put(table, id, value, false)
        return id
    }

    suspend fun <T> get(table: TextTable<T>, key: String): T? =
        tx
            .objectStore(table.name)
            .index(NATURAL_KEY)
            .get(key)
            .await()
            ?.let { decode(table, it) }

    suspend fun has(table: TextTable<*>, key: String): Boolean = tx
        .objectStore(table.name)
        .index(NATURAL_KEY)
        .getKey(key)
        .await() != null

    suspend fun <T> hasLookup(table: IdTable<T>, key: String, predicate: ((T) -> Boolean)?): Boolean {
        val index = tx.objectStore(table.name).index(LOOKUP)
        if (predicate == null) return index.getKey(key).await() != null
        return checkNotNull(index.getAll(key).await()).toList().any { predicate(decode(table, it)) }
    }

    suspend fun <T> rows(table: Table<T>): List<T> =
        checkNotNull(tx.objectStore(table.name).getAll().await()).toList().map { decode(table, it) }

    suspend fun count(table: Table<*>): Int = checkNotNull(tx.objectStore(table.name).count().await()).toInt()

    suspend fun <T> change(table: TextTable<T>, key: String, transform: (T) -> T?) {
        val row = tx
            .objectStore(table.name)
            .index(NATURAL_KEY)
            .get(key)
            .await() ?: return
        transform(decode(table, row))?.let { put(table, row.key.toULong(16).toLong() xor Long.MIN_VALUE, it, true) }
    }

    suspend fun <T> change(table: IdTable<T>, id: Long, transform: (T) -> T?): Int {
        val row = tx.objectStore(table.name).get(rowKey(id)).await() ?: return 0
        val updated = transform(decode(table, row)) ?: return 0
        put(table, id, updated, true)
        return 1
    }

    suspend fun delete(table: TextTable<*>, key: String) {
        val store = tx.objectStore(table.name)
        val rowId = store.index(NATURAL_KEY).getKey(key).await() ?: return
        store.delete(rowId.toString())
        changed += table.name
    }

    suspend fun <T> delete(table: IdTable<T>, id: Long, predicate: ((T) -> Boolean)? = null) {
        val store = tx.objectStore(table.name)
        val key = rowKey(id)
        val row = store.get(key).await() ?: return
        if (predicate == null || predicate(decode(table, row))) {
            store.delete(key)
            changed += table.name
        }
    }

    suspend fun <T> deleteWhere(table: Table<T>, predicate: (T) -> Boolean) {
        val store = tx.objectStore(table.name)
        for (row in checkNotNull(store.getAll().await()).toList()) {
            if (predicate(decode(table, row))) {
                store.delete(row.key)
                changed += table.name
            }
        }
    }

    suspend fun clear(table: Table<*>) {
        if (count(table) != 0) {
            tx.objectStore(table.name).clear()
            changed += table.name
        }
    }
}

internal fun <T> List<T>.sqlLimit(limit: Int): List<T> = if (limit < 0) this else take(limit)

/** SQLite BINARY 按 UTF-8 比较；补充平面码点排在全部 BMP 字符之后。 */
internal val sqliteTextOrder = Comparator<String> { left, right ->
    for (i in 0 until minOf(left.length, right.length)) {
        val a = left[i]
        val b = right[i]
        if (a != b) {
            val first = if (a.isSurrogate()) a.code + 0x10000 else a.code
            val second = if (b.isSurrogate()) b.code + 0x10000 else b.code
            return@Comparator first.compareTo(second)
        }
    }
    left.length.compareTo(right.length)
}

private fun openDatabase(name: String): IdbOpenRequest = js("globalThis.indexedDB.open(name, 1)")

private fun indexOptions(unique: Boolean): JsAny = js("({ unique })")

private fun storedRow(key: String, payload: String, naturalKey: String?, lookup: String?): StoredRow = js("({ key, payload, naturalKey, lookup })")

private fun <T : JsAny> resultOrNull(request: IdbRequest<T>): T? = js("request.result ?? null")

internal external interface StoredRow : JsAny {
    val key: String
    val payload: String
}

internal external interface KeyCursor : JsAny {
    val key: String
}

internal external interface IdbError : JsAny {
    val message: String
}

internal external interface IdbRequest<T : JsAny> : JsAny {
    val result: T?
    val error: IdbError?
    var onsuccess: (() -> Unit)?
    var onerror: (() -> Unit)?
}

private external interface IdbOpenRequest : IdbRequest<IdbDatabase> {
    var onupgradeneeded: (() -> Unit)?
}

private external interface IdbDatabase : JsAny {
    fun createObjectStore(name: String): IdbStore

    fun transaction(names: JsArray<JsString>, mode: String): IdbTransaction

    fun close()

    var onversionchange: (() -> Unit)?
    var onclose: (() -> Unit)?
}

internal external interface IdbTransaction : JsAny {
    fun objectStore(name: String): IdbStore

    fun abort()

    val error: IdbError?
    var onabort: (() -> Unit)?
    var oncomplete: (() -> Unit)?
}

internal external interface IdbStore : JsAny {
    fun createIndex(name: String, path: String, options: JsAny): IdbIndex

    fun index(name: String): IdbIndex

    fun get(key: String): IdbRequest<StoredRow>

    fun getAll(): IdbRequest<JsArray<StoredRow>>

    fun count(): IdbRequest<JsNumber>

    fun openKeyCursor(query: JsAny?, direction: String): IdbRequest<KeyCursor>

    fun put(value: StoredRow, key: String)

    fun add(value: StoredRow, key: String)

    fun delete(key: String)

    fun clear()
}

internal external interface IdbIndex : JsAny {
    fun get(key: String): IdbRequest<StoredRow>

    fun getKey(key: String): IdbRequest<JsString>

    fun getAll(key: String): IdbRequest<JsArray<StoredRow>>
}

private external class BroadcastChannel(
    name: String,
) : JsAny {
    var onmessage: ((StringMessage) -> Unit)?

    fun postMessage(value: String)

    fun close()
}

private external interface StringMessage : JsAny {
    val data: String
}
