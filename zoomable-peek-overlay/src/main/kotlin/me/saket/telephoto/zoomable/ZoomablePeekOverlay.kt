@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.saket.telephoto.zoomable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onPlaced
import kotlinx.coroutines.flow.collectLatest

/**
 * Adds a short-lived, overlaid zoom effect reminiscent of Instagram's "peek" feature.
 * The content zooms in while the user interacts with it and, unlike [Modifier.zoomable][me.saket.telephoto.zoomable],
 * automatically returns to its normal state once the gesture is released.
 */
fun Modifier.zoomablePeekOverlay(
  state: ZoomablePeekOverlayState,
  overlayDecoration: ZoomablePeekOverlayDecoration = ZoomablePeekOverlayDecoration.scrim(),
): Modifier {
  check(state is RealZoomablePeekOverlayState)
  state.overlayDecoration = overlayDecoration
  if (state.graphicsLayer == null) {
    return this
  }
  return this
    .drawWithContent {
      if (isCanvasHardwareAccelerated) {
        state.graphicsLayer.record {
          this@drawWithContent.drawContent()
        }
        if (!state.isZoomedIn) {
          drawLayer(state.graphicsLayer)
        }
      } else {
        drawContent()
      }
    }
    .onPlaced { state.coordinates = it }
    .pinchToZoomable(
      state = state.zoomableState,
      clipToBounds = false,
    )
}

/** Draws decoration behind the zoomed content where [Modifier.zoomablePeekOverlay] is used. */
@Stable
fun interface ZoomablePeekOverlayDecoration {
  /**
   * This is intended for drawing decorations *behind* the content. To control the appearance of your
   * content itself during zoom gestures, apply effects directly to the content instead:
   *
   * ```kotlin
   * val state = rememberZoomableOverlayState()
   * val cornerSize by animateDpAsState(if (state.isZoomedIn) 8.dp else 0.dp)
   *
   * Box(
   *  Modifier
   *    .zoomablePeekOverlay(state)
   *    .clip(RoundedCornerShape(cornerSize))
   * )
   * ```
   */
  @Composable
  fun Decorate(state: ZoomablePeekOverlayState)

  companion object {
    /** Draws a scrim behind the zoomed content. */
    @Stable
    fun scrim(
      color: Color = Color.Black.copy(alpha = 0.4f)
    ): ZoomablePeekOverlayDecoration = Scrim(color)
  }

  private data class Scrim(val color: Color) : ZoomablePeekOverlayDecoration {
    @Composable
    override fun Decorate(state: ZoomablePeekOverlayState) {
      val animatedAlpha = remember { Animatable(initialValue = 0f) }
      LaunchedEffect(state) {
        snapshotFlow { state.zoomableState.isAnimationRunning }.collectLatest { isSettling ->
          animatedAlpha.animateTo(
            targetValue = if (isSettling) 0f else 1f,
            animationSpec = if (isSettling) ZoomableState.DefaultSettleAnimationSpec else tween(600),
          )
        }
      }
      Canvas(Modifier.fillMaxSize()) {
        drawRect(color, alpha = animatedAlpha.value)
      }
    }
  }
}

internal val DrawScope.isCanvasHardwareAccelerated: Boolean
  get() = drawContext.canvas.nativeCanvas.isHardwareAccelerated
