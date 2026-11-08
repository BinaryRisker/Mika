package com.wangzan101.mika.core.pack

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 包清单只含这四项，不接受权限、网络开关、令牌或工具注册。 */
@Serializable
data class PackManifest(
    val id: String,
    val version: String,
    val displayName: String,
    val description: String,
)

object PackManifests {
    private val json = Json { ignoreUnknownKeys = false }
    private val KEYS = setOf("id", "version", "displayName", "description")

    fun parse(text: String): PackManifest? = runCatching {
        val o = json.parseToJsonElement(text) as? JsonObject ?: return null
        if (o.keys != KEYS) return null
        val m = PackManifest(
            id = o.getValue("id").jsonPrimitive.content,
            version = o.getValue("version").jsonPrimitive.content,
            displayName = o.getValue("displayName").jsonPrimitive.content,
            description = o.getValue("description").jsonPrimitive.content,
        )
        if (!PackPath.packId(m.id) || m.version.isBlank() || m.displayName.isBlank() || m.description.isBlank()) null else m
    }.getOrNull()
}
