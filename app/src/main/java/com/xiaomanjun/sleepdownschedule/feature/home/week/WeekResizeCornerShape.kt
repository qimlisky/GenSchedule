package com.xiaomanjun.sleepdownschedule.feature.home.week

import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle

/** A short glass stroke cut from the card's actual continuous outline, not a separate button. */
internal class WeekResizeCornerShape(
    private val cardSize: DpSize,
    private val cardCorner: Dp,
    private val badgeSize: Dp,
    topStart: CornerSize = CornerSize(0.dp),
    topEnd: CornerSize = CornerSize(0.dp),
    bottomEnd: CornerSize = CornerSize(cardCorner),
    bottomStart: CornerSize = CornerSize(0.dp)
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    override fun createOutline(
        size: Size, topStart: Float, topEnd: Float, bottomEnd: Float, bottomStart: Float,
        layoutDirection: LayoutDirection
    ): Outline {
        val scale = size.width / badgeSize.value
        val cardPixels = Size(cardSize.width.value * scale, cardSize.height.value * scale)
        val outline = RoundedRectangle((cardCorner.value * scale).dp)
            .createOutline(cardPixels, layoutDirection, Density(1f))
        val cardPath = androidx.compose.ui.graphics.Path().apply { addOutline(outline) }.asAndroidPath()
        val stroke = Path()
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f * scale
            strokeJoin = Paint.Join.ROUND
        }.getFillPath(cardPath, stroke)
        // The 44dp input host is offset by 4dp. Cancel that offset so the material hugs the
        // source outline at every corner setting and at every step of a resize gesture.
        stroke.offset(size.width - cardPixels.width - 4f * scale, size.height - cardPixels.height - 4f * scale)
        stroke.op(Path().apply { addRect(0f, 0f, size.width, size.height, Path.Direction.CW) }, Path.Op.INTERSECT)
        val rounded = Path()
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            pathEffect = CornerPathEffect(4f * scale)
        }.getFillPath(stroke, rounded)
        return Outline.Generic(rounded.asComposePath())
    }

    override fun copy(
        topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize
    ): CornerBasedShape = WeekResizeCornerShape(cardSize, cardCorner, badgeSize, topStart, topEnd, bottomEnd, bottomStart)
}
