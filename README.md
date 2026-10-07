# server-panel-android · 服务器面板安卓客户端

[server-panel](../server-panel) 的 Android 客户端：在手机上查看宿主机与虚拟机的运行状态、
开关机、执行命令。界面与网页版保持一致（深色 + 蓝鲸蓝）。

> 团队：蓝鲸公益　开发者：zisenuomituan (xianyu)

## 功能

- **账号体系**：登录 / 注册，登录时可选粘贴绑定密钥自动绑定。
- **绑定密钥**：顶栏输入密钥即可绑定宿主机或单台虚拟机。
- **实时监控**：按宿主机分组展示虚拟机卡片，CPU / 内存进度条、上下行速率、
  开机时长、在线状态，5 秒轮询刷新。
- **详情页**：实时指标、规格与系统、磁盘挂载点、登录用户、历史曲线
  与网络吞吐（自绘折线图，无第三方图表库）。
- **电源控制**：开机 / 关机 / 重启 / 强制关机，均带二次确认。
- **命令行**：在详情页对虚拟机执行命令，显示真实输出与退出码。
  手机键盘没有方向键，命令行提供 **↑ / ↓ 按钮**翻历史命令，另有「清屏」。
  面板关闭命令功能或只读账号时自动隐藏。
- **列表工具**：顶部可按名称 / IP / 宿主机名搜索，按运行状态筛选（全部 / 运行中 / 已停止），
  按宿主机 / 名称 / CPU / 内存排序；搜索与筛选都作用在展示层，不影响轮询数据。
- **个人中心**：查看账号信息、修改密码；并显示面板版本、告警通知开关、命令行开关。
  面板关掉告警时，个人中心与告警页都会明确提示「面板已关闭告警」。
- **管理员功能**：网页命令行开关、登记宿主机并生成绑定密钥、用户管理、密钥轮换与撤销。
- **操作日志**：登录、绑定、开关机、执行命令等记录可查。
- **告警通知**：虚拟机停止、CPU / 内存 / 磁盘超阈值、宿主机失联会推送到通知栏。
  后台每 5 分钟拉一次告警摘要（系统 `AlarmManager`，无常驻服务），
  告警页可查看与确认，菜单上显示未读数。需要在面板端开启告警。
- **应用内更新**：从面板的更新清单检查新版本，交给系统下载器后台下载，
  退出应用不中断、已下载的不重复下载，装完自动清理安装包。

## 界面

- 颜色、卡片、进度条、状态圆点、胶囊徽章、等宽数字全部对齐网页版
  （`web/style.css` 的调色板）。
- 纯 Java + 原生 View 实现，网络用 `HttpURLConnection`，JSON 用 `org.json`，
  图表自绘，依赖仅 AndroidX AppCompat 与 Material。

## 构建

环境：

- JDK 21（17 亦可；Gradle 8.7 不支持 25；源码级别为 Java 17）
- Android SDK（compileSdk 34）
- 用仓库自带的 Gradle wrapper 即可，无需另装 Gradle
- x86_64 与 CI 直接 `./gradlew` 就行；**非 x86_64**（aarch64 / Termux 等）要改用系统 aapt2，
  写在自己机器的 `~/.gradle/gradle.properties` 里（属于环境配置，不放进仓库）：
  `android.aapt2FromMavenOverride=/usr/bin/aapt2`
- 可选：`local.properties` 指定 `sdk.dir`

命令：

```sh
./gradlew assembleDebug     # 调试包
./gradlew assembleRelease   # 正式包
```

产物：`app/build/outputs/apk/`。

本仓库只提供源码，不提供成品 APK。

### 改成自己的服务器地址

不要改代码，在项目根目录建 `private.properties`（已在 `.gitignore` 里，不会进库）：

```properties
server.url=https://你的面板地址
```

它会写进 `BuildConfig.DEFAULT_SERVER_URL`，同时决定应用内更新的清单地址
（`<server.url>/android/latest.json`，可在同一个文件里用 `update.manifest=` 覆盖）。
没有这个文件时用 `app/build.gradle` 里的占位地址。

### 正式签名（可选）

默认**回退到 debug 签名**，方便 clone 下来直接编译安装。要发正式包就自己建 keystore，
再把口令写在**仓库之外**的用户级文件 `~/.config/server-panel-android/signing.properties`：

```properties
storeFile=/绝对路径/your-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

文件存在且 `storeFile` 有值时，release 自动改用这把正式签名；文件不存在就回退 debug。
**签名是升级的主键**：同一个包名只有同签名才能覆盖安装，换签名必须卸载重装（数据会丢），
所以正式发布的 keystore 要长期保管好。
