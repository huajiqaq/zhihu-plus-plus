package com.github.zly2006.zhihu.ui.subscreens

import com.github.zly2006.zhihu.platform.desktopSettingsStore
import com.github.zly2006.zhihu.theme.ThemeManager
import com.github.zly2006.zhihu.theme.ThemeStyle
import java.nio.file.Files

/** 把 user.home 指到临时目录并固定 M3E 皮肤：页面读写的设置和主题都不碰开发机上的真实文件。 */
internal fun withIsolatedDesktopHome(block: () -> Unit) {
    val originalUserHome = System.getProperty("user.home")
    val originalTheme = ThemeManager.snapshot()
    val home = Files.createTempDirectory("zhihu-settings-ui-")
    try {
        System.setProperty("user.home", home.toString())
        desktopSettingsStore().putString("uiThemeStyle", "MATERIAL_EXPRESSIVE")
        ThemeManager.setThemeStyle(ThemeStyle.MATERIAL_EXPRESSIVE)
        block()
    } finally {
        ThemeManager.load(originalTheme)
        System.setProperty("user.home", originalUserHome)
        home.toFile().deleteRecursively()
    }
}
