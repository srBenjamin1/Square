package dev.lelonio.square.thor.input

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll as originalHorizontalScroll
import androidx.compose.foundation.verticalScroll as originalVerticalScroll
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Original containers, with an activity-local scroll target for the right stick. */
@Composable
fun ThorLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) = androidx.compose.foundation.lazy.LazyColumn(
    modifier.thorFocusGroup().thorScrollTarget(state, Orientation.Vertical), state, contentPadding, reverseLayout,
    verticalArrangement, horizontalAlignment, flingBehavior, userScrollEnabled, content = content,
)

@Composable
fun ThorLazyRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal = if (!reverseLayout) Arrangement.Start else Arrangement.End,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) = androidx.compose.foundation.lazy.LazyRow(
    modifier.thorFocusGroup().thorScrollTarget(state, Orientation.Horizontal), state, contentPadding, reverseLayout,
    horizontalArrangement, verticalAlignment, flingBehavior, userScrollEnabled, content = content,
)

@Composable
fun ThorLazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyGridScope.() -> Unit,
) = androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
    columns, modifier.thorFocusGroup().thorScrollTarget(state, Orientation.Vertical), state, contentPadding,
    reverseLayout, verticalArrangement, horizontalArrangement, flingBehavior, userScrollEnabled,
    content = content,
)

fun Modifier.thorVerticalScroll(
    state: ScrollState, enabled: Boolean = true, flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false,
) = thorScrollTarget(state, Orientation.Vertical)
    .originalVerticalScroll(state, enabled, flingBehavior, reverseScrolling)

fun Modifier.thorHorizontalScroll(
    state: ScrollState, enabled: Boolean = true, flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false,
) = thorScrollTarget(state, Orientation.Horizontal)
    .originalHorizontalScroll(state, enabled, flingBehavior, reverseScrolling)
