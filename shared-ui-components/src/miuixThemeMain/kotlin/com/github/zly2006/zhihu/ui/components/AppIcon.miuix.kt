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

package com.github.zly2006.zhihu.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Cut
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.MicSlash
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Paste
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Redo
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.icon.extended.Reply
import top.yukonga.miuix.kmp.icon.extended.ReplyAll
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.extended.Translate
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.icon.extended.Unlock
import top.yukonga.miuix.kmp.icon.extended.VolumeOff
import top.yukonga.miuix.kmp.icon.extended.VolumeUp

/** 只匹配 Material 图标，未确认的业务语义不能用相似字形替代。 */
@Composable
internal actual fun miuixAppIconVector(image: ImageVector): ImageVector {
    when (image.name.substringBeforeLast('.')) {
        "Filled", "Outlined", "Rounded", "Sharp", "TwoTone",
        "AutoMirrored.Filled", "AutoMirrored.Outlined", "AutoMirrored.Rounded",
        "AutoMirrored.Sharp", "AutoMirrored.TwoTone",
        -> Unit
        else -> return image
    }
    val native = when (image.name.substringAfterLast('.')) {
        "Home" -> MiuixIcons.Home
        "Newspaper", "Summarize" -> MiuixIcons.Notes
        "History" -> MiuixIcons.Recent
        "Bookmarks", "Folder" -> MiuixIcons.Folder
        "AccountCircle", "Person" -> MiuixIcons.Contacts
        "Add" -> MiuixIcons.Add
        "AddCircle", "AddCircleOutline" -> MiuixIcons.AddCircle
        "CreateNewFolder" -> MiuixIcons.AddFolder
        "ArrowBack" -> MiuixIcons.Back
        "ArrowBackIos", "ArrowBackIosNew", "KeyboardArrowLeft", "ChevronLeft", "NavigateBefore" -> MiuixIcons.ChevronBackward
        "ArrowForward" -> MiuixIcons.Forward
        "ArrowForwardIos" -> MiuixIcons.ChevronForward
        "KeyboardArrowRight", "ChevronRight", "NavigateNext" -> MiuixIcons.Basic.ArrowRight
        "Search" -> MiuixIcons.Basic.Search
        "Clear" -> MiuixIcons.Basic.SearchCleanup
        "Close" -> MiuixIcons.Basic.Close
        "Check", "Done" -> MiuixIcons.Basic.Check
        "MoreVert" -> MiuixIcons.More
        "Settings" -> MiuixIcons.Settings
        "Share" -> MiuixIcons.Share
        "Edit" -> MiuixIcons.Edit
        "ContentCopy" -> MiuixIcons.Copy
        "ContentCut" -> MiuixIcons.Cut
        "ContentPaste" -> MiuixIcons.Paste
        "Download", "FileDownload", "GetApp" -> MiuixIcons.Download
        "Send" -> MiuixIcons.Send
        "PlayArrow" -> MiuixIcons.Play
        "Pause" -> MiuixIcons.Pause
        "Mic", "MicNone" -> MiuixIcons.Mic
        "MicOff" -> MiuixIcons.MicSlash
        "VolumeUp" -> MiuixIcons.VolumeUp
        "VolumeOff" -> MiuixIcons.VolumeOff
        "Visibility" -> MiuixIcons.Show
        "VisibilityOff" -> MiuixIcons.Hide
        "Delete", "DeleteOutline" -> MiuixIcons.Delete
        "Remove" -> MiuixIcons.Remove
        "Refresh" -> MiuixIcons.Refresh
        "Undo" -> MiuixIcons.Undo
        "Redo" -> MiuixIcons.Redo
        "Reply" -> MiuixIcons.Reply
        "ReplyAll" -> MiuixIcons.ReplyAll
        "QueueMusic" -> MiuixIcons.Playlist
        "Link" -> MiuixIcons.Link
        "Image" -> MiuixIcons.Image
        "Info", "InfoOutline" -> MiuixIcons.Info
        "Help", "HelpOutline" -> MiuixIcons.Help
        "FilterList", "FilterAlt" -> MiuixIcons.Filter
        "Sort" -> MiuixIcons.Sort
        "Tune" -> MiuixIcons.Tune
        "List", "ViewList" -> MiuixIcons.ListView
        "Translate", "GTranslate" -> MiuixIcons.Translate
        "Lock", "LockOutline" -> MiuixIcons.Lock
        "LockOpen" -> MiuixIcons.Unlock
        "QrCodeScanner" -> MiuixIcons.Scan
        else -> return image
    }
    if (!image.autoMirror || native.autoMirror) return native
    /** Miuix 0.9.4 方向图标未启用自动镜像，复制几何以保留 RTL 契约。 */
    return remember(native) {
        val builder = ImageVector.Builder(
            name = native.name,
            defaultWidth = native.defaultWidth,
            defaultHeight = native.defaultHeight,
            viewportWidth = native.viewportWidth,
            viewportHeight = native.viewportHeight,
            tintColor = native.tintColor,
            tintBlendMode = native.tintBlendMode,
            autoMirror = true,
        )

        fun copyGroup(group: VectorGroup) {
            builder.addGroup(
                name = group.name,
                rotate = group.rotation,
                pivotX = group.pivotX,
                pivotY = group.pivotY,
                scaleX = group.scaleX,
                scaleY = group.scaleY,
                translationX = group.translationX,
                translationY = group.translationY,
                clipPathData = group.clipPathData,
            )
            for (node in group) {
                when (node) {
                    is VectorGroup -> copyGroup(node)
                    is VectorPath -> builder.addPath(
                        pathData = node.pathData,
                        pathFillType = node.pathFillType,
                        name = node.name,
                        fill = node.fill,
                        fillAlpha = node.fillAlpha,
                        stroke = node.stroke,
                        strokeAlpha = node.strokeAlpha,
                        strokeLineWidth = node.strokeLineWidth,
                        strokeLineCap = node.strokeLineCap,
                        strokeLineJoin = node.strokeLineJoin,
                        strokeLineMiter = node.strokeLineMiter,
                        trimPathStart = node.trimPathStart,
                        trimPathEnd = node.trimPathEnd,
                        trimPathOffset = node.trimPathOffset,
                    )
                }
            }
            builder.clearGroup()
        }
        copyGroup(native.root)
        builder.build()
    }
}
