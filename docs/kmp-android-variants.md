# Android Full/Lite 的 KMP 构建

`app` 保留 `full` 和 `lite` flavor，最低 Android API 为 27，以 `app/build.gradle.kts` 和共享构建约定为准。Android KMP 模块每次只编译一套版本专属 actual，构建脚本从 APK、bundle、install、compile 或单元测试任务名推断版本。

```bash
./gradlew :app:assembleLiteDebug
./gradlew :app:assembleFullDebug
```

两种 flavor 分两次调用，不能在同一 Gradle 命令混用。独立 KMP 任务可用 `-PzhihuAndroidVariant=lite` 或 `full` 显式选择；未指定且任务名不含 flavor 时默认 Lite。非法值、混合任务和属性冲突会报错。

共享 Android 代码放在 `androidMain`，版本专属实现放在 `androidFull`/`androidLite`。Debug APK 位于 `app/build/outputs/apk/<flavor>/debug/`；Release 使用相应 `assemble*Release` 任务。

Android 单元测试也分开运行：`:app:testLiteDebugUnitTest`、`:app:testFullDebugUnitTest`。全局 `test` 会同时调度两版，不能用于一次只选择一套 actual 的构建。
