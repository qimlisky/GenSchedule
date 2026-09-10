package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.app.ui.AddMenuAction
import com.xiaomanjun.sleepdownschedule.app.ui.HomeMode
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.feature.home.week.WeekCourseOverlayCardContent
import com.xiaomanjun.sleepdownschedule.glass.ui.CourseGlassCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class CourseShortcutRequest(
    val course: CourseEntity,
    val week: Int,
    val bounds: Rect,
    val cornerPx: Float,
    val enterEditMode: () -> Unit
)

internal val LocalCourseShortcuts = staticCompositionLocalOf<CourseShortcutController?> { null }

@Stable
internal class CourseShortcutController(private val scope: CoroutineScope) {
    var request by mutableStateOf<CourseShortcutRequest?>(null)
        private set
    var copyRequest by mutableStateOf<CourseShortcutRequest?>(null)
    var closing by mutableStateOf(false)
        private set
    val progress = Animatable(0f)
    val cardScale = Animatable(1f)
    private var motion: Job? = null

    fun open(value: CourseShortcutRequest) {
        motion?.cancel()
        request = value
        closing = false
        motion = scope.launch {
            progress.snapTo(0f)
            cardScale.snapTo(1f)
            launch {
                cardScale.animateTo(1.015f, keyframes {
                    durationMillis = 300
                    1f at 0
                    0.98f at 85 using FastOutSlowInEasing
                    1.02f at 220 using FastOutSlowInEasing
                    1.015f at 300
                })
            }
            progress.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 380f))
        }
    }

    fun close(afterClose: (() -> Unit)? = null) {
        if (request == null || closing) return
        closing = true
        motion?.cancel()
        motion = scope.launch {
            launch { cardScale.animateTo(1f, tween(180, easing = FastOutSlowInEasing)) }
            progress.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
            request = null
            closing = false
            afterClose?.invoke()
        }
    }

    // The source pointer owns the whole gesture. Retire the menu without waiting before
    // handing that pointer to the existing week drag controller.
    fun takeOverDrag() {
        motion?.cancel()
        request = null
        closing = false
        motion = scope.launch {
            progress.animateTo(0f, tween(100))
            cardScale.snapTo(1f)
        }
    }

    fun reset() {
        motion?.cancel()
        request = null
        copyRequest = null
        closing = false
        motion = scope.launch {
            progress.snapTo(0f)
            cardScale.snapTo(1f)
        }
    }
}

internal data class CourseShortcutPlacement(val bounds: Rect, val pivotX: Float)

/** Physical screen thirds, deliberately independent of weekday count and layout direction. */
internal fun courseShortcutPlacement(anchor: Rect, available: Rect, width: Float, height: Float, gap: Float): CourseShortcutPlacement {
    val pivot = when {
        anchor.center.x < available.left + available.width / 3f -> 0f
        anchor.center.x > available.right - available.width / 3f -> 1f
        else -> 0.5f
    }
    val w = width.coerceAtMost(available.width).coerceAtLeast(1f)
    val h = height.coerceAtMost(available.height).coerceAtLeast(1f)
    val x = (anchor.left + anchor.width * pivot - w * pivot)
        .coerceIn(available.left, (available.right - w).coerceAtLeast(available.left))
    val y = (anchor.top - h - gap)
        .coerceIn(available.top, (available.bottom - h).coerceAtLeast(available.top))
    return CourseShortcutPlacement(Rect(x, y, x + w, y + h), pivot)
}

internal fun copiedShortcutCourse(course: CourseEntity, singleWeek: Int?): CourseEntity = course.copy(
    id = 0,
    weeks = singleWeek?.let(::listOf) ?: course.weeks,
    weekParity = if (singleWeek != null) WeekParity.ALL else course.weekParity
)

