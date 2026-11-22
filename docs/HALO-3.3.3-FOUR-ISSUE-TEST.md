# HaloTV 四项兼容修复测试

本轮为本地测试交付，显示版本 3.3.3，Standard / Guard 内部号 244，Lite 100，均高于公开 3.3.3 的 243 / 99。独立版包名、签名、Logo、静态欢迎页保持不变。未发布、未改更新或公告源，未连接电视或模拟器。

## 简短更新说明

- 为反馈机型的本地 1080p SDR 视频增加合成兼容路径，减少返回桌面后的黑屏风险。
- 修正连续黑帧抓取的退避，增加过渡遮挡超时，保护新视频层不被旧释放回调清除。
- 对已开启 ADB 保持的设备增加有限次启动复查，补充 HOME 权限诊断；不承诺突破固件限制。
- 收紧 TCL 信号源切换路径，保留真实输入 ID，避免按名称猜端口或被文件管理器接管。

## #29：MiTV-ASTR0 返回桌面后黑屏

报告中的按键、界面逻辑仍存活，PixelCopy 返回 SUCCESS 却得到黑图，不等于应用已经崩溃，也不等于抓到了可显示的有效帧。报告者另行编译并签名的 TextureView 测试只作为定位线索，不作为本项目的实机验证结果。

内置兼容仅命中 MiTV-ASTR0 / MiTV_ASTR0、Android 11、amlogic。使用 Media3 的 MP4 元数据提取器，在后台确认整个所选播放列表均为非 DRM、无旋转、8-bit AVC、最高 1920×1088、非明确 HDR 的本地视频后，才使用 TextureView。无法确认、4K、HEVC、HDR、旋转视频保留原 SurfaceView 路径；其他电视默认不变。显式设备配置可选择启用或关闭该兼容标志。

元数据不打开第二个视频解码器或 MediaMetadataRetriever；单文件限时 300ms、最多读取 2MiB / 四次定位，整次选择限时 1.5s，缓存最多 64 项，以路径、长度和修改时间区分文件。超限或读取失败使用原路径，不在主线程等待。

TextureView 会增加 GPU 合成成本，故不全局替换 FilmSlot、不改变 4K/HDR 屏保；实际播放流畅度、合成黑屏及功耗需要目标电视验证。自制的微型 MP4 测试素材仅验证主机端元数据读取，不验证厂商解码器。

报告还提到升级后双排布局恢复单排。目前独立版设置初始化中没有找到强制重置该选择的代码；已新增正式版和 Lite 的反复初始化持久化回归。不能据此认定所有配置丢失场景都已解决；若复现，应核对是否发生卸载、清数据、换包或配置被其他客户端改写。

## #12：切页 / 设置时短暂黑屏

该反馈与 #29 的永久合成黑屏分开处理。过渡层保留最后有效底图，拒绝用黑、绿或失真纯色抓图覆盖它。新视频首帧提交后继续按现有短淡出交接；窗口可见且已附着时最多等待 1.5s，缺失首帧也会撤去遮挡、露出已有桌面底图，而非永久盖住界面。超时撤去遮挡不代表视频已恢复。

每次返回有独立代次，旧首帧和旧超时不能揭开新一轮遮挡。切换输出层时，旧 AndroidView 的延迟释放也不能清掉新输出。原有暂停、后台停止解码、首帧等待和有限重试保持不变，不在其他应用后面常驻解码。

SurfaceView 抓图连续三次无效后退避 30s；成功会清零，换 Surface 重新判断。以前成功过再连续失败也会退避，单次夜景或黑帧不会永久封禁。保留原有慢抓图、掉帧和原生崩溃保护，不用连续抓帧掩盖问题。

首次主机测试遗漏了窗口可见事件，因而没有启动可见倒计时。修正测试后，先断言已附着，再派发真实的窗口可见回调；保留原始超时断言，并增加隐藏期间不计时、第二次返回取消旧计时的回归，未删掉失败测试或放宽断言。

## #42：MiTV-AZFP0 / Android 9 的 HOME 和 keep_adb

