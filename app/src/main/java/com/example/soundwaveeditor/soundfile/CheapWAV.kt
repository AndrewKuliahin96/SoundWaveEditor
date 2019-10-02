package com.example.soundwaveeditor.soundfile

import android.util.Log
import java.io.File
import kotlin.math.sqrt


class CheapWAV : CheapSoundFile() {
    companion object {
        const val TAG = "CheapWAV"

        fun getFactory() =  object : Factory {
            override val supportedExtensions = arrayOf("wav")

            override fun create() = CheapWAV()
        }
    }

    // Member variables containing frame info
    override var numFrames = 0
    override var samplesPerFrame = 1024
    override var frameGains = intArrayOf()
    override var fileSizeBytes = 0
    override var sampleRate = 0
    override var channels = 0
    override var avgBitrateKbps = sampleRate * channels * 2 / 1024
    override var filetype = "WAV"

    @Throws(java.io.IOException::class)
    override fun readFile(file: File) {
        super.readFile(file)

        fileSizeBytes = file.length().toInt()

        if (fileSizeBytes < 128) {
            throw java.io.IOException("File too small to parse")
        }
        try {
            val wavFile = WavFile.openWavFile(file)
            numFrames = (wavFile.getNumFrames() / samplesPerFrame).toInt()
            frameGains = IntArray(numFrames)
            sampleRate = wavFile.getSampleRate().toInt()
            channels = wavFile.getNumChannels()

            var gain: Int
            var value: Int
            val buffer = IntArray(samplesPerFrame * channels)

            for (i in 0 until numFrames) {
                gain = -1
                wavFile.readFrames(buffer, samplesPerFrame)

                for (j in 0 until samplesPerFrame) {
                    value = buffer[j]
                    if (gain < value) {
                        gain = value
                    }
                }

                frameGains[i] = sqrt(gain.toDouble()).toInt()

                if (mProgressListener != null) {
                    val keepGoing = mProgressListener?.reportProgress(i * 1.0 / frameGains.size) ?: false
                    if (!keepGoing) {
                        break
                    }
                }
            }

            wavFile.close()
        } catch (e: WavFileException) {
            Log.e(TAG, "Exception while reading wav file", e)
        }
    }
}
