package com.example.quaycamera

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VideoGalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: VideoAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private val videoList = mutableListOf<VideoItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_gallery)
        supportActionBar?.apply {
            title = "Thư viện QUAYVIDEO"
            setDisplayHomeAsUpEnabled(true)
        }

        // Khởi tạo SwipeRefreshLayout
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener {
            refreshVideos()
        }

        // Khởi tạo RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2)

        // Khởi tạo adapter với click listener
        adapter = VideoAdapter(videoList) { videoItem ->
            openVideoPlayer(videoItem)
        }

        recyclerView.adapter = adapter

        // Tải danh sách video
        loadVideos()
    }

    private fun refreshVideos() {
        videoList.clear()
        loadVideos()
        swipeRefreshLayout.isRefreshing = false
    }

    private fun openVideoPlayer(videoItem: VideoItem) {
        try {
            // Mở VideoPlayerActivity với ExoPlayer
            val intent = Intent(this, VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_VIDEO_URI, videoItem.uri.toString())
                putExtra(VideoPlayerActivity.EXTRA_VIDEO_TITLE, videoItem.name)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể mở trình phát video: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun loadVideos() {
        videoList.clear()
        val videoExtensions = arrayOf(".mp4", ".3gp", ".mkv", ".webm")
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10 and above - use MediaStore
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.RELATIVE_PATH
            )
            val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%Movies/QUAYVIDEO%")
            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

            contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val dateAdded = cursor.getLong(dateColumn)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val formattedDate = dateFormat.format(Date(dateAdded * 1000))

                    videoList.add(VideoItem(contentUri, name, formattedDate))
                }
            }

            // Also check app-specific directory
            val privateDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "QUAYVIDEO")
            if (privateDir.exists()) {
                privateDir.listFiles()?.forEach { file ->
                    if (file.isFile && videoExtensions.any { file.name.endsWith(it, ignoreCase = true) }) {
                        val uri = Uri.fromFile(file)
                        val formattedDate = dateFormat.format(Date(file.lastModified()))
                        videoList.add(VideoItem(uri, file.name, formattedDate))
                    }
                }
            }
        } else {
            // For Android 9 and below - use File API
            val publicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "QUAYVIDEO"
            )
            if (publicDir.exists()) {
                publicDir.listFiles()?.forEach { file ->
                    if (file.isFile && videoExtensions.any { file.name.endsWith(it, ignoreCase = true) }) {
                        val uri = Uri.fromFile(file)
                        val formattedDate = dateFormat.format(Date(file.lastModified()))
                        videoList.add(VideoItem(uri, file.name, formattedDate))
                    }
                }
            }

            // Also check app-specific directory
            val privateDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "QUAYVIDEO")
            if (privateDir.exists()) {
                privateDir.listFiles()?.forEach { file ->
                    if (file.isFile && videoExtensions.any { file.name.endsWith(it, ignoreCase = true) }) {
                        val uri = Uri.fromFile(file)
                        val formattedDate = dateFormat.format(Date(file.lastModified()))
                        videoList.add(VideoItem(uri, file.name, formattedDate))
                    }
                }
            }
        }

        if (videoList.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy video nào trong thư mục QUAYVIDEO", Toast.LENGTH_SHORT).show()
        }

        videoList.sortByDescending { it.date }
        adapter.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.gallery_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                refreshVideos()
                true
            }
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        // Giải phóng tài nguyên từ adapter
        if (::adapter.isInitialized) {
            adapter.cleanup()
        }

        // Xóa cache Glide không sử dụng
        Glide.get(this).clearMemory()
    }
}