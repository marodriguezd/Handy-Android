package com.handy.android

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.roundToInt

object AudioFileDecoder {
    fun decode(context: Context, uri: Uri): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val track = (0 until extractor.trackCount)
            .map { index -> index to extractor.getTrackFormat(index) }
            .firstOrNull { (_, format) -> format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ?: error("The selected file does not contain an audio track")
        val format = track.second
        val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
        val sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        extractor.selectTrack(track.first)
        val codec = MediaCodec.createDecoderByType(mime)
        val output = ArrayList<Float>()
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex) ?: error("Unable to access decoder input")
                        val sampleSize = extractor.readSampleData(input, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED, MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val buffer = codec.getOutputBuffer(outputIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val shorts = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            while (shorts.hasRemaining()) {
                                var sample = 0f
                                repeat(channels) {
                                    if (shorts.hasRemaining()) sample += shorts.get() / 32768.0f
                                }
                                output += sample / channels
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
        if (sourceRate == AudioRecorder.SAMPLE_RATE) return output.toFloatArray()
        return resample(output.toFloatArray(), sourceRate, AudioRecorder.SAMPLE_RATE)
    }

    private fun resample(samples: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (samples.isEmpty() || fromRate <= 0 || fromRate == toRate) return samples
        val outputSize = (samples.size.toDouble() * toRate / fromRate).roundToInt().coerceAtLeast(1)
        return FloatArray(outputSize) { index ->
            val sourcePosition = index.toDouble() * fromRate / toRate
            val left = sourcePosition.toInt().coerceIn(0, samples.lastIndex)
            val right = (left + 1).coerceAtMost(samples.lastIndex)
            val fraction = (sourcePosition - left).toFloat()
            samples[left] * (1 - fraction) + samples[right] * fraction
        }
    }
}
