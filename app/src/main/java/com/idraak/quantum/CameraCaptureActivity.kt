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
 * Manual mode is available only for the "کیمرہ کھولیں" command.
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
                startCamera()
            } else {
                statusView.text = "کیمرے کی اجازت نہیں ملی۔"
                Toast.makeText(
                    this,
                    "تصویر لینے کے لیے کیمرے کی اجازت ضروری ہے۔",
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
                performPhotoCapture()
            } else {
                statusView.text = "Android 9 میں تصویر محفوظ کرنے کے لیے اسٹوریج کی اجازت ضروری ہے۔"
                Toast.makeText(
                    this,
                    "ایپ کی ترتیبات میں اسٹوریج کی اجازت فعال کریں۔",
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
                "ادراک کا کیمرہ شروع ہو رہا ہے..."
            } else {
                "کیمرہ تیار ہو رہا ہے..."
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
                text = "تصویر لیں"
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
            startCamera()
        } else if (PermissionRequestGate.tryMarkRequested(this, Manifest.permission.CAMERA)) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            statusView.text = "کیمرے کی اجازت پہلے مانگی جا چکی ہے۔ فون کی ایپ ترتیبات میں اسے فعال کریں۔"
            Toast.makeText(this, "کیمرے کی اجازت کے لیے فون کی ایپ ترتیبات کھولیں۔", Toast.LENGTH_LONG).show()
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
                    "تصویر خود لی جا رہی ہے..."
                } else {
                    "کیمرہ تیار ہے۔ تصویر لینے کے لیے تصویر لیں کا بٹن دبائیں۔"
                }

                if (autoCapture) {
                    previewView.postDelayed(
                        { capturePhoto() },
                        AUTO_CAPTURE_DELAY_MS
                    )
                }
            } catch (error: Exception) {
                statusView.text = "کیمرہ شروع نہیں ہو سکا۔"
                Toast.makeText(
                    this,
                    "کیمرے میں خرابی: " + (error.localizedMessage ?: "Unknown error"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        if (captureStarted) return

        if (
            android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (
                PermissionRequestGate.tryMarkRequested(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            ) {
                storagePermissionLauncher.launch(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            } else {
                statusView.text = "اسٹوریج کی اجازت پہلے مانگی جا چکی ہے۔ فون کی ایپ ترتیبات میں اسے فعال کریں۔"
                Toast.makeText(
                    this,
                    "Android 9 میں تصویر محفوظ کرنے کے لیے ایپ کی ترتیبات میں اسٹوریج کی اجازت فعال کریں۔",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
            return
        }

        performPhotoCapture()
    }

    private fun performPhotoCapture() {
        if (captureStarted) return

        val captureUseCase = imageCapture
        if (captureUseCase == null) {
            statusView.text = "کیمرہ ابھی تیار نہیں ہے۔"
            return
        }

        captureStarted = true
        statusView.text = "تصویر محفوظ کی جا رہی ہے..."

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
                    statusView.text = "تصویر گیلری کے Pictures/Idraak فولڈر میں محفوظ ہو گئی۔"
                    Toast.makeText(
                        this@CameraCaptureActivity,
                        "تصویر کامیابی سے محفوظ ہو گئی۔",
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
                    statusView.text = "تصویر محفوظ نہیں ہوئی۔ دوبارہ کوشش کریں۔"
                    Toast.makeText(
                        this@CameraCaptureActivity,
                        "تصویر محفوظ کرنے میں خرابی: " + (exception.localizedMessage ?: "Unknown error"),
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
