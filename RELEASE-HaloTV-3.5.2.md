# HaloTV 3.5.2 创维 A5F+ 重编译优化版 · 更新报告

> 更新日期：2026-11-22
> 目标设备：Skyworth A5F+（酷开 Coocaa OS，海思 8H672 机芯，Android 12 / SDK 31，SELinux Permissive）
> 上游版本：HaloTV 3.5.2（versionCode 278，独立版包名 `app.halotv.launcher`）

---

## 一、版本信息

| 项目 | 值 |
|------|-----|
| 应用 | HaloTV（Aerial TV 二次开发，Null9797/halo-tv） |
| 版本 | 3.5.2（versionCode 278） |
| 包名 | `app.halotv.launcher`（3.3 起官方从 `app.orchard.tv` 改为独立版命名） |
| Activity | `app.orchard.tv.MainActivity`（namespace 未变） |
| 构建变体 | `standard` flavor + `release` buildType（R8 优化） |
| 签名 | debug key（`CN=Android Debug`，本地编译 fallback） |
| APK 体积 | 7.65 MB（R8 优化后） |
| minSdk / targetSdk | 21 / 34 |

---

## 二、本次重编译的三个关键改动

官方 3.5.2 直装版在酷开 A5F+ 上存在**两个致命问题**：① 重启后桌面失效；② 桌面遥控操作延迟大。本次重编译针对性地修复了这两点，并对构建做了适配。

### 1. 移植「写 persist.* 属性」逻辑 —— 解决重启后 Home 键失效

**根因**：官方 3.5.2 接管 Home 键靠的是无障碍服务 `HomeGuardService`（"桌面守护"），而它**只读不写** `persist.service.homepage/model.*` 属性。酷开固件每次重启都会：
- 把 `settings put secure accessibility_enabled` 重置为 0 → 守护不再运行；
- 把 `persist.service.*` 重置回官方桌面（movieplatform）。

于是 Home 键退回官方桌面，第三方桌面必然失效。

**移植内容**（与 Aerial 1.84 / HaloTV 3.3.2 同一套方案）：

`MainActivity.kt`：
- `import app.orchard.tv.system.CoocaaHome`
- 新增 `firmwareHomeHandler` 字段（`Handler(Looper.getMainLooper())`）
- 新增 `claimFirmwareHome()` / `keepFirmwareHome()` / `claimFirmwareHomeAfterBoot()` 三个方法（写属性逻辑，插在 `systemProperty()` 之后）
- `onDestroy()` 中加 `firmwareHomeHandler.removeCallbacksAndMessages(null)`
- `resumeWork()` 末尾（`GuardSetup.rebindForJmgoKeys(this)` 之后）加 `claimFirmwareHomeAfterBoot()`

`CoocaaHome.kt`：
- `import android.provider.Settings`
- 新增 `SAVED_HOMEPAGE` / `SAVED_MODEL` 常量
- `launcher()` 中加 `saved` 序列项
- 新增 `worthKeeping()` / `PLACEHOLDER`
- **保留 3.5.2 新增的 `modeScreen()` 方法**（不可整文件覆盖）

写属性逻辑用**动态 packageName**，改 applicationId 后自动写对，无需硬编码。

**效果**：app 开机自启后自动把 `persist.service.homepage/model.*` 写回自己，Home 键由酷开 `com.tianci.system` 读取属性直接分发，重启后稳定接管。

### 2. 关闭无障碍服务 HomeGuardService —— 解决遥控延迟

**根因**：`HomeGuardService` 的 `updateKeyFilter()`（`HomeGuardService.kt` 1544-1597 行）在**桌面自身在前台（`aerialResumed`）时**设置 `AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS`，导致**每一个遥控按键**（方向/确认/返回/Home）都要先跨进程送到无障碍服务的 `onKeyEvent`、再由系统重新注入回 app → 桌面操作延迟大。

判定依据（`HomeGuardService.kt` 1581-1582 行）：
```kotlin
val wanted = !keysPassThrough && !screenOff && !padGame && !mouseHang &&
    (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R || aerialInFront || always || homeStillDown)
```
A5F+ 是 Android 12（SDK 31 > R），桌面前台时 `aerialInFront=true` → filter 必然开启。

**处理**：既然写属性逻辑已经完整接管 Home 键，无障碍服务的按键过滤就是纯多余且有害的，直接关闭：

```bash
settings put secure accessibility_enabled 0
settings put secure enabled_accessibility_services ''   # 必须清空，防固件重启后自动重新启用
```

关闭后 Home 键仍由写属性逻辑稳定接管，遥控延迟恢复。

### 3. 签名配置修复 —— 本地可编译

`app/build.gradle.kts` 和 `lite/build.gradle.kts` 里的：
```kotlin
signingConfig = signingConfigs.getByName("aerial")
```
在 `local/independent-signing.properties` 不存在时会抛异常（`create("aerial")` 不执行）。修复为 fallback debug key：
```kotlin
signingConfig = signingConfigs.findByName("aerial") ?: signingConfigs.getByName("debug")
```

---

## 三、构建方法

