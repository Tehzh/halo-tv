## HaloTV 3.3.5 published and verified

- Latest stable GitHub Release: `v3.3.5-halo`. PR #47 merged as `a42b871ffdbf7336f65bc3640c948f6c692b35c2`; wording-only PR #48 merged as `ac5fc4ebb762234ad3bd3d5697abf0d4d1b7823b`, now public main. Release tag targets `4683ce1d2c30a64a9b33efcd0398911985215a3e`. Complete application source/archive revision: `3c30ee4ee3ca8c4b6673d9b9ba005abab20caaf0`.
- Standard / Guard code 250, Lite 106. Fresh three Release builds and 1,412 host tests passed (607/607/198); lint Error/Fatal 0 (351/352/440 existing warnings). Independent identities/signer, minimum SDKs, permissions, ZIP/alignment, icons/welcome/other assets and full dex metadata verified. Includes previously local Dock size/alignment plus Issue 46 content refresh/cache/request fixes. No TV/emulator operation.
- All six GitHub Release assets and three production website APKs actually downloaded and matched Desktop bytes. Production homepage/feed/version, unchanged preview, historical update source and announcements verified. All 16 publication paths and the entire clean publication checkout match final main. Cloudflare production deployment `1ff7cea4-d0bc-4639-a09c-291ce407442f` is success on final main; repository About is 3.3.5. Feedback source/bindings untouched and unauthenticated admin remains 401. No new popup.
- Correction to earlier planned-open wording: final live inspection found Issue #46 already closed by the repository account at 2026-11-18 03:45:56 UTC, with one prior owner comment, before this release. This task did not change its state or comments. Published notes now say real-device behavior still requires feedback, without claiming it remains open. Do not reopen it automatically. Hardware behavior remains unverified.
- Desktop: `/Users/bilinggg/Desktop/HaloTV-3.3.5-正式版`, with source/checksums, build and publication verification and screenshots. Publication checkout: `/Users/bilinggg/Documents/Codex/2026-11-18/halo-335-publication`. Old test deliveries were not overwritten. Future version codes must exceed 250/106.

## HaloTV 3.3.5 publication preparation

Owner explicitly authorizes GitHub and website publication as 3.3.5, including completed Dock size/alignment and Issue 46 wallpaper refresh work. Full code 250 / Lite 106 supersede public 246/102 and local 249/105. See `docs/RELEASE-3.3.5.md`; concise notes must describe changes since public 3.3.4, with host/device validation boundaries. Keep independent IDs/signer, Logo, static welcome, existing settings/performance, remote announcement contents and feedback service/bindings. No new popup, Issue comment/closure, TV/emulator operation or third-party installation. Preserve all old Desktop test folders. Publish three signed packages, complete application source/checksums/verification, website and independent update feed, and verify actual public downloads and deployment before declaring completion.

## Local Issue 46 online static wallpaper fix

Current bug-fix work is based on `7614a3f`, preserving both Dock previews and all previous fixes. No new product feature. See `docs/HALO-ISSUE-46.md`: refresh same-ID online content, version baked/thumbnail caches, capture request intent before I/O, reject stale/ABA requests, defer rotation during manual requests, and stage Lite content before main-thread commit. Visible 3.3.4, full code 249 / Lite 105; future codes exceed these. Three new Desktop test packages and fresh full verification only. Do not use old test results as current evidence; no TV/emulator, third-party installation, repository/site/feed/announcement publication, or Issue replies/status changes. Keep private data and signing material out of artifacts.

Verified delivery: `/Users/bilinggg/Desktop/HaloTV-3.3.4-在线壁纸修复测试`. Application source/archive revision `e134c202848bc0427aae3fa4b3b9cd2fe5186f93`, branch `fix/issue46-online-wallpaper-refresh`. Three signed Release builds and 1,412 host tests passed (607/607/198), lint Error/Fatal 0 (351/352/440 existing warnings). Actual local-image pipeline tests cover same-ID backdrop/frost/thumbnail refresh, identical timestamps, out-of-order/cancelled requests, failed staging and type changes. Restoring only the old same-ID short circuit failed the regression as expected; the final fixed suite has no failures. Independent identities, signer, ZIP/alignment, full dex metadata, icons and all non-profile assets except intentional versioned phone thumbnail URLs verified. Eight Dock geometry/settings sources match `7614a3f` byte-for-byte; original test folders preserved. No device/emulator/live wallpaper-service test or publication/Issue change; affected firmware behavior remains unverified.

