package com.termfold.app.automation.memory

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer

/** Optional bundled MiniLM ONNX; null when asset is absent. */
internal class OnnxMiniLm private constructor(private val session: OrtSession) {

    fun embed(text: String): FloatArray {
        val env = OrtEnvironment.getEnvironment()
        val tokens = simpleTokenIds(text)
        val shape = longArrayOf(1, tokens.size.toLong())
        val buffer = LongBuffer.allocate(tokens.size)
        tokens.forEach { buffer.put(it) }
        buffer.rewind()
        OnnxTensor.createTensor(env, buffer, shape).use { input ->
            session.run(mapOf("input_ids" to input)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val tensor = result[0].value as Array<FloatArray>
                val pooled = tensor[0]
                return if (pooled.size >= EmbeddingDimensions.SIZE) {
                    pooled.copyOf(EmbeddingDimensions.SIZE)
                } else {
                    pooled + FloatArray(EmbeddingDimensions.SIZE - pooled.size)
                }
            }
        }
    }

    private fun simpleTokenIds(text: String): LongArray {
        val words = text.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(64)
        if (words.isEmpty()) return longArrayOf(0L)
        return words.map { word -> (word.hashCode() and 0x7fff).toLong() }.toLongArray()
    }

    companion object {
        fun tryCreate(context: Context): OnnxMiniLm? = runCatching {
            val env = OrtEnvironment.getEnvironment()
            context.assets.open("automation-minilm.onnx").use { input ->
                OnnxMiniLm(env.createSession(input.readBytes()))
            }
        }.getOrNull()
    }
}
