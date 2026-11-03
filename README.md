<div align="center">

<img src="assets/banner.svg" alt="LastLocationPlugin" width="100%">

# LastLocationPlugin

**记录并恢复玩家最后位置** —— 服务器端记录玩家退出时的坐标，下次进入时找回；配套 Velocity 侧模块可查询玩家最后所在子服。

![Platform](https://img.shields.io/badge/Platform-Paper%20%2F%20Velocity-62bda8?style=flat-square&logo=minecraft&logoColor=white)
![Minecraft](https://img.shields.io/badge/MC-26.2-8a9a5b?style=flat-square)
![Java](https://img.shields.io/badge/Java-25-f89820?style=flat-square&logo=openjdk&logoColor=white)

[功能](#-功能) · [构建](#-构建)

</div>

## ✨ 功能

- **位置记录**：玩家退出、服务器关闭时把在线玩家坐标落盘（`savePlayerLocation`）
- **进入恢复**：玩家再次进入时读取上次位置，并向玩家提示找回的坐标（i18n 文案可配）
- **Velocity 端**：`VelocityLastLocationPlugin` 记录玩家最后所在的后端子服，跨服场景下可查询"上一次在哪玩"
- **多语言**：`i18n/Messages` 统一管理提示文案

## 🔨 构建

```bash
./gradlew build
```

Paper 侧与 Velocity 侧分别产出插件 jar，按需安装。
