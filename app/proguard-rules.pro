-keepattributes SourceFile,LineNumberTable

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
