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
    private var numFrames = 0
    private var frameGains: IntArray? = null
    private var fileSize = 0
    private var sampleRate = 0
    private var channels = 0

    override fun getNumFrames() = numFrames

    override fun getSamplesPerFrame() = 1024

    override fun getFrameGains() = frameGains

    override fun getFileSizeBytes() = fileSize

    override fun getAvgBitrateKbps() = sampleRate * channels * 2 / 1024

    override fun getSampleRate() = sampleRate

    override fun getChannels() = channels

    override fun getFiletype() = "WAV"

    @Throws(java.io.IOException::class)
    override fun readFile(file: File) {
        super.readFile(file)

        fileSize = file.length().toInt()

        if (fileSize < 128) {
            throw java.io.IOException("File too small to parse")
        }
        try {
            val wavFile = WavFile.openWavFile(file)
            numFrames = (wavFile.getNumFrames() / getSamplesPerFrame()).toInt()
            frameGains = IntArray(numFrames)
            sampleRate = wavFile.getSampleRate().toInt()
            channels = wavFile.getNumChannels()

            var gain: Int
            var value: Int
            val buffer = IntArray(getSamplesPerFrame())

            for (i in 0 until numFrames) {
                gain = -1
                wavFile.readFrames(buffer, getSamplesPerFrame())
                for (j in 0 until getSamplesPerFrame()) {
                    value = buffer[j]
                    if (gain < value) {
                        gain = value
                    }
                }
                frameGains?.set(i, sqrt(gain.toDouble()).toInt())
                if (mProgressListener != null) {
                    val keepGoing = mProgressListener!!.reportProgress(i * 1.0 / frameGains!!.size)
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
