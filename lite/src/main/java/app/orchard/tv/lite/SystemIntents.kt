package app.orchard.tv.lite

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import android.view.KeyEvent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings

/**
 * The TV's own pages. Keep known OEM routes, then discover public system settings entries when
 * a firmware omits standard Android settings actions. Only a successful start ends the search.
 */
object SystemIntents {
    private fun settingsCandidates(context: Context): List<Intent> = listOf(
        *TclMs901Pages.settings(context),
        // Letv (S40 Air and other eUI sets): its own settings, the only one some stripped Letv
        // firmwares ship — com.android.settings is absent there, so the standard action resolved to
        // nothing and the panel's gear did nothing (S40 Air, Android 4.3, 2026-11-02).
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.stv.t2.globalsetting", "com.stv.t2.globalsetting.GlobalSettingActivity")),
        Intent("com.xiaomi.mitv.settings.SETTINGS").setComponent(ComponentName("com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.entry.MainActivity")),
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.haier.tv.menusetting", "com.haier.tv.menusetting.RootActivity")),
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.konka.tvsettings", "com.konka.settingcenter.settings.view.MainSettingsActivity")),
        // Hisense VIDAA_TV: its own page before AOSP's, as in the full app (#1085).
        Intent(Settings.ACTION_SETTINGS).setComponent(ComponentName("com.android.tv.settings", "com.hisense.systemsettings.MainActivity")),
        // Baofeng AML_T962 exposes its settings by component (#1339).
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.baofengtv.settings", "com.baofengtv.settings.MainActivity")),
        // LeTV Max4-55 (Android 6): its own settings before AOSP's empty page, as in the full app (#1528).
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.stv.globalsetting", "com.stv.globalsetting.activity.SettingActivity")),
        Intent(Settings.ACTION_SETTINGS),
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.konka.systemsetting", "com.konka.systemsetting.MainActivity")),
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.tcl.settings", "com.tcl.settings.ui.MainActivity")),
        // TCL 50P8 (MStar m7221): the main activity in the package root, as in the full app (#1405).
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.tcl.settings", "com.tcl.settings.MainActivity")),
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.pptv.setting", "com.pptv.setting.root.SettingsActivity")),
        Intent(Intent.ACTION_VIEW, Uri.parse("Settings://com.xgimi.misckey.main/mainPage")),
    )

    private fun networkCandidates(context: Context, ethernet: Boolean): List<Intent> = listOf(
        *TclMs901Pages.network(context),
        // Letv: the network page is a tab inside its own settings, and com.android.settings is not
        // installed at all, so Settings.ACTION_WIFI_SETTINGS went nowhere (S40 Air, Android 4.3).
        // openNetworkPanel shows that page itself where the TV has it.
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.stv.t2.globalsetting", "com.stv.t2.globalsetting.GlobalSettingActivity")),
        *listOfNotNull(hisenseNetworkSettingsIntent(context)).toTypedArray(),
        *listOfNotNull(Intent("com.xiaomi.mitv.settings.ETHERNET_SETTINGS").setPackage("com.xiaomi.mitv.settings").takeIf { ethernet }).toTypedArray(),
        Intent("com.xiaomi.mitv.settings.WIFI_SETTINGS").setComponent(ComponentName("com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.wifi.MitvWifiActivity")),
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.android.tv.settings", "com.android.tv.settings.connectivity.NetworkActivity")),
        Intent(Settings.ACTION_WIFI_SETTINGS),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.baofengtv.settings", "com.baofengtv.settings.MainActivity")),
        Intent("com.pptv.intent.action.WIFI_ACTIVITY").setComponent(ComponentName("com.pptv.setting", "com.pptv.setting.wifi.WifiWorkActivity")),
        Intent(Settings.ACTION_SETTINGS),
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.tcl.settings", "com.tcl.settings.ui.MainActivity")),
        Intent("android.intent.action.WIFISETTING").setPackage("com.tcl.settings"),
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.tcl.settings", "com.tcl.settings.MainActivity")),
    )

