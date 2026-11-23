-keepattributes SourceFile,LineNumberTable

# 保留可序列化类的 `Companion` 字段，避免按具名伴生对象的方式通过 `getDeclaredClasses` 查找序列化器。
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# 多态序列化在运行时需要 @Serializable 和 @Polymorphic 注解。
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# 屏蔽 kotlinx-serialization 类配置的提示，参见 https://github.com/Kotlin/kotlinx.serialization/issues/1900。
-dontnote kotlinx.serialization.**

# 序列化核心用 `java.lang.ClassValue` 缓存；缺少该类的平台不会使用此路径，因此可屏蔽 R8/ProGuard 警告。
-dontwarn kotlinx.serialization.internal.ClassValueReferences

# 部分 ProGuard 版本优化 descriptor 字段会生成无法通过验证的字节码，参见 https://github.com/Kotlin/kotlinx.serialization/issues/2719。
-keepclassmembers public class **$$serializer {
    private ** descriptor;
}

-keepnames class ** implements com.github.zly2006.zhihu.data.Feed$Target
# 保留持久化偏好和数据库值使用的应用枚举名称。
-keep enum com.github.zly2006.zhihu.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.ParametersAreNonnullByDefault
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern
