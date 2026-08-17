package com.escola.tabletmanager

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private var apps: MutableList<AppInfo>,
    private val onCheckedChange: (AppInfo, Boolean) -> Unit
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    var onChangeListener: (() -> Unit)? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val cbAllowed: CheckBox = view.findViewById(R.id.cbAllowed)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        val pm = holder.itemView.context.packageManager

        val icon: Drawable? = try { pm.getApplicationIcon(app.packageName) } catch (e: Exception) { null }
        if (icon != null) holder.ivIcon.setImageDrawable(icon)
        else holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)

        holder.tvAppName.text = app.appName

        holder.cbAllowed.setOnCheckedChangeListener(null)
        holder.cbAllowed.isChecked = app.isAllowed
        holder.cbAllowed.setOnCheckedChangeListener { _, isChecked ->
            onCheckedChange(app, isChecked)
            onChangeListener?.invoke()
        }

        holder.itemView.setOnClickListener {
            holder.cbAllowed.isChecked = !holder.cbAllowed.isChecked
        }
    }

    override fun getItemCount() = apps.size

    fun updateList(newList: MutableList<AppInfo>) {
        apps = newList
        notifyDataSetChanged()
    }

    fun selectAll() {
        apps.forEach { it.isAllowed = true }
        notifyDataSetChanged()
        onChangeListener?.invoke()
    }

    fun deselectAll() {
        apps.forEach { it.isAllowed = false }
        notifyDataSetChanged()
        onChangeListener?.invoke()
    }
}
