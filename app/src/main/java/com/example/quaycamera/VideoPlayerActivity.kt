package com.example.quaycamera

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var player: ExoPlayer
    private lateinit var titleTextView: TextView
    private lateinit var backButton: ImageButton

    private var playWhenReady = true
    private var currentPosition = 0L
    private var currentMediaItem = 0

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        // Ẩn ActionBar vì đây là trình phát video toàn màn hình
        supportActionBar?.hide()

        // Ánh xạ các view
        playerView = findViewById(R.id.player_view)
        titleTextView = findViewById(R.id.video_title)
        backButton = findViewById(R.id.back_button)

        // Xử lý nút quay lại
        backButton.setOnClickListener {
            onBackPressed()
        }

        // Lấy thông tin video từ intent
        val videoUriString = intent.getStringExtra(EXTRA_VIDEO_URI)
        val videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "Video"

        // Hiển thị tiêu đề
        titleTextView.text = videoTitle

        // Khởi tạo ExoPlayer
        if (videoUriString != null) {
            initializePlayer(Uri.parse(videoUriString))
        } else {
            Toast.makeText(this, "Không thể phát video", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun initializePlayer(videoUri: Uri) {
        try {
            // Tạo một ExoPlayer instance
            player = ExoPlayer.Builder(this).build()

            // Kết nối ExoPlayer với PlayerView
            playerView.player = player

            // Thiết lập MediaItem từ URI
            val mediaItem = MediaItem.fromUri(videoUri)
            player.setMediaItem(mediaItem)

            // Thiết lập các listener
            player.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    // Xử lý lỗi phát
                    Toast.makeText(
                        this@VideoPlayerActivity,
                        "Lỗi phát video: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        // Video đã sẵn sàng để phát
                        playerView.hideController()
                    } else if (state == Player.STATE_ENDED) {
                        // Video đã kết thúc
                        playerView.showController()
                    }
                }
            })

            // Chuẩn bị và phát video
            player.prepare()
            player.playWhenReady = playWhenReady
            player.seekTo(currentMediaItem, currentPosition)

        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi khởi tạo trình phát: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun releasePlayer() {
        // Lưu trạng thái hiện tại
        playWhenReady = player.playWhenReady
        currentPosition = player.currentPosition
        currentMediaItem = player.currentMediaItemIndex

        // Giải phóng player
        player.release()
    }

    override fun onPause() {
        super.onPause()
        releasePlayer()
    }

    override fun onResume() {
        super.onResume()
        val videoUriString = intent.getStringExtra(EXTRA_VIDEO_URI)
        if (videoUriString != null && !::player.isInitialized) {
            initializePlayer(Uri.parse(videoUriString))
        }
    }

    override fun onStop() {
        super.onStop()
        if (::player.isInitialized) {
            releasePlayer()
        }
    }

    // Xử lý chuyển sang chế độ toàn màn hình khi xoay màn hình
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }
    }
}