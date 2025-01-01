@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.saket.telephoto.zoomable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onPlaced
import kotlinx.coroutines.flow.collectLatest
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.pinchToZoomable

// todo:
//  - settling animation does not resume if it's interrupted by a tap.
//  - see TODOs in RealZoomableOverlaidPeekState.
//  - test:
//    - content is clickable
//    - scroll a list of zoomable items (regression test for undelegation of nodes).
//    - content inside zoomed overlay can update
//    - does not crash when software acceleration is disabled
/**
 * Adds a short-lived, overlaid zoom effect reminiscent of Instagram's "peek" feature.
 * The content zooms in while the user interacts with it and, unlike [Modifier.zoomable][me.saket.telephoto.zoomable], automatically returns
 * to its normal state once the gesture is released.
 */
fun Modifier.zoomableOverlaidPeek(
  state: ZoomableOverlaidPeekState,
  overlayDecoration: ZoomableOverlaidPeekDecoration = ZoomableOverlaidPeekDecoration.scrim(),
): Modifier {
  check(state is RealZoomableOverlaidPeekState)
  state.overlayDecoration = overlayDecoration
  if (state.graphicsLayer == null) {
    return this
  }
  return this
    .drawWithContent {
      state.graphicsLayer.record {
        this@drawWithContent.drawContent()
      }
      if (!state.isZoomedIn) {
        drawLayer(state.graphicsLayer)
      }
    }
    .onPlaced { state.coordinates = it }
    .pinchToZoomable(
      state = state.zoomableState,
      clipToBounds = false,
    )
}

/**
 * Draws decoration around the zoomed content where [Modifier.zoomableOverlaidPeek] is used.
 * Inspired by [TextFieldDecorator].
 * */
@Stable
fun interface ZoomableOverlaidPeekDecoration {
  /**
   * To allow you to control the placement of the inner (zoomed) content relative to your decorations,
   * [Modifier.zoomableOverlaidPeek] will pass in [innerContent] to this method. You must not call
   * [innerContent] more than once.
   * */
  @Composable
  fun Decorate(state: ZoomableOverlaidPeekState, innerContent: @Composable () -> Unit)

  companion object {
    /** Draws a scrim behind the zoomed content. */
    @Stable
    fun scrim(
      color: Color = Color.Black.copy(alpha = 0.4f)
    ): ZoomableOverlaidPeekDecoration = Scrim(color)
  }

  private data class Scrim(val color: Color) : ZoomableOverlaidPeekDecoration {
    @Composable
    override fun Decorate(state: ZoomableOverlaidPeekState, innerContent: @Composable () -> Unit) {
      val animatedAlpha = remember { Animatable(initialValue = 0f) }
      LaunchedEffect(state) {
        snapshotFlow { state.zoomableState.isAnimationRunning }.collectLatest { isSettling ->
          animatedAlpha.animateTo(
            targetValue = if (isSettling) 0f else 1f,
            animationSpec = if (isSettling) ZoomableState.DefaultSettleAnimationSpec else tween(600),
          )
        }
      }
      Box(
        Modifier
          .fillMaxSize()
          .drawBehind {
            drawRect(color, alpha = animatedAlpha.value)
          }
      ) {
        innerContent()
      }
    }
  }
}

