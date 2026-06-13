package com.v2ray.ang.viewmodel

import androidx.lifecycle.ViewModel
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager

/**
 * Holds the in-memory "app -> target node" assignment for the per-app node
 * routing screen. Every mutation persists the map, rebuilds the synthesized
 * routing rulesets, and flags the service for restart.
 */
class PerAppNodeViewModel : ViewModel() {
    private val nodeMap: MutableMap<String, String> = SettingsManager.getPerAppNodeMap()

    /**
     * Target outbound tag assigned to [packageName], or null when the app
     * follows the global proxy (i.e. is not assigned).
     */
    fun getTag(packageName: String): String? = nodeMap[packageName]

    fun getAll(): Map<String, String> = nodeMap.toMap()

    /**
     * Assign [packageName] to [outboundTag]. A blank tag clears the assignment
     * (the app falls back to the global proxy).
     */
    fun setTag(packageName: String, outboundTag: String?) {
        val changed = if (outboundTag.isNullOrBlank()) {
            nodeMap.remove(packageName) != null
        } else {
            nodeMap.put(packageName, outboundTag) != outboundTag
        }
        if (changed) {
            save()
        }
    }

    fun clear(packageName: String) {
        if (nodeMap.remove(packageName) != null) {
            save()
        }
    }

    private fun save() {
        SettingsManager.savePerAppNodeMap(nodeMap)
        SettingsManager.rebuildPerAppNodeRules()
        SettingsChangeManager.makeRestartService()
    }
}