## Local Dock header alignment correction

The user explicitly corrects the previous fixed header inset: left/right clock information and status controls must move with small/default/large Dock geometry. This supersedes the fixed-header-margin note in the preview below. Keep original font sizes and default alignment. Full code 248 / Lite 104, visible 3.3.4, same independent IDs/signer/artwork. Desktop test delivery only; no TV/emulator operation, publication or announcement. Use a new `HaloTV-3.3.4-Dock对齐修复测试` folder and preserve the previous preview. Future codes must exceed 248/104. See `docs/HALO-DOCK-SIZE.md`; final results belong to that folder's verification.

Verified source/archive revision: `20d127151ed7f19f86bdd3723c4eaae962deee26`. Three signed Release builds and 1,399 host tests passed (601/601/197); lint Error/Fatal 0 (351/352/440 warnings remain). Default anchors, linked corner/status geometry, and existing native Lite view relayout across three sizes passed. APK identities, v1/v2 signer, ZIP/alignment, dex metadata and all non-profile assets matched the preceding size preview. Desktop verification, source and checksums are in `/Users/bilinggg/Desktop/HaloTV-3.3.4-Dock对齐修复测试`. Real-TV behavior remains for owner testing; no publication or device operation.

## Local Dock size preview

User requests linked Dock/app-icon sizes: small/default/large. Keep original default, other UI sizes, independent identity/signer and original artwork. Implement in Standard/Guard/native API-14 Lite plus phone settings. Local visible 3.3.4, full code 247 / Lite 103; future codes must exceed these. Desktop test APKs only; no TV/emulator operation, repository/site/feed publication, remote announcement or feedback changes. See `docs/HALO-DOCK-SIZE.md` and the final Desktop verification for actual test results.

Verified delivery: `/Users/bilinggg/Desktop/HaloTV-3.3.4-Dock大小测试`. Application source/archive revision `b59bea096c04f8f4062e9051e9509568d7cee28a`. Three signed Release builds, 1,396 host tests (600/600/196), lint Error/Fatal 0 (351/352/440 warnings), APK identity/signer/ZIP/alignment/dex metadata and unchanged non-phone assets passed. Phone JS syntax, five-language strings and generated shared Lite geometry checked. No device/emulator operation or publication; device visuals remain for owner testing. Full Home only re-prepares faces on size changes, without reloading the app catalog or icon library.

## HaloTV 3.3.4 published and verified

- Latest Release: `v3.3.4-halo`. PR #45 merged as `62a3a148ccc51905ab94e7f3338f5cdecd9b435c`; release tag targets `b445aebaa0a8343c815c12d5b9b11ddcc82033a1`. Full application source archive revision: `efeb0f1aea002e9306570355e0c8d8b870b8a10d`.
- Standard / Guard code 246, Lite 102. Three Release builds and 1,383 host tests passed (595/595/193); lint Error/Fatal 0 (351/352/440 warnings remain). All eight changes appear in repository, website and independent update notes, with Lite scope and device-validation limits. Independent IDs/signer, static welcome and original artwork preserved; no TV/emulator operation.
- All six GitHub assets and three production website APKs were downloaded and matched Desktop bytes. Production homepage, independent feed, version.txt and unchanged welcome preview match prepared files; all 16 merged paths match. Repository About is 3.3.4. Existing announcements and feedback service/bindings unchanged; unauthenticated admin returns 401. No new popup or Issue/feedback reply/status change.
- Desktop: `/Users/bilinggg/Desktop/HaloTV-3.3.4-正式版`, including verification JSON, publication verification and screenshot. Public checkout: `/Users/bilinggg/Documents/Codex/2026-11-17/halo-334-publication`. Future package codes must exceed 246/102. Continue concise release bullets covering every actual fix/optimization.

