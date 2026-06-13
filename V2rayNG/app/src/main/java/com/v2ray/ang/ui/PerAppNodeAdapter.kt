package com.v2ray.ang.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.databinding.ItemRecyclerPerAppNodeBinding
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.viewmodel.PerAppNodeViewModel

/**
 * Lists network apps; each row shows the app's currently assigned target node
 * and opens a node picker when tapped.
 */
class PerAppNodeAdapter(
    val apps: List<AppInfo>,
    private val viewModel: PerAppNodeViewModel,
    private val labelProvider: (String?) -> String,
    private val onPick: (AppInfo) -> Unit,
) : RecyclerView.Adapter<PerAppNodeAdapter.AppViewHolder>() {

    override fun getItemCount() = apps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        return AppViewHolder(
            ItemRecyclerPerAppNodeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    inner class AppViewHolder(private val binding: ItemRecyclerPerAppNodeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(appInfo: AppInfo) {
            binding.icon.setImageDrawable(appInfo.appIcon)
            binding.name.text = if (appInfo.isSystemApp) {
                String.format("** %s", appInfo.appName)
            } else {
                appInfo.appName
            }
            binding.packageName.text = appInfo.packageName
            binding.nodeTag.text = labelProvider(viewModel.getTag(appInfo.packageName))

            binding.root.setOnClickListener { onPick(appInfo) }
        }
    }
}
