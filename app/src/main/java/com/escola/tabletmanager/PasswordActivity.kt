package com.escola.tabletmanager

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password)

        val tvTitle        = findViewById<TextView>(R.id.tvTitle)
        val tvSubtitle     = findViewById<TextView>(R.id.tvSubtitle)
        val etPassword     = findViewById<EditText>(R.id.etPassword)
        val etConfirm      = findViewById<EditText>(R.id.etConfirm)
        val tvConfirmLabel = findViewById<TextView>(R.id.tvConfirmLabel)
        val btnConfirm     = findViewById<Button>(R.id.btnConfirm)
        val btnCancel      = findViewById<Button>(R.id.btnCancel)
        val loadingOverlay = findViewById<View>(R.id.loadingOverlay)

        val isFirstTime = !AppManager.isPasswordSet(this)

        if (isFirstTime) {
            tvTitle.text    = "Criar Senha de Administrador"
            tvSubtitle.text = "Esta senha será usada para acessar o Modo Admin. Guarde-a em local seguro."
            etConfirm.visibility      = View.VISIBLE
            tvConfirmLabel.visibility = View.VISIBLE
            btnConfirm.text = "Criar Senha"
        } else {
            tvTitle.text    = "Acesso Administrativo"
            tvSubtitle.text = "Digite a senha para acessar o painel admin."
            etConfirm.visibility      = View.GONE
            tvConfirmLabel.visibility = View.GONE
            btnConfirm.text = "Entrar"
        }

        btnCancel.setOnClickListener { finish() }

        btnConfirm.setOnClickListener {
            val password = etPassword.text.toString()

            if (password.length < 4) {
                Toast.makeText(this, "A senha precisa ter pelo menos 4 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isFirstTime) {
                val confirm = etConfirm.text.toString()
                if (password != confirm) {
                    Toast.makeText(this, "As senhas não coincidem", Toast.LENGTH_SHORT).show()
                    etConfirm.text.clear()
                    return@setOnClickListener
                }
                AppManager.setPassword(this, password)
                Toast.makeText(this, "Senha criada com sucesso!", Toast.LENGTH_SHORT).show()
                openAdminPanel(loadingOverlay, btnConfirm, btnCancel)
            } else {
                if (AppManager.checkPassword(this, password)) {
                    openAdminPanel(loadingOverlay, btnConfirm, btnCancel)
                } else {
                    Toast.makeText(this, "Senha incorreta. Tente novamente.", Toast.LENGTH_SHORT).show()
                    etPassword.text.clear()
                }
            }
        }
    }

    private fun openAdminPanel(loadingOverlay: View, btnConfirm: Button, btnCancel: Button) {
        // Mostra tela de carregamento imediatamente
        loadingOverlay.visibility = View.VISIBLE
        btnConfirm.isEnabled = false
        btnCancel.isEnabled  = false

        // Executa activateAdminMode em background (é lento — DPM calls bloqueantes)
        Thread {
            AppManager.activateAdminMode(this)
            runOnUiThread {
                startActivity(Intent(this, AdminActivity::class.java))
                finish()
            }
        }.start()
    }
}
