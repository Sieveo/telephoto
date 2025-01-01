package me.saket.telephoto.sample.gallery

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.round
import me.saket.telephoto.zoomable.ZoomableState

@Stable
internal class RealZoomableOverlaidPeekState(
  override val zoomableState: ZoomableState,
  val graphicsLayer: GraphicsLayer?
) : ZoomableOverlaidPeekState {

  var coordinates: LayoutCoordinates? by mutableStateOf(null)
  lateinit var overlayDecoration: ZoomableOverlaidPeekDecoration

  override val isZoomedIn: Boolean by derivedStateOf {
    zoomableState.contentTransformation.scaleMetadata.userZoom > 1f
  }

  @Composable
  internal fun DisplayOverlayEffect() {
    val graphicsLayer = graphicsLayer
    if (isZoomedIn && graphicsLayer != null) {
      rememberDecorOverlay().setContent {
        // todo: the content can scroll while the reset zoom animation is playing.
        //  should this recalculate the bounds on every coordinate update?
        val boundsInWindow = remember {
          // todo: this crashes if the coordinates aren't calculated yet.
          coordinates!!.boundsInWindow()
        }

        Box(Modifier.fillMaxSize()) {
          overlayDecoration.Decorate(state = this@RealZoomableOverlaidPeekState) {
            val density = LocalDensity.current
            Canvas(
              Modifier
                .size(
                  width = density.run { boundsInWindow.width.toDp() },
                  height = density.run { boundsInWindow.height.toDp() },
                )
                .offset { boundsInWindow.topLeft.round() }
            ) {
              drawLayer(graphicsLayer)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun rememberDecorOverlay(): ComposeView {
  val context = LocalContext.current
  val decorView = remember(context) {
    context.findActivity().window.decorView as ViewGroup
  }

  // Note to self: other alternatives considered:
  // - Dialog: animation weren't very smooth for some reason.
  // - ViewGroupOverlay: invalidations/redraws were challenging.
  val parentComposition = rememberCompositionContext()
  return remember {
    object : RememberObserver {
      val overlayView = ComposeView(decorView.context).also {
        it.setParentCompositionContext(parentComposition)
        it.layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )
      }

      override fun onRemembered() = decorView.addView(overlayView)
      override fun onForgotten() = decorView.removeView(overlayView)
      override fun onAbandoned() = onForgotten()
    }
  }.overlayView
}

// todo: delete this when LocalActivity is available in androidx.activity.
private tailrec fun Context.findActivity(): Activity {
  return when (this) {
    is Activity -> this
    is ContextWrapper -> this.baseContext.findActivity()
    else -> throw IllegalArgumentException("Could not find activity!")
  }
}
