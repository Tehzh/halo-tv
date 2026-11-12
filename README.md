# Halo TV 1.66 / Halo TV Lite 1.2 Beta

Halo TV 是基于 [Aerial](https://github.com/pengchujin/aerial-tv) 1.67 与 Aerial Lite 1.6 二次开发的非官方版本，保留上游版权声明与 MIT 许可。

完整版适用于 Android 5.0 及以上，Standard 和 Guard 二选一。Lite 使用独立包名 `app.halotv.lite`，面向 Android 4.0–4.4；Lite 是测试版，Android 4.x 真机尚未验证。Lite 不含动态视频壁纸、应用内更新、桌面守护或屏保后待机。

本版调整右上角按钮大小和间距，在设置控制中心增加自愿支持作者的微信与支付宝收款码，功能均免费。收款码由维护者提供并打包在本应用中。

使用 JDK 17 和 Android SDK 构建：`./gradlew :app:assembleStandardRelease :app:assembleGuardRelease :lite:assembleRelease`。签名由构建环境提供，若与已安装版本不同，不能覆盖安装。下载与安装说明见 [Halo TV 仓库](https://github.com/Null9797/halo-tv)。
