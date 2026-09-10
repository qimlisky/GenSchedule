package com.xiaomanjun.sleepdownschedule.feature.home.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.model.AppState
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalCenteredDialogSceneBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.centeredDialogSceneProducer
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.rememberCenteredDialogVisuals
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.rememberCenteredDialogSceneBackdrop
import com.xiaomanjun.sleepdownschedule.feature.course.editor.NormalizedCourseEditorScreen
import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/** A new draft has no card to morph back into. Use the shared centered dialog lifecycle. */
@Composable
internal fun CopiedCourseEditorOverlay(
    show: Boolean,
    draft: CourseEntity,
    state: AppState,
    backdrop: Backdrop?,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onSave: (List<CourseEntity>) -> Unit
) {
    val width = (LocalWindowInfo.current.containerDpSize.width - 40.dp).coerceAtMost(440.dp)
    val visuals = rememberCenteredDialogVisuals(backdrop, state.config, 16.dp, maxWidth = width)
    val pickerScene = rememberCenteredDialogSceneBackdrop("copied-course-editor-pickers")
    OverlayDialog(
        show = show,
        modifier = Modifier.widthIn(min = width, max = width),
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        backgroundColor = Color.Transparent,
        enableWindowDim = false,
        forceCenter = true,
        insideMargin = DpSize(0.dp, 0.dp),
        outsideMargin = DpSize(20.dp, 20.dp),
        surfaceModifier = visuals.surfaceModifier,
        backgroundModifier = visuals.backgroundModifier,
        animationProgressState = visuals.animationProgress,
        enablePredictiveBackAnimation = false,
        excludeFromBackdropCapture = true
    ) {
        CompositionLocalProvider(LocalCenteredDialogSceneBackdrop provides pickerScene) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                underlayModifier = Modifier.centeredDialogSceneProducer(pickerScene),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                Box(Modifier.fillMaxSize()) {
                    NormalizedCourseEditorScreen(
                        state = state,
                        initialCourse = null,
                        copyDraft = draft,
                        onCancel = onDismissRequest,
                        onSave = { onSave(listOf(it)) },
                        onSaveCourses = onSave,
                        onDelete = {},
                        backdrop = backdrop,
                        pickerRenderInRootScaffold = false
                    )
                }
            }
        }
    }
}
