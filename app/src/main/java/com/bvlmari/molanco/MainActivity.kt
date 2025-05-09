package com.bvlmari.molanco

import android.content.Intent
import android.media.Image
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
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
}