## HaloTV 3.3.4 publication preparation

Owner explicitly authorizes GitHub then website publication as 3.3.4, combining bf61e7d and 5a571d0. Full code 246 / Lite 102 supersede local 245/101 and public 243/99. List all actual fixes and optimizations concisely, with OEM validation limits and Lite scope. Preserve independent IDs/signer, logos/welcome/startup assets, remote announcement content and feedback service/bindings. No new popup, private feedback publication, issue changes, TV/emulator operation or system changes. See `docs/RELEASE-3.3.4.md`. Final builds/tests/lint, release assets, production homepage/update feed and actual downloads must be verified before reporting publication.

## Local public-Issue bug audit (2026-11-17, not published)

- Based on bf61e7d; branch `fix/issue-feedback-bug-audit`. Preserve all four-issue candidates. Visible 3.3.3, full code 245 / Lite 101. Three new Desktop test packages must be checked with fresh results; future codes exceed 245/101.
- Bug fixes only: validate online-wallpaper catalogs before cache/count/grid keys, recover invalid caches on Retry, and do not hide valid network data on cache-write failure. Shared with native Lite; no new dependency or feature. See `docs/HALO-ISSUES-REVIEW-2026-11-17.md`.
- All public Issues/comments were paged. User permits closure only for fully verified complete fixes, with no comments/reopening; device-dependent candidates do not qualify. No code push, Release/site/feed/announcement publication, TV/emulator operation or system changes. Keep private feedback content, device details and credentials out of tracked files and test packages.

## Local four-issue compatibility test (2026-11-17, not published)

- Branch `fix/four-issue-compatibility-review`, based on d65b13f. Visible version 3.3.3; full code 244, Lite 100. Preserve independent IDs/signer, welcome/icon/startup assets and prior performance fixes. Public remains 243/99.
- Targeted local SDR composition for reported MiTV-ASTR0; shared capture backoff and bounded return cover; opt-in, bounded keep-ADB startup checks and honest HOME diagnostics; real-ID TCL input routing. See `docs/HALO-3.3.3-FOUR-ISSUE-TEST.md`. #42 firmware HOME takeover remains unverified and cannot be guaranteed. Lite does not gain full video-wallpaper or accessibility-guard machinery.
- Deliver three Desktop test APKs and fresh verification only. No publication, feeds, announcements, issue replies/status changes, TV/emulator operation, system launcher changes or permission grants. Actual final host build/test/lint/signature results belong to this delivery's `VERIFICATION.json`, not the older public release report. Future codes must exceed 244/100.

## HaloTV 3.3.3 published (verified 2026-11-17)

- Latest Release: `v3.3.3-halo`. PR #44 merged as `3718289873d97263ab31368dc5f8c6500b021674`. Complete application source archive revision: `2bc7e43163e901f547c916aec15e9cb570ce0f20`.
- Standard / Guard code 243, Lite 99. Three Release builds and 1,270 host tests passed (544/544/182); lint Error/Fatal 0 (350/351/440 warnings remain). Independent IDs/signer, static welcome, icons and startup artwork preserved. No TV/emulator operation.
- All six GitHub assets and three production website APKs actually downloaded and matched Desktop bytes. Homepage, independent feed, version.txt and unchanged welcome preview match prepared files; all 16 merged publication paths match. About version is 3.3.3. Announcement sources and feedback service/bindings unchanged; unauthenticated admin remains 401. No new popup or feedback status/reply change.
- Owner requests concise release notes for this and future updates: short bullets stating actual fixes/optimizations; detailed validation stays in separate records. This release lists held-volume handling and completed-welcome upgrade entry only.
- Desktop: `/Users/bilinggg/Desktop/HaloTV-3.3.3-正式版`, with APKs, complete source/checksums, verification, publication proof and screenshot. Future package codes must exceed 243/99.

## HaloTV 3.3.3 publication preparation (2026-11-17)

