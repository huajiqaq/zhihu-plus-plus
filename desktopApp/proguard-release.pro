-dontwarn com.hrm.latex.parser.tokenizer.LatexTokenizer$Companion

# LaTeX 1.6.3 的独立图片导出器引用旧 Skiko 双参数 ABI；应用未调用该导出包，release 收缩必须移除它。
-dontwarn com.hrm.latex.renderer.export.LatexExporter_jvmKt

# JavaFX toolkit、glass/prism 窗口栈依赖按名反射，整包保留以免运行时找不到 toolkit。
-keep class javafx.** { *; }
-keep class com.sun.javafx.** { *; }
-keep class com.sun.glass.** { *; }
-keep class com.sun.prism.** { *; }
-keep class com.sun.scenario.** { *; }
-keep class com.sun.pisces.** { *; }
-keep class com.sun.media.** { *; }
-keep class com.sun.openjfx.** { *; }
-keep class netscape.javascript.** { *; }

# WebKit JNI 与 NativeLibLoader 按名查找桥接类，裁剪会阻止 WebView 和网页登录启动。
-keep class com.sun.webkit.** { *; }

# WebKit 通过 JavaScript 反射调用安全验证桥，方法名不能被裁剪或改名。
-keep class com.github.zly2006.zhihu.account.JvmVerificationBridge {
    public void postMessage(java.lang.String);
}

# Coil 通过 ServiceLoader 创建网络下载器；服务描述仍在但 provider 被裁剪时，所有网络图片都会加载失败。
-keep class coil3.network.ktor3.internal.KtorNetworkFetcherServiceLoaderTarget { *; }

-keep class * implements io.ktor.client.HttpClientEngineContainer {
    *;
}

-keep class * implements io.ktor.serialization.kotlinx.KotlinxSerializationExtensionProvider {
    *;
}

-keep class io.ktor.client.engine.cio.CIOEngineContainer {
    *;
}

-keep class io.ktor.client.engine.java.JavaHttpEngineContainer {
    *;
}

-keep class io.ktor.serialization.kotlinx.json.KotlinxSerializationJsonExtensionProvider {
    *;
}

-keep interface com.github.zly2006.zhihu.viewmodel.PaginationEnvironment {
    *;
}

-keep interface com.github.zly2006.zhihu.viewmodel.CollectionContentEnvironment {
    *;
}

-keep class * implements com.github.zly2006.zhihu.viewmodel.PaginationEnvironment {
    *;
}

-keep class com.github.zly2006.zhihu.viewmodel.filter.*_Impl {
    *;
}

-keep class com.github.zly2006.zhihu.viewmodel.local.*_Impl {
    *;
}

-keep class * extends androidx.room.RoomDatabase {
    *;
}

-keep @androidx.room.Database class * {
    *;
}

-keep @androidx.room.Dao class * {
    *;
}

-keep class androidx.sqlite.driver.bundled.** {
    *;
}

-keepclasseswithmembernames class * {
    native <methods>;
}

# 保持服务描述中的 provider 类名与收缩后的类一致。
-adaptresourcefilecontents META-INF/services/**
