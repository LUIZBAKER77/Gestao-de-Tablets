package com.escola.tabletmanager

import android.app.WallpaperManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import java.security.MessageDigest

data class AppInfo(
    val packageName: String,
    val appName: String,
    var isAllowed: Boolean
)

object AppManager {

    private const val TAG = "GestorEscolar"
    private const val PREFS = "school_prefs"
    private const val KEY_ALLOWED = "allowed_apps"
    private const val KEY_KNOWN = "known_apps"
    private const val KEY_STUDENT = "student_mode"
    private const val KEY_HASH = "password_hash"
    private const val KEY_PASS = "password_set"
    private const val ACCESSIBILITY_SERVICE =
        "com.escola.tabletmanager/com.escola.tabletmanager.PasswordGuardAccessibilityService"

    private val SYSTEM_CORE = setOf(
        "android",
        "com.android.systemui",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.providers.settings",
        "com.android.providers.downloads",
        "com.android.providers.contacts",
        "com.android.providers.calendar",
        "com.android.providers.media",
        "com.android.providers.telephony",
        "com.android.keychain",
        "com.android.externalstorage",
        "com.android.bluetooth",
        "com.android.nfc",
        "com.android.networkstack",
        "com.android.networkstack.permissionconfig",
        "com.android.captiveportallogin",
        "com.android.inputmethod.latin",
        "com.sec.android.app.launcher",
        "com.samsung.android.launcher",
        "com.android.launcher3",
        "com.samsung.android.inputmethod.SamsungKeyboard",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.MtpApplication",
        "com.samsung.android.providers.contacts",
        "com.samsung.android.wifi.resources",
        "com.samsung.android.bluetooth",
        "com.samsung.android.deviceidservice",
        "com.samsung.android.sdm.config",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.samsung.android.gru",
        "com.android.traceur",
        "com.samsung.android.wearable.manager",
        "com.samsung.android.app.watchmanager",
        "com.google.android.wearable.app",
        "com.android.settings",
        "com.samsung.android.settings",
        "com.escola.tabletmanager"
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun applyManagedWallpaper(ctx: Context) {
        try {
            val wallpaperManager = WallpaperManager.getInstance(ctx)
            wallpaperManager.setResource(R.drawable.default_wallpaper)
            Log.i(TAG, "Wallpaper padrao aplicado")
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao aplicar wallpaper padrao", e)
        }
    }

    fun isDeviceOwner(ctx: Context): Boolean {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(ctx.packageName)
    }

    fun isStudentMode(ctx: Context) = prefs(ctx).getBoolean(KEY_STUDENT, false)
    fun isPasswordSet(ctx: Context) = prefs(ctx).getBoolean(KEY_PASS, false)

    fun isPasswordProtectionReady(ctx: Context): Boolean {
        return PasswordTokenManager.isTokenActive(ctx) ||
            (PasswordTokenManager.isEscrowTokenDisabled(ctx) && isPasswordGuardEnabled(ctx))
    }

    fun shouldUseAccessibilityFallback(ctx: Context): Boolean {
        return PasswordTokenManager.isEscrowTokenDisabled(ctx)
    }

    fun isPasswordGuardEnabled(ctx: Context): Boolean {
        return try {
            val enabled = Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabled.split(':').any { it.equals(ACCESSIBILITY_SERVICE, ignoreCase = true) }
        } catch (_: Exception) {
            false
        }
    }

    fun ensurePasswordGuardEnabled(ctx: Context): Boolean {
        return try {
            val enabled = Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()

            val services = enabled
                .split(':')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableSet()

            services.add(ACCESSIBILITY_SERVICE)

            Settings.Secure.putString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                services.joinToString(":")
            )
            Settings.Secure.putInt(
                ctx.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                1
            )

            val finalEnabled = isPasswordGuardEnabled(ctx)
            Log.i(TAG, "PasswordGuard habilitado=$finalEnabled")
            finalEnabled
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao habilitar PasswordGuard", e)
            false
        }
    }

    fun setPassword(ctx: Context, password: String) {
        prefs(ctx).edit().putString(KEY_HASH, sha256(password)).putBoolean(KEY_PASS, true).apply()
    }

