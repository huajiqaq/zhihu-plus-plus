-dontwarn com.hrm.latex.parser.tokenizer.LatexTokenizer$Companion

# PDFBox 的 mmap 清理器与 ORT FP16 转换使用 JDK 签名多态调用，ProGuard 7.8.0 按普通方法描述符误报。
# 待 https://github.com/Guardsquare/proguard-core/pull/180 随正式版本发布后移除。
-dontwarn org.apache.pdfbox.io.IOUtils
-dontwarn ai.onnxruntime.platform.Fp16Conversions

# PDF 导出只创建不加密的文档，不使用 PDFBox POM 中 optional 的 BouncyCastle 公钥加密。
-dontwarn org.apache.pdfbox.pdmodel.encryption.PublicKeySecurityHandler
-dontwarn org.apache.pdfbox.pdmodel.encryption.SecurityProvider

# BatikSVGDrawer 默认 SECURE，不执行 Batik POM 中 optional 的 Jython 脚本。
-dontwarn org.apache.batik.script.jpython.JPythonInterpreter

# jsoup 的 optional RE2J 不在运行时；Regex.hasRe2j() 缺库时使用 JDK 正则。
-dontwarn org.jsoup.helper.Re2jRegex
-dontwarn org.jsoup.helper.Re2jRegex$Re2jMatcher

# XMPBox 的 TypeMapping/XMPSchemaFactory 反射读取属性注解并调用模型构造器。
-keepclassmembers class org.apache.xmpbox.** {
    @org.apache.xmpbox.type.PropertyType <fields>;
}
-keepclassmembers class org.apache.xmpbox.schema.* {
    public <init>(org.apache.xmpbox.XMPMetadata,...);
}
-keepclassmembers class * extends org.apache.xmpbox.type.AbstractSimpleProperty {
    public <init>(org.apache.xmpbox.XMPMetadata,java.lang.String,java.lang.String,java.lang.String,java.lang.Object);
}
-keepclassmembers class * extends org.apache.xmpbox.type.AbstractStructuredType {
    public <init>(org.apache.xmpbox.XMPMetadata);
}
# PDFBox 数字树和加密 handler 只按参数类型反射构造，不保留整库。
-keepclassmembers class * implements org.apache.pdfbox.pdmodel.common.COSObjectable {
    public <init>(org.apache.pdfbox.cos.COSDictionary);
    public <init>(org.apache.pdfbox.cos.COSArray);
}
-keepclassmembers class * extends org.apache.pdfbox.pdmodel.encryption.SecurityHandler {
    public <init>();
    public <init>(org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy);
    public <init>(org.apache.pdfbox.pdmodel.encryption.PublicKeyProtectionPolicy);
}
# Batik CSS parser 在 XMLResourceDescriptor.properties 中按名配置；图像 provider 来自正式服务描述。
-keep class org.apache.batik.css.parser.Parser { public <init>(); }
-keep class org.apache.batik.ext.awt.image.codec.** implements org.apache.batik.ext.awt.image.spi.RegistryEntry { public <init>(); }
-keep class org.apache.batik.ext.awt.image.codec.imageio.* implements org.apache.batik.ext.awt.image.spi.ImageWriter { public <init>(); }
-keep class org.apache.batik.extension.svg.BatikDomExtension { public <init>(); }
-keep class org.apache.batik.extension.svg.BatikBridgeExtension { public <init>(); }
-keep class org.apache.xmlgraphics.util.uri.DataURIResolver { public <init>(); }
-keep class org.apache.xmlgraphics.image.loader.impl.** implements org.apache.xmlgraphics.image.loader.spi.ImageConverter { public <init>(); }
-keep class org.apache.xmlgraphics.image.loader.impl.** implements org.apache.xmlgraphics.image.loader.spi.ImageLoaderFactory { public <init>(); }
-keep class org.apache.xmlgraphics.image.loader.impl.** implements org.apache.xmlgraphics.image.loader.spi.ImagePreloader { public <init>(); }
-keep class org.apache.xmlgraphics.image.writer.** implements org.apache.xmlgraphics.image.writer.ImageWriter { public <init>(); }
-keepdirectories resources/**,org/apache/pdfbox/resources/**,org/apache/fontbox/cmap/**,org/apache/batik/**/resources/**,pdf-fonts/**

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

# ORT JNI 按完整类名创建张量/形状/异常；官方 jar 内五平台 native 与第三方声明原样保留。
-keep class ai.onnxruntime.** { *; }

# JavaCPP 的 Loader 通过注解、嵌套类型名和 JNI 查找 FFmpeg 映射。
-keep class org.bytedeco.javacpp.* { *; }
-keep class org.bytedeco.javacpp.annotation.** { *; }
-keep class org.bytedeco.javacpp.indexer.** { *; }
-keep class org.bytedeco.javacpp.presets.** { *; }
# 同一 jar 中的 Maven 构建工具不属于应用运行时，不能为了它们引入 Maven。
-dontwarn org.bytedeco.javacpp.tools.**
-keep class org.bytedeco.ffmpeg.** { *; }
-keep class org.bytedeco.javacv.FFmpegFrameRecorder { *; }
-keep class org.bytedeco.javacv.FFmpegFrameRecorder$* { *; }
# 未引入 JavaCV 的相机/OpenCV 适配器依赖；这些未使用的适配器仍需由 shrinker 删除。
-dontwarn org.bytedeco.javacv.**

# 系统分享 JNI 导出符号及原生回调保持固定名称。
-keep class com.github.zly2006.zhihu.desktop.DesktopFileShareBridge { *; }
-keep class com.github.zly2006.zhihu.notification.PortableNotificationBridge { *; }

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
    public native void publish(java.lang.String, boolean);
    public native void withdraw(java.lang.String);
    public native void disconnect();
}

# 保持服务描述中的 provider 类名与收缩后的类一致。
-adaptresourcefilecontents META-INF/services/**
