package com.example.soundwaveeditor.soundfile

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*


open class CheapSoundFile {
    companion object {
        private val HEX_CHARS =
            charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

        var subclassFactories = arrayOf(CheapMP3.getFactory(), CheapWAV.getFactory())

        var supportedExtensions = ArrayList<String>()
        var extensionMap = HashMap<String, Factory>()

        init {
            subclassFactories.forEach {
                it.supportedExtensions.forEach { ext ->
                    supportedExtensions.add(ext)
                    extensionMap[ext] = it
                }
            }
        }

        @Throws(java.io.FileNotFoundException::class, java.io.IOException::class)
        fun create(fileName: String, progressListener: ProgressListener): CheapSoundFile? {
            val file = File(fileName)

            if (!file.exists()) {
                throw java.io.FileNotFoundException(fileName)
            }

            val name = file.name.toLowerCase(Locale.getDefault())
            val components = name.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            if (components.size < 2) {
                return null
            }

            val factory = extensionMap[components[components.size - 1]] ?: return null

            return factory.create().apply {
                setProgressListener(progressListener)
                readFile(file)
            }
        }

        interface ProgressListener {
            fun reportProgress(fractionComplete: Double): Boolean
        }
    }

    open var numFrames = 0
    open var samplesPerFrame = 0
    open var frameGains = intArrayOf()
    open var fileSizeBytes = 0
    open var avgBitrateKbps = 0
    open var sampleRate = 0
    open var channels = 0
    open var filetype = "Unknown"
    var mProgressListener: ProgressListener? = null
    var inputFile: File? = null

    interface Factory {
        val supportedExtensions: Array<String>
        fun create(): CheapSoundFile
    }

    @Throws(java.io.FileNotFoundException::class, java.io.IOException::class)
    open fun readFile(file: File) {
        inputFile = file
    }

    open fun setProgressListener(progressListener: ProgressListener) {
        mProgressListener = progressListener
    }

    open fun getSeekableFrameOffset(frame: Int) = -1

    @Throws(java.io.IOException::class)
    open fun trimAudioFile(file: File, startTime: Long, endTime: Long) {
        val startOffset = ((startTime * sampleRate) * 2 * channels).toInt()
        var samplesNumber = ((endTime - startTime) * sampleRate).toInt()

        val channelsNumber = channels.takeUnless { it == 1 }?.let { it } ?: 2
        val mimeType = "audio/m4a-latm"
        val bitrate = 64000 * channelsNumber

        val mediaCodec = MediaCodec.createEncoderByType(mimeType)
        val mediaFormat = MediaFormat.createAudioFormat(mimeType, sampleRate, channelsNumber)

        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, avgBitrateKbps)
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mediaCodec.start()

        var estimatedEncodedSize = ((endTime - startTime) * (bitrate / 8) * 1.1).toInt()

        var encodedBytes = ByteBuffer.allocate(estimatedEncodedSize)

        val inputBuffers = mediaCodec.inputBuffers
        var outputBuffers = mediaCodec.outputBuffers

        val info = MediaCodec.BufferInfo()
        var doneReading = false
        var presentationTime: Long

        val frameSize = 1024
        var buffer = ByteArray(frameSize * channelsNumber * 2)

        decodedBytes?.position(startOffset)

        samplesNumber += (2 * frameSize)

        var totalFramesNumber = 1 + (samplesNumber / frameSize)

        if (samplesNumber % frameSize != 0) {
            totalFramesNumber++
        }

        val frameSizes = IntArray(totalFramesNumber)
        var numberOutFrames = 0
        var numFrames = 0
        var numSamplesLeft = samplesNumber
        var encodedSamplesSize = 0
        var encodedSamples: ByteArray? = null

