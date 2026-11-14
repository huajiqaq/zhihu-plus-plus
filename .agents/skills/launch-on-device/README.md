# Android 部署

在项目根目录执行：

```bash
export ANDROID_SERIAL='<目标设备序列号>'
bash .agents/skills/launch-on-device/deploy.sh
```

脚本构建 Lite debug，保留应用数据安装，再解析 Launcher 启动。未指定设备时只接受唯一连接设备；安装失败停止，不自动卸载。

连接、Full 变体和登录数据的边界见 [SKILL.md](SKILL.md)。
