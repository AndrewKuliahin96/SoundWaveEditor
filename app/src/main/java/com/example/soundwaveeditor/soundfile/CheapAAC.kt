package com.example.soundwaveeditor.soundfile

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.HashMap


class CheapAAC : CheapSoundFile() {
    companion object {
        const val kDINF = 0x64696e66
        const val kFTYP = 0x66747970
        const val kHDLR = 0x68646c72
        const val kMDAT = 0x6d646174
        const val kMDHD = 0x6d646864
        const val kMDIA = 0x6d646961
        const val kMINF = 0x6d696e66
        const val kMOOV = 0x6d6f6f76
        const val kMVHD = 0x6d766864
        const val kSMHD = 0x736d6864
        const val kSTBL = 0x7374626c
        const val kSTCO = 0x7374636f
        const val kSTSC = 0x73747363
        const val kSTSD = 0x73747364
        const val kSTSZ = 0x7374737a
        const val kSTTS = 0x73747473
        const val kTKHD = 0x746b6864
        const val kTRAK = 0x7472616b

        val kRequiredAtoms = intArrayOf(kDINF, kHDLR, kMDHD, kMDIA, kMINF, kMOOV, kMVHD, kSMHD, kSTBL, kSTSD, kSTSZ, kSTTS, kTKHD, kTRAK)
        val kSaveDataAtoms = intArrayOf(kDINF, kHDLR, kMDHD, kMVHD, kSMHD, kTKHD, kSTSD)

        val factory = object : Factory {
            override val supportedExtensions = arrayOf("aac", "m4a")

            override fun create() = CheapAAC()
        }
    }

    override var numFrames = 0
    override var samplesPerFrame = 0
    override var frameOffsets = intArrayOf()
    override var frameLens = intArrayOf()
    override var frameGains = intArrayOf()
    override var fileSizeBytes = 0
    override var sampleRate = 0
    override var channels = 0
    override var avgBitrateKbps = fileSizeBytes / (numFrames * samplesPerFrame)
    override var fileType = "AAC"

    private var atomMap = mutableMapOf<Int, Atom>()
    private var offset = 0
    private var minGain: Int = 0
    private var maxGain: Int = 0
    private var datOffset: Int = 0
    private var mdatLength: Int = 0

    internal inner class Atom {
        var start: Int = 0
        var length: Int = 0
        var data: ByteArray? = null
    }

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
        datOffset = -1
        mdatLength = -1

        atomMap = HashMap()
        fileSizeBytes = file.length().toInt()

        if (fileSizeBytes < 128) {
            throw java.io.IOException("File too small to parse")
        }

        var stream = FileInputStream(file)
        val header = ByteArray(8)

        stream.read(header, 0, 8)

        if (header[0].toInt() == 0 &&
            header[4] == 'f'.toByte() &&
            header[5] == 't'.toByte() &&
            header[6] == 'y'.toByte() &&
            header[7] == 'p'.toByte()) {
            stream = FileInputStream(file)
            parseMp4(stream, fileSizeBytes)
        } else {
            throw java.io.IOException("Unknown file format")
        }

        if (datOffset > 0 && mdatLength > 0) {
            stream = FileInputStream(file)
            stream.skip(datOffset.toLong())
            offset = datOffset
            parseMdat(stream, mdatLength)
        } else {
            throw java.io.IOException("Didn't find mdat")
        }

