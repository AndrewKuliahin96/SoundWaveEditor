package com.example.soundwaveeditor.soundfile

import com.example.soundwaveeditor.extensions.getInt
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.HashMap


class CheapAAC : CheapSoundFile() {
    companion object {
        private const val kDINF = 0x64696e66
        private const val kFTYP = 0x66747970
        private const val kHDLR = 0x68646c72
        private const val kMDAT = 0x6d646174
        private const val kMDHD = 0x6d646864
        private const val kMDIA = 0x6d646961
        private const val kMINF = 0x6d696e66
        private const val kMOOV = 0x6d6f6f76
        private const val kMP4A = 0x6d703461
        private const val kMVHD = 0x6d766864
        private const val kSMHD = 0x736d6864
        private const val kSTBL = 0x7374626c
        private const val kSTCO = 0x7374636f
        private const val kSTSC = 0x73747363
        private const val kSTSD = 0x73747364
        private const val kSTSZ = 0x7374737a
        private const val kSTTS = 0x73747473
        private const val kTKHD = 0x746b6864
        private const val kTRAK = 0x7472616b

        fun getFactory() = object : Factory {
            override val supportedExtensions = arrayOf("aac", "m4a")

            override fun create() = CheapAAC()
        }
    }

    internal inner class Atom {
        var start: Int = 0
        var len: Int = 0
        var data: ByteArray? = null
    }

    private val kRequiredAtoms = intArrayOf(
        kDINF,
        kHDLR,
        kMDHD,
        kMDIA,
        kMINF,
        kMOOV,
        kMVHD,
        kSMHD,
        kSTBL,
        kSTSD,
        kSTSZ,
        kSTTS,
        kTKHD,
        kTRAK
    )

    private val saveDataAtoms = intArrayOf(kDINF, kHDLR, kMDHD, kMVHD, kSMHD, kTKHD, kSTSD)

    // Member variables containing frame info
    override var numFrames: Int = 0
    private var frameLens = intArrayOf()
    override var frameGains = intArrayOf()
    override var fileSizeBytes = 0
    private var atomMap: HashMap<Int, Atom>? = null

    // Member variables containing sound file info
    override var avgBitrateKbps: Int = 0
    override var sampleRate: Int = 0
    override var channels: Int = 0
    override var samplesPerFrame: Int = 0
    override var filetype = "AAC"

    // Member variables used only while initially parsing the file
    private var offset: Int = 0
    private var minGain: Int = 0
    private var maxGain: Int = 0
    private var dataOffset: Int = 0
    private var dataLength: Int = 0

    private fun atomToString(atomType: Int) = StringBuilder().apply {
        append((atomType shr 24 and 0xff).toChar())
        append((atomType shr 16 and 0xff).toChar())
        append((atomType shr 8 and 0xff).toChar())
        append((atomType and 0xff).toChar())
    }.toString()

    @Throws(java.io.FileNotFoundException::class, java.io.IOException::class)
    override fun readFile(file: File) {
        super.readFile(file)
        channels = 0
        sampleRate = 0
        avgBitrateKbps = 0
        samplesPerFrame = 0
        numFrames = 0
        minGain = 255
        maxGain = 0
        offset = 0
        dataOffset = -1
        dataLength = -1

        atomMap = HashMap()

        // No need to handle filesizes larger than can fit in a 32-bit int
        fileSizeBytes = inputFile?.length()?.toInt() ?: 0

        if (fileSizeBytes < 128) {
            throw java.io.IOException("File too small to parse")
        }

        // Read the first 8 bytes
        var stream = inputFile?.let { FileInputStream(it) }
        val header = ByteArray(8)

        stream?.read(header, 0, 8)

        if (header.getInt(0) == 0 &&
            header[4] == 'f'.toByte() &&
            header[5] == 't'.toByte() &&
            header[6] == 'y'.toByte() &&
            header[7] == 'p'.toByte()
        ) {
            // Create a new stream, reset to the beginning of the file
            stream = inputFile?.let { FileInputStream(it) }
            stream?.let { parseMp4(it, fileSizeBytes) }
        } else {
            throw java.io.IOException("Unknown file format")
        }

        if (dataOffset > 0 && dataLength > 0) {
            stream = inputFile?.let { FileInputStream(it) }
            stream?.skip(dataOffset.toLong())
            offset = dataOffset
            stream?.let { parseMdat(it, dataLength) }
        } else {
            throw java.io.IOException("Didn't find mdat")
        }

        var bad = false

        for (requiredAtomType in kRequiredAtoms) {
            if ((atomMap?.containsKey(requiredAtomType))?.not() == true) {
                bad = true
            }
        }

        if (bad) throw java.io.IOException("Could not parse MP4 file")
    }

