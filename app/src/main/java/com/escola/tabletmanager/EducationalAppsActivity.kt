package com.escola.tabletmanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class EducationalApp(
    val name: String,
    val description: String,
    val packageName: String
)

class EducationalAppsActivity : AppCompatActivity() {

    private val apps = listOf(
        EducationalApp("Qibla Compass",       "Bussola e fisica",                  "app.melon.icompass"),
        EducationalApp("Geekie One",          "Plataforma educacional Geekie",     "com.geekie.aluno"),
        EducationalApp("Plural",              "Plataforma de ensino",              "br.com.plural"),
        EducationalApp("Microsoft Word",      "Editor de textos",                  "com.microsoft.office.word"),
        EducationalApp("Microsoft Excel",     "Planilhas",                         "com.microsoft.office.excel"),
        EducationalApp("Microsoft PowerPoint","Apresentacoes",                     "com.microsoft.office.powerpoint"),
        EducationalApp("Arvore de Livros",    "Biblioteca digital",                "arvoredelivros.com.br.arvore"),
        EducationalApp("Adobe Reader",        "Leitura de PDFs",                   "com.adobe.reader"),
        EducationalApp("Canva",               "Design e criatividade",             "com.canva.editor"),
        EducationalApp("Minecraft Education", "Aprendizado com Minecraft",         "com.mojang.minecraftedu"),
        EducationalApp("Sketchboard",         "Quadro branco digital",             "com.lstudios.sketchboard"),
        EducationalApp("Scratch",             "Programacao para criancas",         "org.scratch"),
        EducationalApp("Photomath",           "Resolucao de matematica",           "com.microblink.photomath"),
        EducationalApp("Nearpod",             "Aulas interativas",                 "com.nearpod.nearpod"),
        EducationalApp("ibis Paint X",        "Desenho e arte digital",            "jp.ne.ibis.ibispaintx.app"),
        EducationalApp("Kahoot",              "Quiz e jogos educativos",           "no.mobitroll.kahoot.android"),
        EducationalApp("GeoGebra",            "Matematica e geometria",            "org.geogebra.android"),
        EducationalApp("Microblink",          "Scanner de documentos",             "com.microblink.photomath"),
        EducationalApp("Google Forms",         "Formularios e enquetes",            "com.google.android.apps.docs.editors.forms")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_educational_apps)

        val rv = findViewById<RecyclerView>(R.id.rvEducationalApps)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = EduAppAdapter(apps) { app -> openOrInstall(app) }

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun openOrInstall(app: EducationalApp) {
        val pm = packageManager
        val launch = pm.getLaunchIntentForPackage(app.packageName)
        if (launch != null) {
            startActivity(launch)
        } else {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=${app.packageName}")))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=${app.packageName}")))
            }
        }
    }

    inner class EduAppAdapter(
        private val list: List<EducationalApp>,
        private val onClick: (EducationalApp) -> Unit
    ) : RecyclerView.Adapter<EduAppAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivIcon: ImageView  = v.findViewById(R.id.ivAppIcon)
            val tvName: TextView   = v.findViewById(R.id.tvAppName)
            val tvDesc: TextView   = v.findViewById(R.id.tvAppDesc)
            val tvStatus: TextView = v.findViewById(R.id.tvStatus)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context)
                .inflate(R.layout.item_educational_app, p, false))

        override fun getItemCount() = list.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val app = list[pos]
            h.tvName.text = app.name
            h.tvDesc.text = app.description

            val installed = packageManager.getLaunchIntentForPackage(app.packageName) != null
            if (installed) {
                h.tvStatus.text = "ABRIR"
                h.tvStatus.setTextColor(0xFF43A047.toInt())
                try {
                    h.ivIcon.setImageDrawable(
                        packageManager.getApplicationIcon(app.packageName))
                } catch (_: Exception) { }
            } else {
                h.tvStatus.text = "INSTALAR"
                h.tvStatus.setTextColor(0xFF1E88E5.toInt())
            }

            h.itemView.setOnClickListener { onClick(app) }
        }
    }
}
