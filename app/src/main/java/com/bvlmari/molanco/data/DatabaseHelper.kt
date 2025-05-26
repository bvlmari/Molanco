package com.bvlmari.molanco.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.bvlmari.molanco.data.model.AudioFile

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "MolancoDb"
        private const val DATABASE_VERSION = 1

        // Table Audio Files
        private const val TABLE_AUDIO = "audio_files"
        private const val COLUMN_ID = "id"
        private const val COLUMN_PATH = "path"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_ARTIST = "artist"
        private const val COLUMN_ALBUM = "album"
        private const val COLUMN_DURATION = "duration"
        private const val COLUMN_IS_FAVORITE = "is_favorite"
        private const val COLUMN_LAST_PLAYED = "last_played"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val CREATE_AUDIO_TABLE = """
            CREATE TABLE $TABLE_AUDIO (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_PATH TEXT UNIQUE,
                $COLUMN_TITLE TEXT,
                $COLUMN_ARTIST TEXT,
                $COLUMN_ALBUM TEXT,
                $COLUMN_DURATION INTEGER,
                $COLUMN_IS_FAVORITE INTEGER DEFAULT 0,
                $COLUMN_LAST_PLAYED INTEGER DEFAULT 0
            )
        """.trimIndent()
        
        db.execSQL(CREATE_AUDIO_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_AUDIO")
        onCreate(db)
    }

    fun addAudioFile(audioFile: AudioFile) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PATH, audioFile.path)
            put(COLUMN_TITLE, audioFile.title)
            put(COLUMN_ARTIST, audioFile.artist)
            put(COLUMN_ALBUM, audioFile.album)
            put(COLUMN_DURATION, audioFile.duration)
        }
        
        db.insertWithOnConflict(TABLE_AUDIO, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
    }

    fun getAllAudioFiles(): List<AudioFile> {
        val audioList = mutableListOf<AudioFile>()
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_AUDIO,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_TITLE ASC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                val path = it.getString(it.getColumnIndexOrThrow(COLUMN_PATH))
                val title = it.getString(it.getColumnIndexOrThrow(COLUMN_TITLE))
                val artist = it.getString(it.getColumnIndexOrThrow(COLUMN_ARTIST))
                val album = it.getString(it.getColumnIndexOrThrow(COLUMN_ALBUM))
                val duration = it.getInt(it.getColumnIndexOrThrow(COLUMN_DURATION))
                val isFavorite = it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_FAVORITE)) == 1
                
                audioList.add(AudioFile(path, title, artist, album, duration, isFavorite))
            }
        }
        return audioList
    }

    fun getFavorites(): List<AudioFile> {
        val audioList = mutableListOf<AudioFile>()
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_AUDIO,
            null,
            "$COLUMN_IS_FAVORITE = 1",
            null,
            null,
            null,
            "$COLUMN_TITLE ASC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                val path = it.getString(it.getColumnIndexOrThrow(COLUMN_PATH))
                val title = it.getString(it.getColumnIndexOrThrow(COLUMN_TITLE))
                val artist = it.getString(it.getColumnIndexOrThrow(COLUMN_ARTIST))
                val album = it.getString(it.getColumnIndexOrThrow(COLUMN_ALBUM))
                val duration = it.getInt(it.getColumnIndexOrThrow(COLUMN_DURATION))
                
                audioList.add(AudioFile(path, title, artist, album, duration, true))
            }
        }
        return audioList
    }

    fun toggleFavorite(path: String) {
        val db = this.writableDatabase
        val cursor = db.query(
            TABLE_AUDIO,
            arrayOf(COLUMN_IS_FAVORITE),
            "$COLUMN_PATH = ?",
            arrayOf(path),
            null,
            null,
            null
        )

        if (cursor?.moveToFirst() == true) {
            val currentValue = cursor.getInt(0)
            val values = ContentValues().apply {
                put(COLUMN_IS_FAVORITE, if (currentValue == 0) 1 else 0)
            }
            db.update(TABLE_AUDIO, values, "$COLUMN_PATH = ?", arrayOf(path))
        }
        cursor?.close()
        db.close()
    }

    fun updateLastPlayed(path: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_LAST_PLAYED, System.currentTimeMillis())
        }
        db.update(TABLE_AUDIO, values, "$COLUMN_PATH = ?", arrayOf(path))
        db.close()
    }
} 