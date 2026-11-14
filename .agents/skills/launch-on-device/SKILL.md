---
name: launch-on-device
description: 构建、安装并启动 Zhihu++ Android 调试版，检查 ADB 连接、安装失败和启动错误。用于用户要求部署到已获准设备或模拟器时。
license: CC BY-NC-SA 4.0
---

# Android 部署

## 使用

在项目根目录执行；ADB 在 PATH 中，设备状态应为 `device`。

```bash
adb devices
export ANDROID_SERIAL='<目标设备序列号>'
bash .agents/skills/launch-on-device/deploy.sh
```

未指定 `ANDROID_SERIAL` 时，脚本只接受唯一连接设备。它构建 Lite debug、用 `install -r` 保留数据安装，解析实际 Launcher 后启动；安装失败立即停止，不自动卸载。

| 变体 | 包名 | Debug APK |
| --- | --- | --- |
| Lite | `com.github.zly2006.zhplus.lite` | `app/build/outputs/apk/lite/debug/app-lite-debug.apk` |
| Full | `com.github.zly2006.zhplus` | `app/build/outputs/apk/full/debug/app-full-debug.apk` |

Full 使用 `:app:assembleFullDebug`，两种变体分两次构建。手动安装和启动时，每条 ADB 命令都明确 `-s "$ANDROID_SERIAL"`。

启动组件以包管理器的实际 Launcher 解析结果为准，不写死 Activity 名称。通过 `cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p <包名>` 获取组件，再以同样的 action/category 启动。手动指定 Activity 不能替代 Launcher 启动验收。

## 失败处理

- 设备缺失、未授权或离线：检查连接和授权，不自动创建 AVD、下载镜像或重启共享 ADB 服务。
- 签名不兼容或版本降级：说明原因并停止。卸载和清除数据会丢账号、设置及本地记录，只有用户确认后才能做。
- 存储不足：说明空间问题，不替用户卸载其他应用。
- 启动崩溃：读取目标进程的必要日志，核对包名、Launcher 和实际 APK；不默认清数据重试。
- 安装和启动命令成功不等于页面可用，继续用 [UI 测试](../ui-test/SKILL.md) 确认目标状态。

## 登录数据

账号 JSON 是运行时私密数据，不是构建输入，不加入 Gradle 任务、测试资源或 APK。真实登录缺失时先报告；只在用户明确授权并提供备份来源后恢复，不默认读取仓库密钥目录或用户账号。

获准恢复时，先备份目标数据，停止应用，再经 `adb push` 和 `run-as` 写入目标包的 `files/account.json`，权限设为 600，删除设备临时副本后启动。操作中不打印 Cookie、Token 或文件正文。测试需要真实数据时显式使用 `zhpp_data_mode=real`；默认 mock 测试不带账号。

## 参考

- [ADB 文档](https://developer.android.com/tools/adb)
- [Android 清单](../../../app/src/main/AndroidManifest.xml)
- [KMP 变体](../../../docs/kmp-android-variants.md)
