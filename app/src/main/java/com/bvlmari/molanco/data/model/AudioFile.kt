package com.bvlmari.molanco.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AudioFile(
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
    var isFavorite: Boolean = false,
    val artworkUri: String? = null
) : Parcelable