package com.hylia.app.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * 关键帧提取：将截屏 Bitmap 处理为适合上传的压缩帧。
 * - 降采样到 maxEdge 像素
 * - JPEG 压缩
 * - 基于内容哈希去重，避免重复上传相同画面
 */
object KeyframeExtractor {

    private const val MAX_EDGE = 1080
    private const val JPEG_QUALITY = 70
    private const val MAX_CACHE = 20

    private val recentHashes = ArrayDeque<String>()

    /** 处理 Bitmap → JPEG 字节；若画面与近期相同则返回 null。 */
    fun extract(bitmap: Bitmap): ByteArray? {
        val scaled = downscale(bitmap)
        val bytes = compress(scaled)
        scaled.recycle()

        val hash = bytes.contentHashCode().toString()
        if (recentHashes.contains(hash)) return null
        if (recentHashes.size >= MAX_CACHE) recentHashes.removeFirst()
        recentHashes.addLast(hash)
        return bytes
    }

    /** 供 API < 30 降级路径使用：从已有 Bitmap 提取（或跳过）。 */
    fun fromExisting(bitmap: Bitmap): ByteArray? = extract(bitmap)

    private fun downscale(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val longEdge = maxOf(w, h)
        if (longEdge <= MAX_EDGE) return src.copy(Bitmap.Config.ARGB_8888, false)

        val scale = MAX_EDGE.toFloat() / longEdge
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        val out = Bitmap.createScaledBitmap(src, nw, nh, true)
        return if (out.config == Bitmap.Config.ARGB_8888) out
        else out.copy(Bitmap.Config.ARGB_8888, false).also { out.recycle() }
    }

    private fun compress(bitmap: Bitmap): ByteArray {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
        return baos.toByteArray()
    }

    fun clear() {
        recentHashes.clear()
    }
}
