package com.example.soundwaveeditor.soundfile

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import java.lang.ref.WeakReference
import java.util.HashMap


class SongMetadataReader(private var weakContext: WeakReference<Context>, var filename: String) {
    companion object {
        private var GENRES_URI = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
    }

    var title: String? = null
    var artist: String? = null
    var album: String? = null
    var year: Int? = null

    init {
        title = getBasename(filename)
        readMetadata()
    }

    private fun readMetadata() {
        val genreIdMap = HashMap<String, String>()

        weakContext.get()?.let { context ->
            context.contentResolver?.query(
            GENRES_URI,
            arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
            null,
            null,
            null
        )?.use { cursor ->
                cursor.moveToFirst()

                while (!cursor.isAfterLast) {
                    genreIdMap[cursor.getString(0)] = cursor.getString(1)
                    cursor.moveToNext()
                }

                cursor.close()

                MediaStore.Audio.Media.getContentUriForPath(filename)?.let { uri ->
                    context.contentResolver.query(
                        uri,
                        arrayOf(
                            MediaStore.Audio.Media._ID,
                            MediaStore.Audio.Media.TITLE,
                            MediaStore.Audio.Media.ARTIST,
                            MediaStore.Audio.Media.ALBUM,
                            MediaStore.Audio.Media.YEAR,
                            MediaStore.Audio.Media.DATA
                        ),
                        "${MediaStore.Audio.Media.DATA} LIKE \"$filename\"",
                        null,
                        null
                    )?.use { c ->
                        if (c.count == 0) return

                        c.moveToFirst()

                        getStringFromColumn(c, MediaStore.Audio.Media.TITLE)
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { songTitle -> title = songTitle }

                        getStringFromColumn(c, MediaStore.Audio.Media.ARTIST)?.let { artist =  it }
                        getStringFromColumn(c, MediaStore.Audio.Media.ALBUM)?.let { album = it }
                        year = c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR))
                    }
                }
            }
        }
    }

    private fun getStringFromColumn(cursor: Cursor, columnName: String) =
        cursor.getString(cursor.getColumnIndexOrThrow(columnName))
            .takeIf { it.isNotEmpty() }?.let { it }

    private fun getBasename(filename: String) =
        filename.substring(filename.lastIndexOf('/') + 1, filename.lastIndexOf('.'))
}

