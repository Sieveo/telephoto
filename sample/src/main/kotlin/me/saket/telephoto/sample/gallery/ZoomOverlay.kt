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
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.round
import kotlinx.coroutines.flow.collectLatest
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

// todo:
//  - this blocks click events
//  - try converting this into a modifier.
//  - settling animation does not resume if it's interrupted by a tap.
@Composable
fun ZoomOverlay(
  modifier: Modifier = Modifier,
  state: ZoomableState = rememberZoomableState(
    zoomSpec = ZoomSpec(
      maxZoomFactor = 1f,
      preventOverOrUnderZoom = false,
    ),
  ),
  overlayDecoration: ZoomOverlayDecoration = ZoomOverlayDecoration.Default,
  content: @Composable () -> Unit,
) {
  check(state.zoomSpec.maxZoomFactor == 1f) {
    "The max zoom factor must be 1f to ensure the overlay resets when a zoom gesture is released."
  }

  val isZoomedIn by remember {
    derivedStateOf {
      state.contentTransformation.scaleMetadata.userZoom > 1f
    }
  }

  val graphicsLayer = rememberGraphicsLayer()
  var coordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
  var invalidationTrigger by remember { mutableIntStateOf(0) }

  Box(
    Modifier
      .drawWithContent {
        graphicsLayer.record {
          this@drawWithContent.drawContent()
          invalidationTrigger++
        }
        if (!isZoomedIn) {
          drawLayer(graphicsLayer)
        }
      }
      .onPlaced { coordinates = it }
      .zoomable(
        state = state,
        clipToBounds = false,
        onDoubleClick = { _, _ -> }, // todo: make this nullable.
      )
      .then(modifier),
    propagateMinConstraints = true,
  ) {
    content()
  }

  if (isZoomedIn) {
    val overlay = rememberDecorOverlay()
    val boundsInWindow = remember { coordinates!!.boundsInWindow() }
    overlay.setContent {
      Box(Modifier.fillMaxSize()) {
        overlayDecoration.Decorate(state) {
          val density = LocalDensity.current
          Canvas(
            Modifier
              .size(
                width = density.run { boundsInWindow.width.toDp() },
                height = density.run { boundsInWindow.height.toDp() },
              )
              .offset { boundsInWindow.topLeft.round() }
          ) {
            @Suppress("UNUSED_EXPRESSION")  // https://issuetracker.google.com/issues/386671285
            invalidationTrigger

            drawLayer(graphicsLayer)
          }
        }
      }
    }
  }
}

@Stable
fun interface ZoomOverlayDecoration {
  @Composable
  fun Decorate(state: ZoomableState, innerContent: @Composable () -> Unit)

  companion object {
    val Default = ZoomOverlayDecoration { state, innerContent ->
      val animatedAlpha = remember { Animatable(initialValue = 0f) }
      LaunchedEffect(state) {
        snapshotFlow { state.isAnimationRunning }.collectLatest { isSettling ->
          animatedAlpha.animateTo(
            targetValue = if (isSettling) 0f else 0.4f,
            animationSpec = if (isSettling) ZoomableState.DefaultZoomAnimationSpec else tween(600),
          )
        }
      }

      Box(
        Modifier
          .fillMaxSize()
          .drawBehind {
            drawRect(Color.Black, alpha = animatedAlpha.value)
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
