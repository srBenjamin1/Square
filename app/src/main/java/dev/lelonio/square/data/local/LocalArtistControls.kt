package dev.lelonio.square.data.local

data class LocalArtistControls(val uri: String, val status: LocalResolutionStatus, val visual: LocalArtistVisual?) {
    enum class Action { INFORMATION, STATUS, LINK, CHANGE, UNLINK, RETRY, REJECT }
    val actions: List<Action> get() = buildList {
        add(Action.INFORMATION)
        add(Action.STATUS)
        add(if (visual == null) Action.LINK else Action.CHANGE)
        if (visual != null) add(Action.UNLINK)
        if (status != LocalResolutionStatus.MANUAL) add(Action.RETRY)
        if (status != LocalResolutionStatus.MANUAL && status != LocalResolutionStatus.REJECTED) add(Action.REJECT)
    }
}
