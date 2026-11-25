package com.idraak.quantum

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Visible in-app camera screen.
 * Voice-triggered mode captures once automatically after preview starts.
 * Manual mode is available only for the "open camera" command.
 */
class CameraCaptureActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var statusView: TextView
    private var imageCapture: ImageCapture? = null
    private var autoCapture = false
    private var captureStarted = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            PermissionRequestGate.markResult(this, Manifest.permission.CAMERA, granted)
            if (granted) {
                ensureLegacyStoragePermissionAndStartCamera()
            } else {
                statusView.text = "Camera permission नहीं मिली।"
                Toast.makeText(
                    this,
                    "फोटो लेने के लिए Camera permission जरूरी है।",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            PermissionRequestGate.markResult(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                granted
            )
            if (granted) {
                startCamera()
            } else {
                statusView.text = "Android 9 में फोटो सेव करने के लिए Storage permission जरूरी है।"
                Toast.makeText(
                    this,
                    "App Settings में Storage permission चालू करें।",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        autoCapture = intent.getBooleanExtra(EXTRA_AUTO_CAPTURE, false)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        statusView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 17f
            setPadding(24, 18, 24, 18)
            setBackgroundColor(0xAA101820.toInt())
            text = if (autoCapture) {
                "Idraak कैमरा शुरू हो रहा है..."
            } else {
                "कैमरा तैयार हो रहा है..."
            }
        }

        val root = FrameLayout(this)
        root.addView(
            previewView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(
            statusView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        if (!autoCapture) {
            val captureButton = Button(this).apply {
                text = "TAKE PHOTO"
                setOnClickListener { capturePhoto() }
            }
            val buttonParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
            buttonParams.bottomMargin = 48
            root.addView(captureButton, buttonParams)
        }

        setContentView(root)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            ensureLegacyStoragePermissionAndStartCamera()
        } else if (PermissionRequestGate.tryMarkRequested(this, Manifest.permission.CAMERA)) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            statusView.text = "Camera अनुमति पहले पूछी जा चुकी है। Android App Settings में इसे चालू करें।"
            Toast.makeText(this, "Camera permission के लिए Android App Settings खोलें।", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun ensureLegacyStoragePermissionAndStartCamera() {
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.P) {
            startCamera()
            return
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else if (
            PermissionRequestGate.tryMarkRequested(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        ) {
            storagePermissionLauncher.launch(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        } else {
            statusView.text = "Storage अनुमति पहले पूछी जा चुकी है। Android App Settings में इसे चालू करें।"
            Toast.makeText(
                this,
                "Android 9 में फोटो सेव करने के लिए App Settings में Storage permission चालू करें।",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                val captureUseCase = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    captureUseCase
                )

                imageCapture = captureUseCase
                statusView.text = if (autoCapture) {
                    "फोटो अपने-आप ली जा रही है..."
                } else {
                    "कैमरा तैयार है। फोटो लेने के लिए TAKE PHOTO दबाएँ।"
                }

                if (autoCapture) {
                    previewView.postDelayed(
                        { capturePhoto() },
                        AUTO_CAPTURE_DELAY_MS
                    )
                }
            } catch (error: Exception) {
                statusView.text = "कैमरा शुरू नहीं हो सका।"
                Toast.makeText(
                    this,
                    "Camera error: " + (error.localizedMessage ?: "Unknown error"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        if (captureStarted) return

        val captureUseCase = imageCapture
        if (captureUseCase == null) {
            statusView.text = "कैमरा अभी तैयार नहीं है।"
            return
        }

        captureStarted = true
        statusView.text = "फोटो सेव की जा रही है..."

        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ).format(Date())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IDRAAK_" + timestamp + ".jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/Idraak"
                )
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        captureUseCase.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(
                    outputFileResults: ImageCapture.OutputFileResults
                ) {
                    statusView.text = "फोटो Gallery के Pictures/Idraak फोल्डर में सेव हो गई।"
                    Toast.makeText(
                        this@CameraCaptureActivity,
                        "फोटो सफलतापूर्वक सेव हो गई।",
                        Toast.LENGTH_LONG
                    ).show()

                    if (autoCapture) {
                        Handler(Looper.getMainLooper()).postDelayed(
                            { finish() },
                            1800L
                        )
                    } else {
                        captureStarted = false
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    captureStarted = false
                    statusView.text = "फोटो सेव नहीं हुई। दोबारा कोशिश करें।"
                    Toast.makeText(
                        this@CameraCaptureActivity,
                        "Photo error: " + (exception.localizedMessage ?: "Unknown error"),
                        Toast.LENGTH_LONG
                    ).show()
                    if (autoCapture) {
                        Handler(Looper.getMainLooper()).postDelayed(
                            { finish() },
                            1800L
                        )
                    }
                }
            }
        )
    }

    companion object {
        const val EXTRA_AUTO_CAPTURE =
            "com.idraak.quantum.extra.AUTO_CAPTURE"

        private const val AUTO_CAPTURE_DELAY_MS = 1200L
    }
}
