package com.mokelay.android.navigation

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

enum class PageSource(val wireValue: String) {
    USER("user"),
    SYSTEM("system");

    companion object {
        fun fromWireValue(value: String?): PageSource {
            return if (value == SYSTEM.wireValue) SYSTEM else USER
        }
    }
}

data class PageDestination(
    val uuid: String,
    val source: PageSource = PageSource.USER,
)

fun manualPageDestination(uuid: String, isBuiltinPage: Boolean): PageDestination {
    return PageDestination(
        uuid = uuid.trim(),
        source = if (isBuiltinPage) PageSource.SYSTEM else PageSource.USER,
    )
}

fun PageDestination.toPageRoute(): String {
    return "page/${source.wireValue}/${URLEncoder.encode(uuid, Charsets.UTF_8.name())}"
}

fun extractPageDestination(scannedText: String): PageDestination? {
    val trimmed = scannedText.trim()
    if (trimmed.isEmpty()) {
        return null
    }

    parseUri(trimmed)?.let { uri ->
        uri.fragment?.let { fragment ->
            extractPageDestinationFromPreviewPath(fragment)?.let { return it }
        }

        extractPageDestinationFromPreviewPath(uri.path.orEmpty(), uri.query)?.let { return it }
    }

    val hashIndex = trimmed.indexOf('#')
    if (hashIndex >= 0 && hashIndex < trimmed.lastIndex) {
        extractPageDestinationFromPreviewPath(trimmed.substring(hashIndex + 1))?.let { return it }
    }

    if (!trimmed.contains("/") && !trimmed.contains("?") && !trimmed.contains("#")) {
        return PageDestination(trimmed, PageSource.USER)
    }

    return null
}

fun decodeRouteValue(value: String): String {
    return URLDecoder.decode(value, Charsets.UTF_8.name())
}

private fun parseUri(value: String): URI? {
    return runCatching { URI(value) }.getOrNull()
}

private fun extractPageDestinationFromPreviewPath(path: String, fallbackQuery: String? = null): PageDestination? {
    val normalizedPath = if (path.startsWith("/")) path else "/$path"
    val pathParts = normalizedPath.split("?", limit = 2)
    val pathWithoutQuery = pathParts.firstOrNull().orEmpty()
    val query = pathParts.getOrNull(1) ?: fallbackQuery
    val parts = pathWithoutQuery.split("/").filter { it.isNotEmpty() }

    if (parts.size !in 2..3 || parts[0] != "pages") {
        return null
    }
    if (parts.size == 3 && parts[2] != "preview") {
        return null
    }

    val uuid = decodeRouteValue(parts[1])
    val source = querySource(query) ?: PageSource.USER
    return PageDestination(uuid = uuid, source = source)
}

private fun querySource(query: String?): PageSource? {
    if (query.isNullOrBlank()) {
        return null
    }

    return query
        .split("&")
        .mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.firstOrNull() == "source") parts.getOrNull(1) else null
        }
        .firstOrNull()
        ?.let { PageSource.fromWireValue(decodeRouteValue(it)) }
}
