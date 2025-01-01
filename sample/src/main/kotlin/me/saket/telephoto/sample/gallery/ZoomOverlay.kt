@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package me.saket.telephoto.sample.gallery

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.round
import kotlinx.coroutines.flow.collectLatest
import me.saket.telephoto.zoomable.HardwareShortcutsSpec
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.pinchToZoomable

// todo:
//  - move to a library module
//  - settling animation does not resume if it's interrupted by a tap.
//  - haptic feedback
//    - when zoom starts
//    - when zoom ends (already works)
//  - prevent under zoom
//  - test:
//    - content is clickable
fun Modifier.overlayZoomable(
  state: ZoomableOverlayState,
  overlayDecoration: ZoomOverlayDecoration = ZoomOverlayDecoration.scrim(),
): Modifier {
  check(state is RealZoomableOverlayState)
  state.overlayDecoration = overlayDecoration

  return this
    .drawWithContent {
      state.graphicsLayer.record {
        this@drawWithContent.drawContent()
        state.invalidationTrigger++ // todo: this reads the state as well instead of only updating it.
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

// todo: doc.
@Composable
fun rememberZoomableOverlayState(): ZoomableOverlayState {
  val zoomableState = rememberZoomableState(
    zoomSpec = ZoomSpec(
      maxZoomFactor = 1f,
      preventOverOrUnderZoom = false,
    ),
    hardwareShortcutsSpec = HardwareShortcutsSpec.Disabled,
  )
  val graphicsLayer = rememberGraphicsLayer()
  return remember(zoomableState, graphicsLayer) {
    RealZoomableOverlayState(
      zoomableState = zoomableState,
      graphicsLayer = graphicsLayer,
    )
  }.also {
    it.DisplayOverlayEffect()
  }
}

// todo: review name.
interface ZoomableOverlayState {
  val zoomableState: ZoomableState

  // todo: doc.
  val isZoomedIn: Boolean
}

@Stable
internal class RealZoomableOverlayState(
  override val zoomableState: ZoomableState,
  val graphicsLayer: GraphicsLayer
) : ZoomableOverlayState {

  var coordinates: LayoutCoordinates? by mutableStateOf(null)
  var invalidationTrigger by mutableIntStateOf(0)
  lateinit var overlayDecoration: ZoomOverlayDecoration

  override val isZoomedIn: Boolean by derivedStateOf {
    zoomableState.contentTransformation.scaleMetadata.userZoom > 1f
  }

  @Composable
  internal fun DisplayOverlayEffect() {
    if (isZoomedIn) {
      rememberDecorOverlay().setContent {
        // todo: the content can scroll while the reset zoom animation is playing.
        //  should this recalculate the bounds on every coordinate update?
        val boundsInWindow = remember {
          // todo: this crashes if the coordinates aren't calculated yet.
          coordinates!!.boundsInWindow()
        }

        Box(Modifier.fillMaxSize()) {
          overlayDecoration.Decorate(state = this@RealZoomableOverlayState) {
            val density = LocalDensity.current
            Canvas(
              Modifier
                .size(
                  width = density.run { boundsInWindow.width.toDp() },
                  height = density.run { boundsInWindow.height.toDp() },
                )
                .offset { boundsInWindow.topLeft.round() }
            ) {
              invalidationTrigger // https://issuetracker.google.com/issues/386671285
              drawLayer(graphicsLayer)
            }
          }
        }
      }
    }
  }
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
