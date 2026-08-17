package com.escola.tabletmanager

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.util.Log

class SchoolDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {}

    override fun onDisabled(context: Context, intent: Intent) {}

    override fun onPasswordChanged(context: Context, intent: Intent) {
        handlePasswordChanged(context)
    }

    override fun onPasswordChanged(context: Context, intent: Intent, user: UserHandle) {
        handlePasswordChanged(context)
    }

    /**
     * Chamado sempre que o usuário define ou altera a senha/PIN/padrão.
     *
     * USA enforcePasswordRemoval (sem throttle) em vez de enforceNoPasswordPolicy.
     * Motivo: enforceNoPasswordPolicy tem throttle de 15s e pode ter sido chamado
     * há pouco (ex: ao entrar no modo aluno). Se o throttle barrar a execução aqui,
     * a senha do aluno não seria removida.
     *
     * O delay de 3s ainda é necessário: o system_server precisa terminar de
     * processar a transação de senha antes de resetPasswordWithToken funcionar.
     */
    private fun handlePasswordChanged(context: Context) {
        if (!AppManager.isStudentMode(context)) return

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(context.packageName)) return

        Log.w("GestorEscolar", "Senha alterada no modo aluno — agendando remoção em 3s")

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            AppManager.dpmExecutor.execute {
                AppManager.enforcePasswordRemoval(context)
            }
        }, 3000L)
    }

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, SchoolDeviceAdminReceiver::class.java)
        }
    }
}