    // Same OEM network category as SystemProbe (#760), not the direct Wi-Fi list.
    // Sidebar focus verified on H55E9A / Android 8; Android 4.x Lite still needs device verification.
    private fun hisenseNetworkSettingsIntent(context: Context): Intent? {
        val intent = Intent("android.settings.NETWORK_SETTINGS").setComponent(
            ComponentName("com.android.tv.settings", "com.hisense.systemsettings.MainActivity"),
        )
        val info = runCatching { context.packageManager.resolveActivity(intent, 0)?.activityInfo }.getOrNull()
        return intent.takeIf { info != null && info.exported && info.enabled && info.applicationInfo.enabled }
    }

    /**
     * HDMI 1-3, AV and the antenna through Xiaomi's player, which takes the port as its `input` number: 23-25 for HDMI, the
     * full app's xiaomiSources. Mi TV 2 (49", Android 4.3) lists no inputs, and its owner opened
     * them this way from another launcher (issue #1094); Mi TV 2 and 3 on Android 5.1 did the same
     * in the full app (#599 #309). Not on boxes, which have the player but no HDMI inputs, and
     * only before Android 5, where no TV lists its inputs: later Mi TV 4 boards number them
     * otherwise, which the full app knows.
     * CLEAR_TOP: a player task in the background is otherwise only brought back, its input unchanged.
     */
    fun xiaomiInputs(context: Context): List<Pair<String, Intent>> {
        if (Build.VERSION.SDK_INT >= 21) return emptyList()
        TclMs901Sources.inputs(context).takeIf { it.isNotEmpty() }?.let { return it }
        val player = ComponentName("com.xiaomi.mitv.tvplayer", "com.xiaomi.mitv.tvplayer.ExternalSourceActivity")
        if (runCatching { context.packageManager.getActivityInfo(player, 0) }.isFailure) return emptyList()
        val traits = runCatching {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, "ro.build.characteristics") as String
        }.getOrNull().orEmpty()
        if ("stb" in traits || "mbx" in traits) return emptyList()
        fun play(input: Int) = Intent("com.xiaomi.mitv.tvplayer.EXTSRC_PLAY").setComponent(player)
            .putExtra("input", input)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // AV is 2 and the antenna 1, DTMB its own page where the player has it (MiTV2-49, the
        // owner's list, issue #1094). VGA (0) is left out: not every Mi TV of these years has one,
        // and nothing tells which.
        val dtmb = ComponentName("com.xiaomi.mitv.tvplayer", "com.xiaomi.mitv.tvplayer.dtmb.DTMBActivity")
        val digital = if (runCatching { context.packageManager.getActivityInfo(dtmb, 0) }.isSuccess) {
            listOf("DTMB" to Intent("com.xiaomi.mitv.tvplayer.DTMB_PLAY").setComponent(dtmb).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        } else emptyList()
        return (1..3).map { port -> "HDMI $port" to play(22 + port) } +
            listOf("AV" to play(2), context.getString(R.string.input_antenna) to play(1)) + digital
    }

