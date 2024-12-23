package me.saket.telephoto.subsamplingimage.internal

import android.content.Context
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import dev.drewhamilton.poko.Poko
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * An image decoder, responsible for loading partial regions for
 * [SubSamplingImage][me.saket.telephoto.subsamplingimage.SubSamplingImage]'s tiles.
 *
 * Also see: [AndroidImageRegionDecoder] and [PooledAndroidImageRegionDecoder].
 */
interface ImageRegionDecoder {
  /** Raw size of the image, without any scaling applied. */
  val imageSize: IntSize

  /**
   * Decodes a specific region of the image with sub-sampling to downsize it.
   *
   * This is designed to be called concurrently for all visible regions of the image.
   * Implementations must handle synchronization if needed.
   */
  suspend fun decodeRegion(region: IntRect, sampleSize: Int): DecodeResult

  /** Called when the image is no longer visible. */
  fun close() = Unit

  fun interface Factory {
    suspend fun create(params: FactoryParams): ImageRegionDecoder
  }

  @Poko
  class DecodeResult(
    val painter: Painter,
    val hasUltraHdrContent: Boolean,
  )

  @Poko
  class FactoryParams(
    val context: Context,
    val imageOptions: ImageBitmapOptions,
    val extras: Map<KClass<*>, Any> = emptyMap(),
  ) {
    /** Returns extra factory params of type [type], or null if no such value is held. */
    fun <T : Any> extra(type: KClass<out T>): T? {
      return extras[type]?.let(type::cast)
    }
  }
}
