package app.missu.data.comic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal val comicJson = Json { ignoreUnknownKeys = true }

internal fun String.toJsonElement(): JsonElement = comicJson.parseToJsonElement(this)

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject

internal fun JsonElement?.array(): JsonArray? = this as? JsonArray

internal fun JsonElement?.text(): String? = when (this) {
    null -> null
    else -> runCatching { jsonPrimitive.contentOrNull }.getOrNull()
}

internal fun JsonElement?.int(): Int? = runCatching { this?.jsonPrimitive?.intOrNull }.getOrNull()
    ?: text()?.toIntOrNull()

internal fun JsonElement?.long(): Long? = runCatching { this?.jsonPrimitive?.longOrNull }.getOrNull()
    ?: text()?.toLongOrNull()

internal fun JsonElement?.bool(): Boolean? = runCatching { this?.jsonPrimitive?.booleanOrNull }.getOrNull()

internal fun JsonObject.at(vararg path: String): JsonElement? {
    var current: JsonElement? = this
    path.forEach { segment -> current = current.obj()?.get(segment) }
    return current
}

internal fun JsonObject.listAt(vararg path: String): List<JsonElement> =
    at(*path).array()?.toList().orEmpty()

internal fun parseJsonStringAssignment(html: String, name: String): String? {
    val marker = Regex("\\b(?:var|let|const)\\s+$name\\s*=\\s*").find(html) ?: return null
    val start = marker.range.last + 1
    if (html.getOrNull(start) != '"') return null
    var escaped = false
    for (index in start + 1 until html.length) {
        val char = html[index]
        if (char == '"' && !escaped) {
            return comicJson.parseToJsonElement(html.substring(start, index + 1)).jsonPrimitive.content
        }
        escaped = char == '\\' && !escaped
        if (char != '\\') escaped = false
    }
    return null
}

internal fun parseJsonObjectAssignment(html: String, name: String): JsonObject? {
    val marker = Regex("\\b(?:var|let|const)\\s+$name\\s*=\\s*").find(html) ?: return null
    val start = marker.range.last + 1
    if (html.getOrNull(start) != '{') return null
    var depth = 0
    var quoted = false
    var escaped = false
    for (index in start until html.length) {
        val char = html[index]
        if (quoted) {
            if (char == '"' && !escaped) quoted = false
            escaped = char == '\\' && !escaped
            if (char != '\\') escaped = false
            continue
        }
        when (char) {
            '"' -> quoted = true
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) return html.substring(start, index + 1).toJsonElement().obj()
            }
        }
    }
    return null
}
