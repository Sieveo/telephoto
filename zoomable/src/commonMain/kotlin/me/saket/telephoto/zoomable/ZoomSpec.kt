package me.saket.telephoto.zoomable

import androidx.compose.runtime.Immutable
import dev.drewhamilton.poko.Poko

@Poko
@Immutable
class ZoomSpec(
  /**
   * The maximum zoom level as a percentage of the content's unscaled/original size.
   * For example, a factor of 2.0 allows zooming up to 200% of the original size.
   */
  val maximum: ZoomLimit = ZoomLimit(factor = 2f, overzoomEffect = OverzoomEffect.RubberBanding),

  /**
   * The minimum zoom level relative to the content's base scale.
   * The base scale is calculated using the content's original size and the
   * [content scale][ZoomableState.contentScale]. For example, a factor of 1.0 ensures
   * the content cannot be zoomed out beyond this base scale.
   */
  val minimum: ZoomLimit = ZoomLimit(factor = 1f, overzoomEffect = OverzoomEffect.RubberBanding),
) {
  constructor(
    maxZoomFactor: Float = 2f,
    minZoomFactor: Float = 1f,
    overzoomEffect: OverzoomEffect = OverzoomEffect.RubberBanding,
  ) : this(
    maximum = ZoomLimit(maxZoomFactor, overzoomEffect),
    minimum = ZoomLimit(minZoomFactor, overzoomEffect),
  )

  @Deprecated(
    message = "Use OverZoomEffect instead",
    replaceWith = ReplaceWith("ZoomSpec(maxZoomFactor, overzoomEffect = TODO())")
  )
  constructor(maxZoomFactor: Float, preventOverOrUnderZoom: Boolean) : this(
    maximum = ZoomLimit(
      factor = maxZoomFactor,
      overzoomEffect = if (preventOverOrUnderZoom) OverzoomEffect.RubberBanding else OverzoomEffect.NoLimits,
    )
  )

  @Deprecated("Use OverZoomEffect instead.")
  constructor(preventOverOrUnderZoom: Boolean) : this(
    maximum = ZoomLimit(
      factor = 2f,
      overzoomEffect = if (preventOverOrUnderZoom) OverzoomEffect.RubberBanding else OverzoomEffect.NoLimits,
    )
  )

  @Deprecated(
    message = "Use maximum.factor instead.",
    replaceWith = ReplaceWith("maximum.factor"),
  )
  val maxZoomFactor: Float
    get() = maximum.factor

  @Deprecated(
    message = "Use maximum.overzoomEffect instead.",
    replaceWith = ReplaceWith("maximum.overzoomEffect != OverZoomEffect.None"),
  )
  val preventOverOrUnderZoom: Boolean
    get() = maximum.overzoomEffect != OverzoomEffect.NoLimits

  internal val range = ZoomRange(
    maxZoomAsRatioOfSize = maximum.factor,
    minZoomAsRatioOfBaseZoom = minimum.factor,
  )
}

@Poko
@Immutable
class ZoomLimit(
  /**
   * The zoom limit as a percentage of the content size before [overzoomEffect] kicks in.
   * For example, a value of `3.0` indicates that the content can be zoomed in up to 300%
   * of its original size.
   */
  val factor: Float,
  val overzoomEffect: OverzoomEffect = OverzoomEffect.RubberBanding,
)

@Immutable
interface OverzoomEffect {
  /**
   * Applies a rubber banding effect to zoom gestures when content is zoomed beyond
   * its limit as a form of visual feedback that the content can't be zoomed any further.
   */
  data object RubberBanding : OverzoomEffect

  /**
   * Does not limit over/under zooms in any manner. Content will zoom in a free-form
   * manner even when it goes beyond its limit (until the gesture is released).
   */
  data object NoLimits : OverzoomEffect
}
