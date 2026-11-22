# 创维 A5F+（海思 8H672）优化版说明

> 本分支 `skyworth-a5f-home` 是 **HaloTV** 针对**创维 A5F+ 电视**（酷开 Coocaa OS / 海思 Hi3751V660 机芯 / Android 12 / SDK 31，SELinux Permissive）的优化版。
>
> 当前版本：**v3.5.2**（`versionCode 278`，独立版包名 `app.halotv.launcher`）。历史版本见 `RELEASE-HaloTV-3.0.5.md`，本次更新详见 `RELEASE-HaloTV-3.5.2.md`。

---

## 一、与原版的区别

相对 [Null9797/halo-tv](https://github.com/Null9797/halo-tv) 原版，本分支做了 **三处优化**：

| # | 优化项 | 解决的问题 | 改动范围 |
|---|--------|-----------|---------|
| 1 | 移植「写 `persist.*` 属性」逻辑 | 电视重启后 Home 键退回系统桌面 | `MainActivity.kt` + `CoocaaHome.kt` |
| 2 | 关闭无障碍服务 `HomeGuardService` | 桌面遥控操作延迟大 | 部署配置（业务代码零改动） |
| 3 | release 变体 + R8 优化构建 | 遥控器卡顿（debug 变体问题） | 构建方式（业务代码零改动） |

> 注：3.3 起官方把 applicationId 从 `app.orchard.tv` 改为 `app.halotv.launcher`（独立版命名）；写属性逻辑用动态 `packageName`，自动写对，无需硬编码。

---

## 二、优化一：移植「写 `persist.*` 属性」逻辑（解决重启后 Home 键失效）

### 2.1 问题背景

原版 HaloTV 基于 [pengchujin/aerial-tv](https://github.com/pengchujin/aerial-tv) 基线，其 `CoocaaHome.kt` 被设计为**只读不写** `persist.service.homepage.*` / `persist.service.model.*` 属性（上游 PR #992 明确排除了写属性逻辑，代码注释为 "Aerial only reads them"）。

但在**海思创维 A5F+** 上，酷开系统的 Home 键分发机制不同：

- Home 键由 `com.tianci.system` 的 `AppBunch.startHomePage` 硬编码分发；
- 它读取 `persist.service.homepage.*` / `persist.service.model.*` 属性，决定 Home 键跳转到哪个桌面；
- 开机流程走 `sky_launcher` 的 `modelhome` 规则。

### 2.2 根因

原版「只读不写」，电视重启后酷开固件会把这些属性**重置回 `com.tianci.movieplatform`（系统影视桌面）**。此时 HaloTV 虽能靠开机自启白名单拉起，但 Home 键仍被分发到系统桌面，表现为「重启后 Home 键失效」。

### 2.3 改动内容

移植自 [wzpan/aerial-tv](https://github.com/wzpan/aerial-tv) 1.82（`skyworth-home-task` 分支）的写属性逻辑：

**`app/src/main/java/app/orchard/tv/MainActivity.kt`（5 处）**

1. 新增 import：`import app.orchard.tv.system.CoocaaHome`
2. 新增字段 + 三个方法：`firmwareHomeHandler`、`claimFirmwareHome()`、`keepFirmwareHome()`、`claimFirmwareHomeAfterBoot()`（插在 `systemProperty()` 之后）
3. `onDestroy()` 中清理回调：`firmwareHomeHandler.removeCallbacksAndMessages(null)`
4. `resumeWork()` 末尾（`GuardSetup.rebindForJmgoKeys(this)` 之后）触发 `claimFirmwareHomeAfterBoot()`

**`app/src/main/java/app/orchard/tv/system/CoocaaHome.kt`（4 处）**

1. 新增 import：`import android.provider.Settings`
2. 新增常量：`SAVED_HOMEPAGE` / `SAVED_MODEL`
3. `launcher()` 新增 `saved` 序列项
4. 新增 `worthKeeping()` / `PLACEHOLDER`

> ⚠️ 3.5.2 的 `CoocaaHome.kt` 新增了 `modeScreen()` 方法，移植时**必须保留**，不可整文件覆盖。

### 2.4 安全性说明

`hidesStandardTasks()` 上游已带 `SDK_INT >= 33` 门槛（仅 Android 13 及以上才触发 standard-task 隐藏），而创维 A5F+ 是 **Android 12（SDK_INT=31）**，天然免疫「reopenAsHome 死循环」问题，无需额外补丁。

---

## 三、优化二：关闭无障碍服务 `HomeGuardService`（解决遥控延迟）

### 3.1 问题背景

官方 3.5.2 接管 Home 键靠无障碍服务 `HomeGuardService`（"桌面守护"）。开启后桌面遥控操作（方向/确认/返回键）延迟很大。

### 3.2 根因

`HomeGuardService` 的 `updateKeyFilter()`（`HomeGuardService.kt` 1544-1597 行）在**桌面自身在前台（`aerialResumed`）时**设置 `AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS`，导致**每一个遥控按键**都要先跨进程送到无障碍服务的 `onKeyEvent`、再由系统重新注入回 app → 桌面操作延迟大。

判定依据（`HomeGuardService.kt` 1581-1582 行）：
```kotlin
val wanted = !keysPassThrough && !screenOff && !padGame && !mouseHang &&
    (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R || aerialInFront || always || homeStillDown)
```
A5F+ 是 Android 12（SDK 31 > R），桌面前台时 `aerialInFront=true` → filter 必然开启。

### 3.3 改动内容

既然「优化一」的写属性逻辑已完整接管 Home 键，无障碍服务的按键过滤就是纯多余且有害的，直接关闭：

```bash
settings put secure accessibility_enabled 0
settings put secure enabled_accessibility_services ''   # 必须清空，防固件重启后自动重新启用
```

> 关闭后 Home 键仍由写属性逻辑稳定接管，遥控延迟恢复。**本分支部署后不要再开启 HomeGuardService 无障碍服务。**

---

## 四、优化三：release 变体 + R8 优化构建（解决 debug 版卡顿）

### 4.1 问题背景

用 `assembleStandardDebug` 编译出的版本，遥控器操作延迟非常大。

### 4.2 根因

HaloTV 是 **Compose 动画密集**桌面（动态壁纸、粒子动画、毛玻璃、页面过渡），debug 构建与官方 release 的差异恰好全落在性能上：

| 差异点 | debug 版 | release 版 | 影响 |
|--------|:---:|:---:|------|
| R8 代码压缩 / 优化 | ❌ | ✅ | 代码冗长、执行路径长 |
| `debuggable` 标志 | `true` | `false` | ART 走解释执行+JIT，无 AOT，明显变慢 |
| Compose release 优化 | ❌ | ✅ | 重组 / 动画开销显著放大 |

### 4.3 改动内容

业务代码**零改动**，改用 release 变体编译：

```bash
gradle.bat assembleStandardRelease --no-daemon \
  -x lintVitalAnalyzeStandardRelease -x lintVitalStandardRelease -x lint
```

> `-x lint*` 跳过 lint 静态检查（不影响 APK 产物，且 lint 依赖在受限网络下易超时）。

### 4.4 效果

| 指标 | debug 版 | release 版 |
|------|:---:|:---:|
| APK 体积 | 20.7 MB | 7.65 MB（缩小约 63%） |
| `debuggable` | `true` | `false`（ART 走 AOT/JIT 优化路径） |
| 遥控器流畅度 | 延迟大 | 与原版一致 |

---

## 五、构建方法

### 5.1 环境要求

- JDK 17（Adoptium 17.0.21.1+2）
- Android SDK（`compileSdk 36` + `build-tools 36.0.0`）
- Gradle 8.14.3（AGP 8.12，Kotlin 2.2.10）
- 网络可直连 `dl.google.com` / `repo1.maven.org` / `plugins.gradle.org`（VPN 直连，不设代理）

### 5.2 构建步骤

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
>
> **签名配置修复**：`app/build.gradle.kts` 和 `lite/build.gradle.kts` 的 `signingConfigs.getByName("aerial")` 已改为 `signingConfigs.findByName("aerial") ?: signingConfigs.getByName("debug")`（否则 `local/independent-signing.properties` 不存在时抛异常）。

---

## 六、部署到创维 A5F+

```bash
# 0. 连接 ADB
adb connect 192.168.31.204:5555

# 1. 卸载前：persist 属性改回官方桌面（红线，防 Home 键失效卡死）
#    ⚠️ movieplatform 新类名是 com.coocaa.aigc.home.home.rootpage.HomeActivity（不是旧的 com.ccos.cooui.movieplatform.MainActivity）
adb shell "setprop persist.service.homepage.pkg com.tianci.movieplatform; \
  setprop persist.service.homepage.cls com.coocaa.aigc.home.home.rootpage.HomeActivity; \
  setprop persist.service.model.pkg com.tianci.movieplatform; \
  setprop persist.service.model.cls com.coocaa.aigc.home.home.rootpage.HomeActivity"

# 2. 卸载旧版（debug key 与官方 release key 不同，必须卸载不能 -r 覆盖）
adb shell pm uninstall app.halotv.launcher

# 3. 推送 + 安装
adb push app-standard-release.apk /data/local/tmp/halo.apk
adb shell pm install -r -t /data/local/tmp/halo.apk

# 4. 设为默认 Home
adb shell cmd package set-home-activity --user 0 app.halotv.launcher/app.orchard.tv.MainActivity

# 5. 白名单：后台保留 + 开机自启
adb shell "service call skyresource 5 s16 app.halotv.launcher i32 1 i32 2"
adb shell "service call skyresource 5 s16 app.halotv.launcher i32 0 i32 1"

# 6. 手动写 persist 属性（首次部署；此后重启由 app 自动写回）
adb shell "setprop persist.service.homepage.pkg app.halotv.launcher; \
  setprop persist.service.homepage.cls app.orchard.tv.MainActivity; \
  setprop persist.service.model.pkg app.halotv.launcher; \
  setprop persist.service.model.cls app.orchard.tv.MainActivity"

# 7. 关闭无障碍服务（解决遥控延迟）
adb shell "settings put secure accessibility_enabled 0; settings put secure enabled_accessibility_services ''"
```

---

## 七、注意事项（红线）

1. **卸载前先恢复属性**：卸载接管了 Home 的第三方桌面前，必须先 `setprop` 把 `persist.service.homepage/model.*` 改回官方桌面，否则卸载后 Home 键分发到不存在的包 → `mCurrentFocus=null` 画面卡死。

2. **movieplatform 新类名**：`com.coocaa.aigc.home.home.rootpage.HomeActivity`（非旧的 `com.ccos.cooui.movieplatform.MainActivity`），卸载前改属性 / 卡死恢复都要用新类名（从 `getprop` 实测确认）。

3. **禁用办公桌面是致命操作**：不要 `pm disable` 系统桌面（`com.tianci.movieplatform` / `com.skyworth.officehomepage`），否则可能开机卡 logo。

4. **不要开启 HomeGuardService 无障碍服务**：`FLAG_REQUEST_FILTER_KEY_EVENTS` 在桌面前台时拖慢每个遥控按键；写属性逻辑已足够接管 Home。

5. **覆盖安装后需重设白名单**：`skyresource` 白名单和 Home 设置在覆盖安装后会被清掉，需重新执行第 4–5 步。

6. **包名变化**：3.3 起官方 applicationId 改为 `app.halotv.launcher`。若想保持旧包名 `app.orchard.tv` 无缝覆盖升级，需改 `app/build.gradle.kts` 的 applicationId（namespace 不动）。

---

## 八、相关链接

- 原版：https://github.com/Null9797/halo-tv
- 上游：https://github.com/pengchujin/aerial-tv
- 写属性逻辑来源：https://github.com/wzpan/aerial-tv （`skyworth-home-task` 分支）
