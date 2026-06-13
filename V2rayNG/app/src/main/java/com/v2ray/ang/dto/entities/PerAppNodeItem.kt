package com.v2ray.ang.dto.entities

/**
 * One persisted "app -> target node" assignment.
 *
 * [outboundTag] is either a builtin tag (direct/block) or a node profile's
 * remarks. Apps that should follow the global proxy are simply not stored.
 */
data class PerAppNodeItem(
    var packageName: String = "",
    var outboundTag: String = "",
)
