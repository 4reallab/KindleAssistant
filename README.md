# Kindle助手

为电子墨水屏而做的一个小工具。运行在 Kindle Paperwhite 3（越狱后安装 Readom / Android 8.1）上，把局域网传图、待办清单、沙漏计时和电子台历放到同一块屏幕上。字大、对比强、无动画，按墨水屏的习惯重新设计。

[English](README_EN.md) · [使用协议与免责声明](#使用协议与免责声明)

## 功能

### 1. 给 Kindle 发送图片
手机、电脑或 Pad 连上同一个 Wi-Fi，扫主页二维码或打开主页地址，选好几张照片上传，Kindle 会自动全屏打开最后一张。

- 手机 / 电脑 / Pad 均可上传，支持一次传多张
- 图片永久保存在系统相册可见的 `Pictures` 目录
- 宫格浏览，点击全屏查看，左右滑动切换
- 点击图片显示「旋转 90°」按钮（10 秒无操作自动隐藏），从屏幕边缘向内滑动返回

### 2. 待办清单，多端都能改
在 Kindle 上操作，或用手机、电脑、Pad 的浏览器打开同一个地址操作，数据在同一份清单里。

- 未完成 / 已完成 / 已删除三个标签页，各显示数量
- 添加、修改、删除，删除前二次确认；「已删除」可恢复，再次删除才彻底清除
- 每条显示添加时间、完成/删除时间与累计耗时
- 设备端与网页端数据实时共享，保存在应用内部 `files/todos.json`

### 3. 会漏沙的倒计时
- 5 / 10 / 15 / 30 分钟、1 小时快捷按钮，或用滑条自定义 1～60 分钟
- 沙漏上半显示剩余、下半显示已用，沙子匀速落下
- 暂停时沙漏与沙流一起停下；退出页面自动暂停，回来点「继续」接着走
- 结束显示本次耗时与累计耗时并写回待办
- 为保护墨水屏，时钟与沙漏不显示秒、按整分钟刷新；耗时结果保留到秒

### 4. 一页一天的电子台历
- 公历、农历、星期和当前节气，农历与节气按北京时间计算
- 只有今天页面显示时分，翻到其他日期自动隐藏
- 今天天气在日期时间左侧单独显示，未来一周为明天起 7 天预报，横向一屏排开
- 城市可搜索切换并保存（候选来自 OpenStreetMap 的 Photon 地理编码，天气数据来自 Open-Meteo，需联网）

### 5. 给 AI 助手的 MCP 接口
- 与网页共用局域网端口 8088，端点 `POST http://<Kindle-IP>:8088/mcp`
- 8 个 Resources 读取全部数据（待办、图片、计时器、台历、天气缓存、服务状态）
- 18 个 Tools 写入：增删改待办、上传/删除图片、控制计时器、切换城市，可设置 Bearer token
- 手机/电脑浏览器打开主页的「AI 助手操作指南」可一键复制配置指令，地址按设备 IP 自动生成；WorkBuddy 直接发指令即可添加，豆包需用豆包工作版手动添加
- 协议说明与 AI 客户端配置见 [docs/mcp.md](docs/mcp.md)

## 设备要求

| 项目 | 要求 |
| --- | --- |
| 设备 | Kindle Paperwhite 3（第 7 代，DP75SDI） |
| 系统 | 5.16.2.1.1，需已越狱并运行 Readom（Android 8.1） |
| 前提 | 越狱中 `sh_integration` 可用；设备剩余存储 2.6 GB 以上 |
| 网络 | 传图与待办在同一局域网；天气需能访问互联网 |

越狱与 Readom 是两个独立项目，本应用不负责这一步，风险自行判断：

- 越狱：[kindlemodding.org](https://kindlemodding.org/)
- Readom 安装：[readom.app/#guide](https://readom.app/#guide)
- 推荐把[写给 AI 助手的安装说明](https://readom.app/install_readom.md)交给 AI 助手完成电脑侧步骤

## 安装

下载 `KindleAssistant.apk`，用 adb 安装：

```bash
adb install -r KindleAssistant.apk
```

也可以把 APK 拷进设备后用文件管理器直接打开安装。首次启动允许存储权限，系统会弹出「添加到主屏幕」确认框，点「自动添加」即可把图标放到 Launcher 主页。

安装后手机、电脑或 Pad 与 Kindle 连同一个 Wi-Fi，扫主页二维码或打开主页面上显示的地址（如 `http://192.168.31.192:8088/`）即可传图、管理待办。HTTP 服务监听 `0.0.0.0:8088` 且无鉴权，**请仅在可信局域网内使用**。

## 从源码构建

Android 工程位于本仓库根目录，包名 `com.kindle.assistant`，`minSdk 27` / `targetSdk 27` / `compileSdk 34`。

```bat
scripts\build.bat
```

产物：`app\build\outputs\apk\release\KindleAssistant.apk`（已签名）。

构建依赖 Gradle 8.7、Android SDK 与 Android Studio 自带 JBR（JDK 21）。若用命令行构建且本机 GraalVM 17 缺少 AGP 需要的 jlink 模块，需指定：

```bash
JAVA_HOME="D:\Program Files\Android\Android Studio\jbr"
```

签名凭据不随仓库分发。在 `local.properties` 中配置（或从环境变量读取）：

```properties
signing.storeFile=C:/path/to/photodrop.jks
signing.storePassword=...
signing.keyAlias=...
signing.keyPassword=...
```

环境变量形式：`SIGNING_STORE_FILE` / `SIGNING_STORE_PASSWORD` / `SIGNING_KEY_ALIAS` / `SIGNING_KEY_PASSWORD`。

完整的中文使用说明见 [`docs/usage.md`](docs/usage.md)。项目宣传页与下载地址见 [项目主页](https://4reallab.github.io/KindleAssistant/)（GitHub Pages，源码在 [`docs/index.html`](docs/index.html)）；开源地址 [github.com/4reallab/KindleAssistant](https://github.com/4reallab/KindleAssistant)。

APP 主界面点「关于」可查看授权、使用协议、免责条款、开源地址与致谢，内容与下方一致。

## 致谢

Kindle助手运行在 Readom 之上。感谢 [小蹦大侠@Bilibili](https://www.bilibili.com/video/BV1Zj4v6aETB/) 开源 Readom，让这台 Kindle 有了更多可能。

- [readom.app](https://readom.app/) — 给 Kindle 的 Android 8.1
- [hbyxabc/readom](https://github.com/hbyxabc/readom)
- 天气数据：[Open-Meteo](https://open-meteo.com/)，城市搜索：[Photon](https://photon.komoot.io/) / OpenStreetMap

## 使用协议与免责声明

Copyright (c) 2026 4reallab

本软件仅供个人学习、研究、非商业用途免费使用。

未经作者书面授权，禁止用于任何商业目的，包括但不限于：销售、付费服务、企业内部生产使用、SaaS、为客户开发项目。商业使用请联系作者获取单独授权。

本软件按「现状」提供，不提供任何担保。本软件未经充分测试，使用即表示接受可能对设备造成损害的风险。

联系方式：forreallab@126.com（问题反馈、商业授权咨询）。
