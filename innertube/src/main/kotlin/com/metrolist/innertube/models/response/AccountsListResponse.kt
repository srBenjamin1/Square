package com.metrolist.innertube.models.response

import com.metrolist.innertube.models.Runs
import com.metrolist.innertube.models.Thumbnails
import kotlinx.serialization.Serializable

/**
 * Vendored addition: every channel a Google account can act as.
 *
 * A Google account is not one YouTube identity but several — the personal
 * channel, and any brand channels it owns — and the official apps let the
 * listener pick between them. The library that ships here only ever asked for
 * the active one, so a session opened on the wrong identity showed the wrong
 * library with no way to change it.
 *
 * Each entry carries the tokens that select it: a page id for a brand channel,
 * and the datasync id that tells the rest of the API which library to read.
 */
@Serializable
data class AccountsListResponse(
    val contents: Contents?,
) {
    @Serializable
    data class Contents(
        val accountSectionListRenderer: AccountSectionListRenderer?,
    )

    @Serializable
    data class AccountSectionListRenderer(
        val contents: List<Content>?,
    ) {
        @Serializable
        data class Content(
            val accountItemSectionRenderer: AccountItemSectionRenderer?,
        )
    }

    @Serializable
    data class AccountItemSectionRenderer(
        val contents: List<Content>?,
    ) {
        @Serializable
        data class Content(
            val accountItem: AccountItem?,
        )
    }

    @Serializable
    data class AccountItem(
        val accountName: Runs?,
        val accountByline: Runs?,
        val accountPhoto: Thumbnails?,
        val isSelected: Boolean = false,
        val serviceEndpoint: ServiceEndpoint?,
    )

    @Serializable
    data class ServiceEndpoint(
        val selectActiveIdentityEndpoint: SelectActiveIdentityEndpoint?,
    )

    @Serializable
    data class SelectActiveIdentityEndpoint(
        val supportedTokens: List<SupportedToken>?,
    )

    @Serializable
    data class SupportedToken(
        val pageIdToken: PageIdToken? = null,
        val datasyncIdToken: DatasyncIdToken? = null,
    )

    @Serializable
    data class PageIdToken(val pageId: String? = null)

    @Serializable
    data class DatasyncIdToken(val datasyncIdToken: String? = null)
}
