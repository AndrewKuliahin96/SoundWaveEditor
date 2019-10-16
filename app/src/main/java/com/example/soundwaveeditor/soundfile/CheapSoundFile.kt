package com.example.soundwaveeditor.soundfile

import java.io.File
import java.util.*


open class CheapSoundFile {
    companion object {
        var subclassFactories = arrayOf(CheapMP3.factory, CheapWAV.factory)

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
        fun create(fileName: String, listener: ProgressListener): CheapSoundFile? {
            val file = File(fileName)

            if (!file.exists()) {
                throw java.io.FileNotFoundException(fileName)
            }

            val name = file.name.toLowerCase(Locale.getDefault())
            val components = name.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            if (components.size < 2) {
                return null
            }

            return extensionMap[components[components.size - 1]]?.create()?.apply {
                progressListener = listener
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
    open var frameLens = intArrayOf()
    open var frameOffsets = intArrayOf()
    open var fileSizeBytes = 0
    open var avgBitrateKbps = 0
    open var sampleRate = 0
    open var channels = 0
    open var fileType = "Unknown"

    var progressListener: ProgressListener? = null
    var inputFile: File? = null

    interface Factory {
        val supportedExtensions: Array<String>
        fun create(): CheapSoundFile
    }

    @Throws(java.io.FileNotFoundException::class, java.io.IOException::class)
    open fun readFile(file: File) {
        inputFile = file
    }

    open fun trimAudioFile(outputFile: File, startFrame: Int, frames: Int) = Unit

    fun trimAudioFile(file: File, startTime: Long, endTime: Long) =
        trimAudioFile(file, msToFrames(startTime), msToFrames(endTime - startTime))

    private fun msToFrames(milliseconds: Long) =
        (milliseconds / 1_000 * sampleRate / samplesPerFrame + 0.5).toInt()
}
