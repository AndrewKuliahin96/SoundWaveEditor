package com.example.soundwaveeditor.soundfile

import com.example.soundwaveeditor.extensions.getInt
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException


class WavFile {
    companion object {
        private const val BUFFER_SIZE = 4096
        private const val FMT_CHUNK_ID = 0x20746D66
        private const val DATA_CHUNK_ID = 0x61746164
        private const val RIFF_CHUNK_ID = 0x46464952
        private const val RIFF_TYPE_ID = 0x45564157

        private fun getLE(buffer: ByteArray, pos: Int, numBytes: Int): Long {
            var position = pos
            var numberOfBytes = numBytes

            numberOfBytes--
            position += numberOfBytes

            var acc = (buffer.getInt(position) and 0xFF).toLong()

            for (b in 0 until numberOfBytes)
                acc = (acc shl 8) + (buffer.getInt(--position) and 0xFF)

            return acc
        }

        @Throws(IOException::class, WavFileException::class)
        fun openWavFile(file: File): WavFile {
            // Instantiate new Wavfile and store the file reference
            val wavFile = WavFile()
            wavFile.file = file

            // Create a new file input stream for reading file data
            wavFile.iStream = FileInputStream(file)

            // Read the first 12 bytes of the file
            var bytesRead = wavFile.buffer?.let { wavFile.iStream?.read(it, 0, 12) }

            if (bytesRead != 12) throw WavFileException("Not enough wav file bytes for header")

            // Extract parts from the header
            val riffChunkID = wavFile.buffer?.let { getLE(it, 0, 4) }
            var chunkSize = wavFile.buffer?.let { getLE(it, 4, 4) }
            val riffTypeID = wavFile.buffer?.let { getLE(it, 8, 4) }

            // Check the header bytes contains the correct signature
            if (riffChunkID != RIFF_CHUNK_ID.toLong())
                throw WavFileException("Invalid Wav Header data, incorrect riff chunk ID")
            if (riffTypeID != RIFF_TYPE_ID.toLong()) throw WavFileException("Invalid Wav Header data, incorrect riff type ID")

            // Check that the file size matches the number of bytes listed in header
            if (file.length() != chunkSize?.plus(8)) {
                throw WavFileException("Header chunk size (" + chunkSize + ") does not match file size (" + file.length() + ")")
            }

            wavFile.fileSize = chunkSize

            var foundFormat = false
            var foundData = false

            // Search for the Format and Data Chunks
            while (true) {
                // Read the first 8 bytes of the chunk (ID and chunk size)
                bytesRead = wavFile.buffer?.let { wavFile.iStream?.read(it, 0, 8) }

                if (bytesRead == -1) throw WavFileException("Reached end of file without finding format chunk")
                if (bytesRead != 8) throw WavFileException("Could not read chunk header")

                // Extract the chunk ID and Size
                val chunkID = wavFile.buffer?.let { getLE(it, 0, 4) }
                chunkSize = wavFile.buffer?.let { getLE(it, 4, 4) }

                // Word align the chunk size
                // chunkSize specifies the number of bytes holding data. However,
                // the data should be word aligned (2 bytes) so we need to calculate
                // the actual number of bytes in the chunk
                var numChunkBytes = if (chunkSize?.rem(2) == 1L) chunkSize + 1 else chunkSize

                if (chunkID == FMT_CHUNK_ID.toLong()) {
                    // Flag that the format chunk has been found
                    foundFormat = true

                    // Read in the header info
                    bytesRead = wavFile.buffer?.let { wavFile.iStream?.read(it, 0, 16) }

                    // Check this is uncompressed data
                    val compressionCode = wavFile.buffer?.let { getLE(it, 0, 2).toInt() }
                    if (compressionCode != 1)
                        throw WavFileException("Compression Code $compressionCode not supported")

                    // Extract the format information

                    wavFile.buffer?.let {
                        wavFile.numChannels = getLE(it, 2, 2).toInt()
                        wavFile.sampleRate = getLE(it, 4, 4)
                        wavFile.blockAlign = getLE(it, 12, 2).toInt()
                        wavFile.validBits = getLE(it, 14, 2).toInt()
                    }

                    if (wavFile.numChannels == 0)
                        throw WavFileException("Number of channels specified in header is equal to zero")
                    if (wavFile.blockAlign == 0)
                        throw WavFileException("Block Align specified in header is equal to zero")
                    if (wavFile.validBits < 2) throw WavFileException("Valid Bits specified in header is less than 2")
                    if (wavFile.validBits > 64)
                        throw WavFileException("Valid Bits specified in header is greater than 64, this is greater than a long can hold")

                    // Calculate the number of bytes required to hold 1 sample
                    wavFile.bytesPerSample = (wavFile.validBits + 7) / 8
                    if (wavFile.bytesPerSample * wavFile.numChannels != wavFile.blockAlign)
                        throw WavFileException("Block Align does not agree with bytes required for validBits and number of channels")

                    // Account for number of format bytes and then skip over
                    // any extra format bytes
                    numChunkBytes = numChunkBytes?.minus(16)

                    if (numChunkBytes != null && numChunkBytes > 0) {
                        wavFile.iStream?.skip(numChunkBytes)
                    }
                } else if (chunkID == DATA_CHUNK_ID.toLong()) {
                    // Check if we've found the format chunk,
                    // If not, throw an exception as we need the format information
                    // before we can read the data chunk
                    if (!foundFormat) throw WavFileException("Data chunk found before Format chunk")

                    // Check that the chunkSize (wav data length) is a multiple of the
                    // block align (bytes per frame)
                    if (chunkSize?.rem(wavFile.blockAlign) != 0L)
                        throw WavFileException("Data Chunk size is not multiple of Block Align")

                    // Calculate the number of frames
                    wavFile.numFrames = chunkSize / wavFile.blockAlign

                    // Flag that we've found the wave data chunk
                    foundData = true

                    break
                } else {
                    // If an unknown chunk ID is found, just skip over the chunk data
                    numChunkBytes?.let { wavFile.iStream?.skip(it) }
                }
            }

            // Throw an exception if no data chunk has been found
            if (!foundData) throw WavFileException("Did not find a data chunk")

            // Calculate the scaling factor for converting to a normalised double
            if (wavFile.validBits > 8) {
                // If more than 8 validBits, data is signed
                // Conversion required dividing by magnitude of max negative value
                wavFile.floatOffset = 0f
                wavFile.floatScale = (1 shl wavFile.validBits - 1).toFloat()
            } else {
                // Else if 8 or less validBits, data is unsigned
                // Conversion required dividing by max positive value
                wavFile.floatOffset = -1f
                wavFile.floatScale = 0.5f * ((1 shl wavFile.validBits) - 1)
            }

            wavFile.bufferPointer = 0
            wavFile.bytesRead = 0
            wavFile.frameCounter = 0
            wavFile.ioState = IOState.READING

            return wavFile
        }
    }

