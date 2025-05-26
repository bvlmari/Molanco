package com.bvlmari.molanco

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bvlmari.molanco.data.DatabaseHelper
import com.bvlmari.molanco.data.model.AudioFile
import com.bvlmari.molanco.service.MediaPlayerService
import com.bumptech.glide.Glide
import java.io.File

class PlayerActivity : AppCompatActivity() {
    private lateinit var imageArtwork: ImageView
    private lateinit var textTitle: TextView
    private lateinit var textArtist: TextView
    private lateinit var buttonPlayPause: ImageButton
    private lateinit var buttonFavorite: ImageButton
    private lateinit var buttonShare: ImageButton
    private lateinit var dbHelper: DatabaseHelper
    private var mediaPlayerService: MediaPlayerService? = null
    private var currentAudio: AudioFile? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaPlayerService.MediaPlayerBinder
            mediaPlayerService = binder.getService()
            bound = true
            updatePlaybackState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mediaPlayerService = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbHelper = DatabaseHelper(this)
        
        enableEdgeToEdge()
        setContentView(R.layout.activity_player)

        // Initialize views
        imageArtwork = findViewById(R.id.imageArtwork)
        textTitle = findViewById(R.id.textTitle)
        textArtist = findViewById(R.id.textArtist)
        buttonPlayPause = findViewById(R.id.buttonPlayPause)
        buttonFavorite = findViewById(R.id.buttonFavorite)
        buttonShare = findViewById(R.id.buttonShare)
        val buttonBack = findViewById<ImageButton>(R.id.buttonBack)

        // Get audio file from intent
        currentAudio = intent.getParcelableExtra("audio")
        currentAudio?.let { audio ->
            textTitle.text = audio.title
            textArtist.text = audio.artist
            updateFavoriteButton(audio.isFavorite)

            // Load artwork
            if (!audio.artworkUri.isNullOrEmpty()) {
                Glide.with(this)
                    .load(Uri.parse(audio.artworkUri))
                    .placeholder(R.drawable.bg_artwork_placeholder)
                    .error(R.drawable.bg_artwork_placeholder)
                    .into(imageArtwork)
            } else {
                imageArtwork.setImageResource(R.drawable.bg_artwork_placeholder)
            }
        }

        // Set click listeners
        buttonBack.setOnClickListener {
            finish()
        }

        imageArtwork.setOnClickListener {
            togglePlayback()
        }

        buttonPlayPause.setOnClickListener {
            togglePlayback()
        }

        buttonFavorite.setOnClickListener {
            currentAudio?.let { audio ->
                dbHelper.toggleFavorite(audio.path)
                audio.isFavorite = !audio.isFavorite
                updateFavoriteButton(audio.isFavorite)
            }
        }

        buttonShare.setOnClickListener {
            currentAudio?.let { audio ->
                val file = File(audio.path)
                val uri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    file
                )
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TITLE, audio.title)
                    putExtra(Intent.EXTRA_TEXT, "Check out this song: ${audio.title} by ${audio.artist}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share audio via"))
            }
        }

        // Bind to MediaPlayerService
        Intent(this, MediaPlayerService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun togglePlayback() {
        mediaPlayerService?.let { service ->
            currentAudio?.let { audio ->
                if (service.getCurrentAudio()?.path == audio.path && service.isPlaying()) {
                    service.pauseAudio()
                } else if (service.getCurrentAudio()?.path == audio.path) {
                    service.resumeAudio()
                } else {
                    service.playAudio(audio)
                }
                updatePlaybackState()
            }
        }
    }

    private fun updatePlaybackState() {
        mediaPlayerService?.let { service ->
            currentAudio?.let { audio ->
                val isPlaying = service.getCurrentAudio()?.path == audio.path && service.isPlaying()
                buttonPlayPause.setImageResource(
                    if (isPlaying) R.drawable.ic_pause
                    else R.drawable.ic_play
                )
            }
        }
    }

    private fun updateFavoriteButton(isFavorite: Boolean) {
        buttonFavorite.setImageResource(
            if (isFavorite) R.drawable.ic_favorite_filled
            else R.drawable.ic_favorite_border
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }
} 