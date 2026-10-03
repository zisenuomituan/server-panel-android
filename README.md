# server-panel-android · 服务器面板安卓客户端

[server-panel](../server-panel) 的 Android 客户端。界面与网页版保持一致（深色 + 蓝鲸蓝），
在手机上查看宿主机/虚拟机状态、开关机、执行命令。

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
  手机上键盘没有方向键，命令行提供 **↑ / ↓ 按钮**翻历史命令，另有「清屏」。
  面板关闭命令功能或只读账号时自动隐藏。

## 界面

- 颜色、卡片、进度条、状态圆点、胶囊徽章、等宽数字全部对齐网页版
  （`web/style.css` 的调色板）。
- 纯 Java + 原生 View 实现，网络用 `HttpURLConnection`，JSON 用 `org.json`，
  图表自绘，依赖仅 AndroidX AppCompat 与 Material。

## 构建

环境：

- JDK 17
- Android SDK（compileSdk 34）
- Gradle 8.7
- 非 x86_64 设备（如 aarch64 / Termux）需在 `gradle.properties` 指定系统 aapt2：
  `android.aapt2FromMavenOverride=/usr/bin/aapt2`
- `local.properties` 指定 `sdk.dir`

命令：

```sh
./gradlew assembleDebug     # 调试包
./gradlew assembleRelease   # 正式包（当前用 debug 签名，保证可安装）
```

产物：`app/build/outputs/apk/`。

## 默认服务器地址

默认服务器地址是占位符 `https://your-panel.example`。如要改成自己的地址，
可在项目根目录放一个本地、不进库的 `private.properties`：

```properties
server.url=https://panel.example.com
```

构建时写入 `BuildConfig.DEFAULT_SERVER_URL`，作为登录页的默认地址。
也可以用环境变量 `PANEL_SERVER_URL` 覆盖，或放在
`~/.config/server-panel-android/private.properties`（推荐，配置完全在仓库之外）。

以下文件已被 `.gitignore` 排除，不会进库：`private.properties`、`local.properties`、
`*.keystore`、`*.jks`、`build/`、`.gradle/`。

## 测试

在手机上安装 APK，服务器地址填自己的面板地址（或自用版默认地址），
登录后即可看到已绑定的宿主机与虚拟机。

## 许可证

Apache-2.0，见 [LICENSE](LICENSE)。第三方组件见 [THIRD_PARTY.md](THIRD_PARTY.md)。
