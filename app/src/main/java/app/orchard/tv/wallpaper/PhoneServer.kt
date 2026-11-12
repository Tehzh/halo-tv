package app.orchard.tv.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import app.orchard.tv.apps.AppCatalog
import app.orchard.tv.apps.FOLDER_KEY_PREFIX
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.apps.AppNames
import app.orchard.tv.apps.CustomIcons
import app.orchard.tv.apps.IconLibrary
import app.orchard.tv.apps.SourceDevice
import app.orchard.tv.apps.SourceLooks
import app.orchard.tv.apps.TileArtFactory
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.settings.KidsLock
import app.orchard.tv.settings.Restricted
import app.orchard.tv.settings.RestrictedKind
import app.orchard.tv.settings.RestrictedRules
import app.orchard.tv.guard.HomeGuardService
import app.orchard.tv.guard.KidsWatch
import android.os.SystemClock
import app.orchard.tv.system.Diagnostics
import app.orchard.tv.system.currentNetwork
import app.orchard.tv.settings.UiSounds
import app.orchard.tv.settings.Effects
import app.orchard.tv.settings.FocusStyle
import app.orchard.tv.weather.Weather
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import app.orchard.tv.update.PhoneUpdate
import app.orchard.tv.ui.home.twelveHour
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

/**
 * The page a phone opens after scanning the TV's QR code: choose a wallpaper and the screensaver,
 * send photos, give an app's tile a picture of one's own, and name the weather's city. Choosing
 * pictures or typing a place with a remote is slow; with a phone it is not.
 *
 * It listens on the local network while Aerial runs (see [listen]), and every address carries a
 * random token shown only on the TV (the QR code and the address under it), so another device on
 * the network cannot use it by guessing. The token stays the same from one start to the next.
 */
