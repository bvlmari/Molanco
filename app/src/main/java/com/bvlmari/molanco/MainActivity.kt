package com.bvlmari.molanco

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bvlmari.molanco.data.model.AudioFile
import com.bvlmari.molanco.ui.main.AudioAdapter

class MainActivity : AppCompatActivity() {
    lateinit var mediaObserver: ContentObserver
    lateinit var recyclerView: RecyclerView
    private val PERMISSION_REQUIRED_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver
        )

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        checkAndRequestPermission()
        recyclerView = findViewById(R.id.audioRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val audioList = scanAudioFiles(this)
        recyclerView.adapter = AudioAdapter(audioList)
        val buttonBack = findViewById<ImageButton>(R.id.buttonBack)
        val buttonFavorites = findViewById<ImageButton>(R.id.buttonFavorites)
        val buttonOptions = findViewById<ImageButton>(R.id.buttonOptions)
        buttonBack.setOnClickListener{
            finish()
        }
        buttonFavorites.setOnClickListener{
            val intent = Intent(this, FavoritesActivity::class.java)
            startActivity(intent)
        }
        buttonOptions.setOnClickListener{
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Yooo")
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
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
        val audioList = scanAudioFiles(this)
        recyclerView.adapter = AudioAdapter(audioList)
    }

    fun scanAudioFiles(context: Context): List<AudioFile>{
        val audioList = mutableListOf<AudioFile>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val cursor = context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            null
        )

        cursor?.use {
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (it.moveToNext()) {
                val path = it.getString(pathColumn)
                val title = it.getString(titleColumn)
                val artist = it.getString(artistColumn)
                val album = it.getString(albumColumn)
                val duration = it.getInt(durationColumn)

                audioList.add(
                    AudioFile(
                        path = path,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration
                    )
                )
            }
        }
        return audioList
    }

    fun syncDatabase(scannedList: List<AudioFile>) {

    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(mediaObserver)
    }
}