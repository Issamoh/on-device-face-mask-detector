package com.issamoh.facemaskdetector

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var graphicOverlay: GraphicOverlay
    private lateinit var viewFinder: PreviewView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Request Camera persmissions :
        if(allPermissionsGranted()){
            startCamera()
        }else{
            ActivityCompat.requestPermissions(
                this,REQUIRED_PERMISSIONS,REQUEST_CODE_PERMISSIONS
            )
        }
        cameraExecutor = Executors.newSingleThreadExecutor()

        //an overlay in top of the camera preview in order to draw on it boxes and labels
        graphicOverlay = findViewById(R.id.graphicOverlay)
        viewFinder = findViewById(R.id.viewFinder)
    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }
            //ImageAnalyzer
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)// in order to only one image will be delivered for analysis at a time(from docs)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, FaceAnalyzer(graphicOverlay, lensFacing))
                }

            // Select back camera as a default
            val cameraSelector = lensFacing

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview,imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private var lensFacing = CameraSelector.DEFAULT_BACK_CAMERA

    }
}
private class FaceAnalyzer(val graphicOverlay: GraphicOverlay, val lensFacing: CameraSelector):ImageAnalysis.Analyzer{
    private val detector: FaceDetector
    val overlay = graphicOverlay
   init{
       val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .enableTracking()
        .build()
     detector = FaceDetection.getClient(options)

   }
    @RequiresApi(Build.VERSION_CODES.N)
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageP: ImageProxy) {
        val mediaImage = imageP.getImage()
        if (mediaImage != null) {
            val isImageFlipped = lensFacing.equals(CameraSelector.LENS_FACING_FRONT)
            val rotationDegrees = imageP.imageInfo.rotationDegrees
            if (rotationDegrees == 0 || rotationDegrees == 180) {
                overlay.setImageSourceInfo(
                    imageP.width, imageP.height, isImageFlipped
                )
            } else {
                overlay.setImageSourceInfo(
                    imageP.height, imageP.width, isImageFlipped
                )
            }
            overlay.clear()
            val image = InputImage.fromMediaImage(mediaImage, imageP.imageInfo.rotationDegrees)
            val result = detector.process(image)
                .addOnSuccessListener { faces ->
                  for (face in faces) {
                      Log.d("Face mask detector","one face detected")
                      overlay.add(FaceGraphic(overlay, face))
                    }
                    overlay.postInvalidate()
                    imageP.close()
                }
                .addOnFailureListener { e ->
                    imageP.close()

                }

        }

    }

}