    fun checkPassword(ctx: Context, password: String): Boolean {
        return sha256(password) == (prefs(ctx).getString(KEY_HASH, "") ?: "")
    }

    private fun sha256(s: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun getAllowedApps(ctx: Context): MutableSet<String> {
        return prefs(ctx).getStringSet(KEY_ALLOWED, null)?.toMutableSet()
            ?: mutableSetOf(ctx.packageName)
    }

    fun setAllowedApps(ctx: Context, apps: Set<String>) {
        val set = apps.toMutableSet()
        set.add(ctx.packageName)
        prefs(ctx).edit().putStringSet(KEY_ALLOWED, set).apply()
    }

    private fun saveKnown(ctx: Context, pkgs: Set<String>) {
        prefs(ctx).edit().putStringSet(KEY_KNOWN, pkgs).apply()
    }

    private fun loadKnown(ctx: Context): Set<String> {
        return prefs(ctx).getStringSet(KEY_KNOWN, null) ?: emptySet()
    }

    fun scanAppsWithIcon(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        val result = mutableSetOf<String>()

        val allApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        for (appInfo in allApps) {
            val pkg = appInfo.packageName
            if (pkg in SYSTEM_CORE) continue
            if (pm.getLaunchIntentForPackage(pkg) != null) {
                result.add(pkg)
            }
        }

        return result
    }

    fun getInstalledUserApps(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val allowed = getAllowedApps(ctx)

        val packages: Set<String> = if (!isStudentMode(ctx)) {
            val fresh = scanAppsWithIcon(ctx)
            saveKnown(ctx, fresh)
            fresh
        } else {
            loadKnown(ctx).ifEmpty { scanAppsWithIcon(ctx) }
        }

        return packages
            .filter { it !in SYSTEM_CORE }
            .mapNotNull { pkg ->
                val name = try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    try {
                        val info = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                        pm.getApplicationLabel(info).toString()
                    } catch (_: Exception) {
                        null
                    }
                }

                if (name != null) AppInfo(pkg, name, pkg in allowed) else null
            }
            .sortedWith(compareBy({ !it.isAllowed }, { it.appName }))
    }

    fun reapplyStudentModeOnBoot(ctx: Context) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = SchoolDeviceAdminReceiver.getComponentName(ctx)
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return

        applyManagedWallpaper(ctx)

        for (restriction in studentRestrictions()) {
            try { dpm.addUserRestriction(admin, restriction) } catch (_: Exception) { }
        }

        if (shouldUseAccessibilityFallback(ctx)) {
            ensurePasswordGuardEnabled(ctx)
        }

        lastEnforceAt = 0L
        enforceNoPasswordPolicy(ctx)

        val allowed = getAllowedApps(ctx)
        val known = loadKnown(ctx).ifEmpty { scanAppsWithIcon(ctx) }
        val toBlock = known.filter { it !in allowed }.toTypedArray()

