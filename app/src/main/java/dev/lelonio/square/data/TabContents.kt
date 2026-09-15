package dev.lelonio.square.data

/**
 * Which tab each of Spotify's rows belongs to, so that nothing is shown twice.
 *
 * Home, New and Radio are all built out of the same gateway, and they used to
 * be kept apart by row title alone. Spotify files the same playlists under
 * different titles on different pages, so the daily mixes, Release Radar, the
 * genre mixes and the daylist all turned up on two tabs at once. Each tab now
 * answers its own question, and a playlist goes to the one whose question it
 * answers:
 *
 * - Home, what is for me now: what was being played, what Spotify made for
 *   this account (Discover Weekly, the daily mixes), the day's own soundtrack,
 *   the blends, and the "more like" rows;
 * - New, what is out and what is being played: new releases, Release Radar,
 *   the charts;
 * - Radio, listening that does not stop: every station and every mix.
 *
 * What a playlist is comes from its ID. Spotify's generated lists keep their
 * kind in a prefix, and titles are no use for it: they are in the listener's
 * language and change with the time of day.
 */
object TabContents {

    data class Tabs(
        val home: List<HomeShelf> = emptyList(),
        val new: List<HomeShelf> = emptyList(),
        val radio: List<HomeShelf> = emptyList(),
    )

    enum class Kind { STATION, MIX, DAILY_MIX, DAYLIST, BLEND, RELEASE, OTHER }

    fun kind(item: CatalogPlaylist): Kind {
        val id = item.uri.removePrefix(PLAYLIST)
        if (id == item.uri) return Kind.OTHER
        return when {
            // "Radio di …", one for each artist.
            id.startsWith("37i9dQZF1E4") -> Kind.STATION
            id.startsWith("37i9dQZF1E3") -> Kind.DAILY_MIX
            id.startsWith("37i9dQZF1EP") -> Kind.DAYLIST
            id.startsWith("37i9dQZF1EJ") -> Kind.BLEND
            // By genre and decade, by artist and niche, and by mood.
            id.startsWith("37i9dQZF1EQ") || id.startsWith("37i9dQZF1EI") ||
                id.startsWith("37i9dQZF1EV") -> Kind.MIX
            // Its ID is the account's own, but not its name, in any language.
            item.name == "Release Radar" || item.name.startsWith("New Music Friday") ->
                Kind.RELEASE
            else -> Kind.OTHER
        }
    }

    /**
     * The three tabs out of Spotify's four sources.
     *
     * [home] is the personalised home; [releases], [charts] and [madeForYou]
     * are browse pages. Any of them can be empty, and the tabs are built from
     * whatever has arrived.
     */
    fun split(
        home: List<HomeShelf>,
        releases: List<HomeShelf>,
        charts: List<HomeShelf>,
        madeForYou: List<HomeShelf>,
    ): Tabs {
        // Rows that are mostly stations are Radio's, wherever they turn up.
        val stationRows = home.filter { share(it, Kind.STATION) >= MOSTLY }
        val onRadioStations = stationRows.flatMapTo(mutableSetOf()) { row -> row.items.map { it.uri } }
        val chartUris = charts.flatMapTo(mutableSetOf()) { row -> row.items.map { it.uri } }

        // From "made for you", what is personal comes home: the day's list and
        // the blends. The mixes go to Radio. The daily mixes stay where Spotify
        // puts them, on Home's own row, and the rest (videos, concerts near
        // you) is not music to put on.
        val personal = madeForYou.filter { row ->
            row.items.any { kind(it) == Kind.DAYLIST || kind(it) == Kind.BLEND }
        }
        val mixRows = madeForYou.filter { share(it, Kind.MIX) >= MOSTLY }

        // Home: Spotify's rows, less the stations and the rows of favourite
        // mixes, which Radio has whole. The day's soundtrack stays: it is
        // mixes, but the day's own, and it comes with the daylist.
        val homeRows = (home - stationRows.toSet())
            .filterNot { share(it, Kind.MIX) >= MOSTLY && it.items.none { item -> kind(item) == Kind.DAYLIST } }
            .plus(personal)
            .map { row ->
                // What was being played is the listener's own, so it stays as
                // Spotify gave it.
                if (row.sectionUri == RECENTS) row
                else {
                    // A mix is Radio's wherever it turns up, except in the
                    // day's own soundtrack, which comes with the daylist.
                    val soundtrack = row.items.any { kind(it) == Kind.DAYLIST }
                    row.copy(
                        items = row.items.filterNot {
                            kind(it) == Kind.RELEASE || it.uri in chartUris ||
                                it.uri in onRadioStations || (kind(it) == Kind.MIX && !soundtrack)
                        },
                    )
                }
            }
            .distinctItems()
            .filter { it.items.size >= MIN_ITEMS }

        // What Home shows leaves the other tabs, except what was being
        // played: a mix or a chart heard yesterday is still Radio's and New's.
        val onHome = homeRows.filter { it.sectionUri != RECENTS }
            .flatMapTo(mutableSetOf()) { row -> row.items.map { it.uri } }

        val radioRows = (stationRows + mixRows)
            .map { row -> row.copy(items = row.items.filterNot { it.uri in onHome }) }
            .distinctItems()
            .filter { it.items.size >= MIN_ITEMS }

        val newRows = (releases + charts)
            .map { row -> row.copy(items = row.items.filterNot { it.uri in onHome }) }
            .distinctItems()
            .filter { it.items.size >= MIN_ITEMS }

        return Tabs(home = homeRows, new = newRows, radio = radioRows)
    }

