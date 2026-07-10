package com.example.cameramusicapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Bundle
import android.view.TextureView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.cameramusicapp.audio.MusicVisualizerHelper
import com.example.cameramusicapp.camera.CameraController
import com.example.cameramusicapp.ui.WaveformView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView
    private lateinit var waveformView: WaveformView
    private lateinit var btnPlayRecord: FloatingActionButton
    private lateinit var btnSwitchCamera: FloatingActionButton
    private lateinit var btnSelectMusic: FloatingActionButton
    private lateinit var txtSelectedMusic: TextView
    private lateinit var recordingIndicator: TextView

    private lateinit var cameraController: CameraController
    private lateinit var musicHelper: MusicVisualizerHelper

    private var selectedMusicUri: Uri? = null
    private var isSessionActive = false // play bosilgach true, video+musiqa birga ketmoqda

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            openCameraIfReady()
        } else {
            Toast.makeText(this, getString(R.string.permission_needed), Toast.LENGTH_LONG).show()
        }
    }

    // 2-band: qurilmadan istalgan audio/qo'shiqni tanlash
    private val musicPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedMusicUri = uri
            txtSelectedMusic.text = queryFileName(uri) ?: uri.lastPathSegment ?: "Musiqa tanlandi"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.textureView)
        waveformView = findViewById(R.id.waveformView)
        btnPlayRecord = findViewById(R.id.btnPlayRecord)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        btnSelectMusic = findViewById(R.id.btnSelectMusic)
        txtSelectedMusic = findViewById(R.id.txtSelectedMusic)
        recordingIndicator = findViewById(R.id.recordingIndicator)

        cameraController = CameraController(this, textureView)
        musicHelper = MusicVisualizerHelper(this)

        // 1-band: kamera ochilganda maksimal ruxsatdan foydalanish (CameraController ichida)
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                openCameraIfReady()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }

        // 2-band: chap burchakdagi menyu - musiqa tanlash
        btnSelectMusic.setOnClickListener {
            musicPickerLauncher.launch("audio/*")
        }

        // 3 & 4-band: play bosilganda musiqa + video yozish bir vaqtda boshlanadi
        btnPlayRecord.setOnClickListener {
            if (!isSessionActive) startSession() else stopSession()
        }

        // 6-band: old/orqa kamera almashtirish
        btnSwitchCamera.setOnClickListener {
            cameraController.switchCamera()
        }
    }

    private fun startSession() {
        val uri = selectedMusicUri
        if (uri == null) {
            Toast.makeText(this, "Avval musiqa tanlang", Toast.LENGTH_SHORT).show()
            return
        }

        // 4-band: video yozishni boshlaymiz
        cameraController.startRecording(
            onStarted = { path ->
                runOnUiThread {
                    isSessionActive = true
                    btnPlayRecord.setImageResource(android.R.drawable.ic_media_pause)
                    recordingIndicator.visibility = TextView.VISIBLE
                }
            },
            onError = { e ->
                runOnUiThread {
                    Toast.makeText(this, "Yozishni boshlab bo'lmadi: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        )

        // 3-band: xuddi shu paytda tanlangan musiqa ijro etiladi
        // 5-band: musiqa ovoz to'lqini gorizontal, real vaqtda ko'rinadi
        musicHelper.play(
            uri = uri,
            onAmplitude = { amp ->
                runOnUiThread { waveformView.pushAmplitude(amp) }
            },
            onCompletion = {
                runOnUiThread { stopSession() }
            }
        )
    }

    private fun stopSession() {
        cameraController.stopRecording()
        musicHelper.stop()
        waveformView.clear()
        isSessionActive = false
        btnPlayRecord.setImageResource(android.R.drawable.ic_media_play)
        recordingIndicator.visibility = TextView.INVISIBLE
        cameraController.outputFilePath?.let { path ->
            Toast.makeText(this, "Video saqlandi: $path", Toast.LENGTH_LONG).show()
        }
    }

    private fun openCameraIfReady() {
        if (hasAllPermissions()) {
            cameraController.openCamera()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun hasAllPermissions(): Boolean =
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun queryFileName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun onResume() {
        super.onResume()
        cameraController.startBackgroundThread()
        if (textureView.isAvailable) openCameraIfReady()
    }

    override fun onPause() {
        if (isSessionActive) stopSession()
        cameraController.closeCamera()
        cameraController.stopBackgroundThread()
        super.onPause()
    }
}
