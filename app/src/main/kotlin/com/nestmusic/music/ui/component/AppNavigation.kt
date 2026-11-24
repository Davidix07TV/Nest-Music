/**
 * Nest Music — Nest Music iOS 26 Liquid Glass Navigation Bar + FloatingTabBar
 * Licensed under GPL-3.0
 */

package com.nestmusic.music.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nestmusic.music.ui.component.backdrop.backdrops.EmptyBackdrop
import com.nestmusic.music.ui.component.backdrop.backdrops.layerBackdrop
import com.nestmusic.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.nestmusic.music.ui.component.floatingtabbar.FlatSelectionPill
import com.nestmusic.music.ui.component.floatingtabbar.GlassPillMotion
import com.nestmusic.music.ui.component.floatingtabbar.GlassSelectionPill
import com.nestmusic.music.ui.component.floatingtabbar.PILL_GROW_HEIGHT
import com.nestmusic.music.ui.component.floatingtabbar.pillPlacement
import com.nestmusic.music.ui.screens.Screens
import com.nestmusic.music.ui.theme.NestSunsetBrushHorizontal
import com.nestmusic.music.ui.theme.useNestUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private val GlassSpring = spring<Float>(
    dampingRatio = 0.72f,
    stiffness = 320f,
)

private val CapsuleShape = RoundedCornerShape(percent = 50)
private val TabPillPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
private val TabItemPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp)
private val SlimTabItemPadding = PaddingValues(vertical = 9.dp, horizontal = 8.dp)
private val ComponentSpacing = 12.dp

@Stable
private fun isRouteSelected(currentRoute: String?, screenRoute: String, navigationItems: List<Screens>): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute) return true
    if (navigationItems.any { it.route == screenRoute } &&
        currentRoute.startsWith("$screenRoute/")
    ) {
        return true
    }

    if (screenRoute == "search_input" &&
        (currentRoute.startsWith("search/") || currentRoute == "search/{query}")
    ) {
        return true
    }

    return false
}

/** Rubber-band overshoot past the first or last tab when dragging the selection pill. */
private fun tabPillDragPosition(
    selectedIndex: Int,
    dragPx: Float,
    stepPx: Float,
    lastIndex: Int,
): Float {
    if (stepPx <= 0f || lastIndex <= 0) return 0f
    val raw = selectedIndex + dragPx / stepPx
    return when {
        raw < 0f -> -0.22f * (1f - 1f / (1f + abs(raw) * 2.5f))
        raw > lastIndex -> lastIndex + 0.22f * (1f - 1f / (1f + (raw - lastIndex) * 2.5f))
        else -> raw
    }
}

@Composable
private fun rememberNavPressSource(
    handleClick: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
): MutableInteractionSource {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    if (handleClick) {
        LaunchedEffect(interactionSource) {
            var isLongClick = false
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        isLongClick = false
                        delay(viewConfiguration.longPressTimeoutMillis)
                        isLongClick = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnLongClick?.invoke()
                    }
                    is PressInteraction.Release -> {
                        if (!isLongClick) currentOnClick()
                    }
                    is PressInteraction.Cancel -> {
                        isLongClick = false
                    }
                }
            }
        }
    }
    return interactionSource
}

/**
 * Nest Music iOS 26 Liquid Glass Navigation Bar:
 * - Grouped tab capsule on the left/center with [GlassSelectionPill] spring-lifted
 *   refracting lens + acceleration squash & stretch + horizontal finger drag
 * - Standalone circular Search Liquid Glass button on the right (Apple Music iOS 26 style)
 */