class PhoneServer private constructor(private val context: Context, private val token: String, port: Int) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val prefix = "/$token"
        val uri = session.uri
        if (uri != prefix && !uri.startsWith("$prefix/")) return text(Response.Status.NOT_FOUND, "Not found")
        val path = uri.removePrefix(prefix).ifEmpty { "/" }
        return runCatching {
            // This endpoint stages only a verified Halo TV APK. It never opens the installer.
            if (session.method == Method.POST && path == "/api/halo/update-apk") {
                return receiveUpdate(session)
            }
            // In Kids and Elders mode the page changes nothing without the parental PIN: anyone on
            // the network with the address (a child with a phone) could otherwise undo the mode.
            // The page sends it with each change once it is entered (header X-Aerial-Pin).
            if (session.method == Method.POST && path != "/api/kids/unlock" && (Restricted.active || path.startsWith("/api/kids/")) &&
                path != "/api/kids/start" && !pinOk(session.headers["x-aerial-pin"])
            ) return json(state().put("saved", false).put("kidsLocked", true))
            when {
                path.startsWith("/icons/") && path.removePrefix("/icons/").matches(Regex("[a-z-]+\\.svg")) -> {
                    bytesResponse("image/svg+xml", context.assets.open("web" + path).use { it.readBytes() }).apply { addHeader("Cache-Control", "max-age=86400") }
                }
                session.method == Method.GET && path == "/api/halo/donate-wechat.png" ->
                    bytesResponse("image/png", donationQr())
                session.method == Method.GET && path == "/api/halo/home-preview.jpg" -> {
                    val desktop = runBlocking { PhoneHomePreview.capture() }
                    val bytes = desktop ?: Wallpapers.previewPicture(Wallpapers.selectedId)?.let { bitmap ->
                        try { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray() } finally { bitmap.recycle() }
                    }
                    if (bytes == null) text(Response.Status.NOT_FOUND, "preview unavailable") else
                        newFixedLengthResponse(Response.Status.OK, "image/jpeg", ByteArrayInputStream(bytes), bytes.size.toLong()).apply {
                            addHeader("X-Halo-Preview", if (desktop != null) "desktop" else "wallpaper")
                        }
                }
                path == "/halo.js" -> newFixedLengthResponse(Response.Status.OK, "application/javascript; charset=utf-8", context.assets.open("web/halo.js").bufferedReader().use { it.readText() })
                session.method == Method.GET && path == "/api/halo/bing" -> json(PhoneOnlineLibrary.page(context, session.parameters["page"]?.firstOrNull()?.toIntOrNull() ?: 0, session.parameters["category"]?.firstOrNull().orEmpty()))
                session.method == Method.GET && path == "/api/halo/bing/original" -> json(PhoneOnlineLibrary.original(session.parameters["date"]?.firstOrNull().orEmpty()))
                session.method == Method.POST && path == "/api/halo/bing/apply" -> {
                    val f = form(session)
                    val expected = Wallpapers.selectedId
                    val (bytes, credit) = PhoneOnlineLibrary.picture(f["date"].orEmpty())
                    val saved = runBlocking(Dispatchers.Main) {
                        // Online content is cache-only, including requests from older phone pages.
                        Wallpapers.applyBingCached(bytes, credit, expected)
                    }
                    json(state().put("saved", saved))
                }
                session.method == Method.POST && path == "/api/halo/settings" -> haloSetting(form(session))
                session.method == Method.GET && path == "/api/halo/videos" -> json(JSONObject().put("items", JSONArray().apply {
                    Aerials.all(context).forEach { put(JSONObject().put("id", it.id).put("label", it.label).put("category", it.category).put("preview", it.preview).put("url", it.url)) }
                }))
                session.method == Method.POST && path == "/api/halo/video" -> {
                    val id = form(session)["id"]
                    val valid = Aerials.all(context).any { it.id == id }
                    if (valid) onMain { Wallpapers.chooseAerial(id); OnlineRotation.markVideoApplied() }
                    json(state().put("saved", valid))
                }
                path == "/" -> newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", page())
                session.method == Method.POST && path == "/api/kids/unlock" -> {
                    val ok = pinOk(form(session)["pin"])
                    json(state().put("saved", ok).put("pinRight", ok))
                }
                session.method == Method.POST && path == "/api/kids/allow" -> {
                    val minutes = form(session)["minutes"]?.toIntOrNull()
                    onMain { Restricted.allow(minutes?.takeIf { it > 0 }); KidsWatch.poke() }
                    json(state().put("saved", true))
                }
                session.method == Method.POST && path == "/api/kids/config" -> kidsConfig(form(session))
                session.method == Method.POST && path == "/api/kids/leave" -> {
                    onMain { Restricted.suspend(); KidsWatch.poke() }
                    json(state().put("saved", true))
                }
                session.method == Method.POST && path == "/api/kids/start" -> {
                    val can = Restricted.enabled && Restricted.allowed.isNotEmpty()
                    if (can) onMain { Restricted.start(); KidsWatch.poke() }
                    json(state().put("saved", can))
                }
                path == "/api/state" -> json(state())
                path == "/api/diagnostics" -> text(Response.Status.OK, Diagnostics.report(context))
                path.startsWith("/thumb/") -> thumb(path.removePrefix("/thumb/"))
                session.method == Method.POST && path == "/api/wallpaper" -> {
                    val id = form(session)["id"].orEmpty()
                    onMain { Wallpapers.select(id) }
                    json(state())
                }
                session.method == Method.POST && path == "/api/screensaver" -> {
                    val key = form(session)["mode"]
                    val mode = ScreensaverMode.entries.firstOrNull { it.key == key && it != ScreensaverMode.Clock && it != ScreensaverMode.System }
                    if (mode != null) onMain { Wallpapers.chooseScreensaver(mode) }
                    json(state())
                }
                session.method == Method.POST && path == "/api/setting" -> {
                    // The few on/off settings the phone page shows, as Personalize does.
                    val f = form(session)
                    val on = f["on"] == "1"
                    val known = when (f["key"]) {
                        "wallpaperAlwaysMoves" -> { onMain { AerialSettings.chooseWallpaperAlwaysMoves(on) }; true }
                        "showWeather" -> { onMain { AerialSettings.chooseShowWeather(on) }; true }
                        "screensaverClock" -> { onMain { AerialSettings.chooseScreensaverClock(on) }; true }
                        else -> false
                    }
                    json(state().put("saved", known))
                }
                session.method == Method.POST && path == "/api/focus" -> {
                    val style = FocusStyle.of(form(session)["style"])
                    onMain { AerialSettings.chooseFocusStyle(style) }
                    json(state())
                }
                session.method == Method.POST && path == "/api/delete" -> {
                    val id = form(session)["id"].orEmpty()
                    onMain { Wallpapers.deletePhoto(id) }
                    json(state())
                }
                session.method == Method.POST && path == "/api/upload" -> upload(session)
                session.method == Method.POST && path == "/api/videos/send" -> video(session)
                session.method == Method.POST && path == "/api/videos/poster" -> videoPoster(session)
                session.method == Method.POST && path == "/api/videos/delete" -> {
                    val id = form(session)["id"].orEmpty()
                    val removed = runBlocking(Dispatchers.Main) { Wallpapers.deleteVideo(id) }
                    json(state().put("removed", removed))
                }
                path.startsWith("/tile/") -> tile(java.net.URLDecoder.decode(path.removePrefix("/tile/"), "UTF-8"), own = session.parameters["art"]?.firstOrNull() == "own")
                path.startsWith("/library/") -> libraryPicture(java.net.URLDecoder.decode(path.removePrefix("/library/"), "UTF-8"), session.parameters["c"]?.firstOrNull())
                session.method == Method.POST && path == "/api/icon" -> icon(session)
                session.method == Method.POST && path == "/api/source/name" -> {
                    val f = form(session)
                    val source = AppCatalog.pinnedSources.firstOrNull { it.key == f["app"] }?.source
                    // On the main thread before answering, so the state sent back has the new name.
                    if (source != null) runBlocking(Dispatchers.Main) { SourceLooks.rename(source.id, f["name"]) }
                    json(state())
                }
                session.method == Method.POST && path == "/api/app/name" -> {
                    val f = form(session)
                    val key = f["app"].orEmpty()
                    val source = AppCatalog.pinnedSources.firstOrNull { it.key == key }?.source
                    // On the main thread before answering, so the state sent back has the new name.
                    val saved = when {
                        source != null -> { runBlocking(Dispatchers.Main) { SourceLooks.rename(source.id, f["name"]) }; true }
                        AppCatalog.loaded.any { it.key == key } -> { runBlocking(Dispatchers.Main) { AppNames.rename(key, f["name"]) }; true }
                        else -> false
                    }
                    json(state().put("saved", saved))
                }
                session.method == Method.POST && path == "/api/layout" -> layout(form(session))
                session.method == Method.POST && path == "/api/source/device" -> {
                    val f = form(session)
                    val source = AppCatalog.pinnedSources.firstOrNull { it.key == f["app"] }?.source
                    // As from the tile's menu: a device names the tile after it, "" goes back to the plain input.
                    if (source != null) runBlocking(Dispatchers.Main) { SourceLooks.chooseDevice(source.id, SourceDevice.of(f["device"])) }
                    json(state().put("saved", source != null))
                }
                session.method == Method.POST && path == "/api/source/pin" -> {
                    val f = form(session)
                    val id = f["id"].orEmpty()
                    val pin = AppCatalog.pinSource
                    val known = AppCatalog.sources.any { it.id == id }
                    if (known && pin != null) runBlocking(Dispatchers.Main) { pin(id, f["pinned"] == "1") }
                    json(state().put("saved", known && pin != null))
                }
                session.method == Method.POST && path == "/api/folder/name" -> {
                    val f = form(session)
                    val id = f["id"].orEmpty()
                    val name = f["name"].orEmpty().trim().take(FOLDER_NAME_MAX)
                    val rename = AppCatalog.renameFolder
                    val known = AppCatalog.folders.any { it.folder?.id == id }
                    // On the main thread before answering, so the state sent back has the new name.
                    if (known && name.isNotEmpty() && rename != null) runBlocking(Dispatchers.Main) { rename(id, name) }
                    json(state().put("saved", known && name.isNotEmpty() && rename != null))
                }
                session.method == Method.POST && path == "/api/icon/pick" -> {
                    // One of the library's pictures or the app's own art for one app; either
                    // replaces a picture sent from the phone.
                    val f = form(session)
                    val pkg = f["app"].orEmpty()
                    val pick = when (f["pick"]) {
                        "library" -> IconLibrary.Pick.Library
                        "own" -> IconLibrary.Pick.Own
                        else -> null
                    }
                    val choice = f["choice"]?.takeIf { it.isNotEmpty() }
                    val ok = installed(pkg) && pick != null && (pick != IconLibrary.Pick.Library ||
                        IconLibrary.choices(pkg).any { choice == null || it.id == choice })
                    if (ok) {
                        CustomIcons.clear(pkg)
                        // Kept even for the library, which most apps use anyway: a firmware app
                        // drawn as Aerial's own tile takes it only when picked.
                        IconLibrary.choose(pkg, pick, choice)
                        runBlocking(Dispatchers.Main) { CustomIcons.changed(); IconLibrary.changed() }
                    }
                    json(state().put("saved", ok))
                }
                session.method == Method.POST && path == "/api/icon/reset" -> {
                    val pkg = form(session)["app"].orEmpty()
                    if (installed(pkg)) { CustomIcons.clear(pkg); onMain { CustomIcons.changed() } }
                    json(state())
                }
                session.method == Method.POST && path == "/api/weather/city" -> {
                    val name = form(session)["name"].orEmpty()
                    val found = name.isNotBlank() && runBlocking(Dispatchers.Main) { Weather.choose(name) }
                    json(state().put("found", found))
                }
                session.method == Method.POST && path == "/api/weather/auto" -> {
                    onMain { Weather.useNetworkLocation() }
                    json(state())
                }
                else -> text(Response.Status.NOT_FOUND, "Not found")
            }
        }.getOrElse {
            Log.w(TAG, "request failed", it)
            text(Response.Status.INTERNAL_ERROR, "error")
        }.apply {
            if (path == "/" || path == "/halo.js" || path.startsWith("/api/")) addHeader("Cache-Control", "no-store")
            addHeader("X-Content-Type-Options", "nosniff")
        }
    }

    private fun receiveUpdate(session: IHTTPSession): Response {
        val length = session.headers["content-length"]?.toLongOrNull() ?: 0L
        // A cross-site form cannot send this header, and the LAN server does not allow CORS.
        val result = if (session.headers["x-halo-update"] != "1" ||
            session.headers["content-type"] != "application/vnd.android.package-archive") {
            "transfer_failed"
        } else {
            PhoneUpdate.receive(context, length, session.inputStream)
        }
        return json(JSONObject().put("result", result)).apply {
            addHeader("Cache-Control", "no-store")
            closeConnection(true)
        }
    }

    private fun page(): String = context.assets.open("web/index.html").bufferedReader().use { it.readText() }
        .replace("{{DEVICE}}", Build.MODEL.replace("_", " "))

    private fun state(): JSONObject {
        val list = JSONArray()
        val selected = Wallpapers.selectedId
        Wallpapers.items.filter { !it.id.startsWith("grad:") || it.id == selected }.forEach { w ->
            list.put(JSONObject().put("id", w.id).put("kind", w.kind.name.lowercase()).put("title", w.title).put("caption", w.caption ?: ""))
        }
        return JSONObject()
            .put("halo", haloState())
            .put("selected", selected)
            .put("latestDaily", Wallpapers.LATEST_DAILY)
            .put("rotatePhotos", Wallpapers.ROTATE_PHOTOS)
            .put("screensaver", Wallpapers.screensaver.key)
            .put("screensaverFollows", Wallpapers.screensaverFollows)
            .put("videos", JSONArray().apply {
                ScreensaverVideos.all(context).forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
            })
            .put("videoRoom", ScreensaverVideos.room(context))
            .put("focusStyle", AerialSettings.focusStyle.key)
            .put("kids", kidsState())
            .put("wallpaperAlwaysMoves", AerialSettings.wallpaperAlwaysMoves)
            .put("showWeather", AerialSettings.showWeather)
            .put("screensaverClock", AerialSettings.screensaverClock)
            .put("wallpapers", list)
            .put("apps", JSONArray().apply {
                // Pinned inputs first, then apps by the name Home shows. [default] is the name a
                // blank one goes back to; [name] is the one typed on this page, if any.
                val apps = AppCatalog.loaded.map { it to (AppNames.of(it.key) ?: it.label) }.sortedBy { it.second }
                AppCatalog.pinnedSources.forEach { a ->
                    val src = a.source ?: return@forEach
                    // An input's label as Home will show it once it has arranged the new look.
                    val look = SourceLooks.of(src.id)
                    val label = look?.let { it.name ?: it.device?.let { d -> context.getString(d.label) } } ?: src.label
                    // The stamp makes the phone fetch the tile again once its picture changes.
                    put(JSONObject().put("key", a.key).put("label", label).put("default", src.label).put("custom", CustomIcons.has(a.key)).put("stamp", CustomIcons.stamp(a.key))
                        .put("source", true).put("port", src.label).put("name", look?.name ?: "").put("device", look?.device?.key ?: "").put("look", look.hashCode()))
                }
                apps.forEach { (a, label) ->
                    // [icon] is where the tile's picture comes from now; [library] whether the icon
                    // library has one for this app, which the phone can pick instead.
                    val icon = when {
                        CustomIcons.has(a.key) -> "custom"
                        IconLibrary.has(a.key) && AppCatalog.usesLibrary(a.key) -> "library"
                        else -> "own"
                    }
                    put(JSONObject().put("key", a.key).put("label", label).put("default", a.label).put("name", AppNames.of(a.key) ?: "")
                        .put("custom", CustomIcons.has(a.key)).put("library", IconLibrary.has(a.key)).put("icon", icon)
                        .put("choices", libraryChoices(a.key, icon == "library"))
                        .put("stamp", CustomIcons.stamp(a.key) + IconLibrary.stamp(a.key) + icon.hashCode()))
                }
            })
            .put("layout", layoutState())
            .put("devices", JSONArray().apply { SourceDevice.entries.forEach { put(JSONObject().put("key", it.key).put("label", context.getString(it.label))) } })
            .put("inputs", JSONArray().apply {
                // Every input Home knows of, for pinning to Home from the phone.
                AppCatalog.sources.forEach { src ->
                    val look = SourceLooks.of(src.id)
                    put(JSONObject().put("id", src.id).put("port", src.label).put("device", src.deviceName ?: "")
                        .put("name", look?.let { it.name ?: it.device?.let { d -> context.getString(d.label) } } ?: "")
                        .put("pinned", src.id in AerialSettings.pinnedSources))
                }
            })
            .put("folders", JSONArray().apply {
                AppCatalog.folders.forEach { e ->
                    val f = e.folder ?: return@forEach
                    put(JSONObject().put("id", f.id).put("key", e.key).put("name", f.name).put("count", f.apps.size).put("stamp", e.art.hashCode()))
                }
            })
            .put("weather", JSONObject().apply {
                put("place", Weather.place ?: "")
                put("chosen", Weather.chosen)
                Weather.now?.let { put("celsius", it.celsius); put("kind", it.kind.name.lowercase()); put("day", it.day) }
            })
    }

    /** Home in order, each folder's apps and the hidden apps, by the names Home shows. */
    private fun layoutState(): JSONObject {
        fun tile(e: AppEntry) = JSONObject().put("key", e.key).put("label", e.label)
            .put("kind", when { e.folder != null -> "folder"; e.source != null -> "source"; else -> "app" })
            // Folder and input art changes without a picture from the phone (contents, chosen
            // device), an app's with the icon library too.
            .put("stamp", if (e.folder != null || e.source != null) e.art.hashCode().toLong() else CustomIcons.stamp(e.key) + IconLibrary.stamp(e.key, e.label))
            .apply { e.folder?.let { put("id", it.id) }; e.source?.let { put("port", it.label) } }
        val folders = JSONObject()
        AppCatalog.folderApps.forEach { (id, apps) -> folders.put(id, JSONArray().apply { apps.forEach { put(tile(it)) } }) }
        // With the parental PIN on, hidden apps stay hidden from here: on the TV, seeing them takes the PIN.
        val locked = Restricted.active && AerialSettings.locked
        return JSONObject()
            .put("home", JSONArray().apply { AppCatalog.home.forEach { put(tile(it)) } })
            .put("folders", folders)
            .put("hidden", JSONArray().apply { if (!locked) AppCatalog.hiddenApps.forEach { put(tile(it)) } })
            .put("hiddenCount", AppCatalog.hiddenApps.size)
            .put("columns", AerialSettings.homeColumns)
            .put("locked", locked)
            .put("ready", AppCatalog.layout != null)
    }

    /**
     * One step of arranging Home from the phone: [op] is move, movein, hide, unhide, folder (into
     * [folder], or a new one when it is empty), unfolder or ungroup. Done on the main thread before
     * answering, so the state sent back shows it.
     */
    private fun layout(f: Map<String, String>): Response {
        val ops = AppCatalog.layout ?: return json(state().put("saved", false))
        val key = f["key"].orEmpty()
        val target = f["target"].orEmpty()
        val folder = f["folder"].orEmpty()
        val known = (AppCatalog.home + AppCatalog.folderApps.values.flatten() + AppCatalog.hiddenApps).map { it.key }.toSet()
        val step: (() -> Unit)? = when (f["op"]) {
            "move" -> if (key in known && target in known) ({ ops.move(key, target) }) else null
            "movein" -> if (key in known && target in known) ({ ops.moveInFolder(key, target) }) else null
            "hide" -> if (key in known) ({ ops.hide(key) }) else null
            "unhide" -> if (key in known && !(Restricted.active && AerialSettings.locked)) ({ ops.unhide(key) }) else null
            "folder" -> if (key in known && !key.startsWith(FOLDER_KEY_PREFIX)) ({ ops.toFolder(key, folder.ifEmpty { null }) }) else null
            "unfolder" -> if (key in known) ({ ops.outOfFolder(key) }) else null
            "ungroup" -> if (folder in AppCatalog.folderApps) ({ ops.ungroup(folder) }) else null
            else -> null
        }
        if (step != null) runBlocking(Dispatchers.Main) { step() }
        return json(state().put("saved", step != null))
    }

    /** An app on the TV or a pinned input: the only tiles a picture may be sent for. */
    /** An app, a firmware shortcut such as Xiaomi's children's mode (issue #530), or a pinned input. */
    private fun installed(pkg: String) =
        pkg.isNotEmpty() && (AppCatalog.loaded.any { it.key == pkg } || AppCatalog.pinnedSources.any { it.key == pkg })

    /** The tile as Home shows it; with [own], as the app's own art would draw it. */
    private fun tile(pkg: String, own: Boolean = false): Response {
        val app = (AppCatalog.pinnedSources + AppCatalog.loaded + AppCatalog.folders).firstOrNull { it.key == pkg } ?: return text(Response.Status.NOT_FOUND, "")
        // An upload or a pick replies before Home's asynchronous reload has finished. Draw from the
        // saved picture or the pick directly, so the phone cannot cache the old tile under the new
        // versioned URL (a library pick showed the previous picture until the next change).
        val isApp = app.source == null && app.folder == null
        val library = isApp && !own && IconLibrary.has(pkg)
        val picked = when {
            own || !isApp -> null
            else -> CustomIcons.load(pkg) ?: if (library && AppCatalog.usesLibrary(pkg)) IconLibrary.load(pkg) else null
        }
        val bmp = picked ?: run {
            // The app's own art when asked for, or when the library has a picture it does not use.
            val art = if (isApp && (own || library)) AppCatalog.ownArt(context, app) ?: app.art else app.art
            TileArtFactory.flatten(art)
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bmp.recycle()
        val bytes = out.toByteArray()
        return newFixedLengthResponse(Response.Status.OK, "image/jpeg", ByteArrayInputStream(bytes), bytes.size.toLong()).apply {
            addHeader("Cache-Control", "no-store")
        }
    }

    // Resources.openRawResource also exposes the original bytes of packaged drawable images.
    @android.annotation.SuppressLint("ResourceType")
    private fun donationQr(): ByteArray = context.resources
        .openRawResource(app.orchard.tv.R.drawable.donate_wechat).use { it.readBytes() }

    /**
     * The icon library's pictures of an app on this TV, the default first: [id], [by] (the source's
     * names: "name" and a name per language) and [on] for the one its tile uses ([inUse]: the tile
     * uses the library at all).
     */
    private fun libraryChoices(pkg: String, inUse: Boolean) = JSONArray().apply {
        val on = IconLibrary.chosen(pkg)?.id
        IconLibrary.choices(pkg).forEach { c ->
            put(JSONObject().put("id", c.id).put("by", IconLibrary.credits.optJSONObject(c.by) ?: JSONObject())
                .put("on", inUse && c.id == on))
        }
    }

    /** The icon library's picture of an app, as downloaded: [choice]'s, or the one its tile uses. */
    private fun libraryPicture(pkg: String, choice: String?): Response {
        val f = IconLibrary.file(pkg, choice) ?: return text(Response.Status.NOT_FOUND, "")
        val type = if (f.name.endsWith(".png")) "image/png" else "image/jpeg"
        return newFixedLengthResponse(Response.Status.OK, type, f.inputStream(), f.length()).apply {
            addHeader("Cache-Control", "max-age=600")
        }
    }

    private fun icon(session: IHTTPSession): Response {
        if (tooLarge(session)) return text(Response.Status.BAD_REQUEST, "too large")
        val files = HashMap<String, String>()
        session.parseBody(files)
        val pkg = session.parameters["app"]?.firstOrNull().orEmpty()
        if (!installed(pkg)) return text(Response.Status.BAD_REQUEST, "unknown app")
        val f = files.values.map(::File).firstOrNull { it.exists() && it.length() > 0 }
            ?: return text(Response.Status.BAD_REQUEST, "no picture")
        val saved = f.inputStream().use { CustomIcons.save(pkg, it, context.cacheDir) }
        if (saved) onMain { CustomIcons.changed() }
        return json(state().put("saved", saved))
    }

    private fun thumb(id: String): Response {
        val bmp = Wallpapers.thumbnail(java.net.URLDecoder.decode(id, "UTF-8")) ?: return text(Response.Status.NOT_FOUND, "")
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
        val bytes = out.toByteArray()
        return newFixedLengthResponse(Response.Status.OK, "image/jpeg", ByteArrayInputStream(bytes), bytes.size.toLong()).apply {
            addHeader("Cache-Control", "max-age=600")
        }
    }

    private fun upload(session: IHTTPSession): Response {
        if (tooLarge(session)) return text(Response.Status.BAD_REQUEST, "too large")
        val files = HashMap<String, String>()
        session.parseBody(files)
        var added = 0
        // NanoHTTPD stores each part in a temp file; several photos arrive as photo, photo1, photo2...
        files.values.forEach { path ->
            val f = File(path)
            if (f.exists() && f.length() > 0) {
                val id = f.inputStream().use { input -> runBlocking(Dispatchers.Main) { Wallpapers.addPhoto(input) } }
                if (id != null) added++
            }
        }
        return json(state().put("added", added))
    }

    /**
     * One film per request, its bytes as the body and its name in the address. Not through
     * [IHTTPSession.parseBody]: that writes the body to the cache, copies the file part out again and
     * maps the whole body into memory, which a 2 GB film on a 32-bit TV cannot afford (#1164).
     */
    private fun video(session: IHTTPSession): Response {
        val length = session.headers["content-length"]?.toLongOrNull() ?: 0L
        val name = session.parameters["name"]?.firstOrNull().orEmpty()
        val error = when {
            ScreensaverVideos.titleOf(name) == null -> "unsupported_type"
            length <= 0 -> "empty_file"
            length > ScreensaverVideos.MAX_FILE_BYTES -> "too_large"
            length > ScreensaverVideos.room(context) -> "insufficient_room"
            else -> null
        }
        val film = if (error == null) ScreensaverVideos.receive(context, session.inputStream, length, name) else null
        val saved = film != null
        if (saved) onMain { Wallpapers.videosChanged() }
        // A refused or broken film leaves unread bytes behind; do not read them as the next request.
        return json(state().put("saved", saved).put("error", if (saved) JSONObject.NULL else error ?: "invalid_or_io").put("videoId", film?.id ?: JSONObject.NULL)).apply { if (!saved) closeConnection(true) }
    }

    private fun videoPoster(session: IHTTPSession): Response {
        val id = session.parameters["id"]?.firstOrNull().orEmpty()
        val length = session.headers["content-length"]?.toLongOrNull() ?: 0L
        val saved = ScreensaverVideos.savePoster(context, id, session.inputStream, length)
        if (saved) onMain { Wallpapers.videoPosterChanged(id) }
        return json(JSONObject().put("saved", saved)).apply { if (!saved) closeConnection(true) }
    }

    // NanoHTTPD writes the whole body to the cache before we see it; keep a stray or hostile
    // upload from filling the TV's storage (#57).
    private fun tooLarge(session: IHTTPSession): Boolean {
        val length = session.headers["content-length"]?.toLongOrNull() ?: return true
        return length <= 0 || length > MAX_UPLOAD
    }

    private fun form(session: IHTTPSession): Map<String, String> {
        session.parseBody(HashMap())
        return session.parameters.mapValues { it.value.firstOrNull().orEmpty() }
    }

    private fun onMain(block: () -> Unit) = runBlocking(Dispatchers.Main) { block() }

    /**
     * The parental PIN, from the phone. Five wrong in a row and none is taken for a minute, so that
     * the 10 000 four-digit PINs cannot be tried from a phone in a few minutes.
     */
    private fun pinOk(pin: String?): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (pin.isNullOrEmpty() || now < pinRestUntil) return false
        if (AerialSettings.pinMatches(pin)) { pinMisses = 0; return true }
        if (++pinMisses >= 5) { pinMisses = 0; pinRestUntil = now + 60_000L }
        return false
    }
    @Volatile private var pinMisses = 0
    @Volatile private var pinRestUntil = 0L

    /** Kids and Elders mode as the page shows it: on or not, the day's time, a lock. */
    private fun kidsState(): JSONObject {
        val r = Restricted
        val now = System.currentTimeMillis()
        return JSONObject()
            .put("enabled", r.enabled)
            .put("active", r.active)
            .put("kind", r.kind.key)
            .put("pinSet", AerialSettings.pinSet)
            .put("pinArrows", AerialSettings.pinArrows)
            .put("usedMin", r.usage.usedMs / 60_000L)
            .put("limitMin", r.limitToday(now))
            .put("allowUntil", r.usage.allowUntil.takeIf { it > now } ?: 0L)
            .put("lock", when (r.lock) { is KidsLock.Break -> "break"; KidsLock.DayUsed -> "day"; KidsLock.Late -> "late"; null -> "" })
            .put("startMinute", r.rules.startMinute)
            .put("endMinute", r.rules.endMinute)
            // The whole setup, for the page's editor; changing it takes the PIN.
            .put("blockOthers", r.blockOthers)
            .put("guard", HomeGuardService.isRunning)
            .put("weekdayMinutes", r.rules.weekdayMinutes)
            .put("weekendMinutes", r.rules.weekendMinutes)
            .put("breakEvery", r.rules.breakEvery)
            .put("breakMinutes", r.rules.breakMinutes)
            .put("allowed", JSONArray(r.allowed))
            .put("kidsAllowed", JSONArray(r.allowedFor(RestrictedKind.Kids)))
            .put("eldersAllowed", JSONArray(r.allowedFor(RestrictedKind.Elders)))
            .put("quizRewardMax", r.quizRewardMax)
            .put("elderReminderMinutes", r.elderReminderMinutes)
            .put("candidates", JSONArray().apply {
                AppCatalog.kidsCandidates.forEach { e ->
                    put(JSONObject().put("key", e.key).put("label", e.label).put("source", e.source != null))
                }
            })
    }

    /**
     * The mode set up from the phone, all at once: who it is for, other apps, any number of
     * minutes, any window, the break, the tiles in order (keys one per line) and on or off.
     * Turned on here it starts at once: the parent is not at the TV. Nothing is kept unless all
     * of it is valid.
     */
    private fun kidsConfig(f: Map<String, String>): Response {
        val numeric = setOf("weekday", "weekend", "start", "end", "breakEvery", "breakMinutes", "quizRewardMax", "elderReminderMinutes")
        if (numeric.any { it in f && f[it]?.toIntOrNull() == null } ||
            ("kind" in f && RestrictedKind.entries.none { it.key == f["kind"] }) ||
            ("enabled" in f && f["enabled"] !in setOf("0", "1"))) return json(state().put("saved", false))
        fun int(key: String, default: Int) = f[key]?.trim()?.toIntOrNull() ?: default
        val current = Restricted.rules
        val rules = RestrictedRules(
            weekdayMinutes = int("weekday", current.weekdayMinutes),
            weekendMinutes = int("weekend", current.weekendMinutes),
            startMinute = int("start", current.startMinute),
            endMinute = int("end", current.endMinute),
            breakEvery = int("breakEvery", current.breakEvery),
            breakMinutes = int("breakMinutes", current.breakMinutes),
        )
        val kind = f["kind"]?.let { RestrictedKind.of(it) } ?: Restricted.kind
        val known = AppCatalog.kidsCandidates.mapTo(HashSet()) { it.key }
        val allowed = f["allowed"]?.split('\n')?.map { it.trim() }?.filter { it in known }?.distinct() ?: Restricted.allowedFor(kind)
        val on = f["enabled"]?.let { it == "1" } ?: Restricted.enabled
        if (!rules.valid || (on && allowed.isEmpty()) || f["quizRewardMax"]?.toIntOrNull()?.let { it !in 1..120 } == true || f["elderReminderMinutes"]?.toIntOrNull()?.let { it !in 0..1440 } == true) return json(state().put("saved", false))
        onMain {
            val editingOnly = f["editOnly"] == "1"
            if (!editingOnly) Restricted.chooseKind(kind)
            f["block"]?.let { Restricted.chooseBlockOthers(it == "1") }
            if (kind == RestrictedKind.Kids) Restricted.chooseRules(rules)
            f["quizRewardMax"]?.toIntOrNull()?.let { Restricted.chooseQuizRewardMax(it) }
            f["elderReminderMinutes"]?.toIntOrNull()?.let { Restricted.chooseElderReminder(it) }
            Restricted.chooseAllowedFor(kind, allowed)
            if (!editingOnly && on != Restricted.enabled) {
                Restricted.chooseEnabled(on)
                if (on) Restricted.start()
            }
            KidsWatch.poke()
        }
        return json(state().put("saved", true))
    }


    private fun haloState(): JSONObject = JSONObject()
        .put("oneRowHome", AerialSettings.oneRowHome)
        .put("peekNextRow", AerialSettings.peekNextRow)
        .put("showAllNames", AerialSettings.showAllNames)
        .put("showHomeGreeting", AerialSettings.showHomeGreeting)
        .put("showHomeTime", AerialSettings.showHomeTime)
        .put("showHomeDate", AerialSettings.showHomeDate)
        .put("clock24Hour", !twelveHour(context, AerialSettings.clockHours))
        .put("flipClock24Hour", AerialSettings.flipClock24Hour)
        .put("flipClockSeconds", AerialSettings.flipClockSeconds)
        .put("showWeather", AerialSettings.showWeather)
        .put("showLunarDate", AerialSettings.showLunarDate)
        .put("showDeviceCapacity", AerialSettings.showDeviceCapacity)
        .put("wallpaperAlwaysMoves", AerialSettings.wallpaperAlwaysMoves)
        .put("screensaverClock", AerialSettings.screensaverClock)
        .put("backStartsScreensaver", AerialSettings.backStartsScreensaver)
        .put("videoWallpaperMuted", AerialSettings.videoWallpaperMuted)
        .put("videoWallpaperFit", AerialSettings.videoWallpaperFit)
        .put("autoCleanIdle", AerialSettings.autoCleanIdle)
        .put("homeColumns", AerialSettings.homeColumns)
        .put("plateStyle", AerialSettings.plateStyle)
        .put("settingsIconPosition", AerialSettings.settingsIconPosition)
        .put("settingsIconVisibility", AerialSettings.settingsIconVisibility)
        .put("clockTextColor", AerialSettings.clockTextColor)
        .put("clockPosition", AerialSettings.clockPosition)
        .put("clockSizeMode", AerialSettings.clockSizeMode)
        .put("screensaverMinutes", AerialSettings.screensaverMinutes)
        .put("uiSounds", AerialSettings.uiSounds.key)
        .put("effects", AerialSettings.effects.key)
        .put("focusStyle", AerialSettings.focusStyle.key)
        .put("aerialQuality", AerialSettings.aerialQuality.key)
        .put("bingMinutes", OnlineRotation.bingMinutes)
        .put("videoMinutes", OnlineRotation.videoMinutes)
        .put("version", app.orchard.tv.BuildConfig.VERSION_NAME)
        .put("edition", app.orchard.tv.update.Updates.edition)

    private fun haloSetting(f: Map<String, String>): Response {
        val raw = f["value"].orEmpty()
        fun boolean(): Boolean? = when (raw) { "true" -> true; "false" -> false; else -> null }
        val change: (() -> Unit)? = when (f["key"]) {
            "oneRowHome" -> boolean()?.let { value -> { AerialSettings.chooseOneRowHome(value) } }
            "peekNextRow" -> boolean()?.let { value -> { AerialSettings.choosePeekNextRow(value) } }
            "showAllNames" -> boolean()?.let { value -> { AerialSettings.chooseShowAllNames(value) } }
            "showHomeGreeting" -> boolean()?.let { value -> { AerialSettings.chooseShowHomeGreeting(value) } }
            "showHomeTime" -> boolean()?.let { value -> { AerialSettings.chooseShowHomeTime(value) } }
            "showHomeDate" -> boolean()?.let { value -> { AerialSettings.chooseShowHomeDate(value) } }
            "clock24Hour" -> boolean()?.let { value -> { AerialSettings.chooseClock24Hour(value) } }
            "flipClock24Hour" -> boolean()?.let { value -> { AerialSettings.chooseFlipClock24Hour(value) } }
            "flipClockSeconds" -> boolean()?.let { value -> { AerialSettings.chooseFlipClockSeconds(value) } }
            "showWeather" -> boolean()?.let { value -> { AerialSettings.chooseShowWeather(value) } }
            "showLunarDate" -> boolean()?.let { value -> { AerialSettings.chooseShowLunarDate(value) } }
            "showDeviceCapacity" -> boolean()?.let { value -> { AerialSettings.chooseShowDeviceCapacity(value) } }
            "wallpaperAlwaysMoves" -> boolean()?.let { value -> { AerialSettings.chooseWallpaperAlwaysMoves(value) } }
            "screensaverClock" -> boolean()?.let { value -> { AerialSettings.chooseScreensaverClock(value) } }
            "backStartsScreensaver" -> boolean()?.let { value -> { AerialSettings.chooseBackStartsScreensaver(value) } }
            "videoWallpaperMuted" -> boolean()?.let { value -> { AerialSettings.chooseVideoWallpaperMuted(value) } }
            "videoWallpaperFit" -> boolean()?.let { value -> { AerialSettings.chooseVideoWallpaperFit(value) } }
            "autoCleanIdle" -> boolean()?.let { value -> { AerialSettings.chooseAutoCleanIdle(value) } }
            "homeColumns" -> raw.toIntOrNull()?.takeIf { it in listOf(4, 5, 6) }?.let { value -> { AerialSettings.chooseHomeColumns(value) } }
            "plateStyle" -> raw.toIntOrNull()?.takeIf { it in listOf(0, 1, 2) }?.let { value -> { AerialSettings.choosePlateStyle(value) } }
            "settingsIconPosition" -> raw.toIntOrNull()?.takeIf { it in listOf(0, 1, 2) }?.let { value -> { AerialSettings.chooseSettingsIconPosition(value) } }
            "settingsIconVisibility" -> raw.toIntOrNull()?.takeIf { it in listOf(0, 1, 2) }?.let { value -> { AerialSettings.chooseSettingsIconVisibility(value) } }
            "clockTextColor" -> raw.toIntOrNull()?.takeIf { it in listOf(0, 1, 2) }?.let { value -> { AerialSettings.chooseClockTextColor(value) } }
            "clockPosition" -> raw.toIntOrNull()?.takeIf { it in listOf(0, 1, 2, 3) }?.let { value -> { AerialSettings.chooseClockPosition(value) } }
            "clockSizeMode" -> raw.toIntOrNull()?.takeIf { it in listOf(0, 1, 2) }?.let { value -> { AerialSettings.chooseClockSizeMode(value) } }
            "screensaverMinutes" -> raw.toIntOrNull()?.takeIf { it in listOf(0, 2, 5, 10, 20) }?.let { value -> { AerialSettings.chooseScreensaverMinutes(value) } }
            "uiSounds" -> UiSounds.entries.firstOrNull { it.key == raw }?.let { value -> { AerialSettings.chooseUiSounds(value) } }
            "effects" -> Effects.entries.firstOrNull { it.key == raw }?.let { value -> { AerialSettings.chooseEffects(value) } }
            "focusStyle" -> FocusStyle.entries.firstOrNull { it.key == raw }?.let { value -> { AerialSettings.chooseFocusStyle(value) } }
            "aerialQuality" -> AerialQuality.entries.firstOrNull { it.key == raw }?.let { value -> { AerialSettings.chooseAerialQuality(value) } }
            "bingMinutes" -> raw.toIntOrNull()?.takeIf { it in -1..10080 }?.let { value -> { OnlineRotation.chooseBingMinutes(value, "") } }
            "videoMinutes" -> raw.toIntOrNull()?.takeIf { it in -1..10080 }?.let { value -> { OnlineRotation.chooseVideoMinutes(value) } }
            else -> null
        }
        if (change != null) onMain(change)
        return json(state().put("saved", change != null))
    }

    private fun bytesResponse(mime: String, bytes: ByteArray) = newFixedLengthResponse(Response.Status.OK, mime, ByteArrayInputStream(bytes), bytes.size.toLong())

    private fun json(o: JSONObject) = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", o.toString())

    private fun text(status: Response.Status, msg: String) = newFixedLengthResponse(status, "text/plain; charset=utf-8", msg)

    companion object {
        const val PORT = 8790
        private const val TAG = "AerialPhone"
        /** Enough for a batch of phone photos at full size. */
        private const val MAX_UPLOAD = 200L * 1024 * 1024
        /** As for an input's name: Home's caption has room for about this much. */
        private const val FOLDER_NAME_MAX = 24

        @Volatile
        private var running: PhoneServer? = null

        /**
         * Listens for as long as Aerial runs, from Home's start (MainActivity.startWork), so a
         * computer can open the page at any time without Personalize open on the TV. On every
         * interface: the address the TV has may change, the page stays the same.
         */
        @Synchronized
        fun listen(context: Context): PhoneServer? {
            running?.let { if (it.isAlive) return it }
            val token = token(context)
            for (port in PORT..PORT + 9) {
                val server = PhoneServer(context.applicationContext, token, port)
                val started = runCatching { server.start(SOCKET_READ_TIMEOUT, true) }
                if (started.isSuccess) {
                    running = server
                    return server
                }
                runCatching { server.stop() }
                Log.w(TAG, "could not listen on $port", started.exceptionOrNull())
            }
            return null
        }

        /** The address to put in the QR code, listening first if need be, or null offline. */
        fun start(context: Context): String? {
            val ip = lanAddress(context) ?: return null
            val server = listen(context) ?: return null
            return "http://$ip:${server.listeningPort}/${server.token}/"
        }

        // Keep the settings address stable so it can be bookmarked on the same network.
        private fun token(context: Context): String {
            val prefs = context.getSharedPreferences("phone_page", Context.MODE_PRIVATE)
            prefs.getString("token", null)?.takeIf { it.length == 8 }?.let { return it }
            return newToken().also { prefs.edit().putString("token", it).apply() }
        }

        private fun newToken(): String {
            val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
            val rnd = SecureRandom()
            return (1..8).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
        }

        fun lanAddress(context: Context): String? {
            val cm = context.getSystemService<ConnectivityManager>()
            val current = runCatching { cm?.currentNetwork() }.getOrNull()
            // Under a proxy app's VPN the default network is the tunnel, whose address the phone
            // cannot reach (Clash and the like use 198.18.0.1; Xiaomi S55, issue #667). The Wi-Fi
            // or Ethernet network under it is still in allNetworks: take its address first.
            val networks = runCatching { cm?.allNetworks?.toList().orEmpty() }.getOrDefault(emptyList())
            val lan = networks.filter { n ->
                val caps = runCatching { cm?.getNetworkCapabilities(n) }.getOrNull() ?: return@filter false
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            }.sortedByDescending { it == current }
            val safeCurrent = current?.takeIf { n ->
                runCatching { cm?.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == false }.getOrDefault(false)
            }
            val fromNetworks = (lan + listOfNotNull(safeCurrent)).firstNotNullOfOrNull { n ->
                runCatching { cm?.getLinkProperties(n) }.getOrNull()?.linkAddresses?.map { it.address }
                    ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress && !isBenchmark(it) }
            }?.hostAddress
            if (fromNetworks != null) return fromNetworks
            // Some TV firmware omits connected LANs from ConnectivityManager. Only consider
            // recognizable, active physical interfaces; never advertise a VPN or hotspot address.
            return runCatching {
                NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback && isPhysicalLanName(it.name) }
                    .sortedBy { if (it.name.startsWith("eth")) 0 else 1 }
                    .flatMap { it.inetAddresses.toList() }
                    .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress && !isBenchmark(it) }
                    ?.hostAddress
            }.onFailure { Log.w(TAG, "could not inspect network interfaces", it) }.getOrNull()
        }

        internal fun isPhysicalLanName(name: String): Boolean {
            val n = name.lowercase(java.util.Locale.ROOT)
            return listOf("eth", "wlan", "wifi", "en").any { n.startsWith(it) } &&
                !n.startsWith("wifi-aware")
        }

        /** 198.18.0.0/15, the range proxy apps put their tunnel in; never a home network. */
        private fun isBenchmark(address: java.net.InetAddress): Boolean {
            val b = address.address
            return b.size == 4 && (b[0].toInt() and 0xff) == 198 && (b[1].toInt() and 0xfe) == 18
        }

        /** A QR code, dark modules on white, each module [scale] pixels square. */
        fun qr(text: String, scale: Int = 8): Bitmap {
            val matrix = QRCodeWriter().encode(
                text, BarcodeFormat.QR_CODE, 0, 0,
                mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
            )
            val n = matrix.width
            val bmp = createBitmap(n * scale, n * scale)
            for (y in 0 until n) for (x in 0 until n) {
                if (matrix[x, y]) for (dy in 0 until scale) for (dx in 0 until scale) bmp.setPixel(x * scale + dx, y * scale + dy, Color.BLACK)
                else for (dy in 0 until scale) for (dx in 0 until scale) bmp.setPixel(x * scale + dx, y * scale + dy, Color.WHITE)
            }
            return bmp
        }
    }
}