        if (toBlock.isNotEmpty()) {
            try { dpm.setPackagesSuspended(admin, toBlock, true) } catch (_: Exception) { }
        }
        for (pkg in toBlock) {
            try { dpm.setApplicationHidden(admin, pkg, true) } catch (_: Exception) { }
        }
    }

    fun activateStudentMode(ctx: Context) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = SchoolDeviceAdminReceiver.getComponentName(ctx)
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return

        applyManagedWallpaper(ctx)

        val allPkgsBeforeScan = getAllPackageNames(ctx)
        for (pkg in allPkgsBeforeScan) {
            try { dpm.setApplicationHidden(admin, pkg, false) } catch (_: Exception) { }
        }
        try { dpm.setPackagesSuspended(admin, allPkgsBeforeScan.toTypedArray(), false) } catch (_: Exception) { }

        val visible = scanAppsWithIcon(ctx)
        saveKnown(ctx, visible)

        for (restriction in studentRestrictions()) {
            try {
                dpm.addUserRestriction(admin, restriction)
                Log.i(TAG, "Restricao aplicada no modo aluno: $restriction")
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao aplicar restricao no modo aluno: $restriction", e)
            }
        }

        if (shouldUseAccessibilityFallback(ctx)) {
            ensurePasswordGuardEnabled(ctx)
            try {
                dpm.setPermittedAccessibilityServices(admin, listOf(ACCESSIBILITY_SERVICE))
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao permitir PasswordGuard no DPM", e)
            }
        }

        lastEnforceAt = 0L
        enforceNoPasswordPolicy(ctx)

        try { dpm.setUninstallBlocked(admin, ctx.packageName, true) } catch (_: Exception) { }

        val allowed = getAllowedApps(ctx)
        val toBlock = visible.filter { it !in allowed }.toTypedArray()

        // Força Chrome sempre em modo anônimo (incógnito)
        try {
            val chromePolicy = android.os.Bundle()
            chromePolicy.putInt("IncognitoModeAvailability", 2) // 0=disponível 1=desativado 2=forçado
            chromePolicy.putBoolean("SavingBrowserHistoryDisabled", true)
            chromePolicy.putBoolean("PasswordManagerEnabled", false)
            dpm.setApplicationRestrictions(admin, "com.android.chrome", chromePolicy)
            Log.i(TAG, "Chrome: modo anonimo forcado")
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao configurar politica do Chrome", e)
        }

        prefs(ctx).edit().putBoolean(KEY_STUDENT, true).commit()

        dpmExecutor.execute {
            if (toBlock.isNotEmpty()) {
                try { dpm.setPackagesSuspended(admin, toBlock, true) } catch (_: Exception) { }
            }
            for (pkg in toBlock) {
                try { dpm.setApplicationHidden(admin, pkg, true) } catch (_: Exception) { }
            }
        }
    }

    fun activateAdminMode(ctx: Context) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = SchoolDeviceAdminReceiver.getComponentName(ctx)
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return

        for (restriction in studentRestrictions()) {
            try {
                dpm.clearUserRestriction(admin, restriction)
                Log.i(TAG, "Restricao removida no modo admin: $restriction")
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao remover restricao no modo admin: $restriction", e)
            }
        }

        try { dpm.setKeyguardDisabled(admin, false) } catch (_: Exception) { }
        try { dpm.setKeyguardDisabledFeatures(admin, DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE) } catch (_: Exception) { }
        PasswordTokenManager.clearToken(ctx)

        try { dpm.setPermittedAccessibilityServices(admin, null) } catch (_: Exception) { }
        try { dpm.setUninstallBlocked(admin, ctx.packageName, false) } catch (_: Exception) { }

        // Remove restrições do Chrome (modo admin tem acesso total)
        try {
            dpm.setApplicationRestrictions(admin, "com.android.chrome", android.os.Bundle())
            Log.i(TAG, "Chrome: politica removida no modo admin")
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao remover politica do Chrome", e)
        }

        prefs(ctx).edit().putBoolean(KEY_STUDENT, false).commit()

        val allPkgs = getAllPackageNames(ctx)
        for (pkg in allPkgs) {
            try { dpm.setApplicationHidden(admin, pkg, false) } catch (_: Exception) { }
        }
        try { dpm.setPackagesSuspended(admin, allPkgs.toTypedArray(), false) } catch (_: Exception) { }

        val fresh = scanAppsWithIcon(ctx)
        saveKnown(ctx, fresh)
    }

    fun removeDeviceOwner(ctx: Context): Boolean {
        return try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = SchoolDeviceAdminReceiver.getComponentName(ctx)
            activateAdminMode(ctx)
            dpm.clearDeviceOwnerApp(ctx.packageName)
            dpm.removeActiveAdmin(admin)
            true
        } catch (_: Exception) {
            false
        }
    }

    internal val dpmExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    @Volatile var lastEnforceAt = 0L

    fun enforcePasswordRemoval(ctx: Context) {
        if (PasswordTokenManager.isEscrowTokenDisabled(ctx)) {
            Log.w(TAG, "Remocao automatica ignorada: escrow token desativado neste tablet")
            return
        }

        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = SchoolDeviceAdminReceiver.getComponentName(ctx)

        var resetToken = PasswordTokenManager.loadToken(ctx)
        var tokenActive = PasswordTokenManager.isTokenActive(ctx)

        if (resetToken == null || !tokenActive) {
            Log.w(TAG, "Token inativo ou ausente ao remover senha, tentando registrar novamente")
            val ok = PasswordTokenManager.setupToken(ctx)
            if (ok) {
                resetToken = PasswordTokenManager.loadToken(ctx)
                tokenActive = PasswordTokenManager.isTokenActive(ctx)
            }
        }

        if (resetToken != null && tokenActive) {
            try {
                dpm.resetPasswordWithToken(admin, "", resetToken, 0)
                Log.i(TAG, "Senha removida via token apos onPasswordChanged")
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao remover senha via token", e)
            }
        } else {
            Log.e(TAG, "Token ainda inativo apos nova tentativa de registro")
        }

        try { dpm.setKeyguardDisabled(admin, true) } catch (_: Exception) { }
        try {
            dpm.setKeyguardDisabledFeatures(
                admin,
                DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT or
                    DevicePolicyManager.KEYGUARD_DISABLE_FACE or
                    DevicePolicyManager.KEYGUARD_DISABLE_IRIS or
                    DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA or
                    DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS
            )
        } catch (_: Exception) { }
        try {
            dpm.setPasswordQuality(admin, DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED)
        } catch (_: Exception) { }

        lastEnforceAt = android.os.SystemClock.elapsedRealtime()
    }

    fun enforceNoPasswordPolicy(ctx: Context) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastEnforceAt < 15_000L) return
        lastEnforceAt = now

        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = SchoolDeviceAdminReceiver.getComponentName(ctx)

        if (PasswordTokenManager.isEscrowTokenDisabled(ctx)) {
            Log.w(TAG, "Modo fallback ativo: token desativado, usando PasswordGuard")
            try {
                dpm.setKeyguardDisabled(admin, true)
                Log.i(TAG, "Keyguard desativado para modo aluno")
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao desativar keyguard", e)
            }
            try {
                dpm.setKeyguardDisabledFeatures(
                    admin,
                    DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT or
                        DevicePolicyManager.KEYGUARD_DISABLE_FACE or
                        DevicePolicyManager.KEYGUARD_DISABLE_IRIS or
                        DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA or
                        DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS
                )
                Log.i(TAG, "Biometrias e agentes de confianca desativados")
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao desativar biometrias e agentes de confianca", e)
            }
            ensurePasswordGuardEnabled(ctx)
            return
        }

        var resetToken = PasswordTokenManager.loadToken(ctx)
        var tokenActive = PasswordTokenManager.isTokenActive(ctx)
        if (resetToken == null || !tokenActive) {
            PasswordTokenManager.setupToken(ctx)
            resetToken = PasswordTokenManager.loadToken(ctx)
            tokenActive = PasswordTokenManager.isTokenActive(ctx)
        }

        if (resetToken != null && tokenActive) {
            try { dpm.resetPasswordWithToken(admin, "", resetToken, 0) } catch (_: Exception) { }
        }

        try {
            dpm.setKeyguardDisabled(admin, true)
            Log.i(TAG, "Keyguard desativado para modo aluno")
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao desativar keyguard", e)
        }

        try {
            dpm.setKeyguardDisabledFeatures(
                admin,
                DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT or
                    DevicePolicyManager.KEYGUARD_DISABLE_FACE or
                    DevicePolicyManager.KEYGUARD_DISABLE_IRIS or
                    DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA or
                    DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS
            )
            Log.i(TAG, "Biometrias e agentes de confianca desativados")
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao desativar biometrias e agentes de confianca", e)
        }

        try {
            dpm.setPasswordQuality(admin, DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED)
            Log.i(TAG, "Qualidade minima de senha zerada")
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao redefinir a politica minima de senha", e)
        }
    }

    private fun studentRestrictions() = listOf(
        UserManager.DISALLOW_INSTALL_APPS,
        UserManager.DISALLOW_UNINSTALL_APPS,
        UserManager.DISALLOW_APPS_CONTROL,
        UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_SAFE_BOOT,
        UserManager.DISALLOW_ADD_USER,
        UserManager.DISALLOW_DEBUGGING_FEATURES,
        UserManager.DISALLOW_USB_FILE_TRANSFER,
        UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
        UserManager.DISALLOW_NETWORK_RESET,
        UserManager.DISALLOW_CONFIG_CREDENTIALS,
        UserManager.DISALLOW_SET_WALLPAPER
    )

    private fun getAllPackageNames(ctx: Context): List<String> {
        val pm = ctx.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES)
        }.map { it.packageName }
    }
}
