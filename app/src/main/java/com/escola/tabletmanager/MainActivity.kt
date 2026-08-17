package com.escola.tabletmanager

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // Cacheado no onCreate — nunca muda em runtime e evita IPC ao system_server no onResume
    private var isOwner = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FLAG_KEEP_SCREEN_ON removido: mantinha a CPU acordada permanentemente,
        // causando superaquecimento e reboot de proteção térmica em tablets fracos
        // quando o aluno ficava abrindo/fechando a barra de notificações.
        setContentView(R.layout.activity_main)
        isOwner = AppManager.isDeviceOwner(this)
        AutoUpdater.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        if (isOwner) {
            Thread {
                PasswordTokenManager.prepareTokenIfPossible(this, 8000L)
                if (AppManager.shouldUseAccessibilityFallback(this)) {
                    AppManager.ensurePasswordGuardEnabled(this)
                }
                if (AppManager.isStudentMode(this)) {
                    AppManager.applyManagedWallpaper(this)
                    AppManager.enforceNoPasswordPolicy(this)
                }
            }.start()
        }
        updateUI()
    }

    private fun updateUI() {
        val tvStatus  = findViewById<TextView>(R.id.tvStatus)
        val tvInfo    = findViewById<TextView>(R.id.tvInfo)
        val btnAction = findViewById<Button>(R.id.btnAction)

        if (!AppManager.isDeviceOwner(this)) {
            tvStatus.text = "Nao Configurado"
            tvInfo.text   = "Execute o script ADB no PC para configurar este tablet."
            btnAction.isEnabled = false
            return
        }

        if (AppManager.isStudentMode(this)) {
            tvStatus.text = "Modo Aluno Ativo"
            tvInfo.text   = "Tablet restrito. Apenas apps educacionais disponiveis."
            btnAction.text = "Acesso Administrativo"
            btnAction.setBackgroundColor(0xFF1565C0.toInt())
            btnAction.setOnClickListener {
                startActivity(Intent(this, PasswordActivity::class.java))
            }
        } else {
            tvStatus.text = "Modo Admin Ativo"
            tvInfo.text   = "Acesso total ao dispositivo."
            btnAction.text = "Painel Administrativo"
            btnAction.setBackgroundColor(0xFF2E7D32.toInt())
            btnAction.setOnClickListener {
                startActivity(Intent(this, AdminActivity::class.java))
            }
        }
    }
}
