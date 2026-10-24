package dev.lelonio.square.ui

import dev.lelonio.square.data.MusicSource

/** One ordering for touch, the glass indicator and L1/R1. */
internal object SourceTabs {
    fun routes(source: MusicSource): List<String> = buildList {
        add(Routes.HOME)
        add(Routes.NEW)
        if (source != MusicSource.LOCAL) add(Routes.RADIO)
        add(Routes.LIBRARY)
        add(Routes.SEARCH)
    }
    fun next(source: MusicSource, current: String, delta: Int): String {
        val routes = routes(source)
        return routes[Math.floorMod(routes.indexOf(current).coerceAtLeast(0) + delta, routes.size)]
    }
    fun valid(source: MusicSource, route: String) = if (source == MusicSource.LOCAL && route == Routes.RADIO) Routes.HOME else route
}