**环境**：
- JDK 17（Adoptium 17.0.21.1+2）
- Android SDK：platform android-36 / build-tools 36.0.0
- Gradle 8.14.3，AGP 8.12，Kotlin 2.2.10
- 网络：可直连 `dl.google.com` / `repo1.maven.org` / `plugins.gradle.org`（VPN 直连，不设代理）

**步骤**：
1. 解压 release tag `v3.5.2-halo` 源码快照（**注意：main 分支只有 docs/website/tools，无 Android 源码**）。
2. 按上文移植写属性逻辑、修复签名配置。
3. 创建 `local.properties`，写入 `sdk.dir=...`。
4. 编译：
```bash
export JAVA_HOME="<JDK17路径>"
gradle.bat assembleStandardRelease --no-daemon -x lintVitalAnalyzeStandardRelease -x lintVitalStandardRelease -x lint
```
5. 产物：`app/build/outputs/apk/standard/release/app-standard-release.apk`（约 7.65 MB）。

---

## 四、部署流程（A5F+ 实测通过）

```bash
# 0) 连接电视
adb connect 192.168.31.204:5555

# 1) 卸载前：persist 属性改回官方桌面（红线，防 Home 键失效卡死）
adb shell "setprop persist.service.homepage.pkg com.tianci.movieplatform; \
  setprop persist.service.homepage.cls com.coocaa.aigc.home.home.rootpage.HomeActivity; \
  setprop persist.service.model.pkg com.tianci.movieplatform; \
  setprop persist.service.model.cls com.coocaa.aigc.home.home.rootpage.HomeActivity"
#   ⚠️ movieplatform 新类名是 com.coocaa.aigc.home.home.rootpage.HomeActivity（不是旧的 com.ccos.cooui.movieplatform.MainActivity）

# 2) 卸载旧版（debug key 与官方 release key 不同，必须卸载不能 -r 覆盖）
adb shell pm uninstall app.halotv.launcher

# 3) 推送并安装
adb push app-standard-release.apk /data/local/tmp/halo352.apk
adb shell pm install -r -t /data/local/tmp/halo352.apk

# 4) 设为默认 Home
adb shell cmd package set-home-activity --user 0 app.halotv.launcher/app.orchard.tv.MainActivity

# 5) skyresource 白名单（后台保留 + 开机自启）
adb shell "service call skyresource 5 s16 app.halotv.launcher i32 1 i32 2"
adb shell "service call skyresource 5 s16 app.halotv.launcher i32 0 i32 1"

# 6) 手动写 persist 属性（首次部署；此后重启由 app 自动写回）
adb shell "setprop persist.service.homepage.pkg app.halotv.launcher; \
  setprop persist.service.homepage.cls app.orchard.tv.MainActivity; \
  setprop persist.service.model.pkg app.halotv.launcher; \
  setprop persist.service.model.cls app.orchard.tv.MainActivity"

# 7) 关闭无障碍服务（解决遥控延迟，写属性逻辑已接管 Home，无障碍纯属多余）
adb shell "settings put secure accessibility_enabled 0; settings put secure enabled_accessibility_services ''"
```

---

## 五、验证结果（全部实测通过）

| 验证项 | 结果 |
|--------|------|
| 开机自启 | ✅ 重启后自动进入 HaloTV 前台 |
| persist 属性自动写回 | ✅ 重启后 4 项属性自动恢复为 `app.halotv.launcher` |
| Home 键接管 | ✅ 连续 3 轮"进官方桌面 → 按 Home"均稳定切回，无 ANR |
| 遥控延迟 | ✅ 关闭无障碍后按键响应恢复正常 |
| 桌面显示 | ✅ 时间/天气/应用图标/壁纸正常 |

---

## 六、关键经验与红线

1. **卸载接管了 Home 的第三方桌面前，必须先 `setprop` 把 `persist.service.homepage/model.*` 改回官方桌面**，否则卸载后 Home 键分发到不存在的包 → 画面卡死。
2. **movieplatform 新类名是 `com.coocaa.aigc.home.home.rootpage.HomeActivity`**（非旧的 `com.ccos.cooui.movieplatform.MainActivity`），卸载前改属性 / 卡死恢复都要用新类名。
3. **官方 3.5.2「只读不写 persist + 依赖无障碍」在酷开重启后必然失效**，唯一可靠解是移植写属性逻辑重编译。
4. **A5F+ 上不要开启 HomeGuardService 无障碍服务**（`FLAG_REQUEST_FILTER_KEY_EVENTS` 在桌面前台时拖慢每个遥控按键），写属性逻辑已足够接管 Home。
5. **包名**：3.3 起官方 applicationId 改为 `app.halotv.launcher`，写属性逻辑用动态 packageName 自动写对；若想保持旧包名覆盖升级需改 applicationId（namespace 不动）。
6. **签名**：官方 release 与本地 debug key 不同，跨签名升级必须卸载重装；同 debug key 可 `-r` 覆盖。

---

## 七、最终方案结论

> **A5F+ 上的 HaloTV 3.5.2 = 只靠「写 persist 属性逻辑」接管 Home 键 + 关闭无障碍服务**，这是"重启稳定 + 遥控流畅"的正确组合。

后续若上游再发布新版本，按同一套流程重编译移植即可：① 移植写属性逻辑；② 修复签名 fallback；③ 编译 release 变体；④ 部署后**关闭无障碍服务**。
