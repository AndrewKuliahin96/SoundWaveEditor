package com.example.soundwaveeditor.soundfile

import com.example.soundwaveeditor.extensions.getInt
import java.io.File
import java.io.FileInputStream


class CheapMP3 : CheapSoundFile() {
    companion object {
        private val BIT_RATES_MPEG1_L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
        private val BIT_RATES_MPEG2_L3 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0)
        private val SAMPLE_RATES_MPEG1_L3 = intArrayOf(44100, 48000, 32000, 0)
        private val SAMPLE_RATES_MPEG2_L3 = intArrayOf(22050, 24000, 16000, 0)

        fun getFactory() = object : Factory {
            override val supportedExtensions = arrayOf("mp3")

            override fun create() = CheapMP3()
        }
    }

    override var numFrames = 0
    override var frameGains = intArrayOf()
    override var fileSizeBytes = 0
    override var samplesPerFrame = 1152
    override var avgBitrateKbps = 0
    override var sampleRate = 0
    override var channels = 0
    override var filetype = "MP3"

    // Member variables used during initialization
    private var maxFrames = 0
    private var bitrateSum = 0
    private var minGain = 0
    private var maxGain = 0

    @Throws(java.io.FileNotFoundException::class, java.io.IOException::class)
    override fun readFile(file: File) {
        super.readFile(file)
        numFrames = 0
        maxFrames = 64  // This will grow as needed
        frameGains = IntArray(maxFrames)
        bitrateSum = 0
        minGain = 255
        maxGain = 0

        // No need to handle filesizes larger than can fit in a 32-bit int
        fileSizeBytes = file.length().toInt()

        val stream = FileInputStream(file)

        var pos = 0
        var offset = 0
        val buffer = ByteArray(12)

        while (pos < fileSizeBytes - 12) {
            // Read 12 bytes at a time and look for a sync code (0xFF)
            while (offset < 12) {
                offset += stream.read(buffer, offset, 12 - offset)
            }

            var bufferOffset = 0

            while (bufferOffset < 12 && buffer[bufferOffset].toInt() != -1)
                bufferOffset++

            if (mProgressListener != null) {
                if (mProgressListener?.reportProgress(pos * 1.0 / fileSizeBytes) != true) {
                    break
                }
            }

            if (bufferOffset > 0) {
                // We didn't find a sync code (0xFF) at position 0;
                // shift the buffer over and try again
                for (i in 0 until 12 - bufferOffset)
                    buffer[i] = buffer[bufferOffset + i]

                pos += bufferOffset
                offset = 12 - bufferOffset
                continue
            }

            // Check for MPEG 1 Layer III or MPEG 2 Layer III codes
            var mpgVersion: Int

            if (buffer[1].toInt() == -6 || buffer[1].toInt() == -5) {
                mpgVersion = 1
            } else if (buffer[1].toInt() == -14 || buffer[1].toInt() == -13) {
                mpgVersion = 2
            } else {
                bufferOffset = 1
                for (i in 0 until 12 - bufferOffset)
                    buffer[i] = buffer[bufferOffset + i]
                pos += bufferOffset
                offset = 12 - bufferOffset
                continue
            }

            // The third byte has the bitrate and samplerate
            val bitRate: Int
            val sampleRate: Int
            if (mpgVersion == 1) {
                // MPEG 1 Layer III
                bitRate = BIT_RATES_MPEG1_L3[buffer.getInt(2) and 0xF0 shr 4]
                sampleRate = SAMPLE_RATES_MPEG1_L3[buffer.getInt(2) and 0x0C shr 2]
            } else {
                // MPEG 2 Layer III
                bitRate = BIT_RATES_MPEG2_L3[buffer.getInt(2) and 0xF0 shr 4]
                sampleRate = SAMPLE_RATES_MPEG2_L3[buffer.getInt(2) and 0x0C shr 2]
            }

            if (bitRate == 0 || sampleRate == 0) {
                bufferOffset = 2
                for (i in 0 until 12 - bufferOffset)
                    buffer[i] = buffer[bufferOffset + i]
                pos += bufferOffset
                offset = 12 - bufferOffset
                continue
            }

            // From here on we assume the frame is good
            this.sampleRate = sampleRate
            val padding = buffer.getInt(2) and 2 shr 1
            val frameLen = 144 * bitRate * 1000 / sampleRate + padding

            val gain: Int
            if (buffer.getInt(3) and 0xC0 == 0xC0) {
                // 1 channel
                channels = 1
                gain = if (mpgVersion == 1) {
                    (buffer.getInt(10) and 0x01 shl 7) + (buffer.getInt(11) and 0xFE shr 1)
                } else {
                    (buffer.getInt(9) and 0x03 shl 6) + (buffer.getInt(10) and 0xFC shr 2)
                }
            } else {
                // 2 channels
                channels = 2
                gain = if (mpgVersion == 1) {
                    (buffer.getInt(9) and 0x7F shl 1) + (buffer.getInt(10) and 0x80 shr 7)
                } else {
                    0
                }
            }

            bitrateSum += bitRate
            frameGains[numFrames] = gain

            if (gain < minGain)
                minGain = gain
            if (gain > maxGain)
                maxGain = gain

            numFrames++
            if (numFrames == maxFrames) {
                // We need to grow our arrays.  Rather than naively
                // doubling the array each time, we estimate the exact
                // number of frames we need and add 10% padding.  In
                // practice this seems to work quite well, only one
                // resize is ever needed, however to avoid pathological
                // cases we make sure to always double the size at a minimum.

                avgBitrateKbps = bitrateSum / numFrames

                val totalFramesGuess = fileSizeBytes / avgBitrateKbps * sampleRate / 144000
                var newMaxFrames = totalFramesGuess * 11 / 10

                if (newMaxFrames < maxFrames * 2)
                    newMaxFrames = maxFrames * 2

                val newGains = IntArray(newMaxFrames)

                for (i in 0 until numFrames) {
                    newGains[i] = frameGains[i]
                }

                frameGains = newGains
                maxFrames = newMaxFrames
            }

            stream.skip((frameLen - 12).toLong())
            pos += frameLen
            offset = 0
        }

        // We're done reading the file, do some postprocessing
        avgBitrateKbps = if (numFrames > 0) bitrateSum / numFrames else 0
    }
}
