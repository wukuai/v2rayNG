package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.Menu
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.AppConfig.TAG_BLOCKED
import com.v2ray.ang.AppConfig.TAG_DIRECT
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityPerAppNodeBinding
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.AppManagerUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.viewmodel.PerAppNodeViewModel
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

class PerAppNodeActivity : BaseActivity() {
    private val binding by lazy { ActivityPerAppNodeBinding.inflate(layoutInflater) }

    private val viewModel: PerAppNodeViewModel by viewModels()
    private var adapter: PerAppNodeAdapter? = null
    private var appsAll: List<AppInfo>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = getString(R.string.per_app_node_settings))

        addCustomDividerToRecyclerView(binding.recyclerView, this, R.drawable.custom_divider)

        initList()

        // Set initial state before attaching the listener so the prerequisite
        // dialog is not triggered by the programmatic update below.
        binding.switchPerAppNode.isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_NODE, false)
        binding.switchPerAppNode.setOnCheckedChangeListener { _, isChecked ->
            onToggle(isChecked)
        }

        binding.layoutPerAppNodeTips.setOnClickListener {
            Toasty.info(this, R.string.summary_per_app_node, android.widget.Toast.LENGTH_LONG, true).show()
        }

        // Recover synthesized rules in case a routing preset reset removed them.
        SettingsManager.rebuildPerAppNodeRules()
    }

    private fun onToggle(isChecked: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_NODE, isChecked)
        if (isChecked && !SettingsManager.canUseProcessRouting()) {
            promptEnablePrerequisites()
        } else {
            SettingsManager.rebuildPerAppNodeRules()
            SettingsChangeManager.makeRestartService()
        }
    }

    /**
     * Process routing requires Android 10+, Xray TUN (not HEV) and Route Only.
     * Offer to switch those settings automatically; revert the toggle otherwise.
     */
    private fun promptEnablePrerequisites() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            toast(R.string.per_app_node_requires_android10)
            revertToggle()
            return
        }

        AlertDialog.Builder(this)
            .setMessage(R.string.per_app_node_prerequisite_message)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                MmkvManager.encodeSettings(AppConfig.PREF_USE_HEV_TUNNEL, false)
                MmkvManager.encodeSettings(AppConfig.PREF_ROUTE_ONLY_ENABLED, true)
                SettingsManager.rebuildPerAppNodeRules()
                SettingsChangeManager.makeRestartService()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                revertToggle()
            }
            .show()
    }

    private fun revertToggle() {
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_NODE, false)
        binding.switchPerAppNode.isChecked = false
        SettingsManager.rebuildPerAppNodeRules()
    }

    private fun initList() {
        showLoading()
        lifecycleScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) {
                    val appsList = AppManagerUtil.loadNetworkAppList(this@PerAppNodeActivity)
                    val assigned = viewModel.getAll()
                    val collator = Collator.getInstance()
                    appsList.sortedWith { p1, p2 ->
                        val s1 = if (assigned.containsKey(p1.packageName)) 0 else 1
                        val s2 = if (assigned.containsKey(p2.packageName)) 0 else 1
                        when {
                            s1 != s2 -> s1 - s2
                            p1.isSystemApp != p2.isSystemApp -> if (p1.isSystemApp) 1 else -1
                            else -> collator.compare(p1.appName, p2.appName)
                        }
                    }
                }

                appsAll = apps
                adapter = PerAppNodeAdapter(apps, viewModel, ::labelForTag, ::pickNode)
                binding.recyclerView.adapter = adapter
            } catch (e: Exception) {
                LogUtil.e(ANG_PACKAGE, "Error loading apps", e)
            } finally {
                hideLoading()
            }
        }
    }

    /**
     * Human-readable label for the stored target tag shown on each row.
     */
    private fun labelForTag(tag: String?): String {
        return when {
            tag.isNullOrEmpty() -> getString(R.string.per_app_node_follow_global)
            tag == TAG_DIRECT -> getString(R.string.per_app_node_target_direct)
            tag == TAG_BLOCKED -> getString(R.string.per_app_node_target_block)
            else -> tag
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun pickNode(appInfo: AppInfo) {
        // values[0] == "" means follow global (clear assignment).
        val values = mutableListOf("", TAG_DIRECT, TAG_BLOCKED)
        values.addAll(SettingsManager.getProfileRemarks())
        val labels = values.map { labelForTag(it) }.toTypedArray()

        val current = viewModel.getTag(appInfo.packageName) ?: ""
        val checkedIndex = values.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(appInfo.appName)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                viewModel.setTag(appInfo.packageName, values[which])
                adapter?.notifyDataSetChanged()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_per_app_node, menu)

        val searchItem = menu.findItem(R.id.search_view)
        (searchItem?.actionView as? SearchView)?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterApps(newText.orEmpty())
                return false
            }
        })
        return super.onCreateOptionsMenu(menu)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun filterApps(content: String) {
        val key = content.uppercase()
        val apps = if (key.isEmpty()) {
            appsAll.orEmpty()
        } else {
            appsAll.orEmpty().filter {
                it.appName.uppercase().contains(key) || it.packageName.uppercase().contains(key)
            }
        }
        adapter = PerAppNodeAdapter(apps, viewModel, ::labelForTag, ::pickNode)
        binding.recyclerView.adapter = adapter
    }
}
