package com.escola.tabletmanager

import android.content.Context
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.work.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class UpdateWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        return try {
            AutoUpdater.checkAndUpdate(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.w("GestorEscolar", "Erro update: ${e.message}")
            Result.retry()
        }
    }
}

object AutoUpdater {

    private const val TAG = "GestorEscolar"
    private const val VERSION_URL =
        "https://raw.githubusercontent.com/LUIZBAKER77/Gestor-escolar/main/version.json"

    fun schedule(ctx: Context) {
        val work = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "gestor_update", ExistingPeriodicWorkPolicy.KEEP, work)
        Log.i(TAG, "Auto-update agendado")
    }

    fun checkAndUpdate(ctx: Context) {
        try {
            val json = fetch(VERSION_URL) ?: return
            val obj = JSONObject(json)
            val latestCode = obj.getInt("versionCode")
            val apkUrl = obj.getString("apkUrl")
            val currentCode = ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt()

            Log.i(TAG, "Versao atual=$currentCode disponivel=$latestCode")
            if (latestCode > currentCode) {
                Log.i(TAG, "Atualizando...")
                val file = download(ctx, apkUrl) ?: return
                installSilently(ctx, file)
            }
        } catch (e: Exception) {
            Log.w(TAG, "checkAndUpdate: ${e.message}")
        }
    }

    private fun fetch(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val text = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            text
        } catch (e: Exception) { null }
    }

    private fun download(ctx: Context, url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 120_000
            val file = File(ctx.cacheDir, "gestor_update.apk")
            FileOutputStream(file).use { out ->
                conn.inputStream.copyTo(out)
            }
            conn.disconnect()
            file.absolutePath
        } catch (e: Exception) { null }
    }

    private fun installSilently(ctx: Context, apkPath: String) {
        try {
            val installer = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            val apkFile = File(apkPath)
            session.openWrite("update.apk", 0, apkFile.length()).use { out ->
                apkFile.inputStream().copyTo(out)
                session.fsync(out)
            }
            val intent = android.content.Intent(ctx, MainActivity::class.java)
            val pi = android.app.PendingIntent.getActivity(
                ctx, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE)
            session.commit(pi.intentSender)
            session.close()
            Log.i(TAG, "Instalacao silenciosa iniciada")
        } catch (e: Exception) {
            Log.w(TAG, "installSilently: ${e.message}")
        }
    }
}
