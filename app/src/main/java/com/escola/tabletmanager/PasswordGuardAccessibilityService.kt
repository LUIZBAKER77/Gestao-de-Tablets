package com.escola.tabletmanager

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PasswordGuardAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastBlockAt = 0L

    private val settingsPackages = listOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.google.android.permissioncontroller"
    )

    private val blockWords = listOf(
        "senha",
        "pin",
        "padr",
        "bloqueio de tela",
        "tipo de bloqueio",
        "escolha um tipo de bloqueio",
        "nova senha",
        "confirmar senha",
        "password",
        "screen lock",
        "lock screen",
        "set a screen lock",
        "set screen lock",
        "secure lock",
        "impress",
        "fingerprint",
        "biometr",
        "face unlock",
        "reconhecimento facial",
        "desbloqueio facial",
        "secure folder lock",
        "iris",
        "enroll"
    )

    private val allowWords = listOf(
        "wifi",
        "wi-fi",
        "internet",
        "rede",
        "redes",
        "network",
        "networks",
        "ssid",
        "bluetooth",
        "parear",
        "pareamento",
        "pair",
        "paired",
        "connected devices",
        "dispositivos conectados"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!AppManager.isStudentMode(this)) return
        if (!AppManager.shouldUseAccessibilityFallback(this)) return

        val pkg = event.packageName?.toString().orEmpty()
        val className = event.className?.toString().orEmpty()
        val eventText = event.text?.joinToString(" ").orEmpty()

        if (pkg == packageName) return
        if (!isRelevantPackage(pkg, className)) return

        val root = rootInActiveWindow
        val aggregate = buildAggregateText(root, pkg, className, eventText)

        if (isAllowedSettingsScreen(aggregate)) return
        if (!shouldBlock(aggregate)) return

        Log.w(
            "GestorEscolar",
            "PasswordGuard bloqueando tela agressivamente. pkg=$pkg class=$className text=$eventText"
        )
        blockAggressively()
    }

    override fun onInterrupt() = Unit

    private fun isRelevantPackage(pkg: String, className: String): Boolean {
        val hay = "$pkg $className".lowercase()
        return settingsPackages.any { hay.contains(it) } ||
            hay.contains("settings") ||
            hay.contains("biometric") ||
            hay.contains("knox")
    }

    private fun buildAggregateText(
        root: AccessibilityNodeInfo?,
        pkg: String,
        className: String,
        eventText: String
    ): String {
        val texts = mutableListOf<String>()
        val ids = mutableListOf<String>()
        collectTexts(root, texts, 160)
        collectViewIds(root, ids, 120)
        if (eventText.isNotBlank()) texts.add(eventText)
        return buildString {
            append(pkg)
            append(' ')
            append(className)
            append(' ')
            append(texts.joinToString(" "))
            append(' ')
            append(ids.joinToString(" "))
        }.lowercase()
    }

    private fun isAllowedSettingsScreen(aggregate: String): Boolean {
        val hasAllowedArea = allowWords.any { aggregate.contains(it) }
        if (!hasAllowedArea) return false

        val hasBlockedSignal = blockWords.any { aggregate.contains(it) } ||
            aggregate.contains("setnewpassword") ||
            aggregate.contains("chooselock") ||
            aggregate.contains("screenlock") ||
            aggregate.contains("lockscreen") ||
            aggregate.contains("password") ||
            aggregate.contains("biometric") ||
            aggregate.contains("fingerprint")

        return !hasBlockedSignal
    }

    private fun shouldBlock(aggregate: String): Boolean {
        return blockWords.any { aggregate.contains(it) } ||
            aggregate.contains("setnewpassword") ||
            aggregate.contains("chooselock") ||
            aggregate.contains("screenlock") ||
            aggregate.contains("lockscreen") ||
            aggregate.contains("setupkeyguard") ||
            aggregate.contains("confirmlock") ||
            aggregate.contains("password") ||
            aggregate.contains("lock") ||
            aggregate.contains("biometric") ||
            aggregate.contains("fingerprint") ||
            aggregate.contains("face") ||
            aggregate.contains("iris")
    }

    private fun blockAggressively() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < 1200L) return
        lastBlockAt = now

        val actions = listOf(
            0L to GLOBAL_ACTION_BACK,
            60L to GLOBAL_ACTION_BACK,
            120L to GLOBAL_ACTION_BACK,
            180L to GLOBAL_ACTION_BACK,
            260L to GLOBAL_ACTION_HOME,
            360L to GLOBAL_ACTION_BACK,
            460L to GLOBAL_ACTION_HOME,
            650L to GLOBAL_ACTION_HOME,
            900L to GLOBAL_ACTION_BACK,
            1100L to GLOBAL_ACTION_HOME
        )

        for ((delay, action) in actions) {
            handler.postDelayed({ performGlobalAction(action) }, delay)
        }

        handler.postDelayed({
            try {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                })
            } catch (_: Exception) {
            }
        }, 300L)
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, out: MutableList<String>, limit: Int) {
        if (node == null || out.size >= limit) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until node.childCount) {
            if (out.size >= limit) break
            collectTexts(node.getChild(i), out, limit)
        }
    }

    private fun collectViewIds(node: AccessibilityNodeInfo?, out: MutableList<String>, limit: Int) {
        if (node == null || out.size >= limit) return
        node.viewIdResourceName?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until node.childCount) {
            if (out.size >= limit) break
            collectViewIds(node.getChild(i), out, limit)
        }
    }
}
