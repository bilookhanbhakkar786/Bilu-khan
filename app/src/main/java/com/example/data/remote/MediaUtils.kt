package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object MediaUtils {
    /**
     * Resizes and JPEG-compresses a picked image URI into a compact Base64 Data URI
     * suitable for storing directly in Firestore documents without external Storage setup.
     */
    suspend fun encodeUriToDataUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = 560,
        quality: Int = 72
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalStateException("Could not read selected image."))

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val origW = bounds.outWidth
            val origH = bounds.outHeight
            if (origW <= 0 || origH <= 0) {
                return@withContext Result.failure(IllegalStateException("Unsupported image format."))
            }

            var sampleSize = 1
            while ((origW / sampleSize) > maxDimension * 2 || (origH / sampleSize) > maxDimension * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                ?: return@withContext Result.failure(IllegalStateException("Failed to decode selected image."))

            val largest = max(decoded.width, decoded.height)
            val scaled = if (largest > maxDimension) {
                val scale = maxDimension.toFloat() / largest.toFloat()
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).roundToInt().coerceAtLeast(1),
                    (decoded.height * scale).roundToInt().coerceAtLeast(1),
                    true
                ).also {
                    if (it != decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            val out = ByteArrayOutputStream()
            var q = quality
            scaled.compress(Bitmap.CompressFormat.JPEG, q, out)
            // Ensure encoded base64 fits well inside Firestore security rule limit (< 300 KB)
            while (out.size() > 210_000 && q > 35) {
                out.reset()
                q -= 10
                scaled.compress(Bitmap.CompressFormat.JPEG, q, out)
            }
            scaled.recycle()

            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            Result.success("data:image/jpeg;base64,$b64")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Custom Coil Fetcher enabling AsyncImage(model = "data:image/jpeg;base64,...") across the entire app.
 */
class DataUriFetcher(
    private val uri: Uri,
    private val context: Context
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val raw = uri.toString()
        val commaIndex = raw.indexOf(',')
        require(commaIndex > 0) { "Invalid data URI" }
        val header = raw.substring(5, commaIndex)
        val mimeType = header.substringBefore(';').ifBlank { "image/jpeg" }
        val payload = raw.substring(commaIndex + 1)
        val bytes = Base64.decode(payload, Base64.DEFAULT)
        val buffer = Buffer().write(bytes)
        return SourceResult(
            source = ImageSource(source = buffer, context = context),
            mimeType = mimeType,
            dataSource = DataSource.MEMORY
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.scheme.equals("data", ignoreCase = true)) {
                DataUriFetcher(data, options.context)
            } else {
                null
            }
        }
    }
}
