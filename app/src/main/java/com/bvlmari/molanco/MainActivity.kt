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
        
        val buttonFavorites = findViewById<ImageButton>(R.id.buttonFavorites)
        
        buttonFavorites.setOnClickListener {
            val intent = Intent(this, FavoritesActivity::class.java)
            startActivity(intent)
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
        val permissions = arrayOf(
            android.Manifest.permission.READ_MEDIA_AUDIO,
            android.Manifest.permission.READ_MEDIA_IMAGES
        )
        
        val notGrantedPermissions = permissions.filter { 
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED 
        }.toTypedArray()

        if (notGrantedPermissions.isNotEmpty()) {
            requestPermissions(notGrantedPermissions, PERMISSION_REQUIRED_CODE)
        } else {
            loadAudioFiles()
            handleIncomingIntent()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUIRED_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                loadAudioFiles()
                handleIncomingIntent()
            } else {
                Toast.makeText(this, "Permissions denied. Some features may not work properly.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleIncomingIntent() {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            val uri = intent.data!!
            val audioFile = getAudioFileFromUri(uri)
            if (audioFile != null) {
                // Launch PlayerActivity directly with the audio file
                val playerIntent = Intent(this, PlayerActivity::class.java)
                playerIntent.putExtra("audio", audioFile)
                startActivity(playerIntent)
            }
        }
    }

    private fun getAudioFileFromUri(uri: Uri): AudioFile? {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        return try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: uri.lastPathSegment ?: "Unknown Title"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val album = cursor.getString(albumColumn) ?: "Unknown Album"
                    val duration = cursor.getInt(durationColumn)
                    val path = cursor.getString(dataColumn) ?: uri.path ?: return null

                    // Get artwork URI using the media ID
                    val artworkUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    AudioFile(
                        path = path,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        artworkUri = artworkUri
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error getting audio file from URI: ${e.message}")
            // Fallback to basic file information
            uri.path?.let { path ->
                AudioFile(
                    path = path,
                    title = uri.lastPathSegment ?: "Unknown Title",
                    artist = "Unknown Artist",
                    album = "Unknown Album",
                    duration = 0
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent()
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
            val idColumn = it.getColumnIndexOrThrow(Media._ID)
            val pathColumn = it.getColumnIndexOrThrow(Media.DATA)
            val titleColumn = it.getColumnIndexOrThrow(Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(Media.ARTIST)
            val albumColumn = it.getColumnIndexOrThrow(Media.ALBUM)
            val durationColumn = it.getColumnIndexOrThrow(Media.DURATION)
            val albumIdColumn = it.getColumnIndexOrThrow(Media.ALBUM_ID)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val path = it.getString(pathColumn)
                val title = it.getString(titleColumn)
                val artist = it.getString(artistColumn)
                val album = it.getString(albumColumn)
                val duration = it.getInt(durationColumn)
                val albumId = it.getLong(albumIdColumn)

                // Get album artwork URI using the modern MediaStore approach
                val artworkUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                ).toString()

                audioList.add(
                    AudioFile(
                        path = path,
                        title = title ?: "Unknown Title",
                        artist = artist ?: "Unknown Artist",
                        album = album ?: "Unknown Album",
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