package com.example.quaycamera

import android.content.ContentUris
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.*
import java.io.File

class VideoAdapter(
    private val videos: List<VideoItem>,
    private val onItemClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    // Coroutine scope for thumbnail loading
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Cache để lưu trữ thumbnails đã tải
    private val thumbnailCache = mutableMapOf<String, Bitmap?>()

    inner class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.videoThumbnail)
        val title: TextView = view.findViewById(R.id.videoTitle)
        val dateText: TextView = view.findViewById(R.id.videoDate)
        val duration: TextView = view.findViewById(R.id.videoDuration)
        val playButton: ImageView = view.findViewById(R.id.playButton)

        // Job theo dõi để hủy khi không cần thiết
        var thumbnailJob: Job? = null

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

        // Thiết lập thông tin cơ bản
        holder.title.text = video.name
        holder.dateText.text = video.date

        // Hiển thị placeholder và thêm hiệu ứng loading
        holder.thumbnail.setImageResource(R.drawable.ic_video_thumbnail)
        holder.playButton.visibility = View.VISIBLE

        // Hủy bỏ job tải thumbnail trước đó nếu có
        holder.thumbnailJob?.cancel()

        // Tạo job mới để tải thumbnail
        holder.thumbnailJob = coroutineScope.launch {
            // Kiểm tra xem thumbnail đã được cache chưa
            val uri = video.uri.toString()
            var bitmap = thumbnailCache[uri]

            if (bitmap == null) {
                // Tải thumbnail trong background
                bitmap = withContext(Dispatchers.IO) {
                    loadVideoThumbnail(video)
                }

                // Lưu vào cache
                thumbnailCache[uri] = bitmap
            }

            // Hiển thị thumbnail
            if (bitmap != null && isActive) {
                // Sử dụng Glide để hiển thị thumbnail
                Glide.with(holder.itemView.context)
                    .load(bitmap)
                    .apply(RequestOptions()
                        .centerCrop()
                        .placeholder(R.drawable.ic_video_thumbnail)
                    )
                    .into(holder.thumbnail)
            }

            // Tải và hiển thị thời lượng video
            val duration = withContext(Dispatchers.IO) {
                getVideoDuration(video)
            }

            // Hiển thị thời lượng
            if (isActive) {
                holder.duration.text = duration
            }
        }
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

            // Lấy thumbnail từ giây đầu tiên
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

            // Lấy thời lượng
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            // Chuyển đổi sang định dạng phút:giây
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

    override fun getItemCount() = videos.size

    override fun onViewRecycled(holder: VideoViewHolder) {
        super.onViewRecycled(holder)
        // Hủy job tải thumbnail khi view bị recycle
        holder.thumbnailJob?.cancel()
        holder.thumbnailJob = null

        // Xóa hình ảnh để giải phóng bộ nhớ
        holder.thumbnail.setImageDrawable(null)
    }

    fun cleanup() {
        // Hủy bỏ tất cả coroutines đang chạy
        coroutineScope.cancel()
        // Xóa cache
        thumbnailCache.clear()
    }
}