    @Throws(java.io.IOException::class)
    private fun parseMp4(stream: InputStream, maxLength: Int) {
        var maxLen = maxLength

        while (maxLen > 8) {
            val initialOffset = offset

            val atomHeader = ByteArray(8)

            stream.read(atomHeader, 0, 8)

            var atomLen = 0xff and atomHeader.getInt(0) shl 24 or
                    (0xff and atomHeader.getInt(1) shl 16) or
                    (0xff and atomHeader.getInt(2) shl 8) or
                    (0xff and atomHeader.getInt(3))

            maxLen.takeIf { atomLen > it }?.let { atomLen = maxLen }

            val atomType = 0xff and atomHeader.getInt(4) shl 24 or
                    (0xff and atomHeader.getInt(5) shl 16) or
                    (0xff and atomHeader.getInt(6) shl 8) or
                    (0xff and atomHeader.getInt(7))

            val atom = Atom().apply {
                start = offset
                len = atomLen
            }

            atomMap?.set(atomType, atom)

            offset += 8

            if (atomType == kMOOV ||
                atomType == kTRAK ||
                atomType == kMDIA ||
                atomType == kMINF ||
                atomType == kSTBL
            ) {
                parseMp4(stream, atomLen)
            } else if (atomType == kSTSZ) {
                parseStsz(stream)
            } else if (atomType == kSTTS) {
                parseStts(stream)
            } else if (atomType == kMDAT) {
                dataOffset = offset
                dataLength = atomLen - 8
            } else {
                for (savedAtomType in saveDataAtoms) {
                    if (savedAtomType == atomType) {
                        val data = ByteArray(atomLen - 8)

                        stream.read(data, 0, atomLen - 8)
                        offset += atomLen - 8

                        atomMap?.get(atomType)?.data = data
                    }
                }
            }

            if (atomType == kSTSD) {
                parseMp4aFromStsd()
            }

            maxLen -= atomLen

            val skipLen = atomLen - (offset - initialOffset)

            if (skipLen < 0) {
                throw java.io.IOException("Went over by " + -skipLen + " bytes")
            }

            stream.skip(skipLen.toLong())
            offset += skipLen
        }
    }

    @Throws(java.io.IOException::class)
    internal fun parseStts(stream: InputStream) {
        val sttsData = ByteArray(16)

        stream.read(sttsData, 0, 16)

        offset += 16

        samplesPerFrame = 0xff and sttsData.getInt(12) shl 24 or
                (0xff and sttsData.getInt(13) shl 16) or
                (0xff and sttsData.getInt(14) shl 8) or
                (0xff and sttsData.getInt(15))
    }

    @Throws(java.io.IOException::class)
    internal fun parseStsz(stream: InputStream) {
        val stszHeader = ByteArray(12)

        stream.read(stszHeader, 0, 12)

        offset += 12

        numFrames = 0xff and stszHeader.getInt(8) shl 24 or
                (0xff and stszHeader.getInt(9) shl 16) or
                (0xff and stszHeader.getInt(10) shl 8) or
                (0xff and stszHeader.getInt(11))

        frameLens = IntArray(numFrames)
        frameGains = IntArray(numFrames)

        val length = 4 * numFrames
        val frameLenBytes = ByteArray(length)

        stream.read(frameLenBytes, 0, length)

        offset += length

        for (i in 0 until numFrames) {
            frameLens[i] = (0xff and frameLenBytes.getInt(4 * i + 0) shl 24 or
                    (0xff and frameLenBytes.getInt(4 * i + 1) shl 16) or
                    (0xff and frameLenBytes.getInt(4 * i + 2) shl 8) or
                    (0xff and frameLenBytes.getInt(4 * i + 3)))
        }
    }

