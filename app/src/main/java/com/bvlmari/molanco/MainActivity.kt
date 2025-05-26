package com.bvlmari.molanco

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
import android.provider.MediaStore.Audio.Media.IS_MUSIC
import android.provider.MediaStore.Audio.Media.TITLE
import android.provider.MediaStore.Audio.Media.ARTIST
import android.provider.MediaStore.Audio.Media.ALBUM
import android.provider.MediaStore.Audio.Media.DURATION
import android.provider.MediaStore.Audio.Media.ALBUM_ID
//import android.provider.MediaStore.Audio.Media.CONTENT_URI
import android.provider.MediaStore.Audio.Media.CONTENT_TYPE
//import android.provider.MediaStore.Audio.Media.CONTENT_ITEM_TYPE
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
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

class MainActivity : AppCompatActivity() {
    private lateinit var mediaObserver: ContentObserver
    private lateinit var recyclerView: RecyclerView
    private lateinit var dbHelper: DatabaseHelper
    private var mediaPlayerService: MediaPlayerService? = null
    private var bound = false
    private val PERMISSION_REQUIRED_CODE = 100

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
        
        val handler = Handler(Looper.getMainLooper())
        mediaObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                Log.d("MediaObserver", "MediaStore changed! Rescanning...")
                val newAudioList = scanAudioFiles(this@MainActivity)
                syncDatabase(newAudioList)
            }
        }

        contentResolver.registerContentObserver(
            Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver
        )

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        // Initialize views first
        recyclerView = findViewById(R.id.audioRecyclerView)
        setupRecyclerView()
        
        // Then check permissions which will load the data
        checkAndRequestPermission()
        
        val buttonBack = findViewById<ImageButton>(R.id.buttonBack)
        val buttonFavorites = findViewById<ImageButton>(R.id.buttonFavorites)
        val buttonOptions = findViewById<ImageButton>(R.id.buttonOptions)
        
        buttonBack.setOnClickListener {
            finish()
        }
        
        buttonFavorites.setOnClickListener {
            val intent = Intent(this, FavoritesActivity::class.java)
            startActivity(intent)
        }
        
        buttonOptions.setOnClickListener {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Check out this awesome music player!")
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, "Share via"))
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

    private fun checkAndRequestPermission() {
        if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO),
                PERMISSION_REQUIRED_CODE
            )
        } else {
            loadAudioFiles()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUIRED_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadAudioFiles()
            } else {
                Toast.makeText(this, "Permission denied. Cannot load audio files.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadAudioFiles() {
        val audioList = dbHelper.getAllAudioFiles()
        if (audioList.isEmpty()) {
            val scannedFiles = scanAudioFiles(this)
            syncDatabase(scannedFiles)
            recyclerView.adapter = AudioAdapter(scannedFiles) { audio ->
                onAudioClick(audio)
            }
        } else {
            recyclerView.adapter = AudioAdapter(audioList) { audio ->
                onAudioClick(audio)
            }
        }
    }

    private fun onAudioClick(audio: AudioFile) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("audio", audio)
        startActivity(intent)
    }

    private fun scanAudioFiles(context: Context): List<AudioFile> {
        val audioList = mutableListOf<AudioFile>()
        val collection = Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            Media._ID,
            Media.DATA,
            Media.TITLE,
            Media.ARTIST,
            Media.ALBUM,
            Media.DURATION,
            Media.ALBUM_ID
        )

        val selection = "${Media.IS_MUSIC} != 0"
        val cursor = context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            null
        )

        cursor?.use {
            val pathColumn = it.getColumnIndexOrThrow(Media.DATA)
            val titleColumn = it.getColumnIndexOrThrow(Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(Media.ARTIST)
            val albumColumn = it.getColumnIndexOrThrow(Media.ALBUM)
            val durationColumn = it.getColumnIndexOrThrow(Media.DURATION)
            val albumIdColumn = it.getColumnIndexOrThrow(Media.ALBUM_ID)

            while (it.moveToNext()) {
                val path = it.getString(pathColumn)
                val title = it.getString(titleColumn)
                val artist = it.getString(artistColumn)
                val album = it.getString(albumColumn)
                val duration = it.getInt(durationColumn)
                val albumId = it.getLong(albumIdColumn)

                // Get album artwork URI
                val artworkUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                ).toString()

                audioList.add(
                    AudioFile(
                        path = path,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        artworkUri = artworkUri
                    )
                )
            }
        }
        return audioList
    }

    private fun syncDatabase(scannedList: List<AudioFile>) {
        scannedList.forEach { audio ->
            dbHelper.addAudioFile(audio)
        }
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
        contentResolver.unregisterContentObserver(mediaObserver)
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }
}