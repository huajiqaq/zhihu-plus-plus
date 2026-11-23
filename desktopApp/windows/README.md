# 可选 Windows MSIX 系统通知

常规 MSI/AppImage 和 jpackage app-image 的构建、启动入口不变。MSI 没有可靠的包身份/激活登记，不发送系统通知，也不自注册或修改快捷方式。Windows 普通启动始终可以创建窗口；只有取得通知租约的窗口检查新消息，不为普通启动发送唤醒 IPC。

## 正式分发

此目录是可选 Windows x64 MSIX 包装，不是新的通用运行器。先用项目现有 `:desktopApp:createReleaseDistributable` 生成 Windows app-image，然后执行：

```powershell
./desktopApp/windows/package-msix.ps1 `
  -AppImage <createReleaseDistributable生成的Zhihu++目录> `
  -OutputDirectory <仓外新的空目录路径> `
  -IdentityName <正式包Identity.Name> `
  -Publisher 'CN=<正式签名证书主体>' `
  -Version <四段MSIX包版本号>
```

要求 JDK 21、Visual Studio C++ 桌面工具/CMake、Windows SDK。脚本通过 `JAVA_HOME`/`javac`、`vswhere`、Windows SDK 的已安装根和版本发现工具；也可显式指定 `JavaHome`、`WindowsSdkRoot`、`WindowsSdkVersion`。没有 .NET SDK、NuGet、Windows App SDK runtime 或网络下载依赖。

`OutputDirectory` 必须不存在，且不能在输入 app-image 内。脚本只读取项目 app-image 中固定的 `app/Zhihu++.cfg`，只接受固定 `MainKt`、同目录 JAR 文件名与列明的必要 JVM 选项；不接受主类、外部路径、任意 JVM 参数或运行命令。身份/发布者必须显式传入，示例身份只能用于未签名核验。脚本生成 `Zhihu++.unsigned.msix`，MakeAppx 默认语义验证保持启用。它不签名、安装、注册、启动程序或发送通知。发布者另行通过正常签名/分发渠道提供完整包，不能用修改机器信任来代替正式发布。

新 MSIX 使用 `ZhihuNotifications.exe` 作为本应用宿主，通过标准 JNI Invocation API 启动原 app-image 的固定 MainKt、JAR 和 runtime；不是创建任意子进程，不执行脚本，也不读取外部启动参数。它不替换 MSI 中的 jpackage 启动器。MSIX 内附原 app-image 的常规启动器仍可用于无通知的普通桌面入口。

## 通知与激活边界

- 原生宿主使用 Windows SDK 的 `INotificationActivationCallback` 和 WinRT `ToastNotificationManager`。清单将本应用唯一 CLSID `A198161D-C0D7-4500-87F0-AC2A791570E1` 同时声明为 `desktop:ToastNotificationActivation` 和 `com:ExeServer`。不使用注册表自注册、ProgID、协议或其他应用的 AUMID。
- 宿主先验证签名完整包（不接受 development/稀疏注册）、编译绑定的名称/发布者、当前 AUMID、同包路径、无重解析和不可由普通用户改写的包文件 ACL。原生模块就是该包内宿主，通过 `RegisterNatives` 绑定窄 JVM 桥；JVM 不搜索、提取或 `System.load` 外部 DLL。唯一动态加载为同包固定 `app-image/runtime/bin/server/jvm.dll`，Windows DLL 搜索限同包 runtime 与 System32，不使用 cwd/PATH。
- JVM 入口、JNI 类名/签名在 `desktopApp/proguard-release.pro` 精确保留。JAR 列表编译进宿主；启动时不解释 cfg、环境 JVM 选项或任意命令行。升级后使用新包和相应新宿主，不跨版本拼装模块。
- 通知内容固定为“知乎++ / 有新的消息，点击查看”。唯一载荷是 BCrypt 生成的 128 位随机数，编码为 32 个小写十六进制字符；Tag/Group 各取 16 字符，无截断碰撞，也不以业务 key 覆盖另一条 receipt。撤回用同一 AUMID + Tag + Group。
- 回调拒绝不匹配 AUMID、非固定格式 receipt 和任何用户输入字段；不解析 URL、命令、账号、目标路由或 CLI receipt。仅 SDK callback 写入有界内存队列，暖启动交给正在运行的同一进程，冷启动先登记 callback 并等待真实 receipt，再创建共享应用 UI。没有 callback 的 `-ToastActivated` 空启动不会打开窗口。普通空参数启动不等待通知。
- common 的 `RunningSystemNotificationHost` 在共享导航准备好后取得租约，加载 `JvmNotificationStateStore`，核验当前账号并持久化消费一次性 receipt 后唤醒真实窗口。重复/旧账号 receipt 不导航；不新增路由或账号缓存。
- 只在进程运行时轮询；退出后仅保留系统已投递的通知和冷点击入口。系统通知禁用时宿主仍可报告“已支持但无权限”，不宣称可以显示。未打包、缺 JNI、第二通知所有者、身份/存储/注册失败时明确 unsupported，不影响常规 MSI 窗口。

## 本机存储

复用现有 `JvmNotificationStateStore`，Windows 根来自 `ApplicationData.Current.LocalCacheFolder`，不从环境变量拼装；LocalCache 不参加应用数据备份/漫游。应用仅创建自己的受保护子目录，不读取其他账号数据。新目录使用 protected DACL，文件仍用现有原子临时写/替换。

所有存储路径核验当前 token 的 owner SID、DACL、无重解析和文件硬链接数；访问只信当前用户、`S-1-5-18`（SYSTEM）及 `S-1-5-32-544`（Administrators）。不按本地化名称信任账号，也不添加其他用户权限。包文件额外允许 Windows TrustedInstaller 的固定服务 SID，但不允许普通用户写包文件。跨进程通知租约按用户 SID + 包 AUMID 隔离，不影响普通第二窗口。

## 验证边界

可以在仓外构建 C++、运行 MakeAppx 验证未签名包、检查清单/PE/JAR/JNI 保留规则；这些都不能证明系统通知实际可用。正式签名安装后还必须验证：

1. 普通 MSIX 首次启动能到共享 UI；MSI 和未接桥入口仍正常使用并明确不支持通知。
2. Windows 授权允许/拒绝与勿扰状态；SDK 接受投递不等于已经显示。
3. 同一 receipt 暖点击、应用退出后的 COM 冷点击均到真实窗口；共享导航初始化期间不丢回执。
4. 重复、失效、旧账号 receipt 不导航；登出、关闭订阅、标读和撤回仅影响对应 receipt。
5. 安装后的实际 LocalCache/包 ACL、二次普通启动的单通知租约、签名包更新后的激活。

本脚本不会自动执行这些需要真实安装/用户点击的步骤。不能将静态清单、编译成功或 unsigned 包生成报告成实机通知验收。

## 官方依据

- [SDK INotificationActivationCallback::Activate](https://learn.microsoft.com/en-us/windows/win32/api/notificationactivationcallback/nf-notificationactivationcallback-inotificationactivationcallback-activate)
- [MSIX desktop:ToastNotificationActivation](https://learn.microsoft.com/en-us/uwp/schemas/appxpackage/uapmanifestschema/element-desktop-toastnotificationactivation)
- [MSIX com:ExeServer](https://learn.microsoft.com/en-us/uwp/schemas/appxpackage/uapmanifestschema/element-com-exeserver)
- [ToastNotification.Tag](https://learn.microsoft.com/en-us/uwp/api/windows.ui.notifications.toastnotification.tag) / [Group](https://learn.microsoft.com/en-us/uwp/api/windows.ui.notifications.toastnotification.group)
- [ApplicationData.LocalCacheFolder](https://learn.microsoft.com/en-us/uwp/api/windows.storage.applicationdata.localcachefolder)
- [JNI Invocation API](https://docs.oracle.com/en/java/javase/21/docs/specs/jni/invocation.html)
