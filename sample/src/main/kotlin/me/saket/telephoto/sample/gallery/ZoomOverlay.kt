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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
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
  zoomableState: ZoomableState = rememberZoomableState(
    zoomSpec = ZoomSpec(
      maxZoomFactor = 1f,
      preventOverOrUnderZoom = false,
    ),
  ),
  overlayDecoration: ZoomOverlayDecoration = ZoomOverlayDecoration.Default,
  content: @Composable () -> Unit,
) {
  val isZoomedIn by remember {
    derivedStateOf {
      zoomableState.contentTransformation.scaleMetadata.userZoom > 1f
    }
  }

  val graphicsLayer = rememberGraphicsLayer()
  var coordinates: LayoutCoordinates? by remember { mutableStateOf(null) }

  Box(
    Modifier
      .onPlaced { coordinates = it }
      .drawWithContent {
        graphicsLayer.record {
          this@drawWithContent.drawContent()
        }
        // todo: the content should not be drawn here if it's already being drawn in the overlay.
        //if (!isZoomedIn) {
        drawLayer(graphicsLayer)
        //}
      }
      .zoomable(zoomableState, clipToBounds = false)
      // todo: explain why this is not the first node?
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
        overlayDecoration.Decorate {
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

@Stable
fun interface ZoomOverlayDecoration {
  @Composable
  fun Decorate(innerContent: @Composable () -> Unit)

  companion object {
    val Default = ZoomOverlayDecoration { innerContent ->
      // todo: fade out when the content is released.
      val animatedAlpha = remember { Animatable(initialValue = 0f) }
      LaunchedEffect(Unit) {
        animatedAlpha.animateTo(
          targetValue = 0.5f,
          animationSpec = tween(600),
        )
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
