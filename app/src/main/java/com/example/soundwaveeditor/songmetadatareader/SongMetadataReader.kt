package com.example.soundwaveeditor.songmetadatareader

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.soundwaveeditor.extensions.withNotNull
import java.lang.ref.WeakReference


class SongMetadataReader(private val weakContext: WeakReference<Context>, private val filename: String) {

    companion object {
        private const val EMPTY_STRING = ""
        private const val NO_YEAR = -1
    }

    var title = EMPTY_STRING
    var artist = EMPTY_STRING
    var album = EMPTY_STRING
    var genre = EMPTY_STRING
    var year = NO_YEAR

    init {
        title = getBasename(filename)

        try {
            readMetadata()
        } catch (e: Exception) {
            e.message?.let { Log.e("SoundWaveEditor", it) }
        }
    }

    private fun readMetadata() {
        val genreIdMap = HashMap<String, String>()

        weakContext.get()?.let { context ->
            context.createCursor(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME)) {
                moveToFirst()

                while (!isAfterLast) {
                    genreIdMap[getString(0)] = getString(1)
                    moveToNext()
                }
            }

            genreIdMap.keys.firstOrNull { key->
                context.createCursor(makeGenreUri(key),
                    arrayOf(MediaStore.Audio.Media.DATA),
                    "${MediaStore.Audio.Media.DATA} LIKE \"$filename\"") {
                    (count != 0).apply {
                        genreIdMap[key]?.let { genre = it }
                        close()
                    }
                }

                true
            }

            MediaStore.Audio.Media.getContentUriForPath(filename)?.let { uri ->
                context.createCursor(
                    uri,
                    arrayOf(
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.YEAR,
                        MediaStore.Audio.Media.DATA
                    ),
                    MediaStore.Audio.Media.DATA + " LIKE \"" + filename + "\"") {
                    takeIf { count > 0 }?.let {
                        moveToFirst()

                        getStringFromColumn(this, MediaStore.Audio.Media.TITLE)?.takeIf {
                            it.isEmpty()
                        }?.let {
                            title = it
                        } ?: getBasename(filename)

                        getStringFromColumn(this, MediaStore.Audio.Media.ARTIST)?.let {
                            artist = it
                        }
                        getStringFromColumn(this, MediaStore.Audio.Media.ALBUM)?.let {
                            album = it
                        }
                        year = getIntegerFromColumn(this)
                    } ?: let {
                        title = getBasename(filename)
                    }
                }
            }
        }
    }

    private fun makeGenreUri(genreId: String) = Uri.parse(
        StringBuilder()
            .append(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI)
            .append("/")
            .append(genreId)
            .append("/")
            .append(MediaStore.Audio.Genres.Members.CONTENT_DIRECTORY)
            .toString()
    )

    private fun getStringFromColumn(cursor: Cursor, columnName: String) =
        cursor.getString(cursor.getColumnIndexOrThrow(columnName))?.takeIf { it.isNotEmpty() }

    private fun getIntegerFromColumn(cursor: Cursor) =
        cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR))

    private fun getBasename(filename: String) =
        filename.substring(filename.lastIndexOf('/') + 1, filename.lastIndexOf('.'))

    private fun Context.createCursor(
        uri: Uri,
        projection: Array<String>? = null,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null,
        cursorCallback: Cursor.() -> Unit
    ) = withNotNull(this.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)) {
        cursorCallback(this)
        takeUnless { isClosed }?.let { close() }
    }
}