    private fun parseMp4aFromStsd() {
        atomMap?.get(kSTSD)?.data?.let {
            channels = 0xff and it.getInt(32) shl 8 or (0xff and it.getInt(33))
            sampleRate = 0xff and it.getInt(40) shl 8 or (0xff and it.getInt(41))
        }
    }

    @Throws(java.io.IOException::class)
    internal fun parseMdat(stream: InputStream, maxLen: Int) {
        val initialOffset = offset

        for (i in 0 until numFrames) {
            frameLens[i].takeIf { offset - initialOffset + it > maxLen - 8 }?.let {
                frameGains[i] = 0
            } ?: readFrameAndComputeGain(stream, i)

            when {
                frameGains[i] < minGain -> minGain = frameGains[i]
                frameGains[i] > maxGain -> maxGain = frameGains[i]
            }

            if (mProgressListener != null) {
                val keepGoing = mProgressListener?.reportProgress(
                    offset * 1.0 / fileSizeBytes
                ) ?: false

                if (!keepGoing) break
            }
        }
    }

    @Throws(java.io.IOException::class)
    internal fun readFrameAndComputeGain(stream: InputStream, frameIndex: Int) {
        frameGains[frameIndex].takeIf { it < 4 }?.let {
            frameGains.run {
                set(frameIndex, 0)
                stream.skip(get(frameIndex).toLong())
            }

            return
        }

        val initialOffset = offset

        var data = ByteArray(4)
        stream.read(data, 0, 4)
        offset += 4

        when (0xe0 and data.getInt(0) shr 5) {
            // ID_SCE: mono
            0 -> {
                val monoGain =
                    0x01 and data.getInt(0) shl 7 or (0xfe and data.getInt(1) shr 1)

                frameGains.set(frameIndex, monoGain)
            }
            // ID_CPE: stereo
            1 -> {
                val windowSequence = 0x60 and data.getInt(1) shr 5

                val maxSfb: Int
                val scaleFactorGrouping: Int
                val maskPresent: Int
                var startBit: Int

                if (windowSequence == 2) {
                    maxSfb = 0x0f and data.getInt(1)

                    scaleFactorGrouping = 0xfe and data.getInt(2) shr 1

                    maskPresent = 0x01 and data.getInt(2) shl 1 or (0x80 and data.getInt(3) shr 7)

                    startBit = 25
                } else {
                    maxSfb = 0x0f and data.getInt(1) shl 2 or (0xc0 and data.getInt(2) shr 6)

                    scaleFactorGrouping = -1

                    maskPresent = 0x18 and data.getInt(2) shr 3

                    startBit = 21
                }

                if (maskPresent == 1) {
                    var sfgZeroBitCount = 0
                    for (b in 0..6) {
                        if (scaleFactorGrouping and (1 shl b) == 0) {
                            sfgZeroBitCount++
                        }
                    }

                    val numWindowGroups = 1 + sfgZeroBitCount

                    val skip = maxSfb * numWindowGroups

                    startBit += skip
                }

                // We may need to fill our buffer with more than the 4
                // bytes we've already read, here.
                val bytesNeeded = 1 + (startBit + 7) / 8
                val oldData = data

                data = ByteArray(bytesNeeded)

                data[0] = oldData[0]
                data[1] = oldData[1]
                data[2] = oldData[2]
                data[3] = oldData[3]

                stream.read(data, 4, bytesNeeded - 4)
                offset += bytesNeeded - 4

                var firstChannelGain = 0

                for (b in 0..7) {
                    val b0 = (b + startBit) / 8
                    val b1 = 7 - (b + startBit) % 8
                    val add = 1 shl b1 and data.getInt(b0) shr b1 shl 7 - b

                    firstChannelGain += add
                }

                frameGains[frameIndex] = firstChannelGain
            }

            else -> frameGains[frameIndex] = when (frameIndex > 0) {
                true -> frameGains[frameIndex - 1]
                false -> 0
            }
        }

        val skip = frameLens[frameIndex] - (offset - initialOffset)

        stream.skip(skip.toLong())
        offset += skip
    }
}
