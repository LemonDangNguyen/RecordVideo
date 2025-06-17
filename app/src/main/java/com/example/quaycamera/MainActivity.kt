package com.example.quaycamera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.quaycamera.databinding.ActivityMainBinding
import com.otaliastudios.cameraview.CameraException
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.VideoResult
import com.otaliastudios.cameraview.controls.Flash
import com.otaliastudios.cameraview.controls.Mode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
        //hihi
    }
    private lateinit var binding: ActivityMainBinding
    private var isRecording = false
    private var flashMode = Flash.OFF
    private var recordingStartTime = 0L
    private var tempVideoFile: File? = null
    private val handler = Handler(Looper.getMainLooper())
    private val updateTimerRunnable = object : Runnable {
        override fun run() {
            updateRecordingTime()
            handler.postDelayed(this, 1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.cameraView.setPlaySounds(false)
        initCamera()
        setupButtonListeners()

        if (!allPermissionsGranted()) {
            requestPermissions()
        }

        createQuayvideoDirectory()
    }

    override fun onResume() {
        super.onResume()
        if (!binding.cameraView.isOpened) {
            binding.cameraView.open()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isRecording) {
            stopRecording()
        }
        binding.cameraView.close()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateTimerRunnable)
        binding.cameraView.destroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                showPermissionExplanationDialog()
            }
        }
    }

    private fun initCamera() {
        binding.cameraView.apply {
            mode = Mode.VIDEO
            setLifecycleOwner(this@MainActivity)
        }
        setupCameraCallbacks()
    }

    private fun setupCameraCallbacks() {
        binding.cameraView.addCameraListener(object : CameraListener() {
            override fun onVideoTaken(result: VideoResult) {
                tempVideoFile = result.file
                showFilenameDialog(tempVideoFile!!)
            }

            override fun onVideoRecordingStart() {
                isRecording = true
                recordingStartTime = System.currentTimeMillis()
                binding.recordingTimeText.visibility = View.VISIBLE
                binding.recordingIndicator.visibility = View.VISIBLE
                binding.recordButton.setImageResource(R.drawable.ic_video_stop)
                handler.post(updateTimerRunnable)
            }

            override fun onVideoRecordingEnd() {
                resetRecordingUI()
            }

            override fun onCameraError(exception: CameraException) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.error_recording) + ": ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
                resetRecordingUI()
            }
        })
    }

    private fun setupButtonListeners() {
        binding.recordButton.setOnClickListener {
            if (isRecording) stopRecording() else startRecording()
        }

        binding.flipCameraButton.setOnClickListener {
            binding.cameraView.toggleFacing()
        }

        binding.flashButton.setOnClickListener {
            toggleFlash()
        }

        binding.galleryButton.setOnClickListener {
            openGallery()
        }
    }

    private fun startRecording() {
        if (!allPermissionsGranted()) {
            requestPermissions()
            return
        }

        val videoFile = createTempVideoFile()
        binding.cameraView.takeVideo(videoFile)
    }

    private fun stopRecording() {
        binding.cameraView.stopVideo()
    }

    private fun resetRecordingUI() {
        isRecording = false
        binding.recordButton.setImageResource(R.drawable.ic_video_record)
        binding.recordingTimeText.visibility = View.INVISIBLE
        binding.recordingIndicator.visibility = View.INVISIBLE
        handler.removeCallbacks(updateTimerRunnable)
    }

    private fun updateRecordingTime() {
        val elapsedTime = System.currentTimeMillis() - recordingStartTime
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTime)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime) -
                TimeUnit.MINUTES.toSeconds(minutes)
        val timeStr = String.format("%02d:%02d", minutes, seconds)
        binding.recordingTimeText.text = timeStr
    }

    private fun createQuayvideoDirectory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return
        } else {
            val quayVideoDir = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES), "QUAYVIDEO")
            if (!quayVideoDir.exists()) {
                val success = quayVideoDir.mkdirs()
                if (!success) {
                    Toast.makeText(this, "Cannot create QUAYVIDEO directory",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createTempVideoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "TEMP_$timeStamp.mp4"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val quayVideoDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "QUAYVIDEO")
            if (!quayVideoDir.exists()) {
                quayVideoDir.mkdirs()
            }
            File(quayVideoDir, fileName)
        } else {
            val storageDir = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES), "QUAYVIDEO")
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            File(storageDir, fileName)
        }
    }

    private fun showFilenameDialog(tempFile: File) {
        val editText = EditText(this)
        editText.hint = "Enter video filename"
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        editText.setText("VIDEO_$timeStamp")

        AlertDialog.Builder(this)
            .setTitle("Save Video")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val filename = editText.text.toString().trim()
                if (filename.isEmpty()) {
                    Toast.makeText(this, "Filename cannot be empty", Toast.LENGTH_SHORT).show()
                    saveVideo(tempFile, "VIDEO_$timeStamp")
                } else {
                    saveVideo(tempFile, filename)
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                tempFile.delete()
                Toast.makeText(this, "Video recording canceled", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun saveVideo(tempFile: File, filename: String) {
        val finalFilename = if (!filename.lowercase(Locale.getDefault()).endsWith(".mp4")) {
            "$filename.mp4"
        } else {
            filename
        }

        val finalFile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val quayVideoDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "QUAYVIDEO")
            if (!quayVideoDir.exists()) {
                quayVideoDir.mkdirs()
            }
            File(quayVideoDir, finalFilename)
        } else {
            val storageDir = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES), "QUAYVIDEO")
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            File(storageDir, finalFilename)
        }
        if (finalFile.exists()) {
            finalFile.delete()
        }
        val success = tempFile.renameTo(finalFile)

        if (success) {
            addVideoToGallery(finalFile)
            val message = getString(R.string.video_saved, finalFile.absolutePath)
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Failed to save video with custom name", Toast.LENGTH_SHORT).show()
            addVideoToGallery(tempFile)
        }
    }

    private fun addVideoToGallery(videoFile: File) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + File.separator + "QUAYVIDEO")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                put(MediaStore.Video.Media.DATA, videoFile.absolutePath)
            }
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            uri?.let {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                contentResolver.update(it, values, null, null)
            }
        }
    }

    private fun toggleFlash() {
        flashMode = when (flashMode) {
            Flash.OFF -> {
                binding.flashButton.setImageResource(R.drawable.ic_flash_on)
                Flash.TORCH
            }
            else -> {
                binding.flashButton.setImageResource(R.drawable.ic_flash_off)
                Flash.OFF
            }
        }
        binding.cameraView.flash = flashMode
    }

    private fun openGallery() {
        val intent = Intent(this, VideoGalleryActivity::class.java)
        startActivity(intent)
    }
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_required)
            .setMessage(R.string.permission_message)
            .setPositiveButton(R.string.grant_permission) { _, _ ->
                requestPermissions()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
}