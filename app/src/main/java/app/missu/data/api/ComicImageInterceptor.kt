package app.missu.data.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.security.MessageDigest

class ComicImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val scrambleId = original.url.queryParameter(SCRAMBLE_PARAM)?.toLongOrNull()
        val request = original.newBuilder()
            .url(original.url.newBuilder().removeAllQueryParameters(SCRAMBLE_PARAM).build())
            .apply {
                if (original.header("Referer") == null) {
                    imageReferer(original.url.host)?.let { header("Referer", it) }
                }
            }
            .build()
        val response = chain.proceed(request)
        val reversedBlocks = "sr:1" in request.url.pathSegments
        if (!response.isSuccessful || (!reversedBlocks && scrambleId == null)) return response

        val image = response.body.use { BitmapFactory.decodeStream(it.byteStream()) }
            ?: return response.newBuilder().body("".toResponseBody("image/jpeg".toMediaType())).build()
        val result = if (reversedBlocks) {
            reverseBlocks(image, request.url.pathSegments.last().substringBeforeLast('.'))
        } else {
            joinJmic(image, request.url.encodedPath, scrambleId!!)
        }
        val body = Buffer().run {
            result.compress(Bitmap.CompressFormat.JPEG, 92, outputStream())
            asResponseBody("image/jpeg".toMediaType())
        }
        image.recycle()
        result.recycle()
        return response.newBuilder().body(body).build()
    }

    private fun reverseBlocks(image: Bitmap, filename: String): Bitmap {
        val result = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val blocks = filename.let {
            val digest = MessageDigest.getInstance("MD5").digest(Base64.decode(it, Base64.DEFAULT))
            (digest.last().toInt() and 0xff) % 10 + 5
        }
        val blockHeight = image.height / blocks
        var sourceY = blockHeight * (blocks - 1)
        var targetY = 0
        repeat(blocks) { index ->
            val height = if (index == 0) image.height - sourceY else blockHeight
            canvas.drawBitmap(
                image,
                Rect(0, sourceY, image.width, sourceY + height),
                Rect(0, targetY, image.width, targetY + height),
                null,
            )
            sourceY -= blockHeight
            targetY += height
        }
        return result
    }

    private fun joinJmic(image: Bitmap, path: String, scrambleId: Long): Bitmap {
        val aid = Regex("/media/photos/(\\d+)/").find(path)?.groupValues?.get(1)?.toLongOrNull()
            ?: return image.copy(Bitmap.Config.ARGB_8888, false)
        if (aid < scrambleId) return image.copy(Bitmap.Config.ARGB_8888, false)
        val filename = path.substringAfterLast('/').substringBeforeLast('.')
        val blocks = if (aid < SCRAMBLE_268850) {
            10
        } else {
            val digest = MessageDigest.getInstance("MD5").digest("$aid$filename".toByteArray())
            val hash = digest.joinToString("") { "%02x".format(it) }
            val modulus = if (aid < SCRAMBLE_421926) 10 else 8
            hash.last().code % modulus * 2 + 2
        }
        val result = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val remainder = image.height % blocks
        val move = image.height / blocks
        repeat(blocks) { index ->
            val sourceY = image.height - move * (index + 1) - remainder
            val targetY = move * index
            val height = if (index == 0) move + remainder else move
            canvas.drawBitmap(
                image,
                Rect(0, sourceY, image.width, sourceY + height),
                Rect(0, targetY, image.width, targetY + height),
                null,
            )
        }
        return result
    }

    private fun imageReferer(host: String): String? = when {
        host == "image.kalaimg.top" -> "https://image.kalaimg.top/"
        host.contains("jmapiproxy") || host.contains("jmapinode") -> "https://18comic.vip/"
        else -> null
    }

    private companion object {
        const val SCRAMBLE_PARAM = "_missu_scramble"
        const val SCRAMBLE_268850 = 268850L
        const val SCRAMBLE_421926 = 421926L
    }
}