Owner authorizes GitHub then website publication. Full code 243 / Lite 99 supersede public 241/97 and local test 242/98. Publish the completed-welcome and held-volume fixes from c018e41, preserving independent IDs/signer/artwork, existing announcement sources and the feedback service/bindings. No new popup, TV or emulator operation. Release notes must use short, plain bullets naming actual fixes/optimizations; keep detailed audits separate. Apply this concise format to future releases too. See docs/RELEASE-3.3.3.md. Verify release artifacts, update feed and both live download channels.

## Local upgrade-entry / held-volume feedback fix (2026-11-17)

Visible 3.3.2; full code 242 / Lite 98, test delivery only. Preserve the independent IDs/signer and static welcome/icon/startup artwork. This installation's completed 3.x welcome now carries forward so a recovered guard is not covered by another confirmation page. Guard and Home-window volume handling compensate actual consumed repeat events only when firmware left volume unchanged; retain delayed-firmware, key-up/stale-repeat, learned-held-timer and missing-lift protections. No permission changes or legacy-package imports.

Three release builds and 1,270 host tests passed (544/544/182). Three regressions fail against pre-fix sources and pass after repair. Lint Error/Fatal 0 (350/351/440 warnings; the additional UseKtx style warning is in the welcome source shared with native API-14 Lite, where AndroidX is not allowed). Identity/signer/minSdk/ZIP/alignment/shared assets/full dex metadata verified. No TV/emulator operation, feedback reply/status change, publication, feed change or announcement. Desktop: `/Users/bilinggg/Desktop/HaloTV-3.3.2-守护与音量修复测试`. See `docs/HALO-3.3.2-UPGRADE-VOLUME-FIXES.md`. Public remains 241/97; future builds must exceed 242/98. OEM behavior remains for owner testing.

# Aerial：给 AI 代理的说明

## HaloTV 3.3.2 published (verified 2026-11-17)

- Public Release `v3.3.2-halo` is Latest; PR #43 merged as `eec5f476aa5e479d1f3e39eadd5f206554c6fc16`. Complete tracked application source ZIP revision: `42e3914e4cb339114458d2ce4e56622bbb87c466`.
- Standard / Guard code 241, Lite 97. Three Release builds and 1,231 host tests passed (526/526/179); lint Error/Fatal 0 (349/350/440 warnings remain). Identity/signature/ZIP/alignment/shared assets/full dex metadata verified. No TV or emulator operation.
- Six GitHub Release assets plus three website APKs actually downloaded and matched Desktop bytes. Production homepage, welcome preview image, version.txt and independent update feed match prepared 3.3.2 files. All 16 merged publication paths match local files exactly. GitHub repository About also updated to 3.3.2.
- Existing announcement sources, feedback service code/bindings/credentials are unchanged. No new remote popup; unauthenticated admin remains 401.
- Desktop: `/Users/bilinggg/Desktop/HaloTV-3.3.2-正式版`, including full source, checksums, verification JSON and publication screenshots. Future package codes must exceed 241/97. Preserve the static welcome and existing completion flag; animated notes below are historical.

## HaloTV 3.3.2 publication preparation (2026-11-17)

Owner requests repository/website publication; per standing preference the next patch is 3.3.2. Full code 241 / Lite 97 supersede local 240/96 and public 238/94. Publish the accepted static light welcome in all three editions, preserving completed welcome state, independent IDs/signer, app icons and system startup artwork. Keep existing announcements and feedback backend/bindings/credentials. No new remote popup, TV or emulator operation. See RELEASE-3.3.2.md. Final build, signature, source, update-feed and production download verification must be recorded.

## Local static light welcome test (2026-11-17)

Owner canceled the welcome animation. This supersedes all animated-welcome notes below. `HaloWelcomeView` now presents the entire accepted light design on its first frame, with no animator, reveal/fade, timer, scheduled refresh or two-press skip. One deliberate confirmation enters Home; the existing completion flag is preserved. Same icon/artwork/IDs/signer. Full code 240 / Lite 96, visible 3.3.1, local test packages only; future package codes must exceed these.

