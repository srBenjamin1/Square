package dev.lelonio.square.backend.spotify

import dev.lelonio.square.nativecore.NativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Who made a track, as Spotify's own clients show it.
 *
 * The same list that sits behind "Show credits" there: who performed it, who
 * wrote it, who produced it, and whose recording it is. It comes from an
 * endpoint of the account's own, so the listener's token is all it needs, and
 * it is asked for only when somebody opens the panel — nothing about playing a
 * song requires knowing who engineered it.
 */
object SpotifyCredits {

    private val http = OkHttpClient()

    /** One group of people with the same job on this track. */
    data class Role(val title: String, val people: List<Person>)

    data class Person(
        val name: String,
        /** "main artist", "composer", "lyricist" — what they did, when it is said. */
        val subroles: List<String>,
        /** Their artist page, for the ones who have one. */
        val uri: String?,
    )

    data class Credits(
        val title: String,
        val roles: List<Role>,
        /** The label, as Spotify names it: "Sony Music", "Tattica s.r.l.". */
        val sources: List<String>,
    ) {
        val isEmpty: Boolean get() = roles.all { it.people.isEmpty() } && sources.isEmpty()
    }

    /**
     * Null when the track has none, when the account cannot be asked, or when
     * anything at all goes wrong: this is a panel, not a feature to fail over.
     */
    suspend fun of(trackUri: String): Credits? = withContext(Dispatchers.IO) {
        val id = trackUri.substringAfterLast(':').takeIf { it.isNotEmpty() }
            ?: return@withContext null
        val token = NativeBridge.accessToken() ?: return@withContext null

        runCatching {
            val request = Request.Builder()
                .url("$HOST/track-credits-view/v0/experimental/$id/credits")
                .header("authorization", "Bearer $token")
                .header("accept", "application/json")
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                parse(JSONObject(response.body?.string().orEmpty()))
            }
        }
            .onFailure { android.util.Log.i(TAG, "no credits for $trackUri: ${it.message}") }
            .getOrNull()
    }

    private fun parse(body: JSONObject): Credits {
        val roles = body.optJSONArray("roleCredits").let { array ->
            (0 until (array?.length() ?: 0)).mapNotNull { index ->
                val group = array?.optJSONObject(index) ?: return@mapNotNull null
                val people = group.optJSONArray("artists").let { artists ->
                    (0 until (artists?.length() ?: 0)).mapNotNull { at ->
                        val person = artists?.optJSONObject(at) ?: return@mapNotNull null
                        val name = person.optString("name").takeIf { it.isNotEmpty() }
                            ?: return@mapNotNull null
                        Person(
                            name = name,
                            subroles = person.optJSONArray("subroles").let { list ->
                                (0 until (list?.length() ?: 0)).mapNotNull { role ->
                                    list?.optString(role)?.takeIf { it.isNotEmpty() }
                                }
                            },
                            uri = person.optString("uri").takeIf { it.startsWith("spotify:artist:") },
                        )
                    }
                }
                Role(title = group.optString("roleTitle"), people = people)
                    .takeIf { it.people.isNotEmpty() }
            }
        }

        val sources = body.optJSONArray("sourceNames").let { list ->
            (0 until (list?.length() ?: 0)).mapNotNull { at ->
                list?.optString(at)?.takeIf { it.isNotEmpty() }
            }
        }

        return Credits(body.optString("trackTitle"), roles, sources)
    }

    private const val HOST = "https://spclient.wg.spotify.com"
    private const val TAG = "SquareCredits"
}
