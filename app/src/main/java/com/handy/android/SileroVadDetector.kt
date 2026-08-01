package com.handy.android

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.max

/** Stateful Silero VAD v4 runner for 16 kHz PCM windows. */
class SileroVadDetector(
    context: Context,
    private val silenceThreshold: Float = DEFAULT_SILENCE_THRESHOLD,
    private val silenceLimitMs: Long = DEFAULT_SILENCE_LIMIT_MS,
) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String
    private val sampleRateName: String?
    private val hiddenName: String?
    private val cellName: String?
    private val probabilityOutputIndex: Int
    private val hiddenOutputIndex: Int?
    private val cellOutputIndex: Int?
    private var hiddenState = FloatArray(STATE_SIZE * STATE_LAYERS)
    private var cellState = FloatArray(STATE_SIZE * STATE_LAYERS)
    private var silentMs = 0L
    private var hasVoice = false
    private var closed = false

    init {
        val model = context.assets.open(MODEL_ASSET).use { it.readBytes() }
        session = environment.createSession(model)
        val names = session.inputNames.toList()
        inputName = names.firstOrNull { it.equals("input", ignoreCase = true) }
            ?: names.firstOrNull { !it.equals("sr", ignoreCase = true) && !it.equals("h", ignoreCase = true) && !it.equals("c", ignoreCase = true) }
            ?: error("Silero VAD model has no audio input")
        sampleRateName = names.firstOrNull { it.equals("sr", ignoreCase = true) }
        hiddenName = names.firstOrNull { it.equals("h", ignoreCase = true) }
        cellName = names.firstOrNull { it.equals("c", ignoreCase = true) }
        val outputNames = session.outputNames.toList()
        probabilityOutputIndex = outputNames.indexOfFirst { it.equals("output", ignoreCase = true) }
            .takeIf { it >= 0 } ?: 0
        require(probabilityOutputIndex < outputNames.size) { "Silero VAD model has no probability output" }
        hiddenOutputIndex = outputNames.indexOfFirst { it.equals("hn", ignoreCase = true) }
            .takeIf { it >= 0 }
        cellOutputIndex = outputNames.indexOfFirst { it.equals("cn", ignoreCase = true) }
            .takeIf { it >= 0 }
    }

    /** Returns the voice probability and whether the silence limit was reached. */
    @Synchronized
    fun process(samples: FloatArray, count: Int = samples.size): VadResult {
        check(!closed) { "VAD detector is closed" }
        val safeCount = count.coerceIn(1, samples.size)
        val input = samples.copyOf(safeCount)
        val inputs = mutableMapOf<String, ai.onnxruntime.OnnxTensorLike>()
        var inputTensor: OnnxTensor? = null
        var sampleRateTensor: OnnxTensor? = null
        var hiddenTensor: OnnxTensor? = null
        var cellTensor: OnnxTensor? = null

        return try {
            inputTensor = OnnxTensor.createTensor(environment, directFloatBuffer(input), longArrayOf(1, safeCount.toLong()))
            sampleRateTensor = sampleRateName?.let {
                OnnxTensor.createTensor(environment, directLongBuffer(longArrayOf(AudioRecorder.SAMPLE_RATE.toLong())), longArrayOf(1))
            }
            hiddenTensor = hiddenName?.let {
                OnnxTensor.createTensor(environment, directFloatBuffer(hiddenState), longArrayOf(STATE_LAYERS.toLong(), 1, STATE_SIZE.toLong()))
            }
            cellTensor = cellName?.let {
                OnnxTensor.createTensor(environment, directFloatBuffer(cellState), longArrayOf(STATE_LAYERS.toLong(), 1, STATE_SIZE.toLong()))
            }
            inputs[inputName] = inputTensor!!
            sampleRateName?.let { inputs[it] = sampleRateTensor!! }
            hiddenName?.let { inputs[it] = hiddenTensor!! }
            cellName?.let { inputs[it] = cellTensor!! }

            session.run(inputs).use { result ->
                val probability = probability(result.get(probabilityOutputIndex).value)
                hiddenOutputIndex?.let { updateState(result, it) { hiddenState = it } }
                cellOutputIndex?.let { updateState(result, it) { cellState = it } }
                val windowMs = max(1L, safeCount * 1_000L / AudioRecorder.SAMPLE_RATE)
                if (probability >= silenceThreshold) {
                    hasVoice = true
                    silentMs = 0L
                } else if (hasVoice) {
                    silentMs += windowMs
                }
                VadResult(probability, hasVoice && silentMs >= silenceLimitMs)
            }
        } finally {
            inputTensor?.close()
            sampleRateTensor?.close()
            hiddenTensor?.close()
            cellTensor?.close()
        }
    }

    private fun probability(value: Any?): Float = flattenFloats(value).firstOrNull()?.coerceIn(0f, 1f) ?: 0f

    private fun directFloatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                rewind()
            }

    private fun directLongBuffer(values: LongArray): LongBuffer =
        ByteBuffer.allocateDirect(values.size * Long.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asLongBuffer()
            .apply {
                put(values)
                rewind()
            }

    private fun updateState(result: OrtSession.Result, index: Int, assign: (FloatArray) -> Unit) {
        if (index >= result.size()) return
        val values = flattenFloats(result.get(index).value)
        if (values.size == STATE_SIZE * STATE_LAYERS) assign(values)
    }

    private fun flattenFloats(value: Any?): FloatArray = when (value) {
        is FloatArray -> value.copyOf()
        is Array<*> -> value.flatMap { flattenFloats(it).asList() }.toFloatArray()
        else -> floatArrayOf()
    }

    override fun close() {
        if (closed) return
        closed = true
        session.close()
    }

    data class VadResult(val probability: Float, val shouldAutoStop: Boolean)

    companion object {
        const val MODEL_ASSET = "silero_vad_v4.onnx"
        const val DEFAULT_SILENCE_THRESHOLD = 0.5f
        const val DEFAULT_SILENCE_LIMIT_MS = 1_200L
        const val WINDOW_SAMPLES = 512
        private const val STATE_LAYERS = 2
        private const val STATE_SIZE = 64
    }
}