公开报告正文被截断，缺少完整启动诊断。现有材料显示固件仍解析到系统桌面、系统 HOME 入口受签名权限约束、第三方无障碍收不到按键；这类限制不能凭应用内开关保证绕过。Guard 不声明 HOME，因此也不能把 Guard 视为所有小米固件的通用解法。本轮没有禁用 / 卸载系统桌面、没有授予设备权限、没有增加前台保活服务或强制重开已关闭守护。

已有 keep_adb 选择现在同步确认落盘结果；仅在用户已开启该选择、已有 WRITE_SECURE_SETTINGS、用户存储已解锁且未处于关机保护时，启动后 2 / 15 / 45 / 90 秒进行四次复查。与无障碍是否绑定解耦，90 秒后停止，不常驻轮询。显式关闭、权限撤销、关机保护会阻止后续操作。保留原来对使用中关闭调试的退出判断及 Android 6–10 的一次性传输探测；没有增加绕过授权的 ADB 通道。

诊断补充 HOME 实际解析组件的 exported / enabled / permission / permitted，以及 keep_adb 的最近检查结果。`adb_enabled=1` 仅说明设置值，不能证明无线 ADB 端口、认证或连接正常。

因此本轮交付的是有限恢复与诊断改进，#42 的固件独占 HOME 仍待完整日志和目标设备确认，不能标为全面解决。

## #24：TCL Android 14 HDMI 端口

反馈版本为 3.2.2（218）。当前基线已整合上游对 HW15–18 真实硬件 ID 的处理，不能将这部分重复宣称为新修复。

本轮原生 TCL 路径继续传真实 HW ID，加入 CLEAR_TOP，减少复用旧播放 Activity 时保留上次输入。框架提供硬件 ID 时不再由可重命名的 HDMI 文案计算偏移；缺少 ID 不猜端口。标准 passthrough URI 保留完整 inputId；只将可信、可访问的电视处理器固定为目标，避免通用 VIEW 被文件管理器接走。已验证的 MT5879 框架空列表兼容分支保持不变。

HW15–18、完整 passthrough URI、不可访问及非电视处理器的主机路由测试不代表目标 TCL 固件已实际切换成功。本轮没有扩展繁体中文功能。

## Lite 范围

Lite 提高内部号并补充双排布局持久化回归。本轮完整视频壁纸、SurfaceView / TextureView 及无障碍守护改动不移植进 Lite；不添加 AndroidX、Compose、协程或前台服务，保留 minSdk 14。

## 最少补充信息（需要用户自行复现时）

优先使用“公告与反馈”附带本版诊断，并注明 Standard / Guard / Lite、使用的片源编码及操作步骤。仍需 ADB 日志时，仅使用只读命令；本轮未执行以下命令：

```sh
adb shell dumpsys activity app.halotv.launcher/app.orchard.tv.MainActivity report > halotv-report.txt
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME > home-resolve.txt
adb shell dumpsys tv_input > tv-input.txt
adb logcat -d -v time -t 600 -s AerialDiag AerialGuard ExoPlayerImpl MediaCodec AndroidRuntime > halotv-recent.txt
```

#29：返回黑屏后立刻采集报告与最近日志，检查 `local video output`、首帧及抓图状态；请勿提供私人视频内容。#12：注明是外部应用、设置、屏保哪一种往返。#42：提供冷启动后和 HOME 失效后两份诊断，区分“已配置 / 已绑定 / 真实解析 / 按键事件 / ADB 连接”。#24：提供 tv_input 与系统信号源面板的端口对应，不要求执行任何切换或修改命令。分享前遮盖 IP、令牌、账号和私人文件名。

## 验证记录

桌面交付目录中的 `VERIFICATION.json` 和构建日志记录本轮实际构建、完整测试、lint、身份 / 签名 / ZIP / 对齐及资源检查结果。只对本次 244 / 100 测试包有效，不沿用公开 243 / 99 的旧校验报告。厂商 GPU 合成、真实 HOME、冷启动、无线 ADB 和 HDMI 切换均未进行本轮实机验证。

参考：[Android 官方 Surface 选择说明](https://developer.android.com/media/media3/ui/surface)、[passthrough input URI](https://developer.android.com/reference/android/media/tv/TvContract#buildChannelUriForPassthroughInput(java.lang.String))。
