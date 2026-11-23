# 图标工坊 (Icon Workshop)

**图标工坊**可以给任意已安装的 Android 应用增加一个自定义图标的桌面入口——无需 root，也不修改原应用及其数据。入口是运行时在设备上生成的小型 APK，使用本机生成的密钥签名，并通过 [Shizuku](https://shizuku.rikka.app/)（ADB shell 身份）静默安装。

本项目是一个自包含的个人工具：无统计、无网络访问、无服务端。

---

## 功能

- 列出设备上所有可启动的应用。
- 可选择任意图片（或你自行添加的预设头像）作为图标。
- 在设备上生成一个极简的"启动入口"APK：
  - 一个负责解析目标包名并启动它的 Activity；
  - 所选图标（普通位图 + 自适应图标）；
  - 目标应用的名称；
  - 隐藏、无窗口的启动 Activity（无闪白、无动画）；
  - 从最近任务中排除，并设置 `noHistory`。
- 使用本机生成的密钥签名。
- 通过 Shizuku 执行 `pm install` 静默安装。
- 生成的图标点开就是原应用；入口可在软件内删除。

## 为什么不能直接替换原图标？

Android 不允许第三方应用原地修改别的应用的图标。现代 Android 上三条路都被封死：

1. **RRO 运行时资源覆盖**——从 Android 12 起，未与目标应用签名一致的覆盖包必须声明目标应用 `<overlayable>` 中的名字。绝大多数第三方应用没有声明，因此覆盖包虽能安装，但状态为 `STATE_NO_IDMAP`，无法启用。
2. **`pm hide`**——需要 `android.permission.MANAGE_USERS`，ADB shell 身份没有该权限。
3. **修改已安装的 APK**——任何修改都会破坏签名，必须先卸载再安装。这会丢失应用数据、需要重新登录，并且会导致校验签名的应用（尤其是很多 Google 应用）不可用。

因此，"新增一个启动入口"是唯一非破坏性的方案。原图标可以从桌面手动移除，应用本身仍保留在抽屉里。

## 环境要求

运行：

- Android 8.0+（API 26）
- Shizuku 13.x 正在运行（无线调试或 root），并已授予图标工坊权限

构建：

- JDK 17 或更新版本
- Android SDK，包含 **platform `android-35`** 和 **build-tools `35.0.0`**
- Python 3
- Bash（Windows 上可用 Git Bash）

## 构建

```bash
export ANDROID_HOME=/path/to/Android/Sdk
export JAVA_HOME=/path/to/jdk

./scripts/fetch_deps.sh      # 从 Maven Central 下载 Shizuku 客户端 jar
./scripts/make_keystore.sh   # 生成本地演示签名密钥 -> app/assets/niji.p12
./scripts/build_entry.sh     # 生成入口模板 -> app/assets/entry-template.apk
./scripts/build_app.sh       # 输出 out/IconWorkshop.apk
```

可选预设头像：把图片放到 `app/assets/presets/<name>.png`，并在
`app/src/com/niji/iconchanger/MainActivity.java` 的 `PRESETS` 中把包名映射到该文件。仓库不内置预设图片。

`scripts/make_keystore.sh` 使用演示密码 `iconworkshop`。这不是秘密——密钥在本地生成，且不会提交。如需修改密码，请同时更新 `EntryBuilder.java` 中的 `KS_PASS`。

## 使用

1. 安装 `out/IconWorkshop.apk`。
2. 启动 Shizuku，在图标工坊请求时授予权限。
3. 点击一个应用，然后选择**选择图片**或**使用内置头像**。
4. 等待提示；新图标会出现在桌面抽屉中，把它拖到桌面即可。
5. 删除入口：在图标工坊里点击该应用 → **删除自定义图标**，或像普通应用一样卸载。

## 工作原理

```
图标工坊 (app)
 ├─ 读取 app/assets/entry-template.apk
 ├─ 修改 AndroidManifest.xml 的字符串池（二进制 XML）：
 │    模板包名 -> com.niji.entry.n<sha1(target)[0..8]>
 │    占位目标 -> 选中的应用包名
 │    PLACEHOLDER_LABEL -> 应用名称
 ├─ 按密度重新生成 mipmap PNG（额外生成自适应图标前景层）
 ├─ 重写 APK zip（resources.arsc 与 PNG 保持不压缩）
 ├─ 用 apksig 签名（v2 + v3）
 └─ Shizuku shell：
      cat > /data/local/tmp/niji_tmp.apk
      pm install -r /data/local/tmp/niji_tmp.apk
```

入口 Activity 是 `com.niji.entry.LaunchActivity`：从自身的 `niji_target_pkg`
meta-data 读取目标包名，调用 `getLaunchIntentForPackage()`，以
`NEW_TASK | NO_ANIMATION` 启动后立即结束。

`AxmlPatcher` 是一个小型二进制 XML 编辑器，只重写字符串池条目，其余 chunk
保持字节不变，因此设备端不需要 aapt2。`ZipPatcher` 在保持各条目压缩方式的前提下重建 APK。

## 目录结构

```
app/          图标工坊本体（Java，无 AndroidX）
  src/        MainActivity、ShizukuShell、EntryBuilder、IconGen、
              AxmlPatcher、ZipPatcher
  res/        布局 + 占位图标
entry/        启动入口模板源码（单个 Activity）
scripts/      依赖下载、密钥、入口构建、App 构建、打包
tools/pc/     可选的桌面辅助工具（二进制清单改写测试）
libs/         Shizuku 客户端 jar（构建时下载，已 git 忽略）
```

不使用 AndroidX，也不使用 Gradle；构建流程就是 `aapt2` + `javac` + `d8` +
`zipalign` + `apksigner`。

## 隐私

- 不申请网络权限，无遥测、无统计。
- 数据不离开设备。
- 签名密钥由 `make_keystore.sh` 在本地生成，不会提交。
- 图标工坊仅在执行 `pm install` / `pm uninstall` 以及读取包信息时调用 Shizuku。

## 限制

- 入口是独立包（`com.niji.entry.*`），不会收到原应用的通知、角标或长按快捷方式。
- 原应用及其图标仍然安装；如果只想保留自定义图标，请在桌面上移除原图标。
- 入口会使用目标应用的名称以便桌面显示正常；请仅用于你拥有或获准定制的应用。
- 在 Android 16（HyperOS 3）上测试。其他 ROM 的桌面抽屉与 Shizuku 行为可能不同。

## 许可证

MIT —— 见 [LICENSE](LICENSE)。

第三方组件：

- [Shizuku API](https://github.com/RikkaApps/Shizuku-API) —— Apache License 2.0
- [apksig](https://android.googlesource.com/platform/tools/apksig/) —— Apache
  License 2.0
