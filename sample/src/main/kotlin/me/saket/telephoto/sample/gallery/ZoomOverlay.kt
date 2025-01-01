@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.saket.telephoto.sample.gallery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.collectLatest
import me.saket.telephoto.zoomable.HardwareShortcutsSpec
import me.saket.telephoto.zoomable.OverzoomEffect
import me.saket.telephoto.zoomable.ZoomLimit
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.pinchToZoomable
import me.saket.telephoto.zoomable.rememberZoomableState

// todo:
//  - move to a library module
//  - settling animation does not resume if it's interrupted by a tap.
//  - see TODOs in RealZoomableOverlayState.
//  - test:
//    - content is clickable
//    - scroll a list of zoomable items (regression test for undelegation of nodes).
//    - content inside zoomed overlay can update
//    - does not crash when software acceleration is disabled
fun Modifier.overlayZoomable(
  state: ZoomableOverlayState,
  overlayDecoration: ZoomOverlayDecoration = ZoomOverlayDecoration.scrim(),
): Modifier {
  check(state is RealZoomableOverlayState)
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

// todo: review name.
@Stable
fun interface ZoomOverlayDecoration {

  // todo: doc.
  @Composable
  fun Decorate(state: ZoomableOverlayState, innerContent: @Composable () -> Unit)

  companion object {
    @Stable
    fun scrim(
      color: Color = Color.Black.copy(alpha = 0.4f)
    ): ZoomOverlayDecoration = Scrim(color)
  }

  private data class Scrim(val color: Color) : ZoomOverlayDecoration {
    @Composable
    override fun Decorate(state: ZoomableOverlayState, innerContent: @Composable () -> Unit) {
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
