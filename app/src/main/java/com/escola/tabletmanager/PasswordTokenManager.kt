package com.escola.tabletmanager

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import java.security.SecureRandom

object PasswordTokenManager {

    private const val TAG = "GestorEscolar"
    private const val PREFS = "token_prefs"
    private const val KEY_TOKEN = "reset_token"
    private const val KEY_ESCROW_DISABLED = "escrow_disabled"

    const val REQUEST_ACTIVATE_TOKEN = 9001

    fun setupTokenAndGetActivationIntent(context: Context): Intent? {
        if (isEscrowTokenDisabled(context)) {
            Log.w(TAG, "Escrow token desativado neste tablet; usando fallback")
            return null
        }

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = SchoolDeviceAdminReceiver.getComponentName(context)
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        if (isTokenActive(context) && loadToken(context) != null) {
            Log.i(TAG, "Token ja esta ativo, nenhuma acao necessaria")
            return null
        }

        val token = ByteArray(32)
        SecureRandom().nextBytes(token)

        return try {
            try {
                dpm.clearResetPasswordToken(admin)
            } catch (_: Exception) {
            }

            val registered = dpm.setResetPasswordToken(admin, token)
            if (!registered) {
                Log.e(TAG, "setResetPasswordToken retornou false")
                return null
            }

            setEscrowTokenDisabled(context, false)
            saveToken(context, token)

            if (waitForTokenActivation(context, 3000L)) {
                Log.i(TAG, "Token ativo apos registro inicial")
                null
            } else if (km.isDeviceSecure) {
                Log.i(TAG, "Token inativo e aparelho protegido; solicitando confirmacao de credencial")
                km.createConfirmDeviceCredentialIntent(
                    "Ativar protecao de senha",
                    "Confirme as credenciais do tablet para ativar a remocao automatica de senha."
                )
            } else {
                Log.w(TAG, "Tablet sem senha, mas token continuou inativo apos espera inicial")
                null
            }
        } catch (e: Exception) {
            handleTokenException(context, "Erro ao configurar token", e)
            null
        }
    }

    fun setupToken(context: Context): Boolean {
        if (isEscrowTokenDisabled(context)) {
            Log.w(TAG, "setupToken ignorado porque escrow token esta desativado neste tablet")
            return false
        }

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = SchoolDeviceAdminReceiver.getComponentName(context)

        return try {
            try {
                dpm.clearResetPasswordToken(admin)
            } catch (_: Exception) {
            }

            val token = ByteArray(32)
            SecureRandom().nextBytes(token)

            val set = dpm.setResetPasswordToken(admin, token)
            if (!set) {
                Log.e(TAG, "setupToken: setResetPasswordToken retornou false")
                return false
            }

            setEscrowTokenDisabled(context, false)
            saveToken(context, token)
            val active = waitForTokenActivation(context, 3000L)
            Log.i(TAG, "setupToken concluido. active=$active")
            active
        } catch (e: Exception) {
            handleTokenException(context, "Erro em setupToken", e)
            false
        }
    }

    fun waitUntilTokenReady(context: Context, timeoutMs: Long = 5000L): Boolean {
        if (isEscrowTokenDisabled(context)) return false
        return if (isTokenActive(context)) true else waitForTokenActivation(context, timeoutMs)
    }

    fun prepareTokenIfPossible(context: Context, timeoutMs: Long = 8000L): Boolean {
        if (isEscrowTokenDisabled(context)) {
            Log.w(TAG, "prepareTokenIfPossible: escrow token desativado neste tablet")
            return false
        }
        if (isTokenActive(context) && loadToken(context) != null) return true

        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isDeviceSecure) {
            Log.w(TAG, "prepareTokenIfPossible: aparelho protegido, token depende de confirmacao")
            return false
        }

        repeat(3) { attempt ->
            val ok = setupToken(context)
            val active = if (ok) waitUntilTokenReady(context, timeoutMs) else false
            Log.i(TAG, "prepareTokenIfPossible tentativa=${attempt + 1} active=$active")
            if (active) return true
            if (isEscrowTokenDisabled(context)) return false
            SystemClock.sleep(500L)
        }

        Log.w(TAG, "prepareTokenIfPossible: token continuou inativo em aparelho sem senha")
        return false
    }

    fun loadToken(context: Context): ByteArray? {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null) ?: return null
        return try {
            Base64.decode(encoded, Base64.DEFAULT)
        } catch (_: Exception) {
            null
        }
    }

    fun isTokenActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = SchoolDeviceAdminReceiver.getComponentName(context)
        return try {
            dpm.isResetPasswordTokenActive(admin)
        } catch (_: Exception) {
            false
        }
    }

    fun isEscrowTokenDisabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ESCROW_DISABLED, false)
    }

    fun clearToken(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = SchoolDeviceAdminReceiver.getComponentName(context)
        try {
            dpm.clearResetPasswordToken(admin)
        } catch (_: Exception) {
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_TOKEN)
            .remove(KEY_ESCROW_DISABLED)
            .apply()
    }

    private fun saveToken(context: Context, token: ByteArray) {
        val encoded = Base64.encodeToString(token, Base64.DEFAULT)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, encoded)
            .apply()
    }

    private fun setEscrowTokenDisabled(context: Context, disabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ESCROW_DISABLED, disabled)
            .apply()
    }

    private fun handleTokenException(context: Context, prefix: String, e: Exception) {
        if (isEscrowDisabledError(e)) {
            setEscrowTokenDisabled(context, true)
            Log.e(TAG, "$prefix: escrow token desativado neste tablet", e)
        } else {
            Log.e(TAG, prefix, e)
        }
    }

    private fun isEscrowDisabledError(e: Exception): Boolean {
        val text = buildString {
            append(e.message.orEmpty())
            append(' ')
            append(e.cause?.message.orEmpty())
        }.lowercase()
        return text.contains("escrow token is disabled")
    }

    private fun waitForTokenActivation(context: Context, timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (isTokenActive(context)) return true
            SystemClock.sleep(200L)
        }
        return isTokenActive(context)
    }
}
