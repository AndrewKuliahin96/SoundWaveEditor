package com.example.soundwaveeditor.soundfile

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.abs


class CheapWAV : CheapSoundFile() {
    companion object {
        val factory =  object : Factory {
            override val supportedExtensions = arrayOf("wav")

            override fun create() = CheapWAV()
        }
    }

    override var numFrames = 0
    override var samplesPerFrame = 1024
    override var frameOffsets = intArrayOf()
    override var frameLens = intArrayOf()
    override var frameGains = intArrayOf()
    override var fileSizeBytes = 0
    override var sampleRate = 0
    override var channels = 0
    override var avgBitrateKbps = sampleRate * channels * 2 / 1024
    override var fileType = "WAV"

    private var framesNum = 0
    private var frameBytes = 0
    private var offset = 0

    @Throws(java.io.FileNotFoundException::class, java.io.IOException::class)
    override fun readFile(file: File) {
        super.readFile(file)

        fileSizeBytes = file.length().toInt()

        if (fileSizeBytes < 128) {
            throw java.io.IOException("File too small to parse")
        }

        inputFile?.let {
            val stream = FileInputStream(file)
            val header = ByteArray(12)

            stream.read(header, 0, 12)

            offset += 12

            if (header[0] != 'R'.toByte() || header[1] != 'I'.toByte() ||
                header[2] != 'F'.toByte() || header[3] != 'F'.toByte() ||
                header[8] != 'W'.toByte() || header[9] != 'A'.toByte() ||
                header[10] != 'V'.toByte() || header[11] != 'E'.toByte()) {
                throw java.io.IOException("Not a WAV file")
            }

            channels = 0
            sampleRate = 0

            while (offset + 8 <= fileSizeBytes) {
                val chunkHeader = ByteArray(8)

                stream.read(chunkHeader, 0, 8)
                offset += 8

                val chunkLen = 0xff and chunkHeader[7].toInt() shl 24 or
                        (0xff and chunkHeader[6].toInt() shl 16) or
                        (0xff and chunkHeader[5].toInt() shl 8) or
                        (0xff and chunkHeader[4].toInt())

                if (chunkHeader[0] == 'f'.toByte() && chunkHeader[1] == 'm'.toByte() &&
                    chunkHeader[2] == 't'.toByte() && chunkHeader[3] == ' '.toByte()) {
                    if (chunkLen < 16 || chunkLen > 1024) {
                        throw java.io.IOException("WAV file has bad fmt chunk")
                    }

                    val fmt = ByteArray(chunkLen)
                    stream.read(fmt, 0, chunkLen)
                    offset += chunkLen

                    val format = 0xff and fmt[1].toInt() shl 8 or (0xff and fmt[0].toInt())

                    channels = 0xff and fmt[3].toInt() shl 8 or (0xff and fmt[2].toInt())

                    sampleRate = 0xff and fmt[7].toInt() shl 24 or (0xff and fmt[6].toInt() shl 16) or
                            (0xff and fmt[5].toInt() shl 8) or (0xff and fmt[4].toInt())

                    if (format != 1) {
                        throw java.io.IOException("Unsupported WAV file encoding")
                    }
                } else if (chunkHeader[0] == 'd'.toByte() && chunkHeader[1] == 'a'.toByte() &&
                    chunkHeader[2] == 't'.toByte() && chunkHeader[3] == 'a'.toByte()) {
                    if (channels == 0 || sampleRate == 0) {
                        throw java.io.IOException("Bad WAV file: data chunk before fmt chunk")
                    }

                    val frameSamples = sampleRate * channels / 50
                    frameBytes = frameSamples * 2

                    framesNum = (chunkLen + (frameBytes - 1)) / frameBytes
                    frameOffsets = IntArray(framesNum)
                    frameLens = IntArray(framesNum)
                    frameGains = IntArray(framesNum)

                    val oneFrame = ByteArray(frameBytes)

                    var i = 0
                    var frameIndex = 0

                    while (i < chunkLen) {
                        val oneFrameBytes = frameBytes

                        if (i + oneFrameBytes > chunkLen) {
                            i = chunkLen - oneFrameBytes
                        }

                        stream.read(oneFrame, 0, oneFrameBytes)

                        var maxGain = 0
                        var j = 1

                        while (j < oneFrameBytes) {
                            val frameVal = abs(oneFrame[j].toInt())

                            if (frameVal > maxGain) {
                                maxGain = frameVal
                            }

                            j += 4 * channels
                        }

                        frameOffsets[frameIndex] = offset
                        frameLens[frameIndex] = oneFrameBytes
                        frameGains[frameIndex] = maxGain

                        frameIndex++
                        offset += oneFrameBytes
                        i += oneFrameBytes

                        if (progressListener != null) {
                            if (progressListener?.reportProgress(i * 1.0 / fileSizeBytes) != true) {
                                break
                            }
                        }
                    }
                } else {
                    stream.skip(chunkLen.toLong())
                    offset += chunkLen
                }
            }
        }
    }

