package com.example.soundwaveeditor.soundfile

import java.io.File
import java.util.*


open class CheapSoundFile {
    companion object {
        private val HEX_CHARS =
            charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

        var subclassFactories = arrayOf(
            CheapAAC.getFactory(),
            CheapAMR.getFactory(),
            CheapMP3.getFactory(),
            CheapWAV.getFactory()
        )

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
    }

    interface ProgressListener {
        fun reportProgress(fractionComplete: Double): Boolean
    }

    interface Factory {
        val supportedExtensions: Array<String>
        fun create(): CheapSoundFile
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

    fun isFilenameSupported(filename: String): Boolean {
        val components =
            filename.toLowerCase(Locale.getDefault()).split("\\.".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
        return if (components.size < 2) {
            false
        } else extensionMap.containsKey(components[components.size - 1])
    }

    fun getSupportedExtensions() = supportedExtensions.toTypedArray()

    var mProgressListener: ProgressListener? = null
    var inputFile: File? = null

    @Throws(java.io.FileNotFoundException::class, java.io.IOException::class)
    open fun readFile(file: File) {
        inputFile = file
    }

    open fun setProgressListener(progressListener: ProgressListener) {
        mProgressListener = progressListener
    }

    open fun getNumFrames() = 0

    open fun getSamplesPerFrame() = 0

    open fun getFrameGains(): IntArray? = null

    open fun getFileSizeBytes() = 0

    open fun getAvgBitrateKbps() = 0

    open fun getSampleRate() = 0

    open fun getChannels() = 0

    open fun getFiletype() = "Unknown"

    open fun getSeekableFrameOffset(frame: Int) = -1

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

    @Throws(java.io.IOException::class)
    fun WriteFile(outputFile: File, startFrame: Int, numFrames: Int) = Unit
}
