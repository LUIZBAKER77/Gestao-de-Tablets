package com.escola.tabletmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (AppManager.isStudentMode(context) && AppManager.isDeviceOwner(context)) {
                // Executar em thread separada para não bloquear o broadcast receiver
                Thread { AppManager.reapplyStudentModeOnBoot(context) }.start()
            }
        }
    }
}