    /** The chips on Home: what is made for this account, what is new to it, and whose. */
    enum class Chip { FOR_YOU, DISCOVER, ARTISTS }

    /**
     * Which chip a row of Home's goes under, or none for what was being played,
     * which only the whole page shows.
     *
     * A row about an artist says so: gathered ones name the artist, and
     * Spotify's own "more like" rows lead with that artist's radio and carry
     * artists and albums. Rows of what Spotify made for the account carry the
     * lists only it has (Discover Weekly, the daily mixes, the daylist, the
     * blends, the throwbacks), and what was gathered from the single tiles is
     * Spotify's "just for you" and "based on what you listened to". The rest
     * is the editors'.
     */
    fun chipOf(row: HomeShelf): Chip? = when {
        row.sectionUri == RECENTS -> null
        row.artist != null -> Chip.ARTISTS
        row.gathered -> Chip.FOR_YOU
        row.items.any { personal(it) } -> Chip.FOR_YOU
        row.items.any { it.uri.startsWith("spotify:artist:") } ||
            row.items.firstOrNull()?.let { kind(it) == Kind.STATION } == true -> Chip.ARTISTS
        else -> Chip.DISCOVER
    }

    /** One of the lists Spotify makes for a single account. */
    private fun personal(item: CatalogPlaylist): Boolean {
        val id = item.uri.removePrefix(PLAYLIST)
        return kind(item) in setOf(Kind.DAILY_MIX, Kind.DAYLIST, Kind.BLEND) ||
            // Discover Weekly, and the throwbacks and "on repeat" ones.
            id.startsWith("37i9dQZEVXc") || id.startsWith("37i9dQZF1Ep") || id.startsWith("37i9dQZF1CA")
    }

    /** Each playlist once per tab, in the first row that has it. */
    private fun List<HomeShelf>.distinctItems(): List<HomeShelf> {
        val seen = mutableSetOf<String>()
        return map { row -> row.copy(items = row.items.filter { seen.add(it.uri) }) }
    }

    private fun share(row: HomeShelf, kind: Kind): Float =
        if (row.items.isEmpty()) 0f else row.items.count { kind(it) == kind }.toFloat() / row.items.size

    /** What "mostly" means for a row: one odd tile does not change what it is. */
    private const val MOSTLY = 0.8f

    /** A carousel of one tile is not a row. */
    private const val MIN_ITEMS = 2

    /**
     * The section Spotify's home puts what was being played in, "Jump back in".
     *
     * Its type says nothing, every row on the home is the same generic one, so
     * it is known by its address, which Spotify gives the same section on every
     * account.
     */
    const val RECENTS = "spotify:section:0JQ5DAIiKWzVFULQfUm85X"

    private const val PLAYLIST = "spotify:playlist:"
}
