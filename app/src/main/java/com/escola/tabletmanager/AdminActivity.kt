package com.escola.tabletmanager

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AdminActivity : AppCompatActivity() {

    private lateinit var adapter: AppAdapter
    private var fullList: MutableList<AppInfo> = mutableListOf()
    private lateinit var tvCount: TextView
    private lateinit var loadingOverlay: View
    private var currentQuery: String = ""
    private var pendingAllowedApps: Set<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        tvCount = findViewById(R.id.tvAllowedCount)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        if (!AppManager.isPasswordSet(this)) showCreatePasswordDialog()

        setupRecyclerView()
        setupButtons()
        setupSearch()
    }

    override fun onResume() {
        super.onResume()
        refreshAppList()
        Thread {
            PasswordTokenManager.prepareTokenIfPossible(this, 8000L)
            if (AppManager.shouldUseAccessibilityFallback(this)) {
                AppManager.ensurePasswordGuardEnabled(this)
            }
        }.start()
    }

    @Deprecated("Required for compatibility with older APIs")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PasswordTokenManager.REQUEST_ACTIVATE_TOKEN) {
            if (resultCode == Activity.RESULT_OK) {
                Log.i("GestorEscolar", "Credencial confirmada, token ativo, prosseguindo com modo aluno")
                val allowed = pendingAllowedApps ?: fullList.filter { it.isAllowed }.map { it.packageName }.toSet()
                pendingAllowedApps = null
                AppManager.setAllowedApps(this, allowed)
                doActivateStudentMode()
            } else {
                pendingAllowedApps = null
                loadingOverlay.visibility = View.GONE
                Toast.makeText(
                    this,
                    "Cancelado. Credencial necessaria para ativar o modo aluno.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupRecyclerView() {
        val rv = findViewById<RecyclerView>(R.id.rvApps)
        adapter = AppAdapter(mutableListOf()) { app, checked ->
            fullList.find { it.packageName == app.packageName }?.isAllowed = checked
            updateCount()
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
    }

    private fun refreshAppList() {
        fullList = AppManager.getInstalledUserApps(this).toMutableList()
        applyFilter(currentQuery)
        updateCount()
    }

    private fun applyFilter(query: String) {
        currentQuery = query
        val filtered = if (query.isEmpty()) {
            fullList.toMutableList()
        } else {
            fullList.filter { it.appName.lowercase().contains(query) }.toMutableList()
        }
        adapter.updateList(filtered)
    }

    private fun updateCount() {
        val c = fullList.count { it.isAllowed }
        tvCount.text = "$c app(s) liberado(s) para alunos"
    }

    private fun setupSearch() {
        val etSearch = findViewById<EditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { applyFilter(s.toString().lowercase()) }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val allowed = fullList.filter { it.isAllowed }.map { it.packageName }.toSet()
            AppManager.setAllowedApps(this, allowed)
            showToast("Lista salva!")
            updateCount()
        }

        findViewById<Button>(R.id.btnStudentMode).setOnClickListener {
            if (!AppManager.isPasswordSet(this)) {
                showCreatePasswordDialog()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("Ativar Modo Aluno?")
                .setMessage("Apps nao marcados serao ocultados e o tablet ficara restrito.")
                .setPositiveButton("Ativar") { _, _ ->
                    val allowed = fullList.filter { it.isAllowed }.map { it.packageName }.toSet()
                    AppManager.setAllowedApps(this, allowed)
                    startStudentModeFlow(allowed)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        findViewById<Button>(R.id.btnHome).setOnClickListener {
            startActivity(Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }

        findViewById<Button>(R.id.btnRemoveOwner).setOnClickListener { showRemoveOwnerDialog() }

        findViewById<Button>(R.id.btnChangePassword).setOnClickListener {
            if (!AppManager.isPasswordSet(this)) showCreatePasswordDialog()
            else showChangePasswordDialog()
        }

        findViewById<Button>(R.id.btnSelectAll).setOnClickListener {
            fullList.forEach { it.isAllowed = true }
            applyFilter(currentQuery)
            updateCount()
        }

        findViewById<Button>(R.id.btnDeselectAll).setOnClickListener {
            fullList.forEach { it.isAllowed = false }
            applyFilter(currentQuery)
            updateCount()
        }

        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            showToast("Atualizando lista...")
            refreshAppList()
            showToast("Lista atualizada!")
        }
    }

    private fun startStudentModeFlow(allowed: Set<String>) {
        loadingOverlay.visibility = View.VISIBLE

        Thread {
            PasswordTokenManager.prepareTokenIfPossible(this, 8000L)
            if (AppManager.shouldUseAccessibilityFallback(this)) {
                AppManager.ensurePasswordGuardEnabled(this)
            }
            val activationIntent = PasswordTokenManager.setupTokenAndGetActivationIntent(this)

            runOnUiThread {
                if (activationIntent != null) {
                    pendingAllowedApps = allowed
                    try {
                        @Suppress("DEPRECATION")
                        startActivityForResult(activationIntent, PasswordTokenManager.REQUEST_ACTIVATE_TOKEN)
                    } catch (e: Exception) {
                        Log.w("GestorEscolar", "Nao foi possivel mostrar confirmacao de credencial: ${e.message}")
                        pendingAllowedApps = null
                        loadingOverlay.visibility = View.GONE
                        Toast.makeText(
                            this,
                            "Nao foi possivel confirmar a credencial do aparelho. Remova a senha atual do tablet e tente de novo.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    if (AppManager.shouldUseAccessibilityFallback(this)) {
                        AppManager.ensurePasswordGuardEnabled(this)
                    }
                    if (PasswordTokenManager.prepareTokenIfPossible(this, 8000L) ||
                        PasswordTokenManager.waitUntilTokenReady(this, 8000L) ||
                        AppManager.isPasswordProtectionReady(this)
                    ) {
                        doActivateStudentMode()
                    } else {
                        loadingOverlay.visibility = View.GONE
                        Toast.makeText(
                            this,
                            "A protecao de senha nao ficou pronta neste tablet.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }.start()
    }

    private fun doActivateStudentMode() {
        Thread {
            if (AppManager.shouldUseAccessibilityFallback(this)) {
                AppManager.ensurePasswordGuardEnabled(this)
            }
            if (!PasswordTokenManager.prepareTokenIfPossible(this, 8000L) &&
                !PasswordTokenManager.waitUntilTokenReady(this, 8000L) &&
                !AppManager.isPasswordProtectionReady(this)
            ) {
                runOnUiThread {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(
                        this,
                        "Modo aluno bloqueado: a protecao de senha nao esta ativa neste tablet.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@Thread
            }

            AppManager.activateStudentMode(this)
            runOnUiThread {
                val i = Intent(this, MainActivity::class.java)
                i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(i)
                finish()
            }
        }.start()
    }

    private fun showCreatePasswordDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_change_password, null)
        view.findViewById<View>(R.id.etCurrentPassword).visibility = View.GONE
        view.findViewById<View>(R.id.tvCurrentLabel).visibility = View.GONE
        val etNew = view.findViewById<EditText>(R.id.etNewPassword)
        val etConf = view.findViewById<EditText>(R.id.etConfirmPassword)
        AlertDialog.Builder(this)
            .setTitle("Criar Senha Admin")
            .setMessage("Crie uma senha. Anote em local seguro!")
            .setView(view)
            .setCancelable(false)
            .setPositiveButton("Criar") { _, _ ->
                val p = etNew.text.toString()
                val c = etConf.text.toString()
                if (p.length < 4) {
                    showToast("Minimo 4 caracteres")
                    return@setPositiveButton
                }
                if (p != c) {
                    showToast("Senhas nao coincidem")
                    return@setPositiveButton
                }
                AppManager.setPassword(this, p)
                showToast("Senha criada!")
            }
            .show()
    }

    private fun showChangePasswordDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val etCur = view.findViewById<EditText>(R.id.etCurrentPassword)
        val etNew = view.findViewById<EditText>(R.id.etNewPassword)
        val etConf = view.findViewById<EditText>(R.id.etConfirmPassword)
        AlertDialog.Builder(this)
            .setTitle("Alterar Senha")
            .setView(view)
            .setPositiveButton("Salvar") { _, _ ->
                if (!AppManager.checkPassword(this, etCur.text.toString())) {
                    showToast("Senha atual incorreta")
                    return@setPositiveButton
                }
                val p = etNew.text.toString()
                val c = etConf.text.toString()
                if (p.length < 4) {
                    showToast("Minimo 4 caracteres")
                    return@setPositiveButton
                }
                if (p != c) {
                    showToast("Senhas nao coincidem")
                    return@setPositiveButton
                }
                AppManager.setPassword(this, p)
                showToast("Senha alterada!")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showRemoveOwnerDialog() {
        AlertDialog.Builder(this)
            .setTitle("Remover Gestão de Tablets")
            .setMessage("Remove o Device Owner e todas as restricoes.\nApos isso o app pode ser desinstalado normalmente.")
            .setPositiveButton("Remover") { _, _ ->
                val ok = AppManager.removeDeviceOwner(this)
                if (ok) {
                    showToast("Removido! Pode desinstalar o app.")
                    val i = Intent(this, MainActivity::class.java)
                    i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(i)
                    finish()
                } else {
                    showToast("Erro. Use o script ADB opcao 4.")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
