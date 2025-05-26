package com.bvlmari.molanco

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bvlmari.molanco.data.DatabaseHelper
import com.bvlmari.molanco.data.model.AudioFile
import com.bvlmari.molanco.service.MediaPlayerService
import com.bvlmari.molanco.ui.main.AudioAdapter

class FavoritesActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var dbHelper: DatabaseHelper
    private var mediaPlayerService: MediaPlayerService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaPlayerService.MediaPlayerBinder
            mediaPlayerService = binder.getService()
            bound = true
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
        setContentView(R.layout.activity_favorites)
        
        // Initialize views first
        recyclerView = findViewById(R.id.favoritesRecyclerView)
        setupRecyclerView()
        
        // Then load the data
        loadFavorites()

        val buttonBack = findViewById<ImageButton>(R.id.buttonBack)
        buttonBack.setOnClickListener {
            finish()
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

    private fun setupRecyclerView() {
        val isTablet = resources.getBoolean(R.bool.isTablet)
        val orientation = resources.configuration.orientation
        
        recyclerView.layoutManager = if (isTablet) {
            val spanCount = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2
            GridLayoutManager(this, spanCount)
        } else {
            LinearLayoutManager(this)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupRecyclerView()
    }

    private fun loadFavorites() {
        val favorites = dbHelper.getFavorites()
        recyclerView.adapter = AudioAdapter(favorites) { audio ->
            onAudioClick(audio)
        }
    }

    private fun onAudioClick(audio: AudioFile) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("audio", audio)
        startActivity(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mediaPlayerService?.getCurrentAudio()?.let { audio ->
            outState.putParcelable("current_audio", audio)
            outState.putBoolean("was_playing", mediaPlayerService?.isPlaying() ?: false)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val audio = savedInstanceState.getParcelable<AudioFile>("current_audio")
        val wasPlaying = savedInstanceState.getBoolean("was_playing", false)
        
        audio?.let {
            if (wasPlaying) {
                mediaPlayerService?.playAudio(it)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }
}