    private var file: File? = null                        // File that will be read from or written to
    private var ioState: IOState? = null                // Specifies the IO State of the Wav File (used for snaity checking)
    private var bytesPerSample = 0            // Number of bytes required to store a single sample
    private var numFrames = 0L                    // Number of frames within the data section
    private var oStream: FileOutputStream? = null    // Output stream used for writting data
    private var iStream: FileInputStream? = null        // Input stream used for reading data
    private var floatScale = 0F                // Scaling factor used for int <-> float conversion
    private var floatOffset = 0F      // Offset factor used for int <-> float conversion
    private val wordAlignAdjust = false        // Specify if an extra byte at the end of the data chunk is required for word alignment

    // Wav Header
    private var numChannels = 0                // 2 bytes unsigned, 0x0001 (1) to 0xFFFF (65,535)
    private var sampleRate = 0L                // 4 bytes unsigned, 0x00000001 (1) to 0xFFFFFFFF (4,294,967,295)
    // Although a java int is 4 bytes, it is signed, so need to use a long
    private var blockAlign = 0                    // 2 bytes unsigned, 0x0001 (1) to 0xFFFF (65,535)
    private var validBits = 0                    // 2 bytes unsigned, 0x0002 (2) to 0xFFFF (65,535)

    // Buffering
    private var buffer: ByteArray? = null                  // Local buffer used for IO
    private var bufferPointer = 0                // Points to the current position in local buffer
    private var bytesRead = 0                    // Bytes read after last read into local buffer
    private var frameCounter = 0L                // Current number of frames read or written
    private var fileSize = 0L

