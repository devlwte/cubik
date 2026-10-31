package com.kyrnforge.cubik

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Busca versiones nuevas en GitHub Releases, como mucho una vez cada 4 horas al abrir la app.

private const val RELEASES = "https://api.github.com/repos/devlwte/cubik/releases/latest"
private const val EVERY_MS = 4 * 60 * 60 * 1000L

data class Release(val version: String, val apkUrl: String, val size: Long, val notes: String)

fun isNewer(remote: String, local: String): Boolean {
    fun parts(v: String) = v.trimStart('v', 'V').substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val a = parts(remote)
    val b = parts(local)
    for (i in 0 until maxOf(a.size, b.size)) {
        val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (d != 0) return d > 0
    }
    return false
}

private fun get(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
    connectTimeout = 10_000
    readTimeout = 20_000
    setRequestProperty("Accept", "application/vnd.github+json")
    setRequestProperty("User-Agent", "Cubik-Android")
}

private fun latestRelease(current: String): Release? = runCatching {
    val c = get(RELEASES)
    try {
        if (c.responseCode != 200) return null
        val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val tag = json.optString("tag_name")
        if (!isNewer(tag, current)) return null
        val assets = json.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) } ?: return null
        Release(tag.trimStart('v', 'V'), apk.getString("browser_download_url"), apk.optLong("size"), json.optString("body"))
    } finally {
        c.disconnect()
    }
}.getOrNull()

private fun download(ctx: Context, r: Release, onProgress: (Float) -> Unit): File? = runCatching {
    val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "Cubik_${r.version}.apk")
    val c = get(r.apkUrl)
    try {
        if (c.responseCode != 200) return null
        val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
        c.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        file
    } finally {
        c.disconnect()
    }
}.getOrNull()

private fun install(ctx: Context, file: File) {
    val installer = ctx.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        .apply { setAppPackageName(ctx.packageName) }
    val id = installer.createSession(params)
    installer.openSession(id).use { session ->
        file.inputStream().use { input ->
            session.openWrite("cubik.apk", 0, file.length()).use { out ->
                input.copyTo(out)
                session.fsync(out)
            }
        }
        val done = PendingIntent.getBroadcast(
            ctx, id, Intent(ctx, InstallResult::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        session.commit(done.intentSender)
    }
}

/** Respuesta del instalador: si falta la confirmación del usuario, se abre la pantalla del sistema. */
class InstallResult : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) != PackageInstaller.STATUS_PENDING_USER_ACTION) return
        val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
        confirm?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

private fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

private fun askInstallPermission(ctx: Context) = ctx.startActivity(
    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
)

private sealed interface UpdateState {
    data class Found(val r: Release) : UpdateState
    data class Downloading(val r: Release, val progress: Float) : UpdateState
    data class NeedsPermission(val r: Release, val file: File) : UpdateState
    data class Failed(val r: Release) : UpdateState
}

@Composable
fun UpdateCheck() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState?>(null) }

    LaunchedEffect(Unit) {
        val prefs = ctx.getSharedPreferences("cubik_update", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_check", 0) < EVERY_MS) return@LaunchedEffect
        prefs.edit().putLong("last_check", now).apply()
        val current = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { latestRelease(current) }?.let { state = UpdateState.Found(it) }
    }

    fun start(r: Release) {
        state = UpdateState.Downloading(r, 0f)
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                download(ctx, r) { p -> scope.launch { state = UpdateState.Downloading(r, p) } }
            }
            state = when {
                file == null -> UpdateState.Failed(r)
                !canInstall(ctx) -> UpdateState.NeedsPermission(r, file)
                else -> { install(ctx, file); null }
            }
        }
    }

    when (val s = state) {
        null -> {}
        is UpdateState.Found -> UpdateDialog(
            "Hay una versión nueva",
            "Cubik ${s.r.version} está lista para descargar" + (if (s.r.size > 0) " (${sizeText(s.r.size)})." else "."),
            ok = "Actualizar", onOk = { start(s.r) }, cancel = "Ahora no", onCancel = { state = null }
        )
        is UpdateState.Downloading -> UpdateDialog("Descargando Cubik ${s.r.version}", "No cierre la app.", progress = s.progress)
        is UpdateState.NeedsPermission -> UpdateDialog(
            "Falta un permiso",
            "Toque Continuar, active \"Permitir de esta fuente\" y vuelva a Cubik para instalar.",
            ok = "Continuar", onOk = {
                if (canInstall(ctx)) { install(ctx, s.file); state = null } else askInstallPermission(ctx)
            },
            cancel = "Ahora no", onCancel = { state = null }
        )
        is UpdateState.Failed -> UpdateDialog(
            "No se pudo descargar",
            "Revise la conexión a internet e intente de nuevo.",
            ok = "Reintentar", onOk = { start(s.r) }, cancel = "Cerrar", onCancel = { state = null }
        )
    }
}

private fun sizeText(bytes: Long) = "%.1f MB".format(bytes / 1_048_576.0).replace('.', ',')

private val R16 = RoundedCornerShape(16.dp)

@Composable
private fun UpdateDialog(
    title: String,
    text: String,
    ok: String? = null,
    onOk: () -> Unit = {},
    cancel: String? = null,
    onCancel: () -> Unit = {},
    progress: Float? = null
) = Box(
    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    contentAlignment = Alignment.Center
) {
    Column(
        Modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth().background(C.card, R16).border(2.dp, C.line, R16).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BasicText(title, style = cond(24.sp))
        BasicText(text, style = body(15.sp, C.muted))
        if (progress != null) {
            Box(Modifier.fillMaxWidth().height(12.dp).background(C.line, R16)) {
                Box(Modifier.fillMaxWidth(progress).height(12.dp).background(C.traba, R16))
            }
            BasicText("${(progress * 100).toInt()} %", style = body(14.sp, C.muted))
        }
        if (ok != null || cancel != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (cancel != null) Btn(cancel, Modifier.weight(1f), onClick = onCancel)
            if (ok != null) Btn(ok, Modifier.weight(1f), bg = C.traba, onClick = onOk)
        }
    }
}
