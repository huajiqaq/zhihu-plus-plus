package com.github.zly2006.zhihu.notification

import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.UserPrincipal
import java.util.EnumSet

/** 只存本机通知元数据；缓存目录不参与应用备份，CACHEDIR.TAG 供遵循该规范的外部备份工具排除。 */
object JvmNotificationFiles {
    private val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    private val owner: UserPrincipal by lazy {
        if (windows) {
            Files.getOwner(Path.of(WindowsNotificationBridge.storageDirectory()), NOFOLLOW_LINKS)
        } else {
            Path
                .of(System.getProperty("user.home"))
                .fileSystem.userPrincipalLookupService
                .lookupPrincipalByName(System.getProperty("user.name"))
        }
    }

    fun directory(): Path {
        if (windows) return Path.of(WindowsNotificationBridge.storageDirectory()).also { verifyPrivate(it, directory = true) }
        val home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize()
        val base = System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }?.let { Path.of(it) } ?: home.resolve(".cache")
        check(base.isAbsolute) { "通知私有目录必须是绝对路径" }
        val normalized = base.normalize()
        Files.createDirectories(normalized)
        check(normalized.toRealPath() == normalized) { "通知私有目录不能经过符号链接" }
        val app = normalized.resolve("zhihu-plus-plus-notifications")
        try {
            Files.createDirectory(app, permissions(directory = true))
        } catch (_: FileAlreadyExistsException) {
            /** 多进程同时启动时复用已创建的目录，但仍校验所有者与权限。 */
        }
        verifyPrivate(app, directory = true)
        val tag = app.resolve("CACHEDIR.TAG")
        if (Files.exists(tag, NOFOLLOW_LINKS)) verifyPrivate(tag)
        val temporary = app.resolve("cache-tag-${java.util.UUID.randomUUID()}.tmp")
        try {
            createPrivateFile(temporary).use { channel ->
                val bytes = "Signature: 8a477f597d28d172789f06886806bc55\n# Zhihu++ device-only notification state.\n".toByteArray()
                writeFully(channel, bytes)
                channel.force(true)
            }
            Files.move(temporary, tag, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
        return app
    }

    fun createPrivateFile(path: Path): FileChannel {
        val channel = FileChannel.open(path, setOf(CREATE_NEW, WRITE, NOFOLLOW_LINKS), permissions(directory = false))
        try {
            verifyPrivate(path)
            return channel
        } catch (error: Exception) {
            channel.close()
            Files.deleteIfExists(path)
            throw error
        }
    }

    fun verifyPrivate(path: Path, directory: Boolean = false) {
        check(!Files.isSymbolicLink(path)) { "通知私有文件不能是符号链接" }
        check(if (directory) Files.isDirectory(path, NOFOLLOW_LINKS) else Files.isRegularFile(path, NOFOLLOW_LINKS)) {
            "通知私有文件类型不正确"
        }
        check(path.toRealPath() == path.toAbsolutePath().normalize()) { "通知私有路径不能经过符号链接" }
        if (windows) {
            WindowsNotificationBridge.verifyStorage(path.toAbsolutePath().normalize().toString(), directory)
        } else {
            check(Files.getOwner(path, NOFOLLOW_LINKS) == owner) { "通知私有文件不属于当前系统用户" }
            val expected = PosixFilePermissions.fromString(if (directory) "rwx------" else "rw-------")
            check(Files.getPosixFilePermissions(path, NOFOLLOW_LINKS) == expected) { "通知私有文件权限不正确" }
        }
    }

    private fun permissions(directory: Boolean): FileAttribute<*> {
        if (!windows) return PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(if (directory) "rwx------" else "rw-------"))
        val acl = listOf(
            AclEntry
                .newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission::class.java))
                .build(),
        )
        return object : FileAttribute<List<AclEntry>> {
            override fun name(): String = "acl:acl"

            override fun value(): List<AclEntry> = acl
        }
    }

    internal fun writeFully(channel: FileChannel, bytes: ByteArray) {
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) channel.write(buffer)
    }
}

internal class JvmNotificationStateStore {
    private val json = Json { ignoreUnknownKeys = false }
    private val directory = JvmNotificationFiles.directory()
    private val stateFile = directory.resolve("state.json")

    fun load(): DeviceNotificationState {
        if (!Files.exists(stateFile, NOFOLLOW_LINKS)) return DeviceNotificationState()
        JvmNotificationFiles.verifyPrivate(stateFile)
        return FileChannel.open(stateFile, READ, NOFOLLOW_LINKS).use { channel ->
            check(channel.size() <= 16L * 1024 * 1024) { "本机通知状态过大" }
            val buffer = ByteBuffer.allocate(channel.size().toInt())
            while (buffer.hasRemaining()) check(channel.read(buffer) >= 0) { "本机通知状态不完整" }
            json.decodeFromString<DeviceNotificationState>(buffer.array().decodeToString(throwOnInvalidSequence = true))
        }
    }

    fun save(state: DeviceNotificationState) {
        JvmNotificationFiles.verifyPrivate(directory, directory = true)
        if (Files.exists(stateFile, NOFOLLOW_LINKS)) JvmNotificationFiles.verifyPrivate(stateFile)
        val bytes = json.encodeToString(DeviceNotificationState.serializer(), state).toByteArray()
        check(bytes.size <= 16 * 1024 * 1024) { "本机通知状态过大" }
        val temporary = directory.resolve("state-${java.util.UUID.randomUUID()}.tmp")
        try {
            JvmNotificationFiles.createPrivateFile(temporary).use { channel ->
                JvmNotificationFiles.writeFully(channel, bytes)
                channel.force(true)
            }
            Files.move(temporary, stateFile, ATOMIC_MOVE, REPLACE_EXISTING)
            if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
                FileChannel.open(directory, READ).use { it.force(true) }
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
