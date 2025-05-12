package com.example.quaycamera

import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.lang.Exception

class VideoAdapter(
    private val videos: List<VideoItem>,
    private val onItemClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    inner class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.videoThumbnail)
        val title: TextView = view.findViewById(R.id.videoTitle)
        val dateText: TextView = view.findViewById(R.id.videoDate)

        init {
            view.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(videos[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos[position]
        holder.title.text = video.name
        holder.dateText.text = video.date
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val thumbnail = holder.itemView.context.contentResolver.loadThumbnail(
                    video.uri, android.util.Size(200, 200), null
                )
                holder.thumbnail.setImageBitmap(thumbnail)
            } else {
                val bitmap = MediaStore.Video.Thumbnails.getThumbnail(
                    holder.itemView.context.contentResolver,
                    ContentUris.parseId(video.uri),
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null
                )
                holder.thumbnail.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            holder.thumbnail.setImageResource(R.drawable.ic_video_thumbnail)
        }
    }

    override fun getItemCount() = videos.size
}