Three release builds passed; 11 focused shared welcome/host-render regressions passed; all three lint reports have Error/Fatal 0 (warnings remain). First-frame native PNG is byte-identical to the previously accepted end-state. The native host test verifies identical pixels after five seconds at the same viewport. Installer identities, signatures, ZIP/alignment/dex metadata and assets verified. Desktop: `/Users/bilinggg/Desktop/HaloTV-3.3.1-静态欢迎页测试`. No TV/emulator operation, publication, update feed change or announcement. See `docs/HALO-WELCOME-LIGHT.md`, `design-qa.md` and Desktop verification.

## Local light welcome test (2026-11-17)

Accepted final pearl/lilac design with handwritten greeting, actual HaloTV logo and brand halo/star/horizon. Shared native 3.4-second entrance for all three editions, explicit remote skip/completion, reduced-motion support, no idle animation, full bitmap release on detach, and one completion flag per visual revision. Existing users see this design once; normal future releases do not reset it. See `docs/HALO-WELCOME-LIGHT.md` and `design-qa.md`.

Visible 3.3.1 with full code 239 / Lite 95; local tests above public 238/94. Three Release builds, 1,234 host tests, lint Error/Fatal 0 (warnings remain), independent identity/signature/ZIP/alignment/dex-metadata and shared-asset checks passed. Fixed Kotlin's inferred API-22 BaseInterpolator field by using the API-1 Interpolator interface, plus 32-bit background color to avoid pastel banding. Native host render/animation previews passed visual comparison at TV sizes, including 4:3 letterboxing. No emulator, TV operation, announcement or publication. Independent signer, app icons, system launch art and remote sources untouched. Desktop: `/Users/bilinggg/Desktop/HaloTV-3.3.1-欢迎页动效测试`. Future public package codes must exceed 239/95.

## Active HaloTV independent checkout (3.3.2 published)

- Installed IDs are `app.halotv.launcher` / `app.halotv.launcher.lite`; class namespace remains `app.orchard.tv`. Never construct a component by repeating applicationId on both sides of `/`.
- Inherited upstream `tools/install.sh`, `get.sh` and `restore.sh` still target Aerial; do not execute them for independent HaloTV. Use `tools/halo-guard.sh` for independent guard diagnosis/explicit repair.
- Local 3.2.8 codes 235/91 supersede 234/90 with the requested Control Center layout and persistent unread announcement badge, preserving audited upstream 1.99 fixes plus bounded Lite thumbnails (see `docs/HALO-3.2.8-UPSTREAM-199-REVIEW.md`), retaining wallpaper/saver handoff fixes (see `docs/HALO-3.2.8-WALLPAPER-TRANSITIONS.md`) while retaining explicit guard activation/permission guidance and bounded wake recovery, plus the earlier performance and orphan-system-registration repairs. See `docs/HALO-3.2.8-GUARD-ACTIVATION.md`. Published as 3.3 codes 236/92 via PR #39 and v3.3-halo; production downloads verified. No new announcement, emulator or TV installation. Next public codes must exceed them. See `docs/HALO-3.2.8-WHOLE-APP-PERFORMANCE.md` and `docs/HALO-3.2.8-PERFORMANCE-REGISTRATIONS.md` and Desktop verification for actual test scope.

## HaloTV 3.3.1 published (verified 2026-11-17)

- Public Release `v3.3.1-halo` is Latest; PR #41 merged as `0a90ccb7ef5c21a7a72d21376b2509ff667c316b`. Application source archive revision: `d9124908433cdbaf616df37991e15151eaa3b2e0`.
- Standard / Guard code 238, Lite 94. Three Release builds, 1,220 host tests and 8 backend tests passed; lint Error/Fatal 0 (warnings remain). Same independent IDs, signer and startup art. No TV/emulator operation.
- All six GitHub release assets and all three website APKs actually downloaded and matched Desktop bytes. Production homepage, version.txt and independent update feed are 3.3.1. Source ZIP and checksums are attached to Release; automatically generated GitHub source archives are only publication-repository snapshots.
- Existing announcement files and feedback backend/bindings/credentials unchanged. No new announcement. Feedback production activation remains in force.
- Desktop: `/Users/bilinggg/Desktop/HaloTV-3.3.1-正式版`, with `PUBLICATION-VERIFICATION-3.3.1.json` and website/GitHub screenshots. Next package codes must exceed 238/94. Older preparation/test notes below are historical.

