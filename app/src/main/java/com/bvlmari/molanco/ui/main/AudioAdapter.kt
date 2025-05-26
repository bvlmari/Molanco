package com.bvlmari.molanco.ui.main

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bvlmari.molanco.R
import com.bvlmari.molanco.data.DatabaseHelper
import com.bvlmari.molanco.data.model.AudioFile

class AudioAdapter(
    private val audioFiles: List<AudioFile>,
    private val onItemClick: (AudioFile) -> Unit
) : RecyclerView.Adapter<AudioAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val artwork: ImageView = view.findViewById(R.id.imageArtwork)
        val title: TextView = view.findViewById(R.id.textTitle)
        val artist: TextView = view.findViewById(R.id.textArtist)
        val favorite: ImageButton = view.findViewById(R.id.buttonFavorite)
        val dbHelper = DatabaseHelper(view.context)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_audio, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val audio = audioFiles[position]
        holder.title.text = audio.title
        holder.artist.text = audio.artist
        holder.favorite.setImageResource(
            if (audio.isFavorite) R.drawable.ic_favorite_filled
            else R.drawable.ic_favorite_border
        )

        if (!audio.artworkUri.isNullOrEmpty()) {
            Glide.with(holder.artwork)
                .load(Uri.parse(audio.artworkUri))
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.bg_artwork_placeholder)
                .error(R.drawable.bg_artwork_placeholder)
                .into(holder.artwork)
        } else {
            holder.artwork.setImageResource(R.drawable.bg_artwork_placeholder)
        }

        holder.itemView.setOnClickListener {
            onItemClick(audio)
        }

        holder.favorite.setOnClickListener {
            holder.dbHelper.toggleFavorite(audio.path)
            audio.isFavorite = !audio.isFavorite
            holder.favorite.setImageResource(
                if (audio.isFavorite) R.drawable.ic_favorite_filled
                else R.drawable.ic_favorite_border
            )
        }
    }

    override fun getItemCount() = audioFiles.size
}