        for (requiredAtomType in kRequiredAtoms) {
            if (!atomMap.containsKey(requiredAtomType)) {
                throw java.io.IOException("Could not parse MP4 file")
            }
        }
    }

    @Throws(java.io.IOException::class)
    private fun parseMp4(stream: InputStream, maxLength: Int) {
        var maxLen = maxLength

        while (maxLen > 8) {
            val initialOffset = offset

            val atomHeader = ByteArray(8)
            stream.read(atomHeader, 0, 8)

            var atomLen = 0xff and atomHeader[0].toInt() shl 24 or
                    (0xff and atomHeader[1].toInt() shl 16) or
                    (0xff and atomHeader[2].toInt() shl 8) or
                    (0xff and atomHeader[3].toInt())

            if (atomLen > maxLen) {
                atomLen = maxLen
            }

            val atomType = 0xff and atomHeader[4].toInt() shl 24 or
                    (0xff and atomHeader[5].toInt() shl 16) or
                    (0xff and atomHeader[6].toInt() shl 8) or
                    (0xff and atomHeader[7].toInt())

            val atom = Atom()

            atom.start = offset
            atom.length = atomLen

            atomMap[atomType] = atom

            offset += 8

            if (atomType == kMOOV || atomType == kTRAK ||
                atomType == kMDIA || atomType == kMINF || atomType == kSTBL) {
                parseMp4(stream, atomLen)
            } else if (atomType == kSTSZ || atomType == kSTTS) {
                parseStsz(stream)
            } else if (atomType == kMDAT) {
                datOffset = offset
                mdatLength = atomLen - 8
            } else {
                for (savedAtomType in kSaveDataAtoms) {
                    if (savedAtomType == atomType) {
                        val data = ByteArray(atomLen - 8)
                        stream.read(data, 0, atomLen - 8)
                        offset += atomLen - 8
                        atomMap[atomType]?.data = data
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
    internal fun parseStsz(stream: InputStream) {
        val stszHeader = ByteArray(12)

        stream.read(stszHeader, 0, 12)
        offset += 12

        numFrames = 0xff and stszHeader[8].toInt() shl 24 or
                (0xff and stszHeader[9].toInt() shl 16) or
                (0xff and stszHeader[10].toInt() shl 8) or
                (0xff and stszHeader[11].toInt())

        frameOffsets = IntArray(numFrames)
        frameLens = IntArray(numFrames)
        frameGains = IntArray(numFrames)

        val frameLenBytes = ByteArray(4 * numFrames)

        stream.read(frameLenBytes, 0, 4 * numFrames)
        offset += 4 * numFrames

        for (i in 0 until numFrames) {
            frameLens[i] = 0xff and frameLenBytes[4 * i + 0].toInt() shl 24 or
                    (0xff and frameLenBytes[4 * i + 1].toInt() shl 16) or
                    (0xff and frameLenBytes[4 * i + 2].toInt() shl 8) or
                    (0xff and frameLenBytes[4 * i + 3].toInt())
        }
    }

    private fun parseMp4aFromStsd() {
        atomMap[kSTSD]?.data?.let { stsdData ->
            channels = 0xff and stsdData[32].toInt() shl 8 or (0xff and stsdData[33].toInt())
            sampleRate = 0xff and stsdData[40].toInt() shl 8 or (0xff and stsdData[41].toInt())
        }
    }

    @Throws(java.io.IOException::class)
    internal fun parseMdat(stream: InputStream, maxLen: Int) {
        val initialOffset = offset

        for (i in 0 until numFrames) {
            frameOffsets[i] = offset

            if (offset - initialOffset + frameLens[i] > maxLen - 8) {
                frameGains[i] = 0
            } else {
                readFrameAndComputeGain(stream, i)
            }

            if (frameGains[i] < minGain) {
                minGain = frameGains[i]
            }

            if (frameGains[i] > maxGain) {
                maxGain = frameGains[i]
            }

            if (progressListener != null) {
                val keepGoing = progressListener?.reportProgress(
                    offset * 1.0 / fileSizeBytes
                )
                if (keepGoing != true) {
                    break
                }
            }
        }
    }

    @Throws(java.io.IOException::class)
    internal fun readFrameAndComputeGain(stream: InputStream, frameIndex: Int) {
        if (frameLens[frameIndex] < 4) {
            frameGains[frameIndex] = 0

            stream.skip(frameLens[frameIndex].toLong())

            return
        }

        val initialOffset = offset
        var data = ByteArray(4)

        stream.read(data, 0, 4)
        offset += 4

        when (0xe0 and data[0].toInt() shr 5) {
            0 -> frameGains[frameIndex] = 0x01 and data[0].toInt() shl 7 or (0xfe and data[1].toInt() shr 1)
            1 -> {
                val windowSequence = 0x60 and data[1].toInt() shr 5

                val maxSfb = when (windowSequence) {
                    2 -> 0x0f and data[1].toInt()
                    else -> 0x0f and data[1].toInt() shl 2 or (0xc0 and data[2].toInt() shr 6)
                }

                val scaleFactorGrouping = when (windowSequence) {
                    2 -> 0xfe and data[2].toInt() shr 1
                    else -> 0x18 and data[2].toInt() shr 3
                }

                val maskPresent = when (windowSequence) {
                    2 -> 0x01 and data[2].toInt() shl 1 or (0x80 and data[3].toInt() shr 7)
                    else -> -1
                }

                var startBit = when (windowSequence) {
                    2 -> 25
                    else -> 21
                }

                if (maskPresent == 1) {
                    var sfgZeroBitCount = 0

                    for (b in 0..6) {
                        if (scaleFactorGrouping and (1 shl b) == 0) {
                            sfgZeroBitCount++
                        }
                    }

                    startBit += maxSfb * (1 + sfgZeroBitCount)
                }

                val bytesNeeded = 1 + (startBit + 7) / 8
                val oldData = data

                data = ByteArray(bytesNeeded).apply {
                    this[0] = oldData[0]
                    this[1] = oldData[1]
                    this[2] = oldData[2]
                    this[3] = oldData[3]
                }

                stream.read(data, 4, bytesNeeded - 4)
                offset += bytesNeeded - 4

                var firstChannelGain = 0

                for (b in 0..7) {
                    val b0 = (b + startBit) / 8
                    val b1 = 7 - (b + startBit) % 8
                    val add = 1 shl b1 and data[b0].toInt() shr b1 shl 7 - b

                    firstChannelGain += add
                }

                frameGains[frameIndex] = firstChannelGain
            }

            else -> if (frameIndex > 0) {
                frameGains[frameIndex] = frameGains[frameIndex - 1]
            } else {
                frameGains[frameIndex] = 0
            }
        }

        val skip = frameLens[frameIndex] - (offset - initialOffset)

        stream.skip(skip.toLong())
        offset += skip
    }

    @Throws(java.io.IOException::class)
    fun startAtom(out: FileOutputStream, atomType: Int) {
        val atomHeader = ByteArray(8)

        atomMap[atomType]?.length?.let { atomLen ->
            atomHeader[0] = (atomLen shr 24 and 0xff).toByte()
            atomHeader[1] = (atomLen shr 16 and 0xff).toByte()
            atomHeader[2] = (atomLen shr 8 and 0xff).toByte()
            atomHeader[3] = (atomLen and 0xff).toByte()
            atomHeader[4] = (atomType shr 24 and 0xff).toByte()
            atomHeader[5] = (atomType shr 16 and 0xff).toByte()
            atomHeader[6] = (atomType shr 8 and 0xff).toByte()
            atomHeader[7] = (atomType and 0xff).toByte()
            out.write(atomHeader, 0, 8)
        }
    }

    @Throws(java.io.IOException::class)
    fun writeAtom(out: FileOutputStream, atomType: Int) {
        atomMap[atomType]?.let { atom ->
            startAtom(out, atomType)
            atom.data?.let { out.write(it, 0, atom.length - 8) }
        }
    }

    private fun setAtomData(atomType: Int, data: ByteArray) {
        var atom = atomMap[atomType]

        if (atom == null) {
            atom = Atom()
            atomMap[atomType] = atom
        }

        atom.length = data.size + 8
        atom.data = data
    }

    @Throws(java.io.IOException::class)
    override fun trimAudioFile(outputFile: File, startFrame: Int, frames: Int) {
        outputFile.createNewFile()

        inputFile?.let { file ->
            val fileInputStream = FileInputStream(file)
            val fileOutputStream = FileOutputStream(outputFile)

            setAtomData(
                kFTYP,
                byteArrayOf(
                    'M'.toByte(),
                    '4'.toByte(),
                    'A'.toByte(),
                    ' '.toByte(),
                    0,
                    0,
                    0,
                    0,
                    'M'.toByte(),
                    '4'.toByte(),
                    'A'.toByte(),
                    ' '.toByte(),
                    'm'.toByte(),
                    'p'.toByte(),
                    '4'.toByte(),
                    '2'.toByte(),
                    'i'.toByte(),
                    's'.toByte(),
                    'o'.toByte(),
                    'm'.toByte(),
                    0,
                    0,
                    0,
                    0
                )
            )

            setAtomData(
                kSTTS, byteArrayOf(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    1,
                    (frames shr 24 and 0xff).toByte(),
                    (frames shr 16 and 0xff).toByte(),
                    (frames shr 8 and 0xff).toByte(),
                    (frames and 0xff).toByte(),
                    (samplesPerFrame shr 24 and 0xff).toByte(),
                    (samplesPerFrame shr 16 and 0xff).toByte(),
                    (samplesPerFrame shr 8 and 0xff).toByte(),
                    (samplesPerFrame and 0xff).toByte()
                )
            )

            setAtomData(
                kSTSC, byteArrayOf(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    1,
                    0,
                    0,
                    0,
                    1,
                    (frames shr 24 and 0xff).toByte(),
                    (frames shr 16 and 0xff).toByte(),
                    (frames shr 8 and 0xff).toByte(),
                    (frames and 0xff).toByte(),
                    0,
                    0,
                    0,
                    1
                )
            )

            val stszData = ByteArray(12 + 4 * frames)

            stszData[8] = (frames shr 24 and 0xff).toByte()
            stszData[9] = (frames shr 16 and 0xff).toByte()
            stszData[10] = (frames shr 8 and 0xff).toByte()
            stszData[11] = (frames and 0xff).toByte()

            for (i in 0 until frames) {
                val frameLens = frameLens[startFrame + i]

                stszData[12 + 4 * i] = (frameLens shr 24 and 0xff).toByte()
                stszData[13 + 4 * i] = (frameLens shr 16 and 0xff).toByte()
                stszData[14 + 4 * i] = (frameLens shr 8 and 0xff).toByte()
                stszData[15 + 4 * i] = (frameLens and 0xff).toByte()
            }

            setAtomData(kSTSZ, stszData)

            val mdatOffset = 144 + 4 * frames +
                    (atomMap[kSTSD]?.length ?: 0) +
                    (atomMap[kSTSC]?.length ?: 0) +
                    (atomMap[kMVHD]?.length ?: 0) +
                    (atomMap[kTKHD]?.length ?: 0) +
                    (atomMap[kMDHD]?.length ?: 0) +
                    (atomMap[kHDLR]?.length ?: 0) +
                    (atomMap[kSMHD]?.length ?: 0) +
                    (atomMap[kDINF]?.length ?: 0)

            setAtomData(
                kSTCO, byteArrayOf(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    1,
                    (mdatOffset shr 24 and 0xff).toByte(),
                    (mdatOffset shr 16 and 0xff).toByte(),
                    (mdatOffset shr 8 and 0xff).toByte(),
                    (mdatOffset and 0xff).toByte()
                )
            )

            atomMap[kSTBL]?.length = 8 + (atomMap[kSTSD]?.length ?: 0) + (atomMap[kSTTS]?.length ?: 0) +
                    (atomMap[kSTSC]?.length ?: 0) + (atomMap[kSTSZ]?.length ?: 0) + (atomMap[kSTCO]?.length ?: 0)

            atomMap[kMINF]?.length = 8 + (atomMap[kDINF]?.length ?: 0) +
                    (atomMap[kSMHD]?.length ?: 0) + (atomMap[kSTBL]?.length ?: 0)

            atomMap[kMDIA]?.length = 8 + (atomMap[kMDHD]?.length ?: 0) +
                    (atomMap[kHDLR]?.length ?: 0) + (atomMap[kMINF]?.length ?: 0)

            atomMap[kTRAK]?.length = 8 + (atomMap[kTKHD]?.length ?: 0) + (atomMap[kMDIA]?.length ?: 0)

            atomMap[kMOOV]?.length = 8 + (atomMap[kMVHD]?.length ?: 0) + (atomMap[kTRAK]?.length ?: 0)

            var mdatLen = 8

            for (i in 0 until frames) {
                mdatLen += frameLens[startFrame + i]
            }

            atomMap[kMDAT]?.length = mdatLen

            writeAtom(fileOutputStream, kFTYP)
            startAtom(fileOutputStream, kMOOV)
            run {
                writeAtom(fileOutputStream, kMVHD)
                startAtom(fileOutputStream, kTRAK)
                run {
                    writeAtom(fileOutputStream, kTKHD)
                    startAtom(fileOutputStream, kMDIA)
                    run {
                        writeAtom(fileOutputStream, kMDHD)
                        writeAtom(fileOutputStream, kHDLR)
                        startAtom(fileOutputStream, kMINF)
                        run {
                            writeAtom(fileOutputStream, kDINF)
                            writeAtom(fileOutputStream, kSMHD)
                            startAtom(fileOutputStream, kSTBL)
                            run {
                                writeAtom(fileOutputStream, kSTSD)
                                writeAtom(fileOutputStream, kSTTS)
                                writeAtom(fileOutputStream, kSTSC)
                                writeAtom(fileOutputStream, kSTSZ)
                                writeAtom(fileOutputStream, kSTCO)
                            }
                        }
                    }
                }
            }

            startAtom(fileOutputStream, kMDAT)

            var maxFrameLen = 0

            for (i in 0 until frames) {
                if (frameLens[startFrame + i] > maxFrameLen)
                    maxFrameLen = frameLens[startFrame + i]
            }

            val buffer = ByteArray(maxFrameLen)
            var pos = 0

            for (i in 0 until frames) {
                val skip = frameOffsets[startFrame + i] - pos
                val len = frameLens[startFrame + i]

                if (skip < 0) {
                    continue
                }

                if (skip > 0) {
                    fileInputStream.skip(skip.toLong())
                    pos += skip
                }

                fileInputStream.read(buffer, 0, len)
                fileOutputStream.write(buffer, 0, len)
                pos += len
            }

            fileInputStream.close()
            fileOutputStream.close()
        }
    }
}

