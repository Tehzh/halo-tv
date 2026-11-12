# 创维 A5F+（海思 8H672）优化版说明

> 本分支 `skyworth-a5f-home` 是 **HaloTV v3.0.5** 针对**创维 A5F+ 电视**（酷开 Coocaa OS / 海思 Hi3751V660 机芯 / Android 12）的优化版。

---

## 一、与原版的区别

相对 [Null9797/halo-tv](https://github.com/Null9797/halo-tv) v3.0.5（`versionCode 187`）原版，本分支做了 **两处优化**：

| # | 优化项 | 解决的问题 | 改动范围 |
|---|--------|-----------|---------|
| 1 | 移植「写 `persist.*` 属性」逻辑 | 电视重启后 Home 键退回系统桌面 | `MainActivity.kt` + `CoocaaHome.kt` |
| 2 | release 变体 + R8 优化构建 | 遥控器操作延迟大、卡顿 | 构建方式（业务代码零改动） |

---

## 二、优化一：移植「写 `persist.*` 属性」逻辑（解决重启后 Home 键失效）

### 2.1 问题背景

原版 HaloTV 基于 [pengchujin/aerial-tv](https://github.com/pengchujin/aerial-tv) 1.89 基线，其 `CoocaaHome.kt` 被设计为**只读不写** `persist.service.homepage.*` / `persist.service.model.*` 属性（上游 PR #992 明确排除了写属性逻辑，代码注释为 "Aerial only reads them"）。

但在**海思创维 A5F+** 上，酷开系统的 Home 键分发机制不同：

- Home 键由 `com.tianci.system` 的 `AppBunch.startHomePage` 硬编码分发；
- 它读取 `persist.service.homepage.*` / `persist.service.model.*` 属性，决定 Home 键跳转到哪个桌面；
- 开机流程走 `sky_launcher` 的 `modelhome` 规则。

### 2.2 根因

由于原版「只读不写」，电视重启后，酷开固件会把这些属性**重置回 `com.tianci.movieplatform`（系统影视桌面）**。此时 HaloTV 虽然能靠开机自启白名单拉起，但 Home 键仍被分发到系统桌面，表现为「重启后 Home 键失效」。

### 2.3 改动内容

移植自 [wzpan/aerial-tv](https://github.com/wzpan/aerial-tv) 1.82（`skyworth-home-task` 分支）的写属性逻辑，共 **8 处**：

**`app/src/main/java/app/orchard/tv/MainActivity.kt`（4 处）**

1. 新增 import：`import app.orchard.tv.system.CoocaaHome`
2. 新增字段 + 三个方法：`firmwareHomeHandler`、`claimFirmwareHome()`、`keepFirmwareHome()`、`claimFirmwareHomeAfterBoot()`
3. `onDestroy()` 中清理回调：`firmwareHomeHandler.removeCallbacksAndMessages(null)`
4. `resumeWork()` 中触发接管逻辑

**`app/src/main/java/app/orchard/tv/system/CoocaaHome.kt`（4 处）**

1. 新增 import：`import android.provider.Settings`
2. 新增常量：`SAVED_HOMEPAGE` / `SAVED_MODEL`
3. `launcher()` 新增 `saved` 序列项
4. 新增 `worthKeeping()` / `PLACEHOLDER`

### 2.4 安全性说明

`hidesStandardTasks()` 上游已带 `SDK_INT >= 33` 门槛（仅 Android 13 及以上才触发 standard-task 隐藏），而创维 A5F+ 是 **Android 12（SDK_INT=31）**，天然免疫「reopenAsHome 死循环」问题，无需额外补丁。

---

## 三、优化二：release 变体 + R8 优化构建（解决遥控器延迟）

### 3.1 问题背景

用 `assembleStandardDebug` 编译出的版本，遥控器操作延迟非常大。

### 3.2 根因

HaloTV 是 **Compose 动画密集**桌面（动态壁纸、粒子动画、毛玻璃、页面过渡），debug 构建与官方 release 的差异恰好全落在性能上：

| 差异点 | debug 版 | release 版 | 影响 |
|--------|:---:|:---:|------|
| R8 代码压缩 / 优化 | ❌ | ✅ | 代码冗长、执行路径长 |
| `debuggable` 标志 | `true` | `false` | ART 走解释执行+JIT，无 AOT，明显变慢 |
| Compose release 优化 | ❌ | ✅ | 重组 / 动画开销显著放大 |

### 3.3 改动内容

业务代码**零改动**，改用 release 变体编译：

```bash
gradle.bat assembleStandardRelease --no-daemon \
  -x lintVitalAnalyzeStandardRelease -x lintVitalStandardRelease -x lint \
  -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=<代理端口> \
  -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=<代理端口>
```

> `-x lint*` 跳过 lint 静态检查（不影响 APK 产物，且 lint 依赖在受限网络下易超时）。

### 3.4 效果

| 指标 | debug 版 | release 版 |
|------|:---:|:---:|
| APK 体积 | 20.7 MB | 8.36 MB（缩小约 60%） |
| `debuggable` | `true` | `false`（ART 走 AOT/JIT 优化路径） |
| 遥控器流畅度 | 延迟大 | 与原版一致 |

---

## 四、构建方法

### 4.1 环境要求

- JDK 17
- Android SDK（`compileSdk 36` + `build-tools 36.0.0`）
- Gradle 8.14.3（AGP 8.12）

### 4.2 构建步骤

1. 创建 `local.properties`，写入 SDK 路径：

   ```properties
   sdk.dir=C:/Users/Administrator/.workbuddy/tooling/android-sdk
   ```

2. 执行构建（Standard release 版）：

   ```bash
   gradle.bat assembleStandardRelease --no-daemon \
     -x lintVitalAnalyzeStandardRelease -x lintVitalStandardRelease -x lint
   ```

3. 产物：`app/build/outputs/apk/standard/release/app-standard-release.apk`

> **签名说明**：官方签名密钥未公开，本分支构建时 release 签名 fallback 到 debug key（`Android Debug` 证书），因此**不能**与官方原版 APK 覆盖安装，需先卸载再安装。

---

## 五、部署到创维 A5F+

```bash
# 1. 连接 ADB
adb connect 192.168.31.204:5555

# 2. 开启未知来源安装
adb shell settings put secure install_non_market_apps 1

# 3. 推送 + 安装（同签名可 -r 覆盖）
adb push app-standard-release.apk /data/local/tmp/halo.apk
adb shell pm install -r /data/local/tmp/halo.apk

# 4. 设为系统 Home
adb shell cmd package set-home-activity --user 0 app.orchard.tv/.MainActivity

# 5. 后台保留白名单（防 SkyRMService 强停导致返回桌面慢）
adb shell "service call skyresource 5 s16 app.orchard.tv i32 1 i32 2"

# 6. 开机自启白名单
adb shell "service call skyresource 5 s16 app.orchard.tv i32 0 i32 1"

# 7. 以 HOME intent 启动（不要用 am start -n 直接启动，否则被系统桌面抢焦点）
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
```

---

## 六、注意事项（红线）

1. **卸载前先恢复属性**：若要从本优化版卸载 HaloTV，必须先执行：

   ```bash
   adb shell "setprop persist.service.homepage.pkg com.tianci.movieplatform; \
     setprop persist.service.homepage.cls com.ccos.cooui.movieplatform.MainActivity; \
     setprop persist.service.model.pkg com.tianci.movieplatform; \
     setprop persist.service.model.cls com.ccos.cooui.movieplatform.MainActivity"
   ```

   否则卸载后 Home 键分发到不存在的包，导致 `mCurrentFocus=null` 画面卡死。

2. **禁用办公桌面是致命操作**：不要 `pm disable` 系统桌面（`com.tianci.movieplatform` / `com.skyworth.officehomepage`），否则可能开机卡 logo。

3. **覆盖安装后需重设白名单**：`skyresource` 白名单和 Home 设置在覆盖安装后会被清掉，需重新执行第 5–6 步。

---

## 七、相关链接

- 原版：https://github.com/Null9797/halo-tv
- 上游：https://github.com/pengchujin/aerial-tv
- 写属性逻辑来源：https://github.com/wzpan/aerial-tv （`skyworth-home-task` 分支）