tvOS 风格的 Android TV 桌面，面向国内电视（小米、TCL 等），Kotlin + Jetpack Compose。

开始任何工作前先读：
1. `docs/HANDOFF.md`：现状、操作方式，以及**必须遵守**的规则
2. `docs/ARCHITECTURE.md`：代码结构
3. `docs/LITE.md`：Android 4.0–4.4 用的 Aerial Lite（`lite/` 模块），改它之前先读
4. `docs/devices/*.md`：各台电视的限制和解决办法
5. `local/TVS.md`（如果存在）：电视的 IP、每台电视上改过的设置、户主的数据。这些是本机信息，不要复制进版本库里的文件

## 命令

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew assembleStandardRelease assembleGuardRelease
./gradlew :lite:assembleRelease   # Android 4.x 用的 Aerial Lite
source local/tvs.env && BUILD=1 KEEP_ADB=1 TV=$XIAOMI_TV tools/install.sh   # BUILD=1：用本地编译的 APK；不加就下载最新 Release
```

- 连电视的命令（adb、curl 访问电视）需要关闭沙箱。
- 在电视上测试一律用 release 版，并连同 APK 里的 .dm 一起装（`adb install-multiple -r x.apk x.dm`，命令见 `docs/HANDOFF.md`「日常操作」），装完看 `dumpsys package dexopt` 是 `speed-profile`。debug 版在电视上会卡；小米 MFTR0 上 `install -r` 后再 `compile -m speed` 得到的是没编译的 `verify`。
- 发 TCL 版本前先给 `versionCode` 加 1。

## 代码约定

- 注释和标识符用英文，界面文字放 `res/values`（英文）、`res/values-zh`（中文）、`res/values-ru`（俄语）、`res/values-uk`（乌克兰语）、`res/values-kk`（哈萨克语），五份都要加，占位符要一致。手机设置页 `assets/web/index.html` 的文字在 `KEYS` / `TEXTS` 表里，同样五种语言，脚本里不要写死文字。
- 界面按 960×540dp 的画布设计（`MainActivity` 统一缩放），不要按真实 dpi 计算。
- 动效：使用 `ui/theme/Theme.kt` 里的 `Motion` 曲线；会被打断的动画（比如焦点）用 spring；不在空闲时跑持续动画；每帧最多一次全屏绘制。
- 遥控器可按的控件用 `ui/components/Pressable`，它只在「本控件上开始的按压」抬起时触发，这样长按打开菜单时，那次抬起不会误触菜单的第一项。
- 厂商的特殊处理写成按功能检测（看包是否存在、设置是否存在），并在注释里写明是哪台机型上验证的。只有确实无法检测时，才写进 `DeviceProfile` 按机型硬编码。

## 不要做

- Aerial、安装脚本和给代理的说明（`site/agent.md`、`site/adapt.md`）只安装 Aerial 自己，这是项目的根本。不要加入任何会下载、安装或推荐其他 App 的代码或说明（应用商店、ADB 开启器、各种工具），也不要加第三方 SDK、在应用里连 adbd 的 shell 通道，或者内容不归我们管的第三方服务器；审 PR 时这类改动一律拦下，交给户主决定。
- 不要停用 `com.mitv.tvhome`，也不要卸载、停用任何系统应用，除非用户明确要求且清楚后果。
- 写 `enabled_accessibility_services` 时不要覆盖其他服务；在 zsh 里写 `${VAR}:...`，不要写 `$VAR:...`。
- 不要删除或改动户主的数据（上传的照片、选好的壁纸、电视里别人的 APK）；清理时只按自己创建时记下的 id 删。
- 不要提交 `local/` 目录（签名密钥、IP）。
- 没有在真机上验证过的功能，不要说「已完成」。
