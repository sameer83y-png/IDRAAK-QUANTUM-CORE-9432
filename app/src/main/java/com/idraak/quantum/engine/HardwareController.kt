
package com.idraak.quantum.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.core.content.ContextCompat

/**
 * Idraak Hardware Controller
 *
 * Handles supported phone hardware operations.
 * Current implementation: camera flashlight.
 *
 * CAMERA permission must be granted by the user
 * before the torch can be controlled.
 */
object HardwareController {

    data class Result(
        val success: Boolean,
        val message: String,
        val permissionRequired: Boolean = false
    )

    fun setTorch(
        context: Context,
        enabled: Boolean
    ): Result {
        val appContext = context.applicationContext

        val permissionGranted =
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            return Result(
                success = false,
                message = "टॉर्च चलाने के लिए Camera permission जरूरी है।",
                permissionRequired = true
            )
        }

        return try {
            val cameraManager =
                appContext.getSystemService(
                    Context.CAMERA_SERVICE
                ) as? CameraManager
                    ?: return Result(
                        false,
                        "फोन का Camera Hardware उपलब्ध नहीं है।"
                    )

            val cameraId = cameraManager.cameraIdList
                .firstOrNull { id ->
                    val characteristics =
                        cameraManager.getCameraCharacteristics(id)

                    val hasFlash =
                        characteristics.get(
                            CameraCharacteristics.FLASH_INFO_AVAILABLE
                        ) == true

                    val isBackCamera =
                        characteristics.get(
                            CameraCharacteristics.LENS_FACING
                        ) == CameraCharacteristics.LENS_FACING_BACK

                    hasFlash && isBackCamera
                }
                ?: return Result(
                    false,
                    "आपके फोन में समर्थित Flashlight नहीं मिली।"
                )

            cameraManager.setTorchMode(
                cameraId,
                enabled
            )

            Result(
                success = true,
                message = if (enabled) {
                    "टॉर्च चालू कर दी गई है।"
                } else {
                    "टॉर्च बंद कर दी गई है।"
                }
            )

        } catch (_: SecurityException) {
            Result(
                false,
                "Camera permission नहीं मिली।"
            )
        } catch (_: CameraAccessException) {
            Result(
                false,
                "फोन का Camera Hardware अभी उपलब्ध नहीं है।"
            )
        } catch (error: Exception) {
            Result(
                false,
                "टॉर्च कंट्रोल में समस्या आई: ${
                    error.localizedMessage ?: "Unknown error"
                }"
            )
        }
    }
}