        while (true) {
            val inputBufferIndex = mediaCodec.dequeueInputBuffer(100 )

            if (!doneReading && inputBufferIndex >= 0) {
                if (numSamplesLeft <= 0) {
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, -1, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    doneReading = true
                } else {
                    inputBuffers[inputBufferIndex].clear()

                    if (buffer.size > inputBuffers[inputBufferIndex].remaining()) {
                        continue
                    }

                    val bufferSize = if (channels == 1) buffer.size / 2 else buffer.size

                    decodedBytes?.let { decBytes ->
                        decBytes.takeIf { it.remaining() < bufferSize }?.let {
                            for (i in it.remaining() .. bufferSize) {
                                buffer[i] = 0
                            }

                            decodedBytes?.get(buffer, 0, it.remaining())
                        } ?: let {
                            decodedBytes?.get(buffer, 0, decBytes.remaining())
                        }
                    }

                    if (channels == 1) {
                        for (i in bufferSize - 1 until 1 step -2) {
                            buffer[2 * i + 1] = buffer[i]
                            buffer[2 * i] = buffer[i - 1]
                            buffer[2 * i - 1] = buffer[2 * i + 1]
                            buffer[2 * i - 2] = buffer[2 * i]
                        }
                    }

                    numSamplesLeft -= frameSize
                    inputBuffers[inputBufferIndex].put(buffer)
                    presentationTime = (((numFrames++) * frameSize * 1E6) / sampleRate).toLong()
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, buffer.size, presentationTime, 0)
                }
            }

            val outputBufferIndex = mediaCodec.dequeueOutputBuffer(info, 100)

            if (outputBufferIndex >= 0 && info.size > 0 && info.presentationTimeUs >= 0) {
                if (numberOutFrames < frameSizes.size) {
                    frameSizes[numberOutFrames++] = info.size
                }

                if (encodedSamplesSize < info.size) {
                    encodedSamplesSize = info.size
                    encodedSamples = ByteArray(encodedSamplesSize)
                }

                encodedSamples?.let {
                    outputBuffers[outputBufferIndex].get(it, 0, info.size)
                    outputBuffers[outputBufferIndex].clear()
                }

                mediaCodec.releaseOutputBuffer(outputBufferIndex, false)

                if (encodedBytes.remaining() < info.size) {
                    estimatedEncodedSize = (estimatedEncodedSize * 1.2).toInt()

                    val newEncodedBytes = ByteBuffer.allocate(estimatedEncodedSize)
                    val position = encodedBytes.position()

                    encodedBytes.rewind()
                    newEncodedBytes.put(encodedBytes)
                    encodedBytes = newEncodedBytes
                    encodedBytes.position(position)
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = mediaCodec.outputBuffers
            }

            if ((info.flags.or(MediaCodec.BUFFER_FLAG_END_OF_STREAM)) != 0) {
                break
            }
        }

        val encodedSize = encodedBytes.position()

        encodedBytes.rewind()
        mediaCodec.stop()
        mediaCodec.release()

        buffer = ByteArray(4096)

        try {
            val outputStream = FileOutputStream(file)

            outputStream.write(MP4Header.getMP4Header(sampleRate, channelsNumber, frameSizes, bitrate))

            while (encodedSize - encodedBytes.position() > buffer.size) {
                encodedBytes.get(buffer)
                outputStream.write(buffer)
            }

            val remaining = encodedSize - encodedBytes.position()

            if(remaining > 0) {
                encodedBytes.get(buffer, 0, remaining)
                outputStream.write(buffer, 0, remaining)
            }

            outputStream.close()
        } catch (e: IOException) {
            // TODO refactor it
            Log.e("SOUND FILE TRIM", "Failed to create .m4a file")
            e.message?.let { Log.e("SOUND FILE TRIM", it) }
        }
    }

    private var decodedBytes: ByteBuffer? = null

    fun isFilenameSupported(filename: String): Boolean {
        val components =
            filename.toLowerCase(Locale.getDefault()).split("\\.".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
        return if (components.size < 2) {
            false
        } else extensionMap.containsKey(components[components.size - 1])
    }

    fun getSupportedExtensions() = supportedExtensions.toTypedArray()

    fun bytesToHex(hash: ByteArray): String {
        val buf = CharArray(hash.size * 2)

        var i = 0
        var x = 0

        while (i < hash.size) {
            buf[x++] = HEX_CHARS[hash[i].toInt().ushr(4) and 0xf]
            buf[x++] = HEX_CHARS[hash[i].toInt() and 0xf]
            i++
        }

        return String(buf)
    }
}