/** Root popup sibling of the complete Home underlay; neither card nor menu samples itself. */
@Composable
internal fun CourseShortcutOverlay(
    controller: CourseShortcutController,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    cardBackdrop: Backdrop?,
    onCopy: (CourseEntity) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.reset()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.reset()
        }
    }
    val request = controller.request
    BackHandler(enabled = request != null) { controller.close() }
    if (request != null) {
        val density = LocalDensity.current
        val haptic = LocalHapticFeedback.current
        val safe = WindowInsets.safeDrawing
        var hostBounds by remember { mutableStateOf<Rect?>(null) }
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .onGloballyPositioned { hostBounds = it.boundsInRoot() }
                .pointerInput(controller) { detectTapGestures { controller.close() } }
        ) {
            val host = hostBounds ?: return@BoxWithConstraints
            val available = with(density) {
                Rect(
                    safe.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr).toFloat() + 8.dp.toPx(),
                    safe.getTop(density).toFloat() + 8.dp.toPx(),
                    maxWidth.toPx() - safe.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr) - 8.dp.toPx(),
                    maxHeight.toPx() - safe.getBottom(density) - 8.dp.toPx()
                )
            }
            val source = request.bounds.translate(-host.topLeft)
            val rowHeight = 48.dp * density.fontScale.coerceAtLeast(1f)
            val placement = with(density) {
                val readableWidth = (60.dp + 28.dp * density.fontScale.coerceAtLeast(1f)).toPx()
                courseShortcutPlacement(source, available, maxOf(source.width + 24.dp.toPx(), readableWidth),
                    (rowHeight * 2f + 14.dp).toPx(), 10.dp.toPx())
            }
            Box(
                Modifier.offset { IntOffset(source.left.roundToInt(), source.top.roundToInt()) }
                    .size(with(density) { source.width.toDp() }, with(density) { source.height.toDp() })
                    .graphicsLayer {
                        scaleX = controller.cardScale.value
                        scaleY = controller.cardScale.value
                        translationY = (1f - controller.cardScale.value) * with(density) { 100.dp.toPx() }
                    }
            ) {
                CourseGlassCard(
                    backdrop = cardBackdrop, config = config, course = request.course,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedRectangle(with(density) { request.cornerPx.toDp() }), onClick = null
                ) { WeekCourseOverlayCardContent(request.course, config) }
            }
            val actions = remember(request, controller) {
                listOf(
                    AddMenuAction(R.drawable.ic_edit, "编辑") {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        controller.close(request.enterEditMode)
                    },
                    AddMenuAction(R.drawable.ic_add_course, "复制") {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        controller.close { controller.copyRequest = request }
                    }
                )
            }
            val target = placement.bounds
            HomeAddMenuMorphPanel(
                backdrop = backdrop, config = config, actions = actions,
                homeMode = HomeMode.Week, onHomeModeChange = {},
                targetSizeProvider = { IntSize(target.width.roundToInt(), target.height.roundToInt()) },
                surfaceAlphaProvider = { controller.progress.value.coerceIn(0f, 1f) },
                contentAlphaProvider = { controller.progress.value.coerceIn(0f, 1f) },
                interactive = !controller.closing,
                // Inner capsule radius = (rowHeight - 2dp) / 2, with an equal 8dp inset.
                shape = RoundedRectangle(rowHeight / 2 + 7.dp),
                showModeSwitch = false, actionItemHeight = rowHeight, compactActions = true,
                modifier = Modifier
                    .offset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
                    .size(with(density) { target.width.toDp() }, with(density) { target.height.toDp() })
                    .graphicsLayer {
                        val progress = controller.progress.value
                        transformOrigin = TransformOrigin(placement.pivotX, 1f)
                        scaleX = 0.18f + 0.82f * progress
                        scaleY = 0.18f + 0.82f * progress
                        translationX = (source.left + source.width * placement.pivotX -
                            target.left - target.width * placement.pivotX) * (1f - progress)
                        translationY = (source.top - target.bottom) * (1f - progress)
                        rotationZ = (placement.pivotX - 0.5f) * 6f * (1f - progress)
                    }
            )
        }
    }
    controller.copyRequest?.let { copy ->
        LiquidAlertDialog(
            title = "复制课程",
            message = "复制“${copy.course.name}”的全部上课周，还是仅复制第${copy.week}周？下一步重新选择星期和时间，保存后创建副本。",
            actions = listOf(
                LiquidAlertAction("所有上课周", LiquidAlertActionStyle.Primary) {
                    controller.copyRequest = null
                    onCopy(copiedShortcutCourse(copy.course, null))
                },
                LiquidAlertAction("仅第${copy.week}周", LiquidAlertActionStyle.Secondary) {
                    controller.copyRequest = null
                    onCopy(copiedShortcutCourse(copy.course, copy.week))
                },
                LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary) { controller.copyRequest = null }
            ),
            onDismissRequest = { controller.copyRequest = null },
            backdrop = backdrop, config = config
        )
    }
}
