package com.bvlmari.molanco.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.bvlmari.molanco.R
import androidx.recyclerview.widget.RecyclerView
import com.bvlmari.molanco.data.model.AudioFile

class AudioAdapter(private val audioList: List<AudioFile>) : RecyclerView.Adapter<AudioAdapter.AudioViewHolder>() {

    class AudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val titleText: TextView = itemView.findViewById(R.id.songTitle)
        val artistText: TextView = itemView.findViewById(R.id.songArtist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_audio, parent, false)
        return AudioViewHolder(view)
    }

    override fun onBindViewHolder(holder: AudioViewHolder, position: Int) {
        val audio = audioList[position]
        holder.titleText.text = audio.title
        holder.artistText.text = audio.artist
    }

    override fun getItemCount(): Int = audioList.size
}