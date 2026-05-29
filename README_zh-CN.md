> [!IMPORTANT]
> ## 品牌重塑公告
> 自 **2026年5月10日 EDT 00:00** 起，**RedHead Industries** 正式更名为 **Skidworks**。
>
> 在此过渡期间，仓库、网站、文档、域名、品牌资产和组织名称可能仍会暂时引用 **RedHead Industries**。
>
> Skidworks 是 RedHead Industries 的直接延续和继承者。
>
> 感谢您在品牌重塑期间的耐心等待。

[English](./README.md)

---

# SimpleBoot – 将手机变成可启动的USB设备！
**版本：** v2.1 [BETA] "Monster – Milestone 2"  
**许可证：** GNU 通用公共许可证第三版 (GPLv3)  
**原作者：** RedHead Industries (Technologies Branch) + Matthew DaLuz [@matthewdaluz](https://github.com/matthewdaluz)  
**二改维护：** DistrictBlauw [@DistrictBlauw](https://github.com/DistrictBlauw)

**SimpleBoot** 是一款强大的、需要Root权限的Android应用，可将手机转变为完全可启动的USB设备。通过 **ConfigFS**、**Legacy mass_storage** 或原始 **Loopback** 模式挂载ISO/IMG文件，直接在任意PC上启动实时系统。

> DriveDroid 和 PhoneStick 的继任者，专为 Android 11+ 打造，支持现代内核和 ConfigFS。

---

## 🚀 功能特性

- 🔍 **自动ISO/IMG发现** 从 `/storage/emulated/0/SimpleBootISOs` 扫描
- 📦 **三种挂载方式**：
  - `ConfigFS`：适用于大多数现代内核
  - `Legacy`：适用于较旧的Android USB协议栈
  - `Loopback`：直接挂载，不通过USB gadget（用于测试或链式启动）
- 💿 **CD-ROM启动模式** 带强制描述符，兼容BIOS/UEFI（已弃用，将在后续版本中移除）
- 🔐 **Root Shell集成** 使用 [`libsu`](https://github.com/topjohnwu/libsu)
- 🧠 **完整日志记录** 所有挂载/卸载操作（`SimpleBootLogs`）
- 🌓 **亮色/暗色模式** Jetpack Compose UI
- 🛠️ **每个ISO独立选择** 挂载方式
- ⚙️ **持久化偏好设置** 只读挂载
- 📤 导出日志和诊断信息用于调试

---

## 📸 截图

<p align="center">
<img width="270" height="585" alt="Screenshot_20251112-090816_SimpleBoot" src="https://github.com/user-attachments/assets/e142920c-f9a4-46b2-bb16-caea69cda997" />
<img width="270" height="585" alt="Screenshot_20251112-090820_SimpleBoot" src="https://github.com/user-attachments/assets/48a7adc9-c170-478b-ab4e-ba6f4f744611" />
</p>


## ⚙️ 系统要求

- 📱 Android 11+（API 30 或更高）
- 📲 Root权限（推荐使用 Magisk）
- 🔌 支持OTG的USB端口
- 📦 内核支持 ConfigFS（大多数基于AOSP的ROM）
- 🧪 可选：Legacy USB gadget 协议栈（适用于较旧设备）

---

## 🗂 文件系统布局

- `/storage/emulated/0/SimpleBootISOs/`  
  将 `.iso` 或 `.img` 文件放在此处
- `/storage/emulated/0/SimpleBootLogs/`  
  详细的挂载/卸载日志
- `/storage/emulated/0/SimpleBootLogs/mount_log_YYYYMMDD.txt`  
  每日启动诊断日志
- `/dev/block/loopX`  
  通过 `losetup` 使用循环设备（自动管理）

---

## 💻 工作原理

1. 从界面中选择一个ISO文件
2. 选择所需的**挂载方式**
3. SimpleBoot 使用 ConfigFS 或 Legacy 节点设置 USB gadget
4. 附加循环设备并将 gadget 配置为**可启动CD-ROM**
5. 你的PC将其识别为USB启动盘——开始启动！

---

## 📦 挂载方式说明

| 方式       | 说明                                                                        |
|------------|-----------------------------------------------------------------------------|
| `ConfigFS` | 现代gadget系统，使用 `/config/usb_gadget/...`。Android 11+ 必需            |
| `Legacy`   | 使用 `/sys/class/android_usb/android0/` 和 `f_mass_storage`。适用于较旧设备 |
| `Loopback` | 仅挂载ISO到 `/dev/block/loop7`（不通过USB暴露）。用于开发/测试              |

---

## ⚠️ 免责声明

- 📛 此应用**需要Root权限**和**完整文件系统访问权限**
- 🧱 配置错误或不支持的内核可能导致启动失败或USB协议栈问题
- ⚡ SimpleBoot 会在每次卸载和挂载失败时尝试恢复ADB/充电状态

---

## 🛠️ 技术栈

- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [libsu](https://github.com/topjohnwu/libsu)
- [Kotlin](https://kotlinlang.org/)
- [Android 11+ 权限 API](https://developer.android.com/about/versions/11/privacy/storage)

---

## 📜 许可证

GNU GPLv3 – 详见 [LICENSE](./LICENSE)

---

## 💬 参与贡献

欢迎提交 Pull Request：
- 其他挂载后端（如 FFS + 用户空间）
- USB模式预设（键盘、HID）
- 多ISO启动链（Ventoy风格）
- USB检测回调API（通知PC识别设备）

---

## 🙏 致谢

- **原作者：** [@matthewdaluz](https://github.com/matthewdaluz) / RedHead Industries
- **二改维护：** [@DistrictBlauw](https://github.com/DistrictBlauw)
- **AI助手：** ChatGPT + DeepSeek
- **特别感谢：** 开源Android Root社区

---

## 🔚 结语

SimpleBoot 让Android用户可以随身携带完整的USB启动控制能力。无论你是IT技术人员、Linux用户，还是只想在手机上携带实时系统——这款工具就是你一直在寻找的启动盘伴侣。

> ✨ 挂载。启动。重启。就这么简单。✨
