package site.duanjr.homework.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * 上传前的图片准备：
 * - 读取原始文件名与大小
 * - 小于 10MB 直接复制到缓存文件（保留原始字节，服务器按 sha256 去重）
 * - 超过 10MB 时按 EXIF 方向解码、缩放到最长边 3200、重编码 JPEG(q88)
 */
object ImagePrepare {

    const val MAX_UPLOAD_BYTES = 10L * 1024 * 1024
    private const val MAX_DIMENSION = 3200

    data class Prepared(val file: File, val displayName: String, val originalSize: Long, val compressed: Boolean)

    fun displayName(context: Context, uri: Uri): String {
        var name: String? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) name = c.getString(0) }
        }
        if (name.isNullOrBlank()) name = uri.lastPathSegment
        if (name.isNullOrBlank()) name = "photo.jpg"
        if (!name!!.lowercase().endsWith(".jpg") && !name!!.lowercase().endsWith(".jpeg")) {
            name = name!!.substringBeforeLast('.') + ".jpg"
        }
        return name!!
    }

    fun sizeOf(context: Context, uri: Uri): Long {
        var size: Long = -1
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) size = c.getLong(0) }
        }
        if (size < 0) size = runCatching {
            context.contentResolver.openInputStream(uri)?.use { s -> s.available().toLong() } ?: -1
        }.getOrDefault(-1)
        return size
    }

    /** 从 content uri / file uri 准备待上传文件 */
    fun prepare(context: Context, uri: Uri, fallbackName: String = "photo.jpg"): Prepared {
        val name = displayName(context, uri).ifBlank { fallbackName }
        val declared = sizeOf(context, uri)

        val outFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}_${(0..999).random()}.jpg")
        // 先复制原始流
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("无法读取所选图片")

        val actual = outFile.length()
        if (actual in 1 until MAX_UPLOAD_BYTES) {
            return Prepared(outFile, name, actual, compressed = false)
        }

        // 超限（或无法确定大小时保险起见）：解码-旋转-缩放-重编码
        val bitmap = decodeScaled(outFile) ?: run {
            if (actual in 1 until MAX_UPLOAD_BYTES) {
                return Prepared(outFile, name, actual, compressed = false)
            }
            outFile.delete()
            throw IllegalStateException("所选文件不是有效的图片")
        }
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, fos)
        }
        bitmap.recycle()
        if (outFile.length() > MAX_UPLOAD_BYTES) {
            // 极端情况下再压一次
            val bmp = decodeScaled(outFile, maxDim = 2400)!!
            FileOutputStream(outFile).use { fos -> bmp.compress(Bitmap.CompressFormat.JPEG, 80, fos) }
            bmp.recycle()
        }
        return Prepared(outFile, name, actual, compressed = true)
    }

    /** 相机拍照产生的本地文件同样走超限压缩逻辑 */
    fun prepareFile(context: Context, file: File, displayName: String): Prepared {
        if (file.length() in 1 until MAX_UPLOAD_BYTES) {
            return Prepared(file, displayName, file.length(), compressed = false)
        }
        val bmp = decodeScaled(file) ?: return Prepared(file, displayName, file.length(), compressed = false)
        val target = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
        FileOutputStream(target).use { fos -> bmp.compress(Bitmap.CompressFormat.JPEG, 88, fos) }
        bmp.recycle()
        file.delete()
        return Prepared(target, displayName, file.length(), compressed = true)
    }

    private fun exifOrientation(file: File): Int = runCatching {
        when (ExifInterface(file.path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    private fun decodeScaled(file: File, maxDim: Int = MAX_DIMENSION): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        var w = bounds.outWidth
        var h = bounds.outHeight
        while (maxOf(w, h) / 2 >= maxDim) {
            sample *= 2; w /= 2; h /= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = BitmapFactory.decodeFile(file.path, opts) ?: return null
        val orientation = exifOrientation(file)
        val scale = minOf(1f, maxDim.toFloat() / maxOf(raw.width, raw.height))
        if (orientation == 0 && scale >= 1f) return raw
        val m = Matrix()
        m.postRotate(orientation.toFloat())
        m.postScale(scale, scale)
        val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (out != raw) raw.recycle()
        return out
    }
}
