package com.bvlmari.molanco.service

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import com.bvlmari.molanco.data.DatabaseHelper
import com.bvlmari.molanco.data.model.AudioFile
import java.io.IOException

class MediaPlayerService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var currentAudio: AudioFile? = null
    private lateinit var dbHelper: DatabaseHelper
    private val binder = MediaPlayerBinder()

    inner class MediaPlayerBinder : Binder() {
        fun getService(): MediaPlayerService = this@MediaPlayerService
    }

    override fun onCreate() {
        super.onCreate()
        dbHelper = DatabaseHelper(this)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun playAudio(audio: AudioFile) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audio.path)
                prepare()
                start()
            }
            currentAudio = audio
            dbHelper.updateLastPlayed(audio.path)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun pauseAudio() {
        mediaPlayer?.pause()
    }

    fun resumeAudio() {
        mediaPlayer?.start()
    }

    fun stopAudio() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentAudio = null
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    fun getCurrentAudio(): AudioFile? = currentAudio

    override fun onDestroy() {
        super.onDestroy()
        stopAudio()
    }
} 