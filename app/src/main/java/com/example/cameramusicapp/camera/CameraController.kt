package com.example.cameramusicapp.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * Camera2 API yordamida:
 *  - Kameraning eng yuqori (maksimal) video ruxsatidan foydalanish
 *  - Old/orqa kamerani almashtirish
 *  - MediaRecorder.AudioSource.UNPROCESSED orqali AI shovqin bostirish/AGC
 *    ta'sirisiz XOM (raw) audio yozib olish
 *  - Video va audioni real vaqtda bitta faylga sinxron yozish
 */
class CameraController(
    private val context: Context,
    private val textureView: TextureView
) {
    companion object {
        private const val TAG = "CameraController"
    }

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var mediaRecorder: MediaRecorder? = null
    private var previewSize: Size = Size(1920, 1080)

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /** Hozir CameraCharacteristics.LENS_FACING_BACK yoki LENS_FACING_FRONT */
    private var currentLensFacing: Int = CameraCharacteristics.LENS_FACING_BACK

    var isRecording = false
        private set

    var outputFilePath: String? = null
        private set

    fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Background thread to'xtatishda xatolik", e)
        }
    }

    /** Kamerani ochadi va preview boshlaydi (yozib olishsiz). */
    @Suppress("MissingPermission")
    fun openCamera(lensFacing: Int = currentLensFacing) {
        currentLensFacing = lensFacing
        val cameraId = findCameraId(lensFacing) ?: return
        previewSize = chooseMaxVideoSize(cameraId)

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                startPreviewSession()
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                cameraDevice = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(TAG, "Kamera xatosi: $error")
                camera.close()
                cameraDevice = null
            }
        }, backgroundHandler)
    }

    /** Berilgan lens uchun kamera ID sini topadi. */
    private fun findCameraId(lensFacing: Int): String? {
        for (id in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.LENS_FACING) == lensFacing) {
                return id
            }
        }
        return cameraManager.cameraIdList.firstOrNull()
    }

    /**
     * StreamConfigurationMap orqali qo'llab-quvvatlanadigan eng yuqori
     * video ruxsatni (masalan 4K, agar mavjud bo'lsa) tanlaydi.
     */
    private fun chooseMaxVideoSize(cameraId: String): Size {
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        val map: StreamConfigurationMap =
            chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return Size(1920, 1080)

        val sizes = map.getOutputSizes(MediaRecorder::class.java) ?: return Size(1920, 1080)
        // Eng katta pikselli o'lchamni tanlaymiz (masalan 3840x2160, mavjud bo'lsa)
        return sizes.maxByOrNull { it.width.toLong() * it.height.toLong() } ?: Size(1920, 1080)
    }

    private fun startPreviewSession() {
        val device = cameraDevice ?: return
        val texture: SurfaceTexture = textureView.surfaceTexture ?: return
        texture.setDefaultBufferSize(previewSize.width, previewSize.height)
        val previewSurface = Surface(texture)

        val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        requestBuilder.addTarget(previewSurface)

        device.createCaptureSession(
            listOf(previewSurface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    requestBuilder.set(
                        CaptureRequest.CONTROL_MODE,
                        CameraMetadata.CONTROL_MODE_AUTO
                    )
                    session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Preview sessiyasini sozlab bo'lmadi")
                }
            },
            backgroundHandler
        )
    }

    /**
     * Video + XOM audio yozishni boshlaydi.
     * AudioSource.UNPROCESSED - qurilma qo'llab-quvvatlasa, AI shovqin
     * bostirish / AGC / AEC ishlatilmasdan mikrofondan xom signal oladi.
     */
    fun startRecording(onStarted: (String) -> Unit, onError: (Exception) -> Unit) {
        val device = cameraDevice ?: return
        try {
            val outputFile = createOutputFile()
            outputFilePath = outputFile.absolutePath

            val recorder = MediaRecorder()
            // Diqqat: audio source tartibi muhim - avval audio, keyin video source
            setRawAudioSource(recorder)
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setOutputFile(outputFile.absolutePath)
            recorder.setVideoEncodingBitRate(16_000_000)
            recorder.setVideoFrameRate(30)
            recorder.setVideoSize(previewSize.width, previewSize.height)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(44100)
            recorder.setAudioEncodingBitRate(192_000)
            recorder.prepare()

            val texture = textureView.surfaceTexture ?: throw IllegalStateException("Texture yo'q")
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
            val previewSurface = Surface(texture)
            val recorderSurface = recorder.surface

            val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            requestBuilder.addTarget(previewSurface)
            requestBuilder.addTarget(recorderSurface)

            captureSession?.close()
            device.createCaptureSession(
                listOf(previewSurface, recorderSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
                        mediaRecorder = recorder
                        try {
                            recorder.start()
                            isRecording = true
                            onStarted(outputFile.absolutePath)
                        } catch (e: Exception) {
                            onError(e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        onError(IllegalStateException("Yozib olish sessiyasini sozlab bo'lmadi"))
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * Xom (unprocessed) audio manbasini sozlaydi. Agar qurilma
     * UNPROCESSED ni qo'llab-quvvatlamasa, tizim avtomatik CAMCORDER ga
     * tushadi (u ham deyarli xom signalga yaqin, AI post-processingsiz).
     */
    private fun setRawAudioSource(recorder: MediaRecorder) {
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
        } catch (e: Exception) {
            Log.w(TAG, "UNPROCESSED mavjud emas, CAMCORDER manbasiga o'tildi")
            recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Yozishni to'xtatishda xatolik", e)
        } finally {
            mediaRecorder = null
            isRecording = false
            // Yozishdan keyin oddiy previewga qaytamiz
            startPreviewSession()
        }
    }

    /** Old <-> orqa kamerani almashtiradi. */
    fun switchCamera() {
        val wasRecording = isRecording
        if (wasRecording) stopRecording()

        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null

        val newLensFacing = if (currentLensFacing == CameraCharacteristics.LENS_FACING_BACK)
            CameraCharacteristics.LENS_FACING_FRONT
        else
            CameraCharacteristics.LENS_FACING_BACK

        openCamera(newLensFacing)
    }

    fun closeCamera() {
        try {
            if (isRecording) stopRecording()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            Log.e(TAG, "Kamerani yopishda xatolik", e)
        }
    }

    private fun createOutputFile(): File {
        val dir = File(context.getExternalFilesDir(null), "CameraMusicApp").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(dir, "VID_$timeStamp.mp4")
    }
}
