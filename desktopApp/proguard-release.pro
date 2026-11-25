-dontwarn com.hrm.latex.parser.tokenizer.LatexTokenizer$Companion

# PDFBox 的 mmap 清理器使用 JDK 签名多态调用，ProGuard 7.8.0 按普通方法描述符误报。
# 待 https://github.com/Guardsquare/proguard-core/pull/180 随正式版本发布后移除。
-dontwarn org.apache.pdfbox.io.IOUtils

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

# D-Bus 使用反射读取接口泛型、注解和信号构造器；只保留实际协议模型与系统总线模型。
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keep interface com.github.zly2006.zhihu.notification.FreedesktopNotifications { *; }
-keep class com.github.zly2006.zhihu.notification.FreedesktopNotifications$ActionInvoked { *; }
-keep class com.github.zly2006.zhihu.notification.FreedesktopNotifications$NotificationClosed { *; }
-keep interface org.freedesktop.dbus.interfaces.DBus { *; }
-keep interface org.freedesktop.dbus.interfaces.DBusInterface { *; }
-keep class org.freedesktop.dbus.interfaces.DBus$NameAcquired { *; }
-keep class org.freedesktop.dbus.interfaces.DBus$NameLost { *; }
-keep class org.freedesktop.dbus.interfaces.DBus$NameOwnerChanged { *; }
-keep interface org.freedesktop.dbus.interfaces.Peer { *; }
-keep interface org.freedesktop.dbus.interfaces.Introspectable { *; }
-keep class org.freedesktop.dbus.transport.jre.NativeTransportProvider { public <init>(); }

# MSIX 原生宿主只查找这两个固定入口；普通 MSI 不加载任何通知库。
-keep class com.github.zly2006.zhihu.desktop.MainKt {
    public static void main();
    public static void main(java.lang.String[]);
}
-keep class com.github.zly2006.zhihu.notification.WindowsNotificationBridge {
    private native boolean connected();
    public native java.lang.String storageDirectory();
    public native void verifyStorage(java.lang.String, boolean);
    public native java.lang.String newReceipt();
    public native boolean permissionGranted();
    public native java.lang.String takeReceipt();
    public native void publish(java.lang.String);
    public native void withdraw(java.lang.String);
    public native void disconnect();
}

# 保持服务描述中的 provider 类名与收缩后的类一致。
-adaptresourcefilecontents META-INF/services/**
