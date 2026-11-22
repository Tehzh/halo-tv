# HaloTV 3.5.1 Issues 修复测试版

本批基于上游整合提交 `ac03ba0b56a6671bc6f8e1822c0af846d7d66875`，和第一批为两个独立工作树及交付目录。读取了 18 条开放反馈的正文、现有评论及重点反馈的附件。Standard / Guard 内部版本号 273，Lite 120，三个版本的显示版本号均为 3.5.1。

## 纳入本批的修复

| 反馈 | 定位及处理 | 验证与真机复测 |
| --- | --- | --- |
| [#69 壁纸分类与焦点](https://github.com/Null9797/halo-tv/issues/69) | 每次打开产生新的壁纸页实例，初始化分类、焦点和内容滚动状态。常规入口从「静态壁纸」开始，灵动岛的本地照片／视频入口仍直达指定分类和本地列表。 | Compose 回归覆盖退出再进入、快速更换入口和明确目标分类。小米盒子 3 Pro 可用遥控器复测动态壁纸、屏保选择及深链。 |
| [#71 Home 闪出设置页](https://github.com/Null9797/halo-tv/issues/71) | Home 已直接把当前覆盖层置空。根因是共用内容在退场动画中把空覆盖层解读为普通设置页。退场继续绘制原页，关闭其分类交互与子对话框；Back 仍按原入口返回，常规设置仍可从控制中心打开。 | Compose 时钟在退场中间检查没有设置内容。小米盒子 3 Pro 可在动图、静图及屏保分类分别录屏验证，并检查 Back 行为。 |
| [#72 翻页时钟大数字](https://github.com/Null9797/halo-tv/issues/72) | 小米盒子日志中，OpenGLRenderer 报大字号超出字形缓存。Standard / Guard 在 Android 5.0–5.1 用缓存的 Path 绘制四个大数字，使用原有字宽、位置及翻页裁剪。小字、秒数、冒号及全局硬件加速沿用原设置。 | 使用同一绘制路径在 960×540、1920×1080 校验静止、翻页前半、后半和结束帧。核对了手动预览请求、桌面待机计时和系统 Dream 均进入同一 FlipClockView。真实 SDK 22 GPU 上两种入口仍待复测。 |
| [#63 动态壁纸玻璃颜色残留](https://github.com/Null9797/halo-tv/issues/63) | 旧代码按壁纸大类 ID 保存第一张玻璃，换两部在线影片时 ID 一直为 LIVE。改为换片清空，再从匹配当前影片的样本固定一份玻璃；迟到的旧片样本不能填入新选项。Dock、菜单及灵动岛使用该份玻璃，省去每帧取色。 | 测试用绿色、蓝色样本切换，验证迟到的旧片、相同大类 ID 和同一影片后续帧。小米盒子 3 Pro 待检查连续切片后菜单与 Dock 色调。 |
| [#56 在线视频缩略图](https://github.com/Null9797/halo-tv/issues/56) | 现有缩略图的兼容网络栈只读壁纸 CA，Android 5/6 不加载 XML 里的 Apple 根。给 `sylvan.apple.com` 缩略图连接使用随包的 Apple 根与系统信任，保留主机名校验并停用自动跳转。 | Mac 同一缩略图 HTTPS 返回 200 且无跳转，服务器链可用 APK 内 Apple 根校验。旧 Android 主机测试验证补根仅用于指定 HTTPS 主机、全局信任和主机名校验保持原样。小米盒子 3 Pro 的实际 TLS 握手及可见缩略图仍待复测。 |

## 暂待复测或观察

| 反馈 | 当前判断与所需证据 |
| --- | --- |
| [#58 暴风冷开机残影](https://github.com/Null9797/halo-tv/issues/58) | 3.5 的 4K H.264 对照未再露出开机末帧；1080p HEVC 仍露出。两个片源同时改变了编码和封面条件，不能从中归因为 4K 分支。现有策略在 API 23 继续走 SurfaceView，保护仍取决于选中影片匹配的有效封面和播放器的真实首帧事件。复测用同一内容及各自准确的首帧封面，两次断电开机，同步记录媒体 ID、输出类型、首帧时间和录屏。本批未改 SurfaceView/TextureView 决策、首帧遮罩或封面保存。 |
| [#55 在线 4K](https://github.com/Null9797/halo-tv/issues/55) | 3.5 已恢复画质入口。3.5.1 的 SDR 探测仍在 API 21+ 调用系统 HEVC 解码能力，拒绝报告不支持 3840×2160/30 的解码器。MX Player 能播自己的样片，尚不能证明 HaloTV 通过标准 MediaCodec 能播放对应的线上片源。需要同机完整 `Aerial capabilities` 及 `Aerial playback` 诊断、具体影片和 codec 参数；保留安全回退。 |
| [#56 预览二维码与亮度](https://github.com/Null9797/halo-tv/issues/56) | 在线图库的系统 Dialog 包含独立 `VideoView`，二维码位于底下的壁纸设置层。预览没有独立压暗参数，固件的视频层、透明窗口及色彩路径尚未分开验证。需要同一片源、同一时间点、同一电视图像模式的预览与壁纸对照，以及出现二维码时的短录屏。暂不改变视频输出或亮度。 |
| [#64 长虹 D6U](https://github.com/Null9797/halo-tv/issues/64) | 附件显示 Android 8 / m7221、无标准 TV 输入；长虹设置已有专门入口与回退，诊断未提供可用厂商信号源组件。需要原厂入口实际启动的 Activity/Service 对照及亮度调节前后读数。 |
| [#54 Google TV 绿条](https://github.com/Null9797/halo-tv/issues/54) | 原反馈为 3.3.6。尚缺最新版本与具体片源，以及区分播放层／退场图／网格模糊背景的录屏。保持渲染策略。 |
| [#59 Android 6 守护工具](https://github.com/Null9797/halo-tv/issues/59) | 第一批基线已有仅单用户 0 的兼容回退及 UTF-8 容错。本批沿用。 |
| [#70 当贝 DBB3](https://github.com/Null9797/halo-tv/issues/70) | 报告以 3.3.7 诊断为主。已有 3.5 APK 校验通过的日志，缺少最终安装结果与具体故障表现。 |
| [#66 应用图标](https://github.com/Null9797/halo-tv/issues/66) | 分类为素材适配需求；需要对应包名和可使用的图标。可复测第一批已经合入的通用图标库。 |
| [#24 TCL HDMI](https://github.com/Null9797/halo-tv/issues/24) | 报告为 3.2.2，需要新版本的选中端口、实际端口、原厂入口对照。繁体中文与 #35 一同评估。 |
| [#34 儿童模式直播](https://github.com/Null9797/halo-tv/issues/34) | 需要明确是答题锁定、系统信号源还是第三方直播 App，及所用版本。 |
| [#20 DBF7 Pro](https://github.com/Null9797/halo-tv/issues/20) | 仅有旧版诊断，需要当前版本和具体复现。 |
| [#26](https://github.com/Null9797/halo-tv/issues/26) | 缺少可定位的问题描述和复现信息。 |
| [#33 题库导入导出](https://github.com/Null9797/halo-tv/issues/33)、[#35 繁体中文](https://github.com/Null9797/halo-tv/issues/35) | 功能及本地化建议，留待单独评估。 |

Lite 保留原生界面、现有功能和 Android 4.0 最低版本要求，本批仅提高内部版本号。覆盖升级核验基于同版别 APK 元数据。三包为本地复测用途，未在电视安装，未对 GitHub、官网或 OTA 做写入。