    // Cannot instantiate WavFile directly, must either use newWavFile() or openWavFile()
    init {
        buffer = ByteArray(BUFFER_SIZE)
    }

    fun getNumChannels() = numChannels

    fun getNumFrames() = numFrames

    fun getFramesRemaining() = numFrames - frameCounter

    fun getSampleRate() = sampleRate

    fun getValidBits() = validBits

    fun getDuration() = getNumFrames() / getSampleRate()

    fun getFileSize() = fileSize

    @Throws(IOException::class, WavFileException::class)
    private fun readSample(): Long {
        var acc: Long? = 0L

        for (b in 0 until bytesPerSample) {
            if (bufferPointer == bytesRead) {
                val read = buffer?.let { iStream?.read(it, 0, BUFFER_SIZE) }

                if (read == -1) throw WavFileException("Not enough data available")

                bytesRead = read ?: 0
                bufferPointer = 0
            }

            var v = buffer?.get(bufferPointer)?.toInt()

            if (b < bytesPerSample - 1 || bytesPerSample == 1) v = v?.and(0xFF)
            acc = (v?.shl(b * 8))?.toLong()?.let { acc?.plus(it) }

            bufferPointer++
        }

        return acc ?: 0L
    }

    @Throws(IOException::class, WavFileException::class)
    fun readFrames(sampleBuffer: FloatArray, numFramesToRead: Int): Int {
        return readFramesInternal(sampleBuffer, 0, numFramesToRead)
    }

    @Throws(IOException::class, WavFileException::class)
    private fun readFramesInternal(sampleBuffer: FloatArray, offset: Int, numFramesToRead: Int): Int {
        var off = offset
        if (ioState != IOState.READING) throw IOException("Cannot read from WavFile instance")

        for (f in 0 until numFramesToRead) {
            if (frameCounter == numFrames) return f

            for (c in 0 until numChannels) {
                sampleBuffer[off] = floatOffset + readSample().toFloat() / floatScale
                off++
            }

            frameCounter++
        }

        return numFramesToRead
    }

    @Throws(IOException::class, WavFileException::class)
    fun readFrames(sampleBuffer: IntArray, numFramesToRead: Int): Int {
        return readFramesInternal(sampleBuffer, 0, numFramesToRead)
    }

    @Throws(IOException::class, WavFileException::class)
    private fun readFramesInternal(sampleBuffer: IntArray, offset: Int, numFramesToRead: Int): Int {
        var off = offset
        if (ioState != IOState.READING) throw IOException("Cannot read from WavFile instance")

        for (f in 0 until numFramesToRead) {
            if (frameCounter == numFrames) return f

            for (c in 0 until numChannels) {
                sampleBuffer[off] = readSample().toInt()
                off++
            }

            frameCounter++
        }

        return numFramesToRead
    }

    @Throws(IOException::class)
    fun close() {
        // Close the input stream and set to null
        if (iStream != null) {
            iStream?.close()
            iStream = null
        }

        if (oStream != null) {
            // Write out anything still in the local buffer
            if (bufferPointer > 0) buffer?.let { oStream?.write(it, 0, bufferPointer) }

            // If an extra byte is required for word alignment, add it to the end
            if (wordAlignAdjust) oStream?.write(0)

            // Close the stream and set to null
            oStream?.close()
            oStream = null
        }

        // Flag that the stream is closed
        ioState = IOState.CLOSED
    }

    private enum class IOState { READING, WRITING, CLOSED }
}
