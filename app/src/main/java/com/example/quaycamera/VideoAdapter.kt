package com.example.quaycamera

import android.content.ContentUris
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.quaycamera.databinding.ItemVideoBinding
import kotlinx.coroutines.*
import java.io.File

class VideoAdapter(
    private val videos: List<VideoItem>,
    private val onItemClick: (VideoItem) -> Unit,
    private val onItemLongClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val thumbnailCache = mutableMapOf<String, Bitmap?>()

    inner class VideoViewHolder(val binding: ItemVideoBinding) : RecyclerView.ViewHolder(binding.root) {
        var thumbnailJob: Job? = null

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(videos[position])
                }
            }

            binding.root.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemLongClick(videos[position])
                    true
                } else {
                    false
                }
            }
        }

        fun bind(video: VideoItem) {
            binding.videoTitle.text = video.name
            binding.videoDate.text = video.date
            binding.videoThumbnail.setImageResource(R.drawable.ic_video_thumbnail)
            binding.playButton.visibility = android.view.View.VISIBLE

            thumbnailJob?.cancel()

            thumbnailJob = coroutineScope.launch {
                val uri = video.uri.toString()
                var bitmap = thumbnailCache[uri]

                if (bitmap == null) {
                    bitmap = withContext(Dispatchers.IO) {
                        loadVideoThumbnail(video)
                    }
                    thumbnailCache[uri] = bitmap
                }

                if (bitmap != null && isActive) {
                    Glide.with(binding.root.context)
                        .load(bitmap)
                        .apply(RequestOptions()
                            .centerCrop()
                            .placeholder(R.drawable.ic_video_thumbnail)
                        )
                        .into(binding.videoThumbnail)
                }

                val duration = withContext(Dispatchers.IO) {
                    getVideoDuration(video)
                }
                if (isActive) {
                    binding.videoDuration.text = duration
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(videos[position])
    }

    override fun getItemCount() = videos.size

    override fun onViewRecycled(holder: VideoViewHolder) {
        super.onViewRecycled(holder)
        holder.thumbnailJob?.cancel()
        holder.thumbnailJob = null
        holder.binding.videoThumbnail.setImageDrawable(null)
    }

    private fun loadVideoThumbnail(videoItem: VideoItem): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()

            when {
                videoItem.uri.scheme == "content" -> {
                    retriever.setDataSource(videoItem.uri.toString())
                }
                videoItem.uri.scheme == "file" -> {
                    val path = videoItem.uri.path
                    if (path != null) {
                        retriever.setDataSource(path)
                    }
                }
                else -> {
                    retriever.setDataSource(videoItem.uri.toString())
                }
            }
            retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getVideoDuration(videoItem: VideoItem): String {
        return try {
            val retriever = MediaMetadataRetriever()

            when {
                videoItem.uri.scheme == "content" -> {
                    retriever.setDataSource(videoItem.uri.toString())
                }
                videoItem.uri.scheme == "file" -> {
                    val path = videoItem.uri.path
                    if (path != null) {
                        retriever.setDataSource(path)
                    }
                }
                else -> {
                    retriever.setDataSource(videoItem.uri.toString())
                }
            }

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            val seconds = (durationMs / 1000) % 60
            val minutes = (durationMs / (1000 * 60)) % 60
            val hours = durationMs / (1000 * 60 * 60)

            if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            "--:--"
        }
    }

    fun cleanup() {
        coroutineScope.cancel()
        thumbnailCache.clear()
    }
}