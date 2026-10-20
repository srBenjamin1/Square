package dev.lelonio.square.thor.input

/** Coordinates real Compose focus; analogue sampling remains in AnalogNavigation. */
class SpatialNavigation(
    private val prepare: () -> Unit,
    private val hasFocus: () -> Boolean,
    private val enter: () -> Boolean,
    private val move: (AnalogNavigation.Direction) -> Boolean,
) {
    fun ensureFocus(): Boolean {
        prepare()
        return hasFocus() || enter()
    }

    fun move(direction: AnalogNavigation.Direction): Boolean {
        if (!ensureFocus()) return false
        return move.invoke(direction)
    }
}