    /**
     * The TV's own input panel where Android lists no inputs (every 4.x TV): Hisense's service,
     * TCL's panel, Coocaa's source dialog. False when the TV has none of them.
     */
    fun openInputPicker(context: Context): Boolean {
        val pm = context.packageManager
        val hisense = Intent("com.hisense.tv.start_inputs").setComponent(ComponentName("com.hisense.tv.inputs", "com.hisense.tv.InputsUIService"))
        if (pm.resolveService(hisense, 0) != null) {
            val shown = runCatching { if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(hisense) else context.startService(hisense) }.getOrNull() != null
            if (shown) return true
        }
        val tcl = if (pm.hasSystemFeature("com.tcl.software.systemui_custom_tv_source")) {
            Intent("com.tcl.tvstatusbar.action.SHOW_WINDOW").setPackage("com.android.systemui")
                .putExtra("TvStatusBar_Type", "Source").putExtra("TvStatusBar_StartFrom", "input")
        } else {
            Intent("com.tcl.insrc.SHOW_WINDOW").setPackage("com.tcl.settings")
        }
        if (runCatching { pm.resolveService(tcl, 0) }.getOrNull() != null && runCatching { context.startService(tcl) }.getOrNull() != null) return true
        val coocaa = Intent("coocaa.intent.action.SOURCE_DIALOG").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { pm.resolveActivity(coocaa, 0) }.getOrNull() != null && runCatching { context.startActivity(coocaa) }.isSuccess) return true
        // Coocaa C10's AI launcher picker and the older SkyMovie broadcast (issue #779).
        val aigc = Intent("coocaa.intent.aigc.home.tvsource").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { pm.resolveActivity(aigc, 0) }.getOrNull() != null && runCatching { context.startActivity(aigc) }.isSuccess) return true
        val skyMovie = Intent("com.coocaa.action.SOURCE")
            .setComponent(ComponentName("com.tianci.movieplatform", "com.ccos.cooui.homepage.receiver.LaunchSourceReceiver"))
        if (runCatching { pm.queryBroadcastReceivers(skyMovie, 0) }.getOrNull().orEmpty().any { it.activityInfo.exported }) {
            return runCatching { context.sendBroadcast(skyMovie) }.isSuccess
        }
        // Letv eUI shows its panels on its own remote's keys; its signal source app answers but
        // never comes up on this firmware (S40 Air, Android 4.3, verified with am broadcast).
        if (openInputsPanel(context)) return true
        // LeTV eUI's signal source app, back to the input last used (X3-55, Android 5.0.1, the full
        // app's issues #818 #998); not verified on LeTV's Android 4.x.
        val letv = ComponentName("com.stv.signalsourcemanager", "com.stv.signalsourcemanager.MainActivity")
        if (runCatching { pm.getActivityInfo(letv, 0) }.getOrNull()?.exported == true &&
            runCatching { context.startActivity(Intent(Intent.ACTION_MAIN).setComponent(letv).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        ) return true
        // Keep the known Foxconn route in its original order relative to other OEM panels.
        if (systemFirmware(context, MSTAR_PACKAGES[0]) &&
            tryStart(context, listOf(Intent(Intent.ACTION_MAIN).setComponent(FOXCONN_SOURCE)))) return true
        // Skyworth 42X5 (8S40, Android 4.4.4, issue #1419): the framework turns the remote's input
        // key into a "com.android.sky.SendHotKey" broadcast that ccosservice's exported HotKeyReceiver
        // answers with the input panel, the key in an int extra "specialKey" (the reporter read it
        // from the firmware's PhoneWindowManager and brought the panel up with am broadcast; 1.24's
        // KeyEvent in EXTRA_KEY_EVENT reached the receiver and was ignored). Its HomeService below
        // is there but ignores the key, so this goes first; sent only to an exported receiver.
        if (sendSkyHotKey(context)) return true
        // Baofeng TV (Android 4.4, issue #1225): the remote's source key is the broadcast
        // "baofengtv.action.SOURCE_SWITCH", which the system app BFTVSourceSwitch's exported
        // TVEventReceiver answers with its source overlay (read from the APK the reporter sent;
        // its own launcher entry sends just this). Only where that receiver is there and exported.
        val baofeng = Intent("baofengtv.action.SOURCE_SWITCH")
            .setComponent(ComponentName("com.baofengtv.sourceswitch", "com.baofengtv.sourceswitch.receiver.TVEventReceiver"))
        if (runCatching { pm.queryBroadcastReceivers(baofeng, 0) }.getOrNull().orEmpty().any { it.activityInfo.exported } &&
            runCatching { context.sendBroadcast(baofeng) }.isSuccess
        ) return true
        // Baofeng AML_T962 has no BFTVSourceSwitch. Its FUI source bar has a public component (#1339).
        val bftvRow = ComponentName("com.bftv.fui.biu", "com.bftv.fui.biu.views.activity.FRowActivity")
        val bftvInfo = runCatching { pm.getActivityInfo(bftvRow, 0) }.getOrNull()
        if (bftvInfo?.exported == true && bftvInfo.enabled && bftvInfo.applicationInfo.enabled &&
            runCatching { context.startActivity(Intent(Intent.ACTION_MAIN).setComponent(bftvRow).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        ) return true
        // Older Coocaa (Skyworth 8S61, MStar, Android 4.4.2) has none of the above: on the remote's
        // input key the system starts its launcher's HomeService with the key, which shows the
        // input page ("HomeService onStartCommand ##, keyCode=178"; issue #1272, the reporter's
        // logcat). The extra is read from that log and the dex, not tried from an app; last, so
        // TVs that switch some other way are not touched.
        val coocaaHome = ComponentName("com.tianci.movieplatform", "com.coocaa.homepage.vast.service.HomeService")
        if (runCatching { pm.getServiceInfo(coocaaHome, 0) }.getOrNull()?.exported == true) {
            val key = Intent("coocaa.intent.action.SOURCE_SERVICE").setComponent(coocaaHome)
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TV_INPUT))
            if (runCatching { context.startService(key) }.getOrNull() != null) return true
        }
        // Preserve the priority of working Skyworth/Coocaa routes on MStar-based televisions.
        return openMstarInputPicker(context, includeFoxconn = false)
    }

    private val MSTAR_PACKAGES = listOf("com.mstar.tvsetting", "com.mstar.tv.tvplayer.ui")
    private const val MSTAR_SOURCE_INFO = "com.mstar.tv.tvplayer.ui.intent.action.SOURCE_INFO"
    private val FOXCONN_SOURCE = ComponentName("com.mstar.tvsetting", "com.foxconn.etvg.inputsource.FxcInputSourceActitvity")
    private fun systemFirmware(context: Context, pkg: String): Boolean {
        val info = runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.getOrNull() ?: return false
        return info.enabled && info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    /** Capability detection, not a brand/model guess. Keep a settings fallback visible on these TVs. */
    fun hasMstarInputFirmware(context: Context): Boolean = MSTAR_PACKAGES.any { systemFirmware(context, it) }

    internal fun openMstarInputPicker(context: Context, includeFoxconn: Boolean = true): Boolean {
        val known = ArrayList<Intent>()
        if (includeFoxconn && systemFirmware(context, MSTAR_PACKAGES[0])) {
            // Confirmed by the upstream reporter on Sharp CVTM648 / Android 6 (#1048).
            known += Intent(Intent.ACTION_MAIN).setComponent(FOXCONN_SOURCE)
        }
        if (systemFirmware(context, MSTAR_PACKAGES[0])) {
            // Upstream #1580: the actual input list, before the SOURCE_INFO banner.
            known += Intent(Intent.ACTION_MAIN).setComponent(ComponentName(MSTAR_PACKAGES[0],
                "com.mstar.tvsetting.hotkey.InputSourceListViewActivity"))
        }
        if (systemFirmware(context, MSTAR_PACKAGES[1])) {
            known += Intent(MSTAR_SOURCE_INFO).setPackage(MSTAR_PACKAGES[1])
            // SourceInfoActivity is documented on Sharp LCD_xxSFFOC470A (#1342). Some ROMs
            // remove its action filter; an explicit public component does not need that filter.
            known += Intent(MSTAR_SOURCE_INFO).setComponent(ComponentName(MSTAR_PACKAGES[1],
                "com.mstar.tv.tvplayer.ui.channel.SourceInfoActivity"))
        }
        if (tryStart(context, known)) return true

        // A small, bounded firmware-only scan handles renamed public source-picker activities.
        // Never open service/factory/test pages or infer HDMI port numbers from their names.
        val discovered = ArrayList<Intent>()
        val attempted = known.mapNotNull { it.component }.toSet() + FOXCONN_SOURCE
        for (pkg in MSTAR_PACKAGES) {
            if (!systemFirmware(context, pkg)) continue
            val activities = runCatching { context.packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities }
                .getOrNull().orEmpty()
            for (info in activities) {
                if (!accessible(context, info)) continue
                val name = info.name.lowercase(java.util.Locale.ROOT)
                if (listOf("factory", "engineer", "calibrat", "test", "debug", "reset", "upgrade").any { name.contains(it) }) continue
                val leaf = name.substringAfterLast('.')
                if (!listOf("inputsource", "sourceinfo", "sourceselect", "sourcepicker").any { leaf.contains(it) }) continue
                val component = ComponentName(pkg, info.name)
                if (component in attempted) continue
                discovered += Intent(if (pkg == MSTAR_PACKAGES[1]) MSTAR_SOURCE_INFO else Intent.ACTION_MAIN).setComponent(component)
            }
        }
        return tryStart(context, discovered)
    }

    private fun sendSkyHotKey(context: Context): Boolean {
        val probe = Intent("com.android.sky.SendHotKey")
        val receiver = runCatching { context.packageManager.queryBroadcastReceivers(probe, 0) }.getOrNull().orEmpty()
            .firstOrNull { it.activityInfo.exported && it.activityInfo.enabled }?.activityInfo ?: return false
        val key = Intent(probe).setComponent(ComponentName(receiver.packageName, receiver.name))
            .putExtra("specialKey", KeyEvent.KEYCODE_TV_INPUT)
        return runCatching { context.sendBroadcast(key) }.isSuccess
    }

    /**
     * Letv's own network page — 无线网络 / 有线网络 — shown as its remote's network key shows it
     * (S40 Air, Android 4.3, verified). False where the TV has no such panel, which is where
     * [openNetwork] tries activity routes.
     */
    fun openNetworkPanel(context: Context): Boolean = letvPanel(context, "com.letv.action.network")

    /**
     * Letv's input list — 模拟电视 / 数字电视 / 视频 / HDMI1 — shown as its remote's source key
     * shows it (S40 Air, Android 4.3, verified). [openInputPicker] tries it too; a caller that
     * closes its own panel on success asks first, so it knows which panel it must not close.
     */
    fun openInputsPanel(context: Context): Boolean = letvPanel(context, "com.letv.action.source")

    /**
     * A panel Letv's own settings app shows on a broadcast — its GlobalReceiver takes these, which
     * are what its remote's network, source and settings keys send — for the stripped firmwares
     * that ship no com.android.settings at all, so the standard actions lead nowhere, and whose
     * signal source app resolves but does not come up (S40 Air, Android 4.3, 2026-11-02). False
     * where nothing listens: no such set, no such panel.
     */
    private fun letvPanel(context: Context, action: String): Boolean {
        val listening = runCatching { context.packageManager.queryBroadcastReceivers(Intent(action).setPackage(LETV_SETTINGS), 0) }
            .getOrNull().orEmpty().any { it.activityInfo.exported }
        if (!listening) return false
        return runCatching { context.sendBroadcast(Intent(action).setPackage(LETV_SETTINGS)) }.isSuccess
    }

    /**
     * Konka's full settings menu, a panel its settings app shows as a service (Konka V563, issue
     * #771); false where the TV has no such service.
     */
    fun openSettingsPanel(context: Context): Boolean {
        val konka = Intent("com.konka.settings.action.SETTING_MAIN_VIEW")
            .setComponent(ComponentName("com.konka.tvsettings", "com.konka.service.SettingService"))
        if (runCatching { context.packageManager.resolveService(konka, 0) }.getOrNull()?.serviceInfo?.exported != true) return false
        return runCatching { context.startService(konka) }.getOrNull() != null
    }

    /**
     * Xiaomi's settings menu as the popup its settings app shows on a broadcast, for where its
     * activity closes at once: on Android 6 (Mi TV 4, Mi Box 3S, the full app's issue #834) it only
     * hosts that popup over PatchWall and finishes over any other Home. Older Xiaomi firmwares are
     * not verified. Armed when settings open, sent if Home is back within a moment (onResume).
     */
    private var settingsOpenedAt = 0L

    fun noteSettingsOpened(intent: Intent) {
        settingsOpenedAt = if (intent.component?.packageName == XIAOMI_SETTINGS) SystemClock.uptimeMillis() else 0L
    }

    /** Whether the Xiaomi settings just opened closed at once; asked once, on resume. */
    fun settingsBounced(context: Context): Boolean {
        val at = settingsOpenedAt
        settingsOpenedAt = 0L
        if (at == 0L || SystemClock.uptimeMillis() - at > 800L) return false
        val popup = Intent("com.xiaomi.intent.action.show.globalsetting").setPackage(XIAOMI_SETTINGS)
        return runCatching { context.packageManager.queryBroadcastReceivers(popup, 0) }.getOrNull().orEmpty().any { it.activityInfo.exported }
    }

    fun showXiaomiSettingsPopup(context: Context) {
        runCatching { context.sendBroadcast(Intent("com.xiaomi.intent.action.show.globalsetting").setPackage(XIAOMI_SETTINGS)) }
    }

    private const val XIAOMI_SETTINGS = "com.xiaomi.mitv.settings"
    /** LeTV eUI's settings app, which holds the panels its remote's keys show ([letvPanel]). */
    private const val LETV_SETTINGS = "com.stv.t2.globalsetting"

    /** Try the actual start, not only resolution: OEM activities can reject a resolved intent. */
    fun openSettings(context: Context): Boolean = openSettingsPanel(context) ||
        tryStart(context, settingsCandidates(context)) || tryStart(context, discoveredSettings(context))

    fun openNetwork(context: Context, ethernet: Boolean = false): Boolean = openNetworkPanel(context) ||
        tryStart(context, networkCandidates(context, ethernet)) || openSettings(context)

    fun openApplicationDetails(context: Context, pkg: String): Boolean = tryStart(context, listOf(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)),
    ))

    /** Always asks the system uninstaller to confirm; never deletes app data or shells out. */
    fun requestUninstall(context: Context, pkg: String): Boolean {
        val info = runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.getOrNull() ?: return false
        if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) return false
        val uri = Uri.parse("package:" + pkg)
        return tryStart(context, listOf(Intent(Intent.ACTION_DELETE, uri), Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri)))
    }

    /**
     * Some old TV firmwares omit DEFAULT from their settings/installer filters. Resolve normally
     * first, then explicitly target exported matching activities; do not guess private components.
     */
    internal fun tryStart(context: Context, intents: List<Intent>): Boolean {
        val pm = context.packageManager
        val attempted = HashSet<String>()
        for (intent in intents) {
            val direct = intent.component
            val candidates = if (direct != null) {
                listOfNotNull(runCatching { pm.getActivityInfo(direct, 0) }.getOrNull())
            } else {
                val preferred = runCatching { pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo }.getOrNull()
                val matches = runCatching { pm.queryIntentActivities(intent, 0) }
                    .getOrNull().orEmpty().mapNotNull { it.activityInfo }
                // The resolver chooser is not a settings page and must not be pinned explicitly.
                listOfNotNull(preferred?.takeIf { chosen -> matches.any {
                    it.packageName == chosen.packageName && it.name == chosen.name
                } }) + matches
            }
            for (info in candidates) {
                if (!accessible(context, info)) continue
                val target = Intent(intent).setComponent(ComponentName(info.packageName, info.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!attempted.add(target.toUri(0))) continue
                try {
                    context.startActivity(target)
                    noteSettingsOpened(target)
                    return true
                } catch (e: RuntimeException) {
                    Log.w("HaloLiteSettings", "Cannot open " + target.component?.flattenToShortString(), e)
                }
            }
        }
        return false
    }

    private fun accessible(context: Context, info: ActivityInfo): Boolean {
        if (!info.enabled || !info.applicationInfo.enabled || !info.exported) return false
        val permission = info.permission
        return permission.isNullOrEmpty() || context.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Firmware settings can have neither a standard action nor one of our known component names
     * (reported on Baofeng Android 4.4; not yet verified on that TV). Discover only advertised MAIN
     * entry points in system settings packages. Never start factory/test pages or third-party apps
     * merely because their names contain "settings". This runs only when the normal routes fail.
     */
    private fun discoveredSettings(context: Context): List<Intent> {
        val pm = context.packageManager
        val result = ArrayList<Intent>()
        val seen = HashSet<ComponentName>()
        val probes = listOf(
            Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_SETTINGS"),
            Intent(Intent.ACTION_MAIN).addCategory(AppEntry.CATEGORY_LEANBACK_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            Intent(Intent.ACTION_MAIN),
        )
        for (probe in probes) {
            for (match in runCatching { pm.queryIntentActivities(probe, 0) }.getOrNull().orEmpty()) {
                val info = match.activityInfo ?: continue
                if (info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0 || !accessible(context, info)) continue
                val pkg = info.packageName.lowercase(java.util.Locale.ROOT)
                val name = info.name.lowercase(java.util.Locale.ROOT)
                val settingsCategory = probe.hasCategory("android.intent.category.LEANBACK_SETTINGS")
                if (!settingsCategory && !pkg.split('.').any { it.contains("setting") }) continue
                if (listOf("factory", "engineer", "calibrat", "test", "fallbackhome").any { name.contains(it) || pkg.contains(it) }) continue
                val component = ComponentName(info.packageName, info.name)
                if (seen.add(component)) result.add(Intent(probe).setComponent(component))
            }
        }
        return result
    }
}