    @Throws(java.io.IOException::class)
    override fun trimAudioFile(outputFile: File, startFrame: Int, frames: Int) {
        inputFile?.let {
            val inputStream = FileInputStream(it)
            val outputStream = FileOutputStream(outputFile)

            var totalAudioLen: Long = 0

            for (i in 0 until numFrames) {
                totalAudioLen += frameLens[startFrame + i].toLong()
            }

            val totalDataLen = totalAudioLen + 36
            val longSampleRate = sampleRate.toLong()
            val byteRate = (sampleRate * 2 * channels).toLong()

            val header = ByteArray(44).apply {
                set(0, 'R'.toByte())
                set(1, 'I'.toByte())
                set(2, 'F'.toByte())
                set(3, 'F'.toByte())
                set(4, (totalDataLen and 0xff).toByte())
                set(5, (totalDataLen shr 8 and 0xff).toByte())
                set(6, (totalDataLen shr 16 and 0xff).toByte())
                set(7, (totalDataLen shr 24 and 0xff).toByte())
                set(8, 'W'.toByte())
                set(9, 'A'.toByte())
                set(10, 'V'.toByte())
                set(11, 'E'.toByte())
                set(12, 'f'.toByte())
                set(13, 'm'.toByte())
                set(14, 't'.toByte())
                set(15, ' '.toByte())
                set(16, 16)
                set(17, 0)
                set(18, 0)
                set(19, 0)
                set(20, 1)
                set(21, 0)
                set(22, channels.toByte())
                set(23, 0)
                set(24, (longSampleRate and 0xff).toByte())
                set(25, (longSampleRate shr 8 and 0xff).toByte())
                set(26, (longSampleRate shr 16 and 0xff).toByte())
                set(27, (longSampleRate shr 24 and 0xff).toByte())
                set(28, (byteRate and 0xff).toByte())
                set(29, (byteRate shr 8 and 0xff).toByte())
                set(30, (byteRate shr 16 and 0xff).toByte())
                set(31, (byteRate shr 24 and 0xff).toByte())
                set(32, (2 * channels).toByte())
                set(33, 0)
                set(34, 16)
                set(35, 0)
                set(36, 'd'.toByte())
                set(37, 'a'.toByte())
                set(38, 't'.toByte())
                set(39, 'a'.toByte())
                set(40, (totalAudioLen and 0xff).toByte())
                set(41, (totalAudioLen shr 8 and 0xff).toByte())
                set(42, (totalAudioLen shr 16 and 0xff).toByte())
                set(43, (totalAudioLen shr 24 and 0xff).toByte())
            }

            outputStream.write(header, 0, 44)

            val buffer = ByteArray(frameBytes)
            var pos = 0

            for (i in 0 until numFrames) {
                val skip = frameOffsets[startFrame + i] - pos
                val len = frameLens[startFrame + i]

                if (skip < 0) continue

                if (skip > 0) {
                    inputStream.skip(skip.toLong())
                    pos += skip
                }

                inputStream.read(buffer, 0, len)
                outputStream.write(buffer, 0, len)
                pos += len
            }

            inputStream.close()
            outputStream.close()
        }
    }
}
