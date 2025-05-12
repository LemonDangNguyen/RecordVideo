package com.example.quaycamera

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VideoGalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: VideoAdapter
    private val videoList = mutableListOf<VideoItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_gallery)
        supportActionBar?.apply {
            title = "Thư viện QUAYVIDEO"
            setDisplayHomeAsUpEnabled(true)
        }

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        adapter = VideoAdapter(videoList) { videoItem ->
            try {
                val intent = android.content.Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(videoItem.uri, "video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Không thể phát video", Toast.LENGTH_SHORT).show()
            }
        }
        recyclerView.adapter = adapter
        loadVideos()
    }

    private fun loadVideos() {
        videoList.clear()
        val videoExtensions = arrayOf(".mp4", ".3gp", ".mkv", ".webm")
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
            val privateDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "QUAYVIDEO")
            if (privateDir.exists()) {
                privateDir.listFiles()?.forEach { file ->
                    if (file.isFile && videoExtensions.any { file.name.endsWith(it) }) {
                        val uri = Uri.fromFile(file)
                        val formattedDate = dateFormat.format(Date(file.lastModified()))
                        videoList.add(VideoItem(uri, file.name, formattedDate))
                    }
                }
            }

        } else {
            val publicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "QUAYVIDEO"
            )
            if (publicDir.exists()) {
                publicDir.listFiles()?.forEach { file ->
                    if (file.isFile && videoExtensions.any { file.name.endsWith(it) }) {
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
