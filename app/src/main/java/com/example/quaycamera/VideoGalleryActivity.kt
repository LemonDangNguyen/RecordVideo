package com.example.quaycamera

import android.app.AlertDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
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

        setupActionBar()
        setupSwipeRefresh()
        setupRecyclerView()
        loadVideos()
    }

    private fun setupActionBar() {
        supportActionBar?.apply {
            title = "QUAYVIDEO Library"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener {
            refreshVideos()
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2)

        adapter = VideoAdapter(
            videoList,
            onItemClick = { videoItem ->
                openVideoPlayer(videoItem)
            },
            onItemLongClick = { videoItem ->
                showVideoOptionsDialog(videoItem)
            }
        )

        recyclerView.adapter = adapter
    }

    private fun refreshVideos() {
        videoList.clear()
        loadVideos()
        swipeRefreshLayout.isRefreshing = false
    }

    private fun openVideoPlayer(videoItem: VideoItem) {
        try {
            val intent = Intent(this, VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_VIDEO_URI, videoItem.uri.toString())
                putExtra(VideoPlayerActivity.EXTRA_VIDEO_TITLE, videoItem.name)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open video player: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun showVideoOptionsDialog(videoItem: VideoItem) {
        val options = arrayOf(
            "Show file in storage folder",
            "Rename"
        )

        AlertDialog.Builder(this)
            .setTitle(videoItem.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showFileInFolder(videoItem)
                    1 -> showRenameDialog(videoItem)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFileInFolder(videoItem: VideoItem) {
        try {
            when (videoItem.uri.scheme) {
                "file" -> {
                    val file = File(videoItem.uri.path ?: return)
                    val parentDir = file.parentFile ?: return

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.fromFile(parentDir), "resource/folder")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }

                    val chooser = Intent.createChooser(intent, "Choose app to open folder")
                    if (chooser.resolveActivity(packageManager) != null) {
                        startActivity(chooser)
                    } else {
                        openFolderAlternative(videoItem)
                    }
                }
                "content" -> {
                    openWithDocumentsUI(videoItem)
                }
                else -> {
                    Toast.makeText(this, "Cannot open folder for this file type", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open folder: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun openFolderAlternative(videoItem: VideoItem) {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(Intent.createChooser(intent, "Open file manager"))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open file manager", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWithDocumentsUI(videoItem: VideoItem) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = videoItem.uri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "video/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivity(Intent.createChooser(fallbackIntent, "Choose app"))
            } catch (ex: Exception) {
                Toast.makeText(this, "Cannot open file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRenameDialog(videoItem: VideoItem) {
        val editText = EditText(this).apply {
            setText(getFileNameWithoutExtension(videoItem.name))
            selectAll()
            setPadding(50, 30, 50, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("Rename video")
            .setMessage("Enter new name for video:")
            .setView(editText)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    if (isValidFileName(newName)) {
                        renameVideo(videoItem, newName)
                    } else {
                        Toast.makeText(this, "File name contains invalid characters", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "File name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun isValidFileName(fileName: String): Boolean {
        val invalidChars = arrayOf("/", "\\", ":", "*", "?", "\"", "<", ">", "|")
        return !invalidChars.any { fileName.contains(it) }
    }

    private fun getFileNameWithoutExtension(fileName: String): String {
        val lastDotIndex = fileName.lastIndexOf('.')
        return if (lastDotIndex > 0) {
            fileName.substring(0, lastDotIndex)
        } else {
            fileName
        }
    }

    private fun getFileExtension(fileName: String): String {
        val lastDotIndex = fileName.lastIndexOf('.')
        return if (lastDotIndex > 0) {
            fileName.substring(lastDotIndex)
        } else {
            ""
        }
    }

    private fun renameVideo(videoItem: VideoItem, newName: String) {
        try {
            val extension = getFileExtension(videoItem.name)
            val fullNewName = "$newName$extension"

            when (videoItem.uri.scheme) {
                "file" -> {
                    renameFileDirectly(videoItem, fullNewName)
                }
                "content" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        renameVideoViaMediaStore(videoItem, fullNewName)
                    } else {
                        Toast.makeText(this, "Cannot rename this file on this Android version", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    Toast.makeText(this, "Cannot rename this file type", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error renaming: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun renameFileDirectly(videoItem: VideoItem, fullNewName: String) {
        try {
            val oldFile = File(videoItem.uri.path ?: return)
            val newFile = File(oldFile.parent, fullNewName)

            if (newFile.exists()) {
                Toast.makeText(this, "File with this name already exists", Toast.LENGTH_SHORT).show()
                return
            }

            if (oldFile.renameTo(newFile)) {
                Toast.makeText(this, "Renamed successfully", Toast.LENGTH_SHORT).show()
                refreshVideos()
            } else {
                Toast.makeText(this, "Cannot rename file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error renaming file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renameVideoViaMediaStore(videoItem: VideoItem, newName: String) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, newName)
            }

            val rowsUpdated = contentResolver.update(
                videoItem.uri,
                contentValues,
                null,
                null
            )

            if (rowsUpdated > 0) {
                Toast.makeText(this, "Renamed successfully", Toast.LENGTH_SHORT).show()
                refreshVideos()
            } else {
                Toast.makeText(this, "Cannot rename file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error renaming via MediaStore: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun loadVideos() {
        videoList.clear()
        val videoExtensions = arrayOf(".mp4", ".3gp", ".mkv", ".webm", ".avi", ".mov")
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            loadVideosFromMediaStore(dateFormat)
        } else {
            loadVideosFromFileSystem(videoExtensions, dateFormat)
        }

        loadVideosFromAppDirectory(videoExtensions, dateFormat)

        if (videoList.isEmpty()) {
            Toast.makeText(this, "No videos found in QUAYVIDEO folder", Toast.LENGTH_SHORT).show()
        }

        videoList.sortByDescending {
            try {
                dateFormat.parse(it.date)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }

        adapter.notifyDataSetChanged()
    }

    private fun loadVideosFromMediaStore(dateFormat: SimpleDateFormat) {
        try {
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadVideosFromFileSystem(videoExtensions: Array<String>, dateFormat: SimpleDateFormat) {
        try {
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadVideosFromAppDirectory(videoExtensions: Array<String>, dateFormat: SimpleDateFormat) {
        try {
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        if (::adapter.isInitialized) {
            adapter.cleanup()
        }
        Glide.get(this).clearMemory()
    }
}