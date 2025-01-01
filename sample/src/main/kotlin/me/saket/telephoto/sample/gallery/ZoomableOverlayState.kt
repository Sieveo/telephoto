package me.saket.telephoto.sample.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalView
import me.saket.telephoto.zoomable.HardwareShortcutsSpec
import me.saket.telephoto.zoomable.OverzoomEffect
import me.saket.telephoto.zoomable.ZoomLimit
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.rememberZoomableState

/**
 * Create a [ZoomableOverlayState] that can be used with
 * [Modifier.overlayZoomable][overlayZoomable].
 */
@Composable
fun rememberZoomableOverlayState(): ZoomableOverlayState {
  val zoomableState = rememberZoomableState(
    zoomSpec = ZoomSpec(
      maximum = ZoomLimit(factor = 1f, overzoomEffect = OverzoomEffect.NoLimits),
      minimum = ZoomLimit(factor = 1f, overzoomEffect = OverzoomEffect.RubberBanding),
    ),
    hardwareShortcutsSpec = HardwareShortcutsSpec.Disabled,
  )
  val graphicsLayer = if (LocalView.current.isHardwareAccelerated) {
    rememberGraphicsLayer()
  } else {
    null  // GraphicsLayer does not support SW acceleration.
  }
  return remember(zoomableState, graphicsLayer) {
    RealZoomableOverlayState(
      zoomableState = zoomableState,
      graphicsLayer = graphicsLayer,
    )
  }.also {
    it.DisplayOverlayEffect()
  }
}

/** State class for [Modifier.overlayZoomable][overlayZoomable]. */
sealed interface ZoomableOverlayState {
  val zoomableState: ZoomableState

  val isZoomedIn: Boolean
}