@Composable
private fun NestMusicGlassNavBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchLongClick: (() -> Unit)?,
    slimNav: Boolean = false,
    pureBlack: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val glassBackdrop = LocalGlassBackdrop.current
    val pageBackdrop = glassBackdrop ?: LocalAppBackdrop.current
    val glassActive = LocalLiquidGlassEnabled.current && isLiquidGlassSupported && pageBackdrop !== EmptyBackdrop
    val isDark = pureBlack || glassUsesDarkAppearance()

    // Split Search out as the standalone right-hand circular button when 3+ tabs exist (Apple Music iOS 26).
    val standaloneSearch = remember(navigationItems) {
        if (navigationItems.size >= 3) navigationItems.firstOrNull { it == Screens.Search } else null
    }
    val groupTabs = remember(navigationItems, standaloneSearch) {
        if (standaloneSearch != null) navigationItems.filter { it != standaloneSearch } else navigationItems
    }

    val selectedGroupIndex = remember(currentRoute, groupTabs, navigationItems) {
        groupTabs.indexOfFirst { isRouteSelected(currentRoute, it.route, navigationItems) }
    }
    val currentSelectedIndex by rememberUpdatedState(selectedGroupIndex)

    val coroutineScope = rememberCoroutineScope()
    val pill = remember { GlassPillMotion(coroutineScope, selectedGroupIndex.coerceAtLeast(0)) }
    val barGlass = rememberLayerBackdrop()
    val tabRow = rememberLayerBackdrop()
    val haptics = LocalHapticFeedback.current

    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    var lastSelectedTabIndex by remember { mutableIntStateOf(selectedGroupIndex) }
    var lastHapticTab by remember { mutableIntStateOf(selectedGroupIndex) }

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val tabCount = groupTabs.size
    val tabSpacing = 2.dp
    val tabSpacingPx = with(density) { tabSpacing.toPx() }
    val tabWidthPx = if (rowSize.width > 0 && tabCount > 0) {
        (rowSize.width - tabSpacingPx * (tabCount - 1)) / tabCount
    } else {
        0f
    }
    val tabStepPx = if (rowSize.width > 0 && tabCount > 0) {
        (rowSize.width + tabSpacingPx) / tabCount
    } else {
        0f
    }
    pill.stepDp = tabStepPx / density.density

    val padLeftPx = with(density) { TabPillPadding.calculateLeftPadding(layoutDirection).toPx() }
    val padTopPx = with(density) { TabPillPadding.calculateTopPadding().toPx() }
    val padBottomPx = with(density) { TabPillPadding.calculateBottomPadding().toPx() }
    val rowHeightPx = rowSize.height.toFloat()
    val restSize = Size(tabWidthPx, rowHeightPx)
    val liftedHeightPx = rowHeightPx + padTopPx + padBottomPx + with(density) { PILL_GROW_HEIGHT.toPx() }
    val liftedSize = if (rowHeightPx > 0f) {
        Size(liftedHeightPx * tabWidthPx / rowHeightPx, liftedHeightPx)
    } else {
        restSize
    }
    val pillCenterInRow: (Float) -> Offset = { position ->
        val x = position * tabStepPx + tabWidthPx / 2f
        Offset(if (isRtl) rowSize.width - x else x, rowHeightPx / 2f)
    }
    val showPill = selectedGroupIndex >= 0 && tabWidthPx > 0f

    LaunchedEffect(selectedGroupIndex) {
        if (selectedGroupIndex >= 0) {
            if (lastSelectedTabIndex < 0) {
                pill.snapTo(selectedGroupIndex)
            } else {
                pill.animateTo(selectedGroupIndex)
            }
        }
        lastSelectedTabIndex = selectedGroupIndex
        lastHapticTab = selectedGroupIndex
    }

    val indicatorColor = if (isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.Black.copy(alpha = 0.09f)
    }
    val activeContentColor = MaterialTheme.colorScheme.primary
    val inactiveContentColor = if (isDark) {
        Color.White.copy(alpha = 0.85f)
    } else {
        Color(0xFF141418).copy(alpha = 0.82f)
    }
    val fallbackBarBg = if (pureBlack) {
        Color(0xFF101014).copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ComponentSpacing),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
        ) {
            if (groupTabs.isNotEmpty()) {
                Box(modifier = Modifier.weight(1f)) {
                    CompositionLocalProvider(LocalGlassExport provides barGlass) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (glassActive) {
                                        Modifier.nestLiquidGlass(
                                            shape = CapsuleShape,
                                            backdrop = pageBackdrop,
                                            outerShadow = true,
                                        )
                                    } else {
                                        Modifier
                                            .shadow(14.dp, CapsuleShape, ambientColor = Color.Black.copy(alpha = 0.45f))
                                            .clip(CapsuleShape)
                                            .background(fallbackBarBg)
                                            .border(0.75.dp, Color.White.copy(alpha = if (isDark) 0.12f else 0.35f), CapsuleShape)
                                    },
                                )
                                .padding(TabPillPadding),
                        ) {
                            if (showPill && (!glassActive || pill.isFlat)) {
                                FlatSelectionPill(
                                    color = indicatorColor,
                                    modifier = Modifier
                                        .matchParentSize()
                                        .pillPlacement(
                                            center = { pillCenterInRow(pill.position) },
                                            size = { pill.liveSize(restSize, liftedSize) },
                                        ),
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(tabSpacing),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onSizeChanged { rowSize = it }
                                    .layerBackdrop(tabRow)
                                    .pointerInput(tabCount, tabStepPx, currentSelectedIndex, isRtl) {
                                        if (currentSelectedIndex < 0 || tabStepPx <= 0f) return@pointerInput
                                        val direction = if (isRtl) -1f else 1f
                                        var totalDrag = 0f
                                        detectHorizontalDragGestures(
                                            onDragStart = {
                                                totalDrag = 0f
                                                GlassMotion.nav.moved()
                                                pill.startDrag()
                                            },
                                            onDragCancel = {
                                                GlassMotion.nav.settled()
                                                pill.release(currentSelectedIndex)
                                            },
                                            onDragEnd = {
                                                GlassMotion.nav.settled()
                                                val ratio = totalDrag / tabStepPx
                                                val shift = when {
                                                    ratio > 0.35f -> maxOf(1, ratio.roundToInt())
                                                    ratio < -0.35f -> minOf(-1, ratio.roundToInt())
                                                    else -> 0
                                                }
                                                val newIndex = (currentSelectedIndex + shift)
                                                    .coerceIn(0, groupTabs.lastIndex)
                                                pill.release(newIndex)
                                                if (newIndex != currentSelectedIndex) {
                                                    onItemClick(groupTabs[newIndex], false)
                                                }
                                            },
                                            onHorizontalDrag = { _, delta ->
                                                GlassMotion.nav.moved()
                                                totalDrag += delta * direction
                                                val dragPos = tabPillDragPosition(
                                                    selectedIndex = currentSelectedIndex,
                                                    dragPx = totalDrag,
                                                    stepPx = tabStepPx,
                                                    lastIndex = groupTabs.lastIndex,
                                                )
                                                pill.dragTo(dragPos)
                                                val approx = dragPos
                                                    .coerceIn(0f, groupTabs.lastIndex.toFloat())
                                                    .roundToInt()
                                                if (approx != lastHapticTab) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    lastHapticTab = approx
                                                }
                                            },
                                        )
                                    },
                            ) {
                                groupTabs.forEachIndexed { idx, screen ->
                                    val isSelected = idx == selectedGroupIndex
                                    val currentIsSelected by rememberUpdatedState(isSelected)
                                    val iconRes = if (isSelected) screen.iconIdActive else screen.iconIdInactive
                                    val iconScale by animateFloatAsState(
                                        targetValue = if (isSelected) 1.08f else 1f,
                                        animationSpec = GlassSpring,
                                        label = "glassTabScale",
                                    )
                                    val isSearchTab = screen == Screens.Search && onSearchLongClick != null
                                    val interactionSource = rememberNavPressSource(
                                        handleClick = isSearchTab,
                                        onClick = { onItemClick(screen, currentIsSelected) },
                                        onLongClick = onSearchLongClick,
                                    )
                                    val tint = if (isSelected) activeContentColor else inactiveContentColor

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(CapsuleShape)
                                            .clickable(
                                                interactionSource = interactionSource,
                                                indication = null,
                                                onClick = {
                                                    if (!isSearchTab) {
                                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onItemClick(screen, currentIsSelected)
                                                    }
                                                },
                                            )
                                            .padding(if (slimNav) SlimTabItemPadding else TabItemPadding),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(iconRes),
                                            contentDescription = stringResource(screen.titleId),
                                            tint = tint,
                                            modifier = Modifier
                                                .size(23.dp)
                                                .graphicsLayer {
                                                    scaleX = iconScale
                                                    scaleY = iconScale
                                                },
                                        )
                                        if (!slimNav) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = stringResource(screen.titleId),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                color = tint,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (glassActive && showPill && !pill.isFlat) {
                        GlassSelectionPill(
                            motion = pill,
                            fillColor = indicatorColor,
                            barGlass = barGlass,
                            tabRow = tabRow,
                            pageBackdrop = pageBackdrop,
                            modifier = Modifier
                                .matchParentSize()
                                .pillPlacement(
                                    center = { pillCenterInRow(pill.position) + Offset(padLeftPx, padTopPx) },
                                    size = { pill.liveSize(restSize, liftedSize) },
                                ),
                        )
                    }
                }
            }

            if (standaloneSearch != null) {
                val isSearchSelected = remember(currentRoute, standaloneSearch.route, navigationItems) {
                    isRouteSelected(currentRoute, standaloneSearch.route, navigationItems)
                }
                val currentIsSearchSelected by rememberUpdatedState(isSearchSelected)
                val searchIconRes = if (isSearchSelected) standaloneSearch.iconIdActive else standaloneSearch.iconIdInactive
                val searchScale by animateFloatAsState(
                    targetValue = if (isSearchSelected) 1.08f else 1f,
                    animationSpec = GlassSpring,
                    label = "standaloneSearchScale",
                )
                val searchTint = if (isSearchSelected) activeContentColor else inactiveContentColor
                val searchInteraction = rememberNavPressSource(
                    handleClick = onSearchLongClick != null,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onItemClick(standaloneSearch, currentIsSearchSelected)
                    },
                    onLongClick = onSearchLongClick,
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .then(
                            if (glassActive) {
                                Modifier.nestLiquidGlass(
                                    shape = CircleShape,
                                    backdrop = pageBackdrop,
                                    outerShadow = true,
                                )
                            } else {
                                Modifier
                                    .shadow(14.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.45f))
                                    .clip(CircleShape)
                                    .background(fallbackBarBg)
                                    .border(0.75.dp, Color.White.copy(alpha = if (isDark) 0.12f else 0.35f), CircleShape)
                            },
                        )
                        .then(
                            if (isSearchSelected) {
                                Modifier.background(indicatorColor, CircleShape)
                            } else {
                                Modifier
                            },
                        )
                        .clickable(
                            interactionSource = searchInteraction,
                            indication = null,
                            onClick = {
                                if (onSearchLongClick == null) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onItemClick(standaloneSearch, currentIsSearchSelected)
                                }
                            },
                        ),
                ) {
                    Icon(
                        painter = painterResource(searchIconRes),
                        contentDescription = stringResource(standaloneSearch.titleId),
                        tint = searchTint,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                scaleX = searchScale
                                scaleY = searchScale
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmberRail(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(26.dp)
    val glassBackdrop = LocalGlassBackdrop.current
    val pageBackdrop = glassBackdrop ?: LocalAppBackdrop.current
    val glassActive = LocalLiquidGlassEnabled.current && isLiquidGlassSupported && pageBackdrop !== EmptyBackdrop

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 10.dp, end = 6.dp, top = 16.dp, bottom = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(58.dp)
                .then(
                    if (glassActive) {
                        Modifier.nestLiquidGlass(shape = shape, backdrop = pageBackdrop, outerShadow = true)
                    } else {
                        Modifier
                            .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.45f))
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    },
                )
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            navigationItems.forEach { screen ->
                val isSelected = remember(currentRoute, screen.route) {
                    isRouteSelected(currentRoute, screen.route, navigationItems)
                }
                val currentIsSelected by rememberUpdatedState(isSelected)
                val iconRes = if (isSelected) screen.iconIdActive else screen.iconIdInactive
                val isSearchItem = screen == Screens.Search && onSearchLongClick != null
                val interactionSource = rememberNavPressSource(
                    handleClick = isSearchItem,
                    onClick = { onItemClick(screen, currentIsSelected) },
                    onLongClick = onSearchLongClick,
                )
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (isSelected) Modifier.background(NestSunsetBrushHorizontal) else Modifier,
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = {
                                if (!isSearchItem) onItemClick(screen, currentIsSelected)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = stringResource(screen.titleId),
                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigationRail(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
) {
    if (useNestUi() || LocalLiquidGlassEnabled.current) {
        EmberRail(
            navigationItems = navigationItems,
            currentRoute = currentRoute,
            onItemClick = onItemClick,
            onSearchLongClick = onSearchLongClick,
            modifier = modifier,
        )
    } else {
        LegacyAppNavigationRail(
            navigationItems = navigationItems,
            currentRoute = currentRoute,
            onItemClick = onItemClick,
            modifier = modifier,
            pureBlack = pureBlack,
            onSearchLongClick = onSearchLongClick,
        )
    }
}

@Composable
private fun LegacyAppNavigationRail(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
) {
    val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    NavigationRail(
        modifier = modifier,
        containerColor = containerColor,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        navigationItems.forEach { screen ->
            val isSelected = remember(currentRoute, screen.route) {
                isRouteSelected(currentRoute, screen.route, navigationItems)
            }
            val currentIsSelected by rememberUpdatedState(isSelected)
            val iconRes = remember(isSelected, screen) {
                if (isSelected) screen.iconIdActive else screen.iconIdInactive
            }

            val isSearchItem = screen == Screens.Search && onSearchLongClick != null
            val interactionSource = remember { MutableInteractionSource() }

            if (isSearchItem) {
                LaunchedEffect(interactionSource) {
                    var isLongClick = false
                    interactionSource.interactions.collectLatest { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> {
                                isLongClick = false
                                delay(viewConfiguration.longPressTimeoutMillis)
                                isLongClick = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSearchLongClick.invoke()
                            }
                            is PressInteraction.Release -> {
                                if (!isLongClick) {
                                    onItemClick(screen, currentIsSelected)
                                }
                            }
                            is PressInteraction.Cancel -> {
                                isLongClick = false
                            }
                        }
                    }
                }
            }

            NavigationRailItem(
                selected = isSelected,
                onClick = {
                    if (!isSearchItem) {
                        onItemClick(screen, currentIsSelected)
                    }
                },
                interactionSource = interactionSource,
                icon = {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = stringResource(screen.titleId),
                    )
                },
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun AppNavigationBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    slimNav: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
) {
    if (useNestUi() || LocalLiquidGlassEnabled.current) {
        NestMusicGlassNavBar(
            navigationItems = navigationItems,
            currentRoute = currentRoute,
            onItemClick = onItemClick,
            onSearchLongClick = onSearchLongClick,
            slimNav = slimNav,
            pureBlack = pureBlack,
            modifier = modifier,
        )
    } else {
        LegacyAppNavigationBar(
            navigationItems = navigationItems,
            currentRoute = currentRoute,
            onItemClick = onItemClick,
            modifier = modifier,
            pureBlack = pureBlack,
            slimNav = slimNav,
            onSearchLongClick = onSearchLongClick,
        )
    }
}

@Composable
private fun LegacyAppNavigationBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    slimNav: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
) {
    val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    NavigationBar(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        navigationItems.forEach { screen ->
            val isSelected = remember(currentRoute, screen.route) {
                isRouteSelected(currentRoute, screen.route, navigationItems)
            }
            val currentIsSelected by rememberUpdatedState(isSelected)
            val iconRes = remember(isSelected, screen) {
                if (isSelected) screen.iconIdActive else screen.iconIdInactive
            }

            val isSearchItem = screen == Screens.Search && onSearchLongClick != null
            val interactionSource = remember { MutableInteractionSource() }

            if (isSearchItem) {
                LaunchedEffect(interactionSource) {
                    var isLongClick = false
                    interactionSource.interactions.collectLatest { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> {
                                isLongClick = false
                                delay(viewConfiguration.longPressTimeoutMillis)
                                isLongClick = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSearchLongClick.invoke()
                            }
                            is PressInteraction.Release -> {
                                if (!isLongClick) {
                                    onItemClick(screen, currentIsSelected)
                                }
                            }
                            is PressInteraction.Cancel -> {
                                isLongClick = false
                            }
                        }
                    }
                }
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (!isSearchItem) {
                        onItemClick(screen, currentIsSelected)
                    }
                },
                interactionSource = interactionSource,
                icon = {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = stringResource(screen.titleId),
                    )
                },
                label = if (!slimNav) {
                    {
                        Text(
                            text = stringResource(screen.titleId),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}
