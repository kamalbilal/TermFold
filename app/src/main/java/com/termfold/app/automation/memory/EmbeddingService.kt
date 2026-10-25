package com.termfold.app.automation.memory

import android.content.Context
import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * On-device text embeddings for ObjectBox HNSW search. Uses ONNX when
 * `assets/automation-minilm.onnx` is present; otherwise a deterministic hash embedding.
 */
class EmbeddingService(private val context: Context) {

    private val onnx by lazy { OnnxMiniLm.tryCreate(context) }

    fun embed(text: String): FloatArray {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return FloatArray(EmbeddingDimensions.SIZE)
        return onnx?.embed(trimmed) ?: hashEmbed(trimmed)
    }

    private fun hashEmbed(text: String): FloatArray {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        val out = FloatArray(EmbeddingDimensions.SIZE)
        for (i in out.indices) {
            val b = digest[i % digest.size].toInt() and 0xff
            out[i] = (b / 127.5f) - 1f
        }
        normalize(out)
        return out
    }

    private fun normalize(v: FloatArray) {
        var sum = 0.0
        for (x in v) sum += x * x
        val norm = sqrt(sum).toFloat().takeIf { it > 1e-6f } ?: return
        for (i in v.indices) v[i] /= norm
    }

    companion object {
        @Volatile
        private var instance: EmbeddingService? = null

        fun get(context: Context): EmbeddingService =
            instance ?: synchronized(this) {
                instance ?: EmbeddingService(context.applicationContext).also { instance = it }
            }
